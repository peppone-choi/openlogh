package com.openlogh.engine.tactical

import kotlin.random.Random

/**
 * 전술 전투 테스트용 팩토리/헬퍼.
 * 각 테스트에서 반복되는 세션/함대/유닛 생성 로직을 공유.
 */
object TacticalTestFixtures {

    fun defaultOfficerStats(
        officerId: Long = 1L,
        name: String = "테스트 제독",
        leadership: Int = 80,
        command: Int = 70,
        intelligence: Int = 60,
        mobility: Int = 50,
        attack: Int = 65,
        defense: Int = 55,
    ) = OfficerStats(officerId, name, leadership, command, intelligence, mobility, attack, defense)

    fun createUnit(
        id: Int,
        fleetId: Long = 1L,
        factionId: Long = 1L,
        officerId: Long = 1L,
        shipClass: TacticalShipClass = TacticalShipClass.BATTLESHIP,
        hp: Int = 300,
        x: Double = 100.0,
        y: Double = 100.0,
        isFlagship: Boolean = false,
    ) = TacticalUnit(
        id = id,
        fleetId = fleetId,
        factionId = factionId,
        officerId = officerId,
        shipClass = shipClass,
        hp = hp,
        x = x,
        y = y,
        isFlagship = isFlagship,
    )

    fun createFleet(
        fleetId: Long = 1L,
        officerId: Long = 1L,
        factionId: Long = 1L,
        officer: OfficerStats = defaultOfficerStats(officerId),
        units: List<TacticalUnit> = listOf(
            createUnit(0, fleetId, factionId, officerId, TacticalShipClass.BATTLESHIP, 300, 100.0, 100.0, isFlagship = true),
            createUnit(1, fleetId, factionId, officerId, TacticalShipClass.CRUISER, 300, 130.0, 100.0),
            createUnit(2, fleetId, factionId, officerId, TacticalShipClass.DESTROYER, 300, 160.0, 100.0),
        ),
        formation: Formation = Formation.SPINDLE,
        energy: EnergyAllocation = EnergyAllocation.BALANCED,
        morale: Int = 100,
    ) = TacticalFleet(
        fleetId = fleetId,
        officerId = officerId,
        factionId = factionId,
        officer = officer,
        units = units.toMutableList(),
        formation = formation,
        energy = energy,
        morale = morale,
    )

    fun createSession(
        attackerFleets: List<TacticalFleet> = listOf(createFleet(fleetId = 1L, officerId = 1L, factionId = 1L)),
        defenderFleets: List<TacticalFleet> = listOf(
            createFleet(
                fleetId = 2L, officerId = 2L, factionId = 2L,
                officer = defaultOfficerStats(officerId = 2L, name = "방어 제독"),
                units = listOf(
                    createUnit(10, 2L, 2L, 2L, TacticalShipClass.BATTLESHIP, 300, 100.0, 900.0, isFlagship = true),
                    createUnit(11, 2L, 2L, 2L, TacticalShipClass.CRUISER, 300, 130.0, 900.0),
                    createUnit(12, 2L, 2L, 2L, TacticalShipClass.DESTROYER, 300, 160.0, 900.0),
                ),
            )
        ),
        grid: TacticalGrid = TacticalGrid(),
    ): TacticalBattleSession {
        // 그리드에 모든 유닛 배치
        for (fleet in attackerFleets + defenderFleets) {
            for (unit in fleet.units) {
                grid.placeUnit(unit.id, unit.position())
            }
        }
        return TacticalBattleSession(
            sessionId = 1L,
            planetId = 1L,
            attackerFleets = attackerFleets.toMutableList(),
            defenderFleets = defenderFleets.toMutableList(),
            grid = grid,
        )
    }

    fun seededEngine(seed: Long = 42L) = TacticalBattleEngine(Random(seed))
}
