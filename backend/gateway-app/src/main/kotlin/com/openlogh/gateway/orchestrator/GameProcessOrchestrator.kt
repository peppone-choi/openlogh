package com.openlogh.gateway.orchestrator

import com.openlogh.gateway.dto.AttachWorldProcessRequest
import com.openlogh.gateway.dto.GameInstanceStatus
import com.openlogh.gateway.service.WorldRouteRegistry
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
@ConditionalOnProperty("gateway.docker.enabled", havingValue = "false", matchIfMissing = true)
class GameProcessOrchestrator(
    private val worldRouteRegistry: WorldRouteRegistry,
    @Value("\${gateway.orchestrator.health-timeout-ms:120000}")
    private val healthTimeoutMs: Long,
) : GameOrchestrator {
    private val log = LoggerFactory.getLogger(GameProcessOrchestrator::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    private data class ManagedGameInstance(
        val commitSha: String,
        val gameVersion: String,
        val jarPath: String,
        val port: Int,
        val process: Process,
        val worldIds: MutableSet<Long>,
    )

    private data class StartingEntry(
        val commitSha: String,
        val gameVersion: String,
        val jarPath: String,
        val port: Int,
        val javaCommand: String,
        val retainedWorldIds: Set<Long>,
        val future: CompletableFuture<ManagedGameInstance>,
    )

    private sealed interface InstanceResult {
        data class Running(val instance: ManagedGameInstance) : InstanceResult
        data class Pending(val future: CompletableFuture<ManagedGameInstance>) : InstanceResult
    }

    private sealed interface PhaseOneResult {
        data class Running(val instance: ManagedGameInstance) : PhaseOneResult
        data class Pending(val future: CompletableFuture<ManagedGameInstance>) : PhaseOneResult
        data class StartNew(val entry: StartingEntry) : PhaseOneResult
    }

    private val lock = ReentrantLock()
    private val instances = ConcurrentHashMap<String, ManagedGameInstance>()
    private val starting = ConcurrentHashMap<String, CompletableFuture<ManagedGameInstance>>()
    private val startingPorts = ConcurrentHashMap<String, Int>()

    override fun attachWorld(worldId: Long, request: AttachWorldProcessRequest): GameInstanceStatus {
        val commitSha = request.commitSha.trim()
        require(commitSha.isNotEmpty()) { "commitSha is required" }

        ensureVersion(request)

        return lock.withLock {
            cleanupDeadInstances()

            val currentlyAssigned = instances.values.firstOrNull { worldId in it.worldIds }
            if (currentlyAssigned != null && currentlyAssigned.commitSha != commitSha) {
                currentlyAssigned.worldIds.remove(worldId)
                if (currentlyAssigned.worldIds.isEmpty()) {
                    stopInstance(currentlyAssigned)
                    instances.remove(currentlyAssigned.commitSha)
                }
            }

            val managed = instances[commitSha]
                ?: throw IllegalStateException("Game instance not found for commitSha=$commitSha")
            managed.worldIds.add(worldId)
            worldRouteRegistry.attach(worldId, baseUrl(managed.port))
            toStatus(managed)
        }
    }

    override fun ensureVersion(request: AttachWorldProcessRequest): GameInstanceStatus {
        val commitSha = request.commitSha.trim()
        require(commitSha.isNotEmpty()) { "commitSha is required" }

        val result = getOrStartInstance(commitSha, request)
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
                stopInstance(managed)
                instances.remove(managed.commitSha)
            }

            true
        }
    }

    override fun stopVersion(gameVersion: String): Boolean {
        return lock.withLock {
            val managed = instances.values.firstOrNull { it.gameVersion == gameVersion }
                ?: return false
            managed.worldIds.forEach { worldRouteRegistry.detach(it) }
            stopInstance(managed)
            instances.remove(managed.commitSha)
            true
        }
    }

    override fun statuses(): List<GameInstanceStatus> {
        return lock.withLock {
            cleanupDeadInstances()

            instances.values
                .map { toStatus(it) }
                .sortedBy { it.commitSha }
        }
    }

    @PreDestroy
    override fun shutdownAll() {
        lock.withLock {
            instances.values.forEach { stopInstance(it) }
            instances.clear()
            starting.clear()
            startingPorts.clear()
        }
    }

    private fun getOrStartInstance(commitSha: String, request: AttachWorldProcessRequest): InstanceResult {
        val phaseOne = lock.withLock {
            cleanupDeadInstances()

            val existing = instances[commitSha]
            if (existing != null && existing.process.isAlive) {
                return@withLock PhaseOneResult.Running(existing)
            }

            val inFlight = starting[commitSha]
            if (inFlight != null) {
                return@withLock PhaseOneResult.Pending(inFlight)
            }

            if (existing != null) {
                val retainedWorldIds = existing.worldIds.toSet()
                instances.remove(commitSha)
                log.warn("Restarting dead game instance for commitSha={}", commitSha)
                return@withLock PhaseOneResult.StartNew(createStartingEntry(commitSha, request, retainedWorldIds))
            }

            PhaseOneResult.StartNew(createStartingEntry(commitSha, request, emptySet()))
        }

        when (phaseOne) {
            is PhaseOneResult.Running -> return InstanceResult.Running(phaseOne.instance)
            is PhaseOneResult.Pending -> return InstanceResult.Pending(phaseOne.future)
            is PhaseOneResult.StartNew -> {
                val entry = phaseOne.entry
                try {
                    val managed = startNewInstance(entry)
                    lock.withLock {
                        completeStartup(entry, managed)
                    }
                } catch (ex: Exception) {
                    lock.withLock {
                        failStartup(entry, ex)
                    }
                    throw ex
                }
                return InstanceResult.Pending(entry.future)
            }
        }
    }

    private fun createStartingEntry(
        commitSha: String,
        request: AttachWorldProcessRequest,
        retainedWorldIds: Set<Long>,
    ): StartingEntry {
        val gameVersion = request.gameVersion.trim().ifEmpty { "dev" }
        val jarPath = resolveJarPath(commitSha, request.jarPath)
        require(Files.exists(jarPath)) { "JAR not found: $jarPath" }

        val port = request.port ?: allocatePort()
        val future = CompletableFuture<ManagedGameInstance>()
        starting[commitSha] = future
        startingPorts[commitSha] = port

        return StartingEntry(
            commitSha = commitSha,
            gameVersion = gameVersion,
            jarPath = jarPath.toString(),
            port = port,
            javaCommand = request.javaCommand,
            retainedWorldIds = retainedWorldIds,
            future = future,
        )
    }

    private fun startNewInstance(entry: StartingEntry): ManagedGameInstance {
        val logsDir = Paths.get("logs")
        Files.createDirectories(logsDir)
        val logFile = logsDir.resolve("game-${entry.commitSha}.log").toFile()

        val command = listOf(
            entry.javaCommand,
            "-jar",
            entry.jarPath,
            "--server.port=${entry.port}",
            "--game.commit-sha=${entry.commitSha}",
            "--game.version=${entry.gameVersion}",
        )

        val process = ProcessBuilder(command)
            .directory(File(System.getProperty("user.dir")))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .start()

        waitForHealth(entry.port, process)

        return ManagedGameInstance(
            commitSha = entry.commitSha,
            gameVersion = entry.gameVersion,
            jarPath = entry.jarPath,
            port = entry.port,
            process = process,
            worldIds = mutableSetOf(),
        )
    }

    private fun completeStartup(entry: StartingEntry, managed: ManagedGameInstance) {
        starting.remove(entry.commitSha, entry.future)
        startingPorts.remove(entry.commitSha, entry.port)
        managed.worldIds.addAll(entry.retainedWorldIds)
        instances[entry.commitSha] = managed
        entry.retainedWorldIds.forEach { worldRouteRegistry.attach(it, baseUrl(managed.port)) }
        entry.future.complete(managed)
    }

    private fun failStartup(entry: StartingEntry, ex: Exception) {
        starting.remove(entry.commitSha, entry.future)
        startingPorts.remove(entry.commitSha, entry.port)
        entry.future.completeExceptionally(ex)
    }

    private fun awaitStartup(future: CompletableFuture<ManagedGameInstance>): ManagedGameInstance {
        return try {
            future.get()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for game startup", ex)
        } catch (ex: ExecutionException) {
            val cause = ex.cause
            if (cause is RuntimeException) {
                throw cause
            }
            throw IllegalStateException("Failed to start game instance", cause ?: ex)
        }
    }

    private fun resolveJarPath(commitSha: String, explicitJarPath: String?): Path {
        if (!explicitJarPath.isNullOrBlank()) {
            return Paths.get(explicitJarPath).toAbsolutePath().normalize()
        }
        return Paths.get("artifacts", "game-app-$commitSha.jar").toAbsolutePath().normalize()
    }

    private fun allocatePort(): Int {
        val usedPorts = instances.values.map { it.port }.toMutableSet()
        usedPorts.addAll(startingPorts.values)
        for (candidate in 9001..9999) {
            if (candidate !in usedPorts) return candidate
        }
        throw IllegalStateException("No available port in range 9001..9999")
    }

    private fun cleanupDeadInstances() {
        val deadInstances = instances.values.filter { !it.process.isAlive }
        deadInstances.forEach { instance ->
            instance.worldIds.forEach { worldRouteRegistry.detach(it) }
            instances.remove(instance.commitSha)
            log.warn("Removed dead game instance commitSha={}", instance.commitSha)
        }
    }

    private fun stopInstance(instance: ManagedGameInstance) {
        if (!instance.process.isAlive) return
        instance.process.destroy()
        val exited = instance.process.waitFor(5, TimeUnit.SECONDS)
        if (!exited && instance.process.isAlive) {
            instance.process.destroyForcibly()
            instance.process.waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun toStatus(instance: ManagedGameInstance): GameInstanceStatus {
        return GameInstanceStatus(
            commitSha = instance.commitSha,
            gameVersion = instance.gameVersion,
            jarPath = instance.jarPath,
            port = instance.port,
            worldIds = instance.worldIds.toList().sorted(),
            alive = instance.process.isAlive,
            pid = instance.process.pid(),
            baseUrl = baseUrl(instance.port),
        )
    }

    override fun resolveImageVersion(gameVersion: String): String? = null

    override fun listAvailableVersions(): List<String> = emptyList()

    private fun baseUrl(port: Int): String {
        return "http://127.0.0.1:$port"
    }

    private fun waitForHealth(port: Int, process: Process) {
        val deadline = System.currentTimeMillis() + healthTimeoutMs
        val uri = URI.create("${baseUrl(port)}/internal/health")
        var lastErrorMessage: String? = null

        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                throw IllegalStateException("Game process exited before health check on port=$port")
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

        process.destroyForcibly()
        throw IllegalStateException(
            "Game process health check timed out on port=$port (${lastErrorMessage ?: "unknown"})",
        )
    }
}
