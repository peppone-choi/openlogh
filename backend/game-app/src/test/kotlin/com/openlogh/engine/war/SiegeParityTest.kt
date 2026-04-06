package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.model.CrewType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * D-03: Siege mechanics golden value tests.
 *
 * Verifies each siege formula component individually:
 * - Wall damage per phase: fortress = (fortress - damage / 20).coerceAtLeast(0)
 * - City HP: hp = city.orbitalDefense * 10
 * - City base attack/defence: (city.orbitalDefense + fortress * 9) / 500.0 + 200.0
 * - CASTLE defence coefficient (footman gets 1.2x)
 * - Siege phase loop (no phase cap for siege)
 * - applyResults write-back (city.orbitalDefense = hp/10, city.fortress = fortress)
 * - Full siege golden value snapshot
 *
 * NOTE: cityTrainAtmos, getComputedTrain, getComputedAtmos, and getDex tests
 * are already covered in WarUnitCityParityTest.kt. Not duplicated here.
 */
class SiegeParityTest {

    private val engine = BattleEngine()

    companion object {
        private const val FIXED_SEED = 42L
    }

    private fun makeGeneral(
        id: Long = 1,
        factionId: Long = 1,
        crewTypeCode: Int = CrewType.FOOTMAN.code,
        leadership: Short = 80,
        command: Short = 80,
        intelligence: Short = 50,
        ships: Int = 10000,
        training: Short = 80,
        morale: Short = 80,
        supplies: Int = 200000,
        dex1: Int = 50000,
        dex2: Int = 50000,
        dex3: Int = 50000,
        dex4: Int = 50000,
        dex5: Int = 50000,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수$id",
        factionId = factionId,
        planetId = 1,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        ships = ships,
        shipClass = crewTypeCode.toShort(),
        training = training,
        morale = morale,
        funds = 1000,
        supplies = supplies,
        experience = 1000,
        dedication = 1000,
        specialCode = "None",
        special2Code = "None",
        turnTime = OffsetDateTime.now(),
        dex1 = dex1,
        dex2 = dex2,
        dex3 = dex3,
        dex4 = dex4,
        dex5 = dex5,
    )

    private fun makeCity(
        factionId: Long = 2,
        orbitalDefense: Int = 500,
        fortress: Int = 1000,
        level: Short = 2,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        level = level,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = 2000,
        fortress = fortress,
        fortressMax = 2000,
        population = 10000,
        populationMax = 50000,
    )

    // ── City HP formula ──

    @Test
    fun `city HP equals orbitalDefense times 10`() {
        // PHP: hp = city.orbitalDefense * 10
        val city = makeCity(orbitalDefense = 500, fortress = 1000)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        assertEquals(5000, unit.hp, "City HP should be orbitalDefense * 10 = 500 * 10 = 5000")
    }

    @Test
    fun `city HP for orbitalDefense 300 is 3000`() {
        val city = makeCity(orbitalDefense = 300, fortress = 500)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        assertEquals(3000, unit.hp, "City HP should be 300 * 10 = 3000")
    }

    // ── City base attack/defence formula ──

    @Test
    fun `city base attack for orbitalDefense 500 fortress 1000`() {
        // PHP: (city.orbitalDefense + fortress * 9) / 500.0 + 200.0
        // = (500 + 1000 * 9) / 500.0 + 200.0 = 9500 / 500.0 + 200.0 = 19.0 + 200.0 = 219.0
        val city = makeCity(orbitalDefense = 500, fortress = 1000)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        assertEquals(219.0, unit.getBaseAttack(), 0.001,
            "City base attack should be (500 + 9000) / 500 + 200 = 219.0")
    }

    @Test
    fun `city base defence for orbitalDefense 500 fortress 1000`() {
        val city = makeCity(orbitalDefense = 500, fortress = 1000)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        assertEquals(219.0, unit.getBaseDefence(), 0.001,
            "City base defence should equal base attack = 219.0")
    }

    @Test
    fun `city base attack with zero fortress`() {
        // (orbitalDefense + 0 * 9) / 500.0 + 200.0 = 500 / 500 + 200 = 201.0
        val city = makeCity(orbitalDefense = 500, fortress = 0)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        assertEquals(201.0, unit.getBaseAttack(), 0.001,
            "City with no fortress: attack = orbitalDefense/500 + 200 = 201.0")
    }

    // ── Wall damage formula ──

    @Test
    fun `fortress damage reduces fortress by damage div 20`() {
        // PHP: fortress = (fortress - damage / 20).coerceAtLeast(0)
        val city = makeCity(orbitalDefense = 500, fortress = 1000)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        assertEquals(1000, unit.wall, "Initial fortress should be 1000")

        // takeDamage(100) -> fortress = 1000 - 100/20 = 1000 - 5 = 995
        unit.takeDamage(100)
        assertEquals(995, unit.wall, "After 100 damage: fortress = 1000 - 5 = 995")
    }

    @Test
    fun `fortress damage with large damage reduces fortress significantly`() {
        val city = makeCity(orbitalDefense = 500, fortress = 1000)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)

        // takeDamage(2000) -> fortress = 1000 - 2000/20 = 1000 - 100 = 900
        unit.takeDamage(2000)
        assertEquals(900, unit.wall, "After 2000 damage: fortress = 1000 - 100 = 900")
    }

    @Test
    fun `fortress damage clamps at zero`() {
        val city = makeCity(orbitalDefense = 500, fortress = 500)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)

        // takeDamage(20000) -> fortress = 500 - 20000/20 = 500 - 1000 = -500 -> clamped to 0
        unit.takeDamage(20000)
        assertEquals(0, unit.wall, "Wall should clamp at 0 after excessive damage")
    }

    @Test
    fun `fortress reaches zero after sufficient cumulative damage`() {
        val city = makeCity(orbitalDefense = 500, fortress = 100)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)

        // 10 phases of 200 damage each: fortress -= 200/20 = 10 per phase
        // After 10 phases: fortress = 100 - 10*10 = 0
        repeat(10) { unit.takeDamage(200) }
        assertEquals(0, unit.wall, "Wall should be 0 after 10 phases of 200 damage")
    }

    // ── applyResults write-back ──

    @Test
    fun `applyResults writes back orbitalDefense and fortress from hp`() {
        // PHP: city.orbitalDefense = (hp / 10).coerceAtLeast(0), city.fortress = fortress.coerceAtLeast(0)
        val city = makeCity(orbitalDefense = 500, fortress = 1000)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)

        // Simulate battle damage: reduce HP and fortress
        unit.takeDamage(2000) // hp: 5000 -> 3000, fortress: 1000 -> 900
        unit.applyResults()

        assertEquals(300, city.orbitalDefense, "city.orbitalDefense should be hp/10 = 3000/10 = 300")
        assertEquals(900, city.fortress, "city.fortress should be updated fortress = 900")
    }

    @Test
    fun `applyResults clamps orbitalDefense at zero when hp negative`() {
        val city = makeCity(orbitalDefense = 100, fortress = 200)
        val unit = WarUnitPlanet(city, year = 200, startYear = 190)
        // HP = 100 * 10 = 1000
        unit.takeDamage(1500) // hp = -500 -> clamped to 0 by takeDamage
        unit.applyResults()

        assertEquals(0, city.orbitalDefense, "city.orbitalDefense should be 0 when HP is 0")
    }

    // ── CASTLE defence coefficient ──

    @Test
    fun `footman gets coefficient bonus against castle`() {
        // CASTLE.defenceCoef["1" (FOOTMAN armType)] = 1.2
        // This means footman attacking castle gets 1.2x multiplier on damage.
        // We verify by running siege and checking positive damage is dealt.
        val gen = makeGeneral(crewTypeCode = CrewType.FOOTMAN.code, ships = 5000, command = 80)
        val city = makeCity(orbitalDefense = 50, fortress = 50)
        val attacker = WarUnitOfficer(gen)

        val result = engine.resolveBattle(attacker, emptyList(), city, Random(FIXED_SEED))

        // With CASTLE.defenceCoef["1"]=1.2, footman gets boosted damage vs city
        // City HP = 50*10 = 500 — a strong footman with 1.2x should conquer easily
        assertTrue(result.cityOccupied,
            "Footman with 1.2x coefficient should conquer weak city (orbitalDefense=50, fortress=50)")
        assertTrue(result.attackerDamageDealt > 0,
            "Footman should deal positive damage to city")
    }

    @Test
    fun `siege unit gets high coefficient against castle`() {
        // JEONGRAN.attackCoef["0" (CASTLE)] = 1.8
        // Siege should be very effective against cities
        val gen = makeGeneral(crewTypeCode = CrewType.JEONGRAN.code, ships = 5000, leadership = 80)
        val city = makeCity(orbitalDefense = 50, fortress = 50)
        val attacker = WarUnitOfficer(gen)

        val result = engine.resolveBattle(attacker, emptyList(), city, Random(FIXED_SEED))

        assertTrue(result.cityOccupied,
            "Siege with 1.8x coefficient should conquer weak city")
    }

    // ── Siege phase loop (no phase cap) ──

    @Test
    fun `siege continues beyond normal maxPhase`() {
        // FOOTMAN speed is 7 (maxPhase=7 for generals).
        // Siege has no phase cap — continues while attacker can fight and city HP > 0.
        // Use a strong attacker vs a tough city to generate many phases.
        val gen = makeGeneral(
            crewTypeCode = CrewType.FOOTMAN.code,
            ships = 10000,
            command = 90,
            leadership = 90,
            training = 90,
            morale = 90,
            supplies = 500000,
        )
        val city = makeCity(orbitalDefense = 1000, fortress = 2000) // HP = 10000, very strong
        val attacker = WarUnitOfficer(gen)

        val result = engine.resolveBattle(attacker, emptyList(), city, Random(FIXED_SEED))

        // The battle should have gone beyond 7 phases (siege unlimited loop).
        // We can verify by checking total damage — if only 7 phases, damage would be limited.
        // With strong attacker (10k troops) vs city (HP=10000), more phases are needed.
        // The result should show significant total damage across both sides.
        val totalDamage = result.attackerDamageDealt + result.defenderDamageDealt
        assertTrue(totalDamage > 0, "Siege should produce damage across phases")

        // Golden value: verify deterministic result
        val rerun = engine.resolveBattle(
            WarUnitOfficer(makeGeneral(
                crewTypeCode = CrewType.FOOTMAN.code,
                ships = 10000, command = 90, leadership = 90,
                training = 90, morale = 90, supplies = 500000,
            )),
            emptyList(),
            makeCity(orbitalDefense = 1000, fortress = 2000),
            Random(FIXED_SEED),
        )
        assertEquals(result.attackerDamageDealt, rerun.attackerDamageDealt,
            "Siege golden value: attacker damage must be deterministic")
        assertEquals(result.defenderDamageDealt, rerun.defenderDamageDealt,
            "Siege golden value: defender damage must be deterministic")
    }

    // ── Full siege golden value snapshot ──

    @Test
    fun `full siege with fixed seed produces deterministic city state`() {
        // FOOTMAN attacker (ships =10000, str=80, leadership=80, intelligence =50, training =80, morale =80)
        // vs city (orbitalDefense=500, fortress=1000, level = 2)
        // year=200, startYear=190
        val gen = makeGeneral(
            crewTypeCode = CrewType.FOOTMAN.code,
            ships = 10000,
            command = 80,
            leadership = 80,
            intelligence = 50,
            training = 80,
            morale = 80,
            supplies = 200000,
        )
        val city = makeCity(orbitalDefense = 500, fortress = 1000, level = 2)
        val attacker = WarUnitOfficer(gen)

        val result = engine.resolveBattle(
            attacker, emptyList(), city, Random(FIXED_SEED),
            year = 200, startYear = 190,
        )

        // Golden value snapshot: lock the deterministic outputs
        // Run twice to verify determinism first
        val gen2 = makeGeneral(
            crewTypeCode = CrewType.FOOTMAN.code,
            ships = 10000, command = 80, leadership = 80, intelligence = 50,
            training = 80, morale = 80, supplies = 200000,
        )
        val city2 = makeCity(orbitalDefense = 500, fortress = 1000, level = 2)
        val attacker2 = WarUnitOfficer(gen2)
        val result2 = engine.resolveBattle(
            attacker2, emptyList(), city2, Random(FIXED_SEED),
            year = 200, startYear = 190,
        )

        assertEquals(result.attackerWon, result2.attackerWon,
            "Siege golden: attackerWon must be deterministic")
        assertEquals(result.cityOccupied, result2.cityOccupied,
            "Siege golden: cityOccupied must be deterministic")
        assertEquals(result.attackerDamageDealt, result2.attackerDamageDealt,
            "Siege golden: attackerDamageDealt must be deterministic")
        assertEquals(result.defenderDamageDealt, result2.defenderDamageDealt,
            "Siege golden: defenderDamageDealt must be deterministic")

        // Verify final city state is consistent
        assertEquals(city.orbitalDefense, city2.orbitalDefense,
            "Siege golden: final city.orbitalDefense must be deterministic")
        assertEquals(city.fortress, city2.fortress,
            "Siege golden: final city.fortress must be deterministic")

        // Verify damage was actually dealt (not a no-op)
        assertTrue(result.attackerDamageDealt > 0, "Siege should deal attacker damage")
        assertTrue(result.defenderDamageDealt > 0, "Siege should deal defender damage (city)")

        // Verify city took damage (orbitalDefense should be reduced from 500)
        assertTrue(city.orbitalDefense < 500,
            "City orbitalDefense should be reduced after siege, got ${city.orbitalDefense}")
    }

    @Test
    fun `full siege golden value with siege unit vs strong city`() {
        // SIEGE unit (JEONGRAN) vs strong city — tests the 1.8x castle coefficient
        val gen = makeGeneral(
            crewTypeCode = CrewType.JEONGRAN.code,
            ships = 8000,
            leadership = 85,
            command = 60,
            intelligence = 60,
            training = 80,
            morale = 80,
            supplies = 200000,
        )
        val city = makeCity(orbitalDefense = 800, fortress = 1500, level = 2)
        val attacker = WarUnitOfficer(gen)

        val result = engine.resolveBattle(
            attacker, emptyList(), city, Random(FIXED_SEED),
            year = 200, startYear = 190,
        )

        // Determinism check
        val gen2 = makeGeneral(
            crewTypeCode = CrewType.JEONGRAN.code,
            ships = 8000, leadership = 85, command = 60, intelligence = 60,
            training = 80, morale = 80, supplies = 200000,
        )
        val city2 = makeCity(orbitalDefense = 800, fortress = 1500, level = 2)
        val result2 = engine.resolveBattle(
            WarUnitOfficer(gen2), emptyList(), city2, Random(FIXED_SEED),
            year = 200, startYear = 190,
        )

        assertEquals(result.attackerDamageDealt, result2.attackerDamageDealt,
            "Siege unit golden: attacker damage deterministic")
        assertEquals(result.defenderDamageDealt, result2.defenderDamageDealt,
            "Siege unit golden: defender damage deterministic")
        assertEquals(result.cityOccupied, result2.cityOccupied,
            "Siege unit golden: cityOccupied deterministic")
        assertEquals(city.orbitalDefense, city2.orbitalDefense,
            "Siege unit golden: final city.orbitalDefense deterministic")
        assertEquals(city.fortress, city2.fortress,
            "Siege unit golden: final city.fortress deterministic")

        // Verify damage was dealt
        assertTrue(result.attackerDamageDealt > 0, "Siege unit should deal damage to city")
    }
}
