package com.openlogh.engine.tactical

import org.springframework.stereotype.Service

/**
 * 전술 커맨드 타이밍 서비스.
 *
 * gin7 §10.21: 모든 전술 커맨드에는 실행 대기 시간(waitSeconds)과 실행 지속 시간(durationSeconds)이 있다.
 * - waitSeconds: 커맨드 입력 후 실제 실행까지 대기 시간 (초)
 * - durationSeconds: 실행 시작 후 완료까지 지속 시간 (초, 0이면 즉시 완료)
 *
 * 커맨드 타이밍 표 (gin7 §10.21):
 * | 커맨드           | 대기(초) | 지속(초)  | 비고                      |
 * |-----------------|--------|---------|--------------------------|
 * | 이동(move)       | 5      | 0       |                          |
 * | 선회(turn)       | 5      | 0       |                          |
 * | 병렬이동(parallel) | 5     | 0       | 속도 50%                  |
 * | 후진(reverse)    | 10     | 0       |                          |
 * | 철퇴(retreat)    | 5      | 150     | 2.5분 지속                |
 * | 편대변경(formation)| 5    | 0       |                          |
 * | 공격(attack)     | 5      | 0       |                          |
 * | 사격(fire)       | 5      | 0       |                          |
 * | 함재기출격(fighter)| 5    | 0       |                          |
 * | 지상전개(deploy)  | 5      | 20      |                          |
 * | 지상철수(withdraw)| 5     | 20      |                          |
 * | 자세변경(stance)  | 10     | 0       |                          |
 * | 행성발진(launch)  | 5      | 20      | 유닛당                    |
 * | 정지(stop)       | 0      | 0       | 즉시                      |
 * | 요새포(fortress)  | 5      | 0       |                          |
 */
@Service
class CommandTimingService {

    /**
     * 커맨드 타이밍.
     *
     * @param waitSeconds 실행 대기 시간 (초)
     * @param durationSeconds 실행 지속 시간 (초, 0=즉시완료)
     */
    data class CommandTiming(
        val waitSeconds: Double,
        val durationSeconds: Double,
    ) {
        /** 커맨드가 완전히 끝날 때까지 걸리는 총 시간 (초) */
        val totalSeconds: Double get() = waitSeconds + durationSeconds
    }

    /**
     * 커맨드 유형별 타이밍 반환.
     *
     * gin7 §10.21 커맨드 타이밍 표 기반.
     */
    fun getTiming(commandType: String): CommandTiming = when (commandType) {
        // 이동 계열
        "move"          -> CommandTiming(5.0, 0.0)
        "turn"          -> CommandTiming(5.0, 0.0)
        "parallel_move" -> CommandTiming(5.0, 0.0)   // 속도 50%
        "reverse"       -> CommandTiming(10.0, 0.0)  // 후진: 대기 2배
        "retreat"       -> CommandTiming(5.0, 150.0) // 철퇴: 2.5분 지속
        // 편대
        "formation"     -> CommandTiming(5.0, 0.0)
        // 전투
        "attack"        -> CommandTiming(5.0, 0.0)
        "fire"          -> CommandTiming(5.0, 0.0)
        "fighter_launch"-> CommandTiming(5.0, 0.0)   // 함재기 출격
        // 지상전
        "ground_deploy" -> CommandTiming(5.0, 20.0)  // 지상 전개
        "ground_withdraw"->CommandTiming(5.0, 20.0)  // 지상 철수
        // 기타
        "stance_change" -> CommandTiming(10.0, 0.0)  // 자세 변경: 대기 2배
        "launch_from_planet" -> CommandTiming(5.0, 20.0) // 행성 발진 (유닛당)
        "stop"          -> CommandTiming(0.0, 0.0)   // 즉시 정지
        "fortress_gun"  -> CommandTiming(5.0, 0.0)   // 요새포
        else            -> CommandTiming(5.0, 0.0)   // 기본값
    }

    /**
     * 커맨드 실행 중 여부 확인.
     *
     * @param startTime 커맨드 시작 시각 (epoch ms)
     * @param commandType 커맨드 유형
     * @param currentTime 현재 시각 (epoch ms)
     * @return 아직 실행 중이면 true
     */
    fun isExecuting(startTime: Long, commandType: String, currentTime: Long): Boolean {
        val timing = getTiming(commandType)
        val endTime = startTime + ((timing.waitSeconds + timing.durationSeconds) * 1000).toLong()
        return currentTime < endTime
    }

    /**
     * 새 커맨드 시작 가능 여부.
     *
     * 이전 커맨드가 완전히 끝난 후에만 새 커맨드 입력 가능.
     *
     * @param lastCommandEndTime 직전 커맨드 종료 시각 (epoch ms)
     * @param currentTime 현재 시각 (epoch ms)
     */
    fun canStartNew(lastCommandEndTime: Long, currentTime: Long): Boolean =
        currentTime >= lastCommandEndTime
}
