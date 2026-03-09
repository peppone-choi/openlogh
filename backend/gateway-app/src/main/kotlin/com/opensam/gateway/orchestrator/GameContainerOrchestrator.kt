package com.opensam.gateway.orchestrator

import com.opensam.gateway.dto.AttachWorldProcessRequest
import com.opensam.gateway.dto.GameInstanceStatus
import com.opensam.gateway.service.WorldRouteRegistry
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
@ConditionalOnProperty("gateway.docker.enabled", havingValue = "true")
class GameContainerOrchestrator(
    private val worldRouteRegistry: WorldRouteRegistry,
    @Value("\${gateway.orchestrator.health-timeout-ms:120000}")
    private val healthTimeoutMs: Long,
    @Value("\${gateway.docker.network:opensam-net}")
    private val dockerNetwork: String,
    @Value("\${gateway.docker.image-prefix:opensam/game-app}")
    private val imagePrefix: String,
    @Value("\${DB_HOST:postgres}")
    private val dbHost: String,
    @Value("\${DB_PORT:5432}")
    private val dbPort: String,
    @Value("\${DB_NAME:opensam}")
    private val dbName: String,
    @Value("\${DB_USER:opensam}")
    private val dbUser: String,
    @Value("\${DB_PASSWORD:opensam123}")
    private val dbPassword: String,
    @Value("\${REDIS_HOST:redis}")
    private val redisHost: String,
    @Value("\${REDIS_PORT:6379}")
    private val redisPort: String,
) : GameOrchestrator {

    private val log = LoggerFactory.getLogger(GameContainerOrchestrator::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    private data class ManagedDockerInstance(
        val commitSha: String,
        val gameVersion: String,
        val imageTag: String,
        val containerName: String,
        val containerId: String,
        val worldIds: MutableSet<Long>,
    )

    private data class StartingEntry(
        val commitSha: String,
        val gameVersion: String,
        val imageTag: String,
        val containerName: String,
        val retainedWorldIds: Set<Long>,
        val future: CompletableFuture<ManagedDockerInstance>,
    )

    private sealed interface InstanceResult {
        data class Running(val instance: ManagedDockerInstance) : InstanceResult
        data class Pending(val future: CompletableFuture<ManagedDockerInstance>) : InstanceResult
    }

    private sealed interface PhaseOneResult {
        data class Running(val instance: ManagedDockerInstance) : PhaseOneResult
        data class Pending(val future: CompletableFuture<ManagedDockerInstance>) : PhaseOneResult
        data class StartNew(val entry: StartingEntry) : PhaseOneResult
    }

    private val lock = ReentrantLock()
    private val instances = ConcurrentHashMap<String, ManagedDockerInstance>()
    private val starting = ConcurrentHashMap<String, CompletableFuture<ManagedDockerInstance>>()
    private val startingNames = ConcurrentHashMap<String, String>()

    override fun attachWorld(worldId: Long, request: AttachWorldProcessRequest): GameInstanceStatus {
        val gameVersion = resolveGameVersion(request)
        require(gameVersion.isNotEmpty()) { "gameVersion is required" }

        ensureVersion(request)

        return lock.withLock {
            cleanupDeadInstances()

            val currentlyAssigned = instances.values.firstOrNull { worldId in it.worldIds }
            if (currentlyAssigned != null && currentlyAssigned.gameVersion != gameVersion) {
                currentlyAssigned.worldIds.remove(worldId)
                if (currentlyAssigned.worldIds.isEmpty()) {
                    stopContainer(currentlyAssigned)
                    instances.remove(currentlyAssigned.gameVersion)
                }
            }

            val managed = instances[gameVersion]
                ?: throw IllegalStateException("Game container not found for gameVersion=$gameVersion")
            managed.worldIds.add(worldId)
            worldRouteRegistry.attach(worldId, containerBaseUrl(managed.containerName))
            toStatus(managed)
        }
    }

    override fun ensureVersion(request: AttachWorldProcessRequest): GameInstanceStatus {
        val gameVersion = resolveGameVersion(request)
        require(gameVersion.isNotEmpty()) { "gameVersion is required" }

        val result = getOrStartInstance(request)
        val managed = when (result) {
            is InstanceResult.Running -> result.instance
            is InstanceResult.Pending -> awaitStartup(result.future)
        }
        return lock.withLock { toStatus(managed) }
    }

    override fun detachWorld(worldId: Long): Boolean {
        return lock.withLock {
            cleanupDeadInstances()

            val entry = instances.entries.firstOrNull { (_, instance) -> worldId in instance.worldIds }
                ?: return false

            val managed = entry.value
            managed.worldIds.remove(worldId)
            worldRouteRegistry.detach(worldId)

            if (managed.worldIds.isEmpty()) {
                stopContainer(managed)
                instances.remove(managed.gameVersion)
            }

            true
        }
    }

    override fun stopVersion(gameVersion: String): Boolean {
        return lock.withLock {
            val managed = instances[gameVersion] ?: return false
            managed.worldIds.forEach { worldRouteRegistry.detach(it) }
            stopContainer(managed)
            instances.remove(gameVersion)
            true
        }
    }

    override fun statuses(): List<GameInstanceStatus> {
        return lock.withLock {
            cleanupDeadInstances()
            instances.values
                .map { toStatus(it) }
                .sortedBy { it.gameVersion }
        }
    }

    @PreDestroy
    override fun shutdownAll() {
        lock.withLock {
            instances.values.forEach { stopContainer(it) }
            instances.clear()
            starting.clear()
            startingNames.clear()
        }
    }

    private fun getOrStartInstance(request: AttachWorldProcessRequest): InstanceResult {
        val gameVersion = resolveGameVersion(request)

        val phaseOne = lock.withLock {
            cleanupDeadInstances()

            val existing = instances[gameVersion]
            if (existing != null && dockerIsRunning(existing.containerName)) {
                return@withLock PhaseOneResult.Running(existing)
            }

            val inFlight = starting[gameVersion]
            if (inFlight != null) {
                return@withLock PhaseOneResult.Pending(inFlight)
            }

            if (existing != null) {
                val retainedWorldIds = existing.worldIds.toSet()
                instances.remove(gameVersion)
                log.warn("Restarting dead container for gameVersion={}", gameVersion)
                dockerRemove(existing.containerName)
                return@withLock PhaseOneResult.StartNew(createStartingEntry(request, retainedWorldIds))
            }

            PhaseOneResult.StartNew(createStartingEntry(request, emptySet()))
        }

        when (phaseOne) {
            is PhaseOneResult.Running -> return InstanceResult.Running(phaseOne.instance)
            is PhaseOneResult.Pending -> return InstanceResult.Pending(phaseOne.future)
            is PhaseOneResult.StartNew -> {
                val entry = phaseOne.entry
                try {
                    val managed = startNewContainer(entry)
                    lock.withLock {
                        completeStartup(entry, managed)
                    }
                } catch (ex: Exception) {
                    lock.withLock {
                        failStartup(entry)
                    }
                    throw ex
                }
                return InstanceResult.Pending(entry.future)
            }
        }
    }

    private fun createStartingEntry(
        request: AttachWorldProcessRequest,
        retainedWorldIds: Set<Long>,
    ): StartingEntry {
        val commitSha = request.commitSha.trim().ifEmpty { "local" }
        val gameVersion = resolveGameVersion(request)
        val imageTag = request.imageTag?.takeIf { it.isNotBlank() } ?: "$imagePrefix:$gameVersion"
        val cName = containerName(gameVersion)
        val future = CompletableFuture<ManagedDockerInstance>()
        starting[gameVersion] = future
        startingNames[gameVersion] = cName

        return StartingEntry(
            commitSha = commitSha,
            gameVersion = gameVersion,
            imageTag = imageTag,
            containerName = cName,
            retainedWorldIds = retainedWorldIds,
            future = future,
        )
    }

    private fun startNewContainer(entry: StartingEntry): ManagedDockerInstance {
        dockerRemove(entry.containerName)

        val command = listOf(
            "docker", "run", "-d",
            "--name", entry.containerName,
            "--network", dockerNetwork,
            "--label", "opensam.role=game",
            "-e", "SERVER_PORT=9001",
            "-e", "SPRING_PROFILES_ACTIVE=docker",
            "-e", "GAME_COMMIT_SHA=${entry.commitSha}",
            "-e", "GAME_VERSION=${entry.gameVersion}",
            "-e", "DB_HOST=$dbHost",
            "-e", "DB_PORT=$dbPort",
            "-e", "DB_NAME=$dbName",
            "-e", "DB_USER=$dbUser",
            "-e", "DB_PASSWORD=$dbPassword",
            "-e", "REDIS_HOST=$redisHost",
            "-e", "REDIS_PORT=$redisPort",
            entry.imageTag,
        )

        log.info("Starting container: {} image={}", entry.containerName, entry.imageTag)

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor(30, TimeUnit.SECONDS)
        if (!exitCode || process.exitValue() != 0) {
            throw IllegalStateException("docker run failed for ${entry.containerName}: $output")
        }

        val containerId = output.take(12)

        waitForHealth(entry.containerName)

        log.info("Container started: {} id={}", entry.containerName, containerId)
        return ManagedDockerInstance(
            commitSha = entry.commitSha,
            gameVersion = entry.gameVersion,
            imageTag = entry.imageTag,
            containerName = entry.containerName,
            containerId = containerId,
            worldIds = mutableSetOf(),
        )
    }

    private fun completeStartup(entry: StartingEntry, managed: ManagedDockerInstance) {
        starting.remove(entry.gameVersion, entry.future)
        startingNames.remove(entry.gameVersion, entry.containerName)
        managed.worldIds.addAll(entry.retainedWorldIds)
        instances[entry.gameVersion] = managed
        entry.retainedWorldIds.forEach { worldRouteRegistry.attach(it, containerBaseUrl(managed.containerName)) }
        entry.future.complete(managed)
    }

    private fun failStartup(entry: StartingEntry) {
        starting.remove(entry.gameVersion, entry.future)
        startingNames.remove(entry.gameVersion, entry.containerName)
        entry.future.completeExceptionally(
            IllegalStateException("Failed to start container for gameVersion=${entry.gameVersion}"),
        )
    }

    private fun awaitStartup(future: CompletableFuture<ManagedDockerInstance>): ManagedDockerInstance {
        return try {
            future.get()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for container startup", ex)
        } catch (ex: ExecutionException) {
            val cause = ex.cause
            if (cause is RuntimeException) {
                throw cause
            }
            throw IllegalStateException("Failed to start game container", cause ?: ex)
        }
    }

    private fun cleanupDeadInstances() {
        val deadInstances = instances.values.filter { !dockerIsRunning(it.containerName) }
        deadInstances.forEach { instance ->
            instance.worldIds.forEach { worldRouteRegistry.detach(it) }
            instances.remove(instance.gameVersion)
            log.warn("Removed dead container gameVersion={} name={}", instance.gameVersion, instance.containerName)
        }
        cleanupOrphanGameContainers()
    }

    private fun cleanupOrphanGameContainers() {
        try {
            val process = ProcessBuilder(
                "docker", "ps", "-a",
                "--filter", "label=$ROLE_LABEL=$ROLE_GAME",
                "--format", "{{.Names}}",
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(10, TimeUnit.SECONDS)
            if (process.exitValue() != 0 || output.isBlank()) return

            val trackedNames = instances.values.map { it.containerName }.toMutableSet()
            trackedNames.addAll(startingNames.values)
            output.lines()
                .filter { it.isNotBlank() && it !in trackedNames }
                .forEach { orphan ->
                    log.warn("Removing orphan game container: {}", orphan)
                    dockerRemove(orphan)
                }
        } catch (_: Exception) {
            // best-effort cleanup
        }
    }

    private fun stopContainer(instance: ManagedDockerInstance) {
        log.info("Stopping container: {}", instance.containerName)
        runDockerCommand("docker", "stop", instance.containerName)
        dockerRemove(instance.containerName)
    }

    private fun dockerRemove(containerName: String) {
        runDockerCommand("docker", "rm", "-f", containerName)
    }

    private fun dockerIsRunning(containerName: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "docker", "inspect", "-f",
                "{{.State.Running}}|{{index .Config.Labels \"opensam.role\"}}",
                containerName,
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(5, TimeUnit.SECONDS)
            if (process.exitValue() != 0) return false
            val parts = output.split("|", limit = 2)
            val running = parts.getOrElse(0) { "false" } == "true"
            val role = parts.getOrElse(1) { "" }
            if (running && role != ROLE_GAME) {
                log.warn("Container {} is running but has role='{}', expected '{}'", containerName, role, ROLE_GAME)
                return false
            }
            running
        } catch (_: Exception) {
            false
        }
    }

    private fun runDockerCommand(vararg command: String): Int {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            process.waitFor(15, TimeUnit.SECONDS)
            process.exitValue()
        } catch (_: Exception) {
            -1
        }
    }

    private fun toStatus(instance: ManagedDockerInstance): GameInstanceStatus {
        return GameInstanceStatus(
            commitSha = instance.commitSha,
            gameVersion = instance.gameVersion,
            jarPath = "",
            port = CONTAINER_PORT,
            worldIds = instance.worldIds.toList().sorted(),
            alive = dockerIsRunning(instance.containerName),
            pid = -1,
            baseUrl = containerBaseUrl(instance.containerName),
            containerId = instance.containerId,
            imageTag = instance.imageTag,
        )
    }

    private fun resolveGameVersion(request: AttachWorldProcessRequest): String {
        return request.gameVersion.trim().ifEmpty { "latest" }
    }

    private fun containerName(gameVersion: String): String {
        val sanitized = gameVersion.replace(Regex("[^a-zA-Z0-9-]"), "-").lowercase()
        return "game-$sanitized"
    }

    private fun containerBaseUrl(containerName: String): String {
        return "http://$containerName:$CONTAINER_PORT"
    }

    private fun waitForHealth(containerName: String) {
        val deadline = System.currentTimeMillis() + healthTimeoutMs
        val baseUrl = containerBaseUrl(containerName)
        val uri = URI.create("$baseUrl/internal/health")
        var lastErrorMessage: String? = null

        while (System.currentTimeMillis() < deadline) {
            if (!dockerIsRunning(containerName)) {
                throw IllegalStateException("Container $containerName exited before health check")
            }

            try {
                val request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
                if (response.statusCode() in 200..299) {
                    return
                }
                lastErrorMessage = "health status=${response.statusCode()}"
            } catch (_: Exception) {
                lastErrorMessage = "health endpoint not ready"
            }

            Thread.sleep(500)
        }

        runDockerCommand("docker", "stop", containerName)
        runDockerCommand("docker", "rm", "-f", containerName)
        throw IllegalStateException(
            "Container $containerName health check timed out (${lastErrorMessage ?: "unknown"})",
        )
    }

    override fun resolveImageVersion(gameVersion: String): String? {
        val imageRef = "$imagePrefix:$gameVersion"
        return try {
            val process = ProcessBuilder(
                "docker", "inspect", imageRef,
                "--format", "{{index .Config.Labels \"org.opencontainers.image.revision\"}}",
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(10, TimeUnit.SECONDS)
            if (process.exitValue() != 0 || output.isBlank() || output == "<no value>") {
                null
            } else {
                output.take(7)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun listAvailableVersions(): List<String> {
        return try {
            val process = ProcessBuilder(
                "docker", "images", "--format", "{{.Tag}}", imagePrefix,
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(10, TimeUnit.SECONDS)
            if (process.exitValue() != 0 || output.isBlank()) {
                emptyList()
            } else {
                output.lines().filter { it.isNotBlank() && it != "<none>" }.distinct().sorted()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val CONTAINER_PORT = 9001
        private const val ROLE_GAME = "game"
        private const val ROLE_LABEL = "opensam.role"
    }
}
