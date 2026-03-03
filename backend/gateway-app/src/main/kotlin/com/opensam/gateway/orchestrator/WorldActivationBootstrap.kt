package com.opensam.gateway.orchestrator

import com.opensam.gateway.dto.AttachWorldProcessRequest
import com.opensam.gateway.entity.WorldState
import com.opensam.gateway.service.WorldService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class WorldActivationBootstrap(
    private val worldService: WorldService,
    private val gameOrchestrator: GameOrchestrator,
    @Value("\${gateway.orchestrator.restore-active-worlds:true}")
    private val restoreActiveWorlds: Boolean,
    @Value("\${gateway.orchestrator.restore-max-retries:3}")
    private val maxRetries: Int,
    @Value("\${gateway.orchestrator.restore-retry-delay-ms:30000}")
    private val retryDelayMs: Long,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(WorldActivationBootstrap::class.java)

    override fun run(args: ApplicationArguments) {
        if (!restoreActiveWorlds) {
            log.info("Gateway world restore is disabled")
            return
        }

        val worlds = worldService.listWorlds()
        val activeWorlds = worlds.filter { isGatewayActive(it.meta["gatewayActive"]) }

        if (activeWorlds.isEmpty()) {
            log.info("No previously active worlds to restore")
            return
        }

        log.info("Restoring {} active world(s) asynchronously", activeWorlds.size)
        Thread({ restoreWithRetry(activeWorlds) }, "world-restore").apply {
            isDaemon = true
            start()
        }
    }

    private fun restoreWithRetry(activeWorlds: List<WorldState>) {
        var remaining = activeWorlds.toList()

        for (attempt in 1..maxRetries) {
            if (remaining.isEmpty()) break

            if (attempt > 1) {
                log.info(
                    "World restore retry {}/{} for {} world(s) after {}ms",
                    attempt, maxRetries, remaining.size, retryDelayMs,
                )
                try {
                    Thread.sleep(retryDelayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.warn("World restore interrupted")
                    return
                }
            }

            val failed = mutableListOf<WorldState>()

            for (world in remaining) {
                try {
                    gameOrchestrator.attachWorld(
                        worldId = world.id.toLong(),
                        request = AttachWorldProcessRequest(
                            commitSha = world.commitSha,
                            gameVersion = world.gameVersion,
                        ),
                    )
                    log.info(
                        "Restored world={} commitSha={} gameVersion={}",
                        world.id,
                        world.commitSha,
                        world.gameVersion,
                    )
                } catch (e: Exception) {
                    log.warn(
                        "Failed to restore world={} (attempt {}/{}): {}",
                        world.id,
                        attempt,
                        maxRetries,
                        e.message,
                    )
                    failed.add(world)
                }
            }

            remaining = failed
        }

        if (remaining.isNotEmpty()) {
            log.error(
                "Gave up restoring {} world(s) after {} attempts: worldIds={}",
                remaining.size,
                maxRetries,
                remaining.map { it.id },
            )
        }
    }

    private fun isGatewayActive(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }
}
