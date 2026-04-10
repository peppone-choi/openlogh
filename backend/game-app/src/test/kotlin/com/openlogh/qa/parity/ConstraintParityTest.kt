package com.openlogh.qa.parity

import com.openlogh.command.constraint.*
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Constraint system parity tests verifying Kotlin matches legacy PHP.
 *
 * Legacy references:
 * - hwe/sammo/Constraint/ConstraintHelper.php: all constraint factory methods
 * - hwe/sammo/Constraint/Constraint.php: base interface
 *
 * These test that each constraint produces the same Pass/Fail for the
 * same input state as the legacy PHP implementation.
 */
@DisplayName("Constraint System Legacy Parity")
class ConstraintParityTest {

    // ──────────────────────────────────────────────────
    //  Nation membership constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("NotBeNeutral - legacy ConstraintHelper::NotBeNeutral()")
    inner class NotBeNeutralParity {

        @Test
        fun `fails when factionId is 0`() {
            val gen = createGeneral(factionId = 0)
            val result = NotBeNeutral().test(ctx(gen))
            assertTrue(result is ConstraintResult.Fail)
        }

        @Test
        fun `passes when factionId is nonzero`() {
            val gen = createGeneral(factionId = 1)
            assertEquals(ConstraintResult.Pass, NotBeNeutral().test(ctx(gen)))
        }
    }

    @Nested
    @DisplayName("BeNeutral")
    inner class BeNeutralParity {

        @Test
        fun `passes when factionId is 0`() {
            assertEquals(ConstraintResult.Pass, BeNeutral().test(ctx(createGeneral(factionId = 0))))
        }

        @Test
        fun `fails when factionId is nonzero`() {
            assertTrue(BeNeutral().test(ctx(createGeneral(factionId = 1))) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Resource constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("ReqGeneralGold / ReqGeneralRice")
    inner class ResourceConstraintParity {

        @Test
        fun `gold passes when exactly equal`() {
            val gen = createGeneral(funds = 100)
            assertEquals(ConstraintResult.Pass, ReqGeneralGold(100).test(ctx(gen)))
        }

        @Test
        fun `gold fails when under`() {
            val gen = createGeneral(funds = 99)
            assertTrue(ReqGeneralGold(100).test(ctx(gen)) is ConstraintResult.Fail)
        }

        @Test
        fun `rice passes when over`() {
            val gen = createGeneral(supplies = 200)
            assertEquals(ConstraintResult.Pass, ReqGeneralRice(100).test(ctx(gen)))
        }

        @Test
        fun `rice fails when zero`() {
            val gen = createGeneral(supplies = 0)
            assertTrue(ReqGeneralRice(1).test(ctx(gen)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Crew constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("ReqGeneralCrew")
    inner class CrewConstraintParity {

        @Test
        fun `passes with default minCrew 1`() {
            val gen = createGeneral(ships = 1)
            assertEquals(ConstraintResult.Pass, ReqGeneralCrew().test(ctx(gen)))
        }

        @Test
        fun `fails with 0 ships`() {
            val gen = createGeneral(ships = 0)
            assertTrue(ReqGeneralCrew().test(ctx(gen)) is ConstraintResult.Fail)
        }

        @Test
        fun `custom minCrew threshold`() {
            val gen = createGeneral(ships = 99)
            assertTrue(ReqGeneralCrew(100).test(ctx(gen)) is ConstraintResult.Fail)
            assertEquals(ConstraintResult.Pass, ReqGeneralCrew(99).test(ctx(gen)))
        }
    }

    @Nested
    @DisplayName("ReqGeneralTrainMargin / ReqGeneralAtmosMargin")
    inner class TrainAtmosMarginParity {

        @Test
        fun `train margin passes when below max`() {
            val gen = createGeneral(training = 79)
            assertEquals(ConstraintResult.Pass, ReqGeneralTrainMargin(80).test(ctx(gen)))
        }

        @Test
        fun `train margin fails at max`() {
            val gen = createGeneral(training = 80)
            assertTrue(ReqGeneralTrainMargin(80).test(ctx(gen)) is ConstraintResult.Fail)
        }

        @Test
        fun `morale margin passes when below max`() {
            val gen = createGeneral(morale = 79)
            assertEquals(ConstraintResult.Pass, ReqGeneralAtmosMargin(80).test(ctx(gen)))
        }

        @Test
        fun `morale margin fails at max`() {
            val gen = createGeneral(morale = 80)
            assertTrue(ReqGeneralAtmosMargin(80).test(ctx(gen)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  City constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("OccupiedCity / SuppliedCity")
    inner class CityConstraintParity {

        @Test
        fun `occupied city passes when factionId matches`() {
            val gen = createGeneral(factionId = 1)
            val city = createCity(factionId = 1)
            val c = ctx(gen, city = city)
            assertEquals(ConstraintResult.Pass, OccupiedCity().test(c))
        }

        @Test
        fun `occupied city fails when factionId differs`() {
            val gen = createGeneral(factionId = 1)
            val city = createCity(factionId = 2)
            assertTrue(OccupiedCity().test(ctx(gen, city = city)) is ConstraintResult.Fail)
        }

        @Test
        fun `occupied city with allowNeutral passes for factionId 0`() {
            val gen = createGeneral(factionId = 1)
            val city = createCity(factionId = 0)
            assertEquals(ConstraintResult.Pass, OccupiedCity(allowNeutral = true).test(ctx(gen, city = city)))
        }

        @Test
        fun `supplied city passes when supplyState above 0`() {
            val city = createCity(supplyState = 1)
            assertEquals(ConstraintResult.Pass, SuppliedCity().test(ctx(createGeneral(), city = city)))
        }

        @Test
        fun `supplied city fails when supplyState is 0`() {
            val city = createCity(supplyState = 0)
            assertTrue(SuppliedCity().test(ctx(createGeneral(), city = city)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Officer level constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BeChief / BeLord / NotChief")
    inner class OfficerParity {

        @Test
        fun `BeChief passes at level 20`() {
            val gen = createGeneral(officerLevel = 20)
            assertEquals(ConstraintResult.Pass, BeChief().test(ctx(gen)))
        }

        @Test
        fun `BeChief fails below 20`() {
            val gen = createGeneral(officerLevel = 11)
            assertTrue(BeChief().test(ctx(gen)) is ConstraintResult.Fail)
        }

        @Test
        fun `NotChief passes below 20`() {
            val gen = createGeneral(officerLevel = 5)
            assertEquals(ConstraintResult.Pass, NotChief().test(ctx(gen)))
        }

        @Test
        fun `NotChief fails at 20`() {
            val gen = createGeneral(officerLevel = 20)
            assertTrue(NotChief().test(ctx(gen)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Crew margin constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("ReqGeneralCrewMargin")
    inner class CrewMarginParity {

        @Test
        fun `passes when different ships type`() {
            val gen = createGeneral(shipClass = 1100, ships = 10000, leadership = 50)
            assertEquals(ConstraintResult.Pass, ReqGeneralCrewMargin(1200).test(ctx(gen)))
        }

        @Test
        fun `passes when same type with room`() {
            val gen = createGeneral(shipClass = 1100, ships = 4999, leadership = 50)
            assertEquals(ConstraintResult.Pass, ReqGeneralCrewMargin(1100).test(ctx(gen)))
        }

        @Test
        fun `fails when same type at max`() {
            // maxCrew = 50*100 = 5000; ships =5000 → not > → fail
            val gen = createGeneral(shipClass = 1100, ships = 5000, leadership = 50)
            assertTrue(ReqGeneralCrewMargin(1100).test(ctx(gen)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Nation resource constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("ReqNationGold / ReqNationRice")
    inner class NationResourceParity {

        @Test
        fun `nation gold passes when sufficient`() {
            val nation = createNation(funds = 1000)
            assertEquals(ConstraintResult.Pass,
                ReqNationGold(1000).test(ctx(createGeneral(), nation = nation)))
        }

        @Test
        fun `nation gold fails when insufficient`() {
            val nation = createNation(funds = 999)
            assertTrue(ReqNationGold(1000).test(ctx(createGeneral(), nation = nation)) is ConstraintResult.Fail)
        }

        @Test
        fun `nation rice passes when sufficient`() {
            val nation = createNation(supplies = 500)
            assertEquals(ConstraintResult.Pass,
                ReqNationRice(500).test(ctx(createGeneral(), nation = nation)))
        }
    }

    // ──────────────────────────────────────────────────
    //  Constraint chain
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Constraint chain - all-pass and first-fail")
    inner class ChainParity {

        @Test
        fun `empty chain passes`() {
            assertEquals(ConstraintResult.Pass, ConstraintChain.testAll(emptyList(), ctx(createGeneral())))
        }

        @Test
        fun `chain stops at first failure`() {
            val gen = createGeneral(factionId = 0, funds = 0)
            val constraints = listOf(
                NotBeNeutral(),
                ReqGeneralGold(100),
            )
            val result = ConstraintChain.testAll(constraints, ctx(gen))
            assertTrue(result is ConstraintResult.Fail)
            // Should fail on NotBeNeutral, not ReqGeneralGold
            assertTrue((result as ConstraintResult.Fail).reason.contains("소속 국가"))
        }

        @Test
        fun `chain passes when all pass`() {
            val gen = createGeneral(factionId = 1, funds = 500)
            val constraints = listOf(
                NotBeNeutral(),
                ReqGeneralGold(100),
            )
            assertEquals(ConstraintResult.Pass, ConstraintChain.testAll(constraints, ctx(gen)))
        }
    }

    // ──────────────────────────────────────────────────
    //  AlwaysFail
    // ──────────────────────────────────────────────────

    @Test
    fun `AlwaysFail always fails with given reason`() {
        val result = AlwaysFail("테스트 실패 사유").test(ctx(createGeneral()))
        assertTrue(result is ConstraintResult.Fail)
        assertEquals("테스트 실패 사유", (result as ConstraintResult.Fail).reason)
    }

    // ──────────────────────────────────────────────────
    //  Dest general/city constraints
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Dest entity constraints")
    inner class DestEntityParity {

        @Test
        fun `ExistsDestGeneral passes when present`() {
            val gen = createGeneral()
            val dest = createGeneral(id = 2)
            assertEquals(ConstraintResult.Pass, ExistsDestGeneral().test(ctx(gen, destOfficer = dest)))
        }

        @Test
        fun `ExistsDestGeneral fails when null`() {
            assertTrue(ExistsDestGeneral().test(ctx(createGeneral())) is ConstraintResult.Fail)
        }

        @Test
        fun `FriendlyDestGeneral passes when same nation`() {
            val gen = createGeneral(factionId = 1)
            val dest = createGeneral(id = 2, factionId = 1)
            assertEquals(ConstraintResult.Pass, FriendlyDestGeneral().test(ctx(gen, destOfficer = dest)))
        }

        @Test
        fun `FriendlyDestGeneral fails when different nation`() {
            val gen = createGeneral(factionId = 1)
            val dest = createGeneral(id = 2, factionId = 2)
            assertTrue(FriendlyDestGeneral().test(ctx(gen, destOfficer = dest)) is ConstraintResult.Fail)
        }

        @Test
        fun `NotSameDestCity passes when different`() {
            val gen = createGeneral(planetId = 1)
            val destPlanet = createCity(id = 2)
            assertEquals(ConstraintResult.Pass, NotSameDestCity().test(ctx(gen, destPlanet = destPlanet)))
        }

        @Test
        fun `NotSameDestCity fails when same`() {
            val gen = createGeneral(planetId = 1)
            val destPlanet = createCity(id = 1)
            assertTrue(NotSameDestCity().test(ctx(gen, destPlanet = destPlanet)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Injury constraint
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("NotInjured")
    inner class InjuryConstraintParity {

        @Test
        fun `passes when no injury`() {
            val gen = createGeneral(injury = 0)
            assertEquals(ConstraintResult.Pass, NotInjured().test(ctx(gen)))
        }

        @Test
        fun `fails when injured`() {
            val gen = createGeneral(injury = 1)
            assertTrue(NotInjured().test(ctx(gen)) is ConstraintResult.Fail)
        }

        @Test
        fun `passes with custom maxInjury threshold`() {
            val gen = createGeneral(injury = 20)
            assertEquals(ConstraintResult.Pass, NotInjured(maxInjury = 20).test(ctx(gen)))
            assertTrue(NotInjured(maxInjury = 19).test(ctx(gen)) is ConstraintResult.Fail)
        }
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    private fun ctx(
        general: Officer,
        city: Planet? = null,
        nation: Faction? = null,
        destOfficer: Officer? = null,
        destPlanet: Planet? = null,
        destFaction: Faction? = null,
        env: Map<String, Any> = emptyMap(),
    ) = ConstraintContext(
        general = general,
        city = city,
        nation = nation,
        destOfficer = destOfficer,
        destPlanet = destPlanet,
        destFaction = destFaction,
        env = env,
    )

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        funds: Int = 500,
        supplies: Int = 500,
        ships: Int = 1000,
        shipClass: Short = 0,
        training: Short = 60,
        morale: Short = 60,
        officerLevel: Short = 5,
        leadership: Short = 70,
        injury: Short = 0,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수",
        factionId = factionId,
        planetId = planetId,
        funds = funds,
        supplies = supplies,
        ships = ships,
        shipClass = shipClass,
        training = training,
        morale = morale,
        leadership = leadership,
        command = 70,
        intelligence = 70,
        politics = 60,
        administration = 60,
        officerLevel = officerLevel,
        injury = injury,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        id: Long = 1,
        factionId: Long = 1,
        supplyState: Short = 1,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        orbitalDefense: Int = 500,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 500,
        fortressMax: Int = 1000,
        approval: Float = 80f,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        population = 50000,
        populationMax = 100000,
        production = production,
        productionMax = productionMax,
        commerce = commerce,
        commerceMax = commerceMax,
        security = security,
        securityMax = securityMax,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = orbitalDefenseMax,
        fortress = fortress,
        fortressMax = fortressMax,
        approval = approval,
        supplyState = supplyState,
    )

    private fun createNation(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "테스트국",
        funds = funds,
        supplies = supplies,
    )
}
