package com.openlogh.engine.tactical

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 전술 전투 턴 타이머 관리.
 * - 각 세션별 30초 카운트다운
 * - 매초 타이머 브로드캐스트
 * - 시간 초과 시 콜백 호출
 * - 전원 준비 완료 시 즉시 콜백 호출
 */
@Component
class TacticalTurnScheduler(
    private val messagingTemplate: SimpMessagingTemplate?,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TacticalTurnScheduler::class.java)
        const val DEFAULT_TURN_DURATION = 30
    }

    private val executor = Executors.newScheduledThreadPool(4)
    private val countdownTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val expireTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val remainingSeconds = ConcurrentHashMap<String, AtomicInteger>()
    private val expireCallbacks = ConcurrentHashMap<String, () -> Unit>()

    /**
     * 턴 타이머 시작.
     * @param sessionCode 세션 코드
     * @param durationSeconds 타이머 초 (기본 30초)
     * @param onExpire 타이머 만료 또는 전원 준비 완료 시 호출할 콜백
     */
    fun startTimer(sessionCode: String, durationSeconds: Int = DEFAULT_TURN_DURATION, onExpire: () -> Unit) {
        cancelTimer(sessionCode)

        remainingSeconds[sessionCode] = AtomicInteger(durationSeconds)
        expireCallbacks[sessionCode] = onExpire

        // 매초 타이머 브로드캐스트
        val countdownTask = executor.scheduleAtFixedRate({
            try {
                val remaining = remainingSeconds[sessionCode]?.decrementAndGet() ?: return@scheduleAtFixedRate
                broadcastTimer(sessionCode, remaining)
                if (remaining <= 0) {
                    triggerExpire(sessionCode)
                }
            } catch (e: Exception) {
                log.error("Timer countdown error for session {}", sessionCode, e)
            }
        }, 1, 1, TimeUnit.SECONDS)

        countdownTasks[sessionCode] = countdownTask

        // 안전장치: durationSeconds + 2초 후 강제 만료
        val expireTask = executor.schedule({
            triggerExpire(sessionCode)
        }, durationSeconds.toLong() + 2, TimeUnit.SECONDS)

        expireTasks[sessionCode] = expireTask

        broadcastTimer(sessionCode, durationSeconds)
        log.debug("Timer started: session={}, duration={}s", sessionCode, durationSeconds)
    }

    /** 전원 준비 완료 시 즉시 턴 진행 */
    fun onAllReady(sessionCode: String) {
        log.debug("All ready, triggering immediate turn: session={}", sessionCode)
        triggerExpire(sessionCode)
    }

    /** 타이머 취소 (세션 종료 시) */
    fun cancelTimer(sessionCode: String) {
        countdownTasks.remove(sessionCode)?.cancel(false)
        expireTasks.remove(sessionCode)?.cancel(false)
        remainingSeconds.remove(sessionCode)
        expireCallbacks.remove(sessionCode)
    }

    /** 현재 남은 시간 조회 */
    fun getRemainingSeconds(sessionCode: String): Int =
        remainingSeconds[sessionCode]?.get() ?: 0

    private fun triggerExpire(sessionCode: String) {
        // Cancel timers first to prevent double-trigger
        countdownTasks.remove(sessionCode)?.cancel(false)
        expireTasks.remove(sessionCode)?.cancel(false)
        remainingSeconds.remove(sessionCode)

        val callback = expireCallbacks.remove(sessionCode)
        if (callback != null) {
            try {
                callback()
            } catch (e: Exception) {
                log.error("Turn expire callback error for session {}", sessionCode, e)
            }
        }
    }

    private fun broadcastTimer(sessionCode: String, remaining: Int) {
        val update = TimerUpdate(sessionCode = sessionCode, remaining = remaining)
        messagingTemplate?.convertAndSend("/topic/tactical/$sessionCode/timer", update)
    }

    /**
     * Schedule a one-shot task on the managed executor.
     * Used for delayed cleanup after battle end (replacing inline Executors.newSingleThreadScheduledExecutor).
     * HARD-02: Prevents thread leak by reusing the managed thread pool.
     */
    fun scheduleOnce(delay: Long, unit: TimeUnit, task: () -> Unit): ScheduledFuture<*> {
        return executor.schedule({
            try {
                task()
            } catch (e: Exception) {
                log.error("Scheduled one-shot task failed", e)
            }
        }, delay, unit)
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
        log.info("TacticalTurnScheduler shut down")
    }
}
