package com.openlogh.engine.tactical

data class OfficerStats(
    val officerId: Long,
    val name: String,
    val leadership: Int,
    val command: Int,
    val intelligence: Int,
    val mobility: Int,
    val attack: Int,
    val defense: Int,
    val fighterSkill: Int = 30,   // 공전: 함재기 공격력
    val groundCombat: Int = 30,   // 육전: 강습양륙/지상전 효율
)

data class TacticalFleet(
    val fleetId: Long,
    val officerId: Long,
    val factionId: Long,
    val officer: OfficerStats,
    val units: MutableList<TacticalUnit>,
    var formation: Formation = Formation.SPINDLE,
    var energy: EnergyAllocation = EnergyAllocation.BALANCED,
    var morale: Int,
    val maxMorale: Int = morale,
) {
    fun totalHp(): Int = units.sumOf { it.hp }
    fun totalMaxHp(): Int = units.sumOf { it.maxHp }
    fun aliveUnits(): List<TacticalUnit> = units.filter { it.isAlive() }
    fun isDefeated(): Boolean = aliveUnits().isEmpty()
    fun flagship(): TacticalUnit? = units.firstOrNull { it.isFlagship && it.isAlive() }

    fun applyMoraleLoss(amount: Int) {
        morale = (morale - amount).coerceAtLeast(0)
    }

    fun applyMoraleGain(amount: Int) {
        morale = (morale + amount).coerceAtMost(maxMorale)
    }

    fun isForcedRetreat(): Boolean = morale <= 20

    companion object {
        fun calculateMaxMorale(leadership: Int, baseMorale: Int): Int =
            (leadership * baseMorale) / 100
    }
}
