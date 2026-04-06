package com.openlogh.model

/**
 * Serializable per-unit state within a tactical battle.
 * Stored in TacticalBattle.battleState JSONB.
 */
data class TacticalUnitState(
    val fleetId: Long,
    val officerId: Long,
    val officerName: String,
    val factionId: Long,

    // Position on 2D tactical grid
    val posX: Double = 0.0,
    val posY: Double = 0.0,

    // Velocity (units per tick)
    val velX: Double = 0.0,
    val velY: Double = 0.0,

    // Combat stats
    val hp: Int = 0,
    val maxHp: Int = 0,
    val ships: Int = 0,
    val maxShips: Int = 0,
    val training: Int = 0,
    val morale: Int = 0,
    val supplies: Int = 0,

    // Officer stats
    val leadership: Int = 0,
    val command: Int = 0,
    val intelligence: Int = 0,
    val mobility: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,

    // Energy and formation
    val energy: Map<String, Int> = EnergyAllocation.toMap(EnergyAllocation.BALANCED),
    val formation: String = Formation.MIXED.name,

    // Unit stance (태세 4종)
    val stance: String = UnitStance.NAVIGATION.name,
    val stanceChangeTicksRemaining: Int = 0,

    // Command range (expands over time based on command stat)
    val commandRange: Double = 0.0,
    val commandRangeMax: Double = 100.0,
    val commandRangeExpansionRate: Double = 1.0,

    // Status flags
    val isAlive: Boolean = true,
    val isRetreating: Boolean = false,
    val retreatProgress: Double = 0.0,  // 0.0 to 1.0, retreat complete at 1.0

    // Unit type info
    val unitType: String = "FLEET",
    val shipClass: Int = 0,
    val shipSubtypeCode: Int? = null,

    // Weapon cooldowns: weapon type name -> ticks remaining
    val weaponCooldowns: Map<String, Int> = emptyMap(),

    // Active debuffs: debuff type -> ticks remaining
    val debuffs: Map<String, Int> = emptyMap(),

    // Detection capability
    val detectionBaseRange: Double = 5.0,
    val detectionBasePrecision: Double = 0.5,
    val detectionEvasionRating: Double = 0.2,

    // Injury state
    val injurySeverity: Int = 0,
    val returnPlanetId: Long? = null,
) {
    fun getEnergyAllocation(): EnergyAllocation = EnergyAllocation.fromMap(energy)
    fun getFormation(): Formation = Formation.fromString(formation)
    fun getStance(): UnitStance = UnitStance.fromString(stance)
    fun getShipSubtype(): ShipSubtype? = shipSubtypeCode?.let { ShipSubtype.fromCode(it) }
    fun getDetectionCapability(): DetectionCapability = DetectionCapability(
        baseRange = detectionBaseRange,
        basePrecision = detectionBasePrecision,
        evasionRating = detectionEvasionRating,
        isStopped = velX == 0.0 && velY == 0.0,
    )
}
