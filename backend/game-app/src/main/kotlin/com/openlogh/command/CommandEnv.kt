package com.openlogh.command

data class CommandEnv(
    val year: Int,
    val month: Int,
    val startYear: Int,
    val worldId: Long,
    val realtimeMode: Boolean = false,
    val develCost: Int = 100,
    val scenario: Int = 0,
    val exchangeFee: Double = 0.03,
    val initialNationGenLimit: Int = 5,
    val defaultMaxGeneral: Int = 30,
    val startMonth: Int? = 1,
    val sabotageDamageMin: Int = 200,
    val sabotageDamageMax: Int = 400,
    val sabotageProbCoefByStat: Int = 200,
    val sabotageDefenceCoefByGeneralCnt: Double = 0.05,
    val minAvailableRecruitPop: Int = 30000,
    val maxTrainByCommand: Int = 100,
    val maxAtmosByCommand: Int = 100,
    val trainDelta: Double = 30.0,
    val atmosDelta: Double = 30.0,
    val atmosSideEffectByTraining: Double = 1.0,
    val trainSideEffectByAtmosTurn: Double = 1.0,
    val killturn: Short = 0,
    val gameStor: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Legacy TechLimit: checks if current tech exceeds the year-based limit.
     */
    fun isTechLimited(currentTech: Double): Boolean {
        val relYear = year - startYear
        // Legacy: techLevel = floor(tech / 1000), relMaxTech = floor(relYear / 5) + 1
        val techLevel = kotlin.math.floor(currentTech / 1000.0).toInt().coerceIn(0, 12)
        val relMaxTech = (relYear / 5 + 1).coerceIn(1, 12)
        return techLevel >= relMaxTech
    }
}

