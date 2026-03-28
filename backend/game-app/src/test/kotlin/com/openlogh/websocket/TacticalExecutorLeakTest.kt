package com.openlogh.websocket

import com.openlogh.engine.tactical.TacticalTurnScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * HARD-02: Tactical executor thread leak tests.
 *
 * Verifies:
 * 1. TacticalTurnScheduler.scheduleOnce executes tasks after delay
 * 2. TacticalWebSocketController no longer contains Executors.newSingleThreadScheduledExecutor
 * 3. TacticalTurnScheduler.shutdown() properly terminates scheduled tasks
 */
class TacticalExecutorLeakTest {

    private val scheduler = TacticalTurnScheduler(messagingTemplate = null)

    @AfterEach
    fun tearDown() {
        scheduler.shutdown()
    }

    @Test
    fun `scheduleOnce executes task after delay`() {
        val latch = CountDownLatch(1)

        scheduler.scheduleOnce(100, TimeUnit.MILLISECONDS) {
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Scheduled task should execute within 2 seconds")
    }

    @Test
    fun `scheduleOnce returns cancellable ScheduledFuture`() {
        val latch = CountDownLatch(1)

        val future = scheduler.scheduleOnce(5, TimeUnit.SECONDS) {
            latch.countDown()
        }

        assertFalse(future.isDone, "Future should not be done immediately")
        future.cancel(false)
        assertTrue(future.isCancelled, "Future should be cancelled")

        // Task should NOT execute after cancellation
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS), "Cancelled task should not execute")
    }

    @Test
    fun `TacticalWebSocketController source does not contain newSingleThreadScheduledExecutor`() {
        val sourceFile = File("src/main/kotlin/com/openlogh/websocket/TacticalWebSocketController.kt")
        assertTrue(sourceFile.exists(), "TacticalWebSocketController.kt must exist")

        val content = sourceFile.readText()
        assertFalse(
            content.contains("newSingleThreadScheduledExecutor"),
            "TacticalWebSocketController must not contain Executors.newSingleThreadScheduledExecutor (HARD-02 leak)"
        )
        assertTrue(
            content.contains("turnScheduler.scheduleOnce"),
            "TacticalWebSocketController must use turnScheduler.scheduleOnce for delayed cleanup"
        )
    }

    @Test
    fun `shutdown terminates pending scheduled tasks`() {
        val latch = CountDownLatch(1)

        scheduler.scheduleOnce(10, TimeUnit.SECONDS) {
            latch.countDown()
        }

        // Shutdown immediately - task should not execute
        scheduler.shutdown()

        assertFalse(latch.await(500, TimeUnit.MILLISECONDS), "Task should not execute after shutdown")
    }
}
