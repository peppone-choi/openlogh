package com.openlogh.command

import com.openlogh.command.constraint.*
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class ConstraintChainTest {

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        funds: Int = 1000,
        supplies: Int = 1000,
        ships: Int = 100,
        officerLevel: Short = 5,
        injury: Short = 0,
        fleetId: Long = 0,
        makeLimit: Short = 0,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "테스트",
            factionId = factionId,
            planetId = planetId,
            funds = funds,
            supplies = supplies,
            ships = ships,
            officerLevel = officerLevel,
            injury = injury,
            fleetId = fleetId,
            makeLimit = makeLimit,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(factionId: Long = 1, supplyState: Short = 1): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
            supplyState = supplyState,
        )
    }

    private fun ctx(
        general: Officer = createGeneral(),
        city: Planet? = null,
        nation: Faction? = null,
        destPlanet: Planet? = null,
        env: Map<String, Any> = emptyMap(),
    ) = ConstraintContext(general = general, city = city, nation = nation, destPlanet = destPlanet, env = env)

    // ========== ConstraintChain.testAll ==========

    @Test
    fun `testAll returns Pass when all constraints pass`() {
        val general = createGeneral(factionId = 1, funds = 500)
        val city = createCity(factionId = 1, supplyState = 1)
        val constraints = listOf(
            NotBeNeutral(),
            OccupiedCity(),
            ReqGeneralGold(100),
        )
        val result = ConstraintChain.testAll(constraints, ctx(general = general, city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `testAll returns first failure`() {
        val general = createGeneral(factionId = 0)
        val constraints = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )
        val result = ConstraintChain.testAll(constraints, ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("소속 국가"))
    }

    @Test
    fun `testAll with empty list returns Pass`() {
        val result = ConstraintChain.testAll(emptyList(), ctx())
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `testAll stops at first failure`() {
        val general = createGeneral(factionId = 1, funds = 50)
        val city = createCity(factionId = 2) // wrong nation
        val constraints = listOf(
            OccupiedCity(),      // will fail
            ReqGeneralGold(100), // would also fail but won't be reached
        )
        val result = ConstraintChain.testAll(constraints, ctx(general = general, city = city))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("아군 도시"))
    }

    // ========== New constraints: HasRoute ==========

    @Test
    fun `HasRoute passes with dest city`() {
        val destPlanet = createCity().apply { id = 2; factionId = 1 }
        val env = mapOf(
            "mapAdjacency" to mapOf(1L to listOf(2L), 2L to listOf(1L)),
            "cityNationById" to mapOf(1L to 1L, 2L to 1L),
            "dbToMapId" to mapOf(1L to 1L, 2L to 2L),
            "mapToDbId" to mapOf(1L to 1L, 2L to 2L),
            "cityNationByMapId" to mapOf(1L to 1L, 2L to 1L),
        )
        val result = HasRoute().test(ctx(destPlanet = destPlanet, env = env))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `HasRoute fails without dest city`() {
        val result = HasRoute().test(ctx())
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("목적지"))
    }

    @Test
    fun `NearCity passes for adjacent city`() {
        val general = createGeneral(planetId = 1)
        val destPlanet = createCity().apply { id = 2 }
        val env = mapOf(
            "mapAdjacency" to mapOf(1L to listOf(2L), 2L to listOf(1L)),
            "dbToMapId" to mapOf(1L to 1L, 2L to 2L),
            "cityNationByMapId" to mapOf(1L to 0L, 2L to 0L),
        )
        val result = NearCity(1).test(ctx(general = general, destPlanet = destPlanet, env = env))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NearCity fails when destination is too far`() {
        val general = createGeneral(planetId = 1)
        val destPlanet = createCity().apply { id = 3 }
        val env = mapOf(
            "mapAdjacency" to mapOf(1L to listOf(2L), 2L to listOf(1L, 3L), 3L to listOf(2L)),
            "dbToMapId" to mapOf(1L to 1L, 2L to 2L, 3L to 3L),
            "cityNationByMapId" to mapOf(1L to 0L, 2L to 0L, 3L to 0L),
        )
        val result = NearCity(1).test(ctx(general = general, destPlanet = destPlanet, env = env))
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `ReqTroopMembers passes when troop has members`() {
        val general = createGeneral(id = 10, fleetId = 10)
        val env = mapOf("troopMemberExistsByTroopId" to mapOf(10L to true))
        val result = ReqTroopMembers().test(ctx(general = general, env = env))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqTroopMembers fails when troop has no members`() {
        val general = createGeneral(id = 10, fleetId = 10)
        val env = mapOf("troopMemberExistsByTroopId" to mapOf(10L to false))
        val result = ReqTroopMembers().test(ctx(general = general, env = env))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("부대원"))
    }

    @Test
    fun `AllowWar passes when war state allows war`() {
        val nation = Faction(warState = 0)
        val result = AllowWar().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `AllowWar fails when war state blocks war`() {
        val nation = Faction(warState = 1)
        val result = AllowWar().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("전쟁 금지"))
    }

    @Test
    fun `HasRouteWithEnemy passes when target nation is at war and route exists`() {
        val general = createGeneral(factionId = 1, planetId = 1)
        val destPlanet = createCity(factionId = 2).apply { id = 3 }
        val env = mapOf(
            "atWarNationIds" to setOf(2L),
            "mapAdjacency" to mapOf(1L to listOf(2L), 2L to listOf(1L, 3L), 3L to listOf(2L)),
            "cityNationById" to mapOf(1L to 1L, 2L to 0L, 3L to 2L),
            "dbToMapId" to mapOf(1L to 1L, 2L to 2L, 3L to 3L),
            "mapToDbId" to mapOf(1L to 1L, 2L to 2L, 3L to 3L),
            "cityNationByMapId" to mapOf(1L to 1L, 2L to 0L, 3L to 2L),
        )
        val result = HasRouteWithEnemy().test(ctx(general = general, destPlanet = destPlanet, env = env))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `HasRouteWithEnemy fails when target nation is not at war`() {
        val general = createGeneral(factionId = 1, planetId = 1)
        val destPlanet = createCity(factionId = 3).apply { id = 3 }
        val env = mapOf(
            "atWarNationIds" to setOf(2L),
            "mapAdjacency" to mapOf(1L to listOf(3L), 3L to listOf(1L)),
            "cityNationById" to mapOf(1L to 1L, 3L to 3L),
            "dbToMapId" to mapOf(1L to 1L, 3L to 3L),
            "mapToDbId" to mapOf(1L to 1L, 3L to 3L),
            "cityNationByMapId" to mapOf(1L to 1L, 3L to 3L),
        )
        val result = HasRouteWithEnemy().test(ctx(general = general, destPlanet = destPlanet, env = env))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("교전중"))
    }

    @Test
    fun `AllowJoinAction passes when makeLimit is zero`() {
        val result = AllowJoinAction().test(ctx(general = createGeneral(makeLimit = 0)))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `AllowJoinAction fails when makeLimit is active`() {
        val result = AllowJoinAction().test(ctx(general = createGeneral(makeLimit = 3), env = mapOf("joinActionLimit" to 12)))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("12턴"))
    }

    @Test
    fun `BattleGroundCity passes for neutral destination`() {
        val general = createGeneral(factionId = 1)
        val destPlanet = createCity(factionId = 0).apply { id = 2 }
        val result = BattleGroundCity().test(ctx(general = general, destPlanet = destPlanet, env = mapOf("atWarNationIds" to emptySet<Long>())))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `BattleGroundCity fails when destination nation is not at war`() {
        val general = createGeneral(factionId = 1)
        val destPlanet = createCity(factionId = 3).apply { id = 2 }
        val result = BattleGroundCity().test(ctx(general = general, destPlanet = destPlanet, env = mapOf("atWarNationIds" to setOf(2L))))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("교전중"))
    }

    // ========== New constraints: AllowDiplomacy ==========

    @Test
    fun `AllowDiplomacy passes with sufficient officer level`() {
        val general = createGeneral(officerLevel = 5)
        val result = AllowDiplomacy(5).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `AllowDiplomacy fails with insufficient officer level`() {
        val general = createGeneral(officerLevel = 3)
        val result = AllowDiplomacy(5).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("외교 권한"))
    }

    // ========== New constraints: NotInjured ==========

    @Test
    fun `NotInjured passes when not injured`() {
        val general = createGeneral(injury = 0)
        val result = NotInjured(0).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotInjured passes when injury below threshold`() {
        val general = createGeneral(injury = 10)
        val result = NotInjured(20).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotInjured fails when injury exceeds threshold`() {
        val general = createGeneral(injury = 50)
        val result = NotInjured(20).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("부상"))
    }
}
