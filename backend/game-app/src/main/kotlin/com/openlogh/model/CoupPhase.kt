package com.openlogh.model

/**
 * Coup lifecycle state machine for Empire faction.
 *
 * State transitions:
 *   PLANNING -> ACTIVE (when politicalPower >= threshold)
 *   PLANNING -> ABORTED (leader cancels)
 *   ACTIVE -> SUCCESS (coup supporters overpower loyalists)
 *   ACTIVE -> FAILED (loyalists suppress the coup)
 */
enum class CoupPhase(
    val code: String,
    val nameKo: String,
    val nameEn: String,
    val isTerminal: Boolean,
) {
    PLANNING("PLANNING", "비밀 모의 중", "Planning", false),
    ACTIVE("ACTIVE", "쿠데타 진행 중", "Active", false),
    SUCCESS("SUCCESS", "성공", "Success", true),
    FAILED("FAILED", "실패", "Failed", true),
    ABORTED("ABORTED", "중단", "Aborted", true);

    companion object {
        fun fromCode(code: String): CoupPhase =
            entries.firstOrNull { it.code == code } ?: PLANNING
    }
}
