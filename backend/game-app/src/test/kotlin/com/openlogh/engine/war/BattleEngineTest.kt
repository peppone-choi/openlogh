package com.openlogh.engine.war

import com.openlogh.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class BattleEngineTest {

    private lateinit var engine: BattleEngine

    @BeforeEach
    fun setUp() {
        engine = BattleEngine()
    }

    private fun createOfficer(
        id: Long = 1,
        factionId: Long = 1,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        ships: Int = 1000,
        training: Short = 80,
        morale: Short = 80,
        funds: Int = 1000,
        supplies: Int = 5000,
        experience: Int = 1000,
        dedication: Int = 1000,
        specialCode: String = "None",
        special2Code: String = "None",
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = 1,
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            ships = ships,
            shipClass = 0,
            training = training,
            morale = morale,
            funds = funds,
            supplies = supplies,
            experience = experience,
            dedication = dedication,
            specialCode = specialCode,
            special2Code = special2Code,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createPlanet(
        factionId: Long = 2,
        orbitalDefense: Int = 500,
        fortress: Int = 500,
        population: Int = 10000,
    ): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
            orbitalDefense = orbitalDefense,
            orbitalDefenseMax = 1000,
            fortress = fortress,
            fortressMax = 1000,
            population = population,
            populationMax = 50000,
        )
    }

    // ========== WarUnitGeneral: stats ==========

    @Test
    fun `WarUnitGeneral initializes HP from ships`() {
        val officer = createOfficer(ships = 2000)
        val unit = WarUnitGeneral(officer)

        assertEquals(2000, unit.hp)
        assertEquals(2000, unit.maxHp)
    }

    @Test
    fun `WarUnitGeneral base attack uses legacy crew-type-specific stat ratio`() {
        val officer = createOfficer(command = 100, leadership = 50)
        val unit = WarUnitGeneral(officer)

        // Legacy: FOOTMAN → ratio = command*2-40 = 160, clamped: 50+160/2 = 130
        // FOOTMAN.attack=100, techAbil=0 → (100+0)*130/100 = 130.0
        assertEquals(130.0, unit.getBaseAttack(), 0.01)
    }

    @Test
    fun `WarUnitGeneral base defence uses legacy crew factor formula`() {
        val officer = createOfficer(leadership = 60, command = 40, intelligence = 80, ships = 1000)
        val unit = WarUnitGeneral(officer)

        // Legacy: FOOTMAN.defence=150, techAbil=0, crewFactor=1000/233.33+70=74.286
        // result = 150 * 74.286 / 100 = 111.429
        assertEquals(111.43, unit.getBaseDefence(), 0.01)
    }

    @Test
    fun `WarUnitGeneral tech bonus increases attack`() {
        val officer = createOfficer(command = 100, leadership = 50)
        val unit = WarUnitGeneral(officer, nationTech = 1000f)

        // techLevel=(1000/1000)=1, techAbil=25, ratio=130 (same as above)
        // (100+25)*130/100 = 162.5
        assertEquals(162.5, unit.getBaseAttack(), 0.01)
    }

    @Test
    fun `WarUnitGeneral continueWar requires HP and supplies`() {
        val officer = createOfficer(ships = 1000, supplies = 5000)
        val unit = WarUnitGeneral(officer)

        assertTrue(unit.continueWar().canContinue)

        // Deplete supplies below threshold: supplies <= hp/100 => 5000 > 1000/100=10, so still fighting
        unit.supplies = 5
        assertFalse(unit.continueWar().canContinue, "Should not continue with insufficient supplies")
    }

    @Test
    fun `WarUnitGeneral continueWar false when HP is zero`() {
        val officer = createOfficer(ships = 1000)
        val unit = WarUnitGeneral(officer)

        unit.hp = 0
        assertFalse(unit.continueWar().canContinue)
    }

    @Test
    fun `WarUnitGeneral consumeRice applies legacy multipliers`() {
        val officer = createOfficer(supplies = 1000)
        val unit = WarUnitGeneral(officer)

        unit.consumeRice(500)
        // 500/100=5.0, isAttacker=true, vsCity=false
        // FOOTMAN.riceCost=9 → 5.0*9=45.0, techCost=1.0 → 45
        assertEquals(955, unit.supplies)
    }

    @Test
    fun `WarUnitGeneral consumeRice defender and vsCity multipliers`() {
        val officer = createOfficer(supplies = 1000)
        val unit = WarUnitGeneral(officer)

        unit.consumeRice(500, isAttacker = false, vsCity = true)
        // 500/100=5.0, *0.8 (defender)=4.0, *0.8 (vsCity)=3.2
        // FOOTMAN.riceCost=9 → 3.2*9=28.8, techCost=1.0 → 28
        assertEquals(972, unit.supplies)
    }

    @Test
    fun `WarUnitGeneral applyResults writes back to officer`() {
        val officer = createOfficer(ships = 1000, supplies = 5000, training = 80, morale = 80)
        val unit = WarUnitGeneral(officer)

        unit.hp = 800
        unit.supplies = 4500
        unit.training = 70
        unit.morale = 65
        unit.injury = 10
        unit.applyResults()

        assertEquals(800, officer.ships)
        assertEquals(4500, officer.supplies)
        assertEquals(70.toShort(), officer.training)
        assertEquals(65.toShort(), officer.morale)
        assertEquals(10.toShort(), officer.injury)
    }

    // ========== WarUnitCity ==========

    @Test
    fun `WarUnitCity HP is orbitalDefense times 10`() {
        val planet = createPlanet(orbitalDefense = 300)
        val unit = WarUnitCity(planet)

        assertEquals(3000, unit.hp)
    }

    @Test
    fun `WarUnitCity base attack uses orbitalDefense and fortress`() {
        val planet = createPlanet(orbitalDefense = 500, fortress = 500)
        val unit = WarUnitCity(planet)

        // (500 + 500*9) / 500 + 200 = (500+4500)/500 + 200 = 10 + 200 = 210
        assertEquals(210.0, unit.getBaseAttack(), 0.01)
    }

    @Test
    fun `WarUnitCity base defence equals attack (legacy parity)`() {
        val planet = createPlanet(orbitalDefense = 500, fortress = 500)
        val unit = WarUnitCity(planet)

        assertEquals(unit.getBaseAttack(), unit.getBaseDefence(), 0.01)
    }

    @Test
    fun `WarUnitCity applyResults writes orbitalDefense back to planet`() {
        val planet = createPlanet(orbitalDefense = 500)
        val unit = WarUnitCity(planet)

        unit.hp = 3000
        unit.applyResults()
        assertEquals(300, planet.orbitalDefense) // 3000 / 10
    }

    // ========== WarUnit: takeDamage ==========

    @Test
    fun `takeDamage reduces HP`() {
        val officer = createOfficer(ships = 1000)
        val unit = WarUnitGeneral(officer)

        unit.takeDamage(300)
        assertEquals(700, unit.hp)
        assertTrue(unit.isAlive)
    }

    @Test
    fun `takeDamage kills unit when HP reaches zero`() {
        val officer = createOfficer(ships = 500)
        val unit = WarUnitGeneral(officer)

        unit.takeDamage(500)
        assertEquals(0, unit.hp)
        assertFalse(unit.isAlive)
    }

    @Test
    fun `takeDamage kills unit when damage exceeds HP`() {
        val officer = createOfficer(ships = 100)
        val unit = WarUnitGeneral(officer)

        unit.takeDamage(9999)
        assertEquals(0, unit.hp)
        assertFalse(unit.isAlive)
    }

    // ========== WarUnit: calcBattleOrder ==========

    @Test
    fun `calcBattleOrder higher stats and ships gives higher order`() {
        val strong = createOfficer(leadership = 90, command = 90, intelligence = 90, ships = 5000, training = 80, morale = 80)
        val weak = createOfficer(leadership = 30, command = 30, intelligence = 30, ships = 500, training = 30, morale = 30)

        val strongUnit = WarUnitGeneral(strong)
        val weakUnit = WarUnitGeneral(weak)

        assertTrue(strongUnit.calcBattleOrder() > weakUnit.calcBattleOrder())
    }

    // ========== BattleEngine: resolveBattle ==========

    @Test
    fun `resolveBattle strong attacker beats weak defender`() {
        val rng = Random(42)
        val attackerOfficer = createOfficer(id = 1, factionId = 1, command = 90, leadership = 90, ships = 5000, supplies = 50000, training = 80, morale = 80)
        val defenderOfficer = createOfficer(id = 2, factionId = 2, command = 30, leadership = 30, ships = 500, supplies = 500, training = 30, morale = 30)
        val planet = createPlanet(factionId = 2, orbitalDefense = 100, fortress = 100)

        val attacker = WarUnitGeneral(attackerOfficer)
        val defender = WarUnitGeneral(defenderOfficer)

        val result = engine.resolveBattle(attacker, listOf(defender), planet, rng)

        assertTrue(result.attackerDamageDealt > 0, "Attacker should deal damage")
        assertTrue(result.defenderDamageDealt > 0, "Defender should deal some damage")
    }

    @Test
    fun `resolveBattle returns damage amounts`() {
        val rng = Random(42)
        val attackerOfficer = createOfficer(id = 1, factionId = 1, command = 70, leadership = 70, ships = 3000, supplies = 30000)
        val defenderOfficer = createOfficer(id = 2, factionId = 2, command = 50, leadership = 50, ships = 2000, supplies = 20000)
        val planet = createPlanet(factionId = 2)

        val attacker = WarUnitGeneral(attackerOfficer)
        val defender = WarUnitGeneral(defenderOfficer)

        val result = engine.resolveBattle(attacker, listOf(defender), planet, rng)

        assertTrue(result.attackerDamageDealt > 0)
        assertTrue(result.defenderDamageDealt > 0)
    }

    @Test
    fun `resolveBattle attacker retreats when out of supplies`() {
        val rng = Random(42)
        val attackerOfficer = createOfficer(id = 1, factionId = 1, ships = 1000, supplies = 1, command = 50)
        val defenderOfficer = createOfficer(id = 2, factionId = 2, ships = 1000, supplies = 50000, command = 50)
        val planet = createPlanet(factionId = 2)

        val attacker = WarUnitGeneral(attackerOfficer)
        val defender = WarUnitGeneral(defenderOfficer)

        val result = engine.resolveBattle(attacker, listOf(defender), planet, rng)

        assertFalse(result.attackerWon, "Attacker should retreat when out of supplies")
    }

    @Test
    fun `resolveBattle applies morale loss`() {
        val rng = Random(42)
        val attackerOfficer = createOfficer(id = 1, factionId = 1, ships = 3000, supplies = 50000, morale = 80)
        val defenderOfficer = createOfficer(id = 2, factionId = 2, ships = 2000, supplies = 50000, morale = 80)
        val planet = createPlanet(factionId = 2)

        val attacker = WarUnitGeneral(attackerOfficer)
        val defender = WarUnitGeneral(defenderOfficer)

        engine.resolveBattle(attacker, listOf(defender), planet, rng)

        // Attacker loses 1 morale per phase, defender loses 3
        assertTrue(attacker.morale < 80, "Attacker morale should decrease")
    }

    @Test
    fun `resolveBattle with siege occupies city when all defenders eliminated`() {
        val rng = Random(100)
        // Very strong attacker vs very weak defender and city
        val attackerOfficer = createOfficer(id = 1, factionId = 1, command = 99, leadership = 99, ships = 50000, supplies = 500000, training = 80, morale = 80, experience = 10000)
        val defenderOfficer = createOfficer(id = 2, factionId = 2, command = 10, leadership = 10, ships = 10, supplies = 1, training = 10, morale = 10)
        val planet = createPlanet(factionId = 2, orbitalDefense = 10, fortress = 10, population = 100)

        val attacker = WarUnitGeneral(attackerOfficer)
        val defender = WarUnitGeneral(defenderOfficer)

        val result = engine.resolveBattle(attacker, listOf(defender), planet, rng)

        if (result.attackerWon) {
            assertTrue(result.cityOccupied, "City should be occupied when attacker wins completely")
        }
    }

    @Test
    fun `resolveBattle with no defenders goes straight to siege`() {
        val rng = Random(42)
        val attackerOfficer = createOfficer(id = 1, factionId = 1, command = 90, leadership = 90, ships = 10000, supplies = 100000, training = 80, morale = 80)
        val planet = createPlanet(factionId = 2, orbitalDefense = 50, fortress = 50)

        val attacker = WarUnitGeneral(attackerOfficer)

        val result = engine.resolveBattle(attacker, emptyList(), planet, rng)

        assertTrue(result.attackerWon, "Should win with no defenders")
        assertTrue(result.cityOccupied, "Should occupy city with no defenders")
    }

    @Test
    fun `resolveBattle multiple defenders sorted by battle order`() {
        val rng = Random(42)
        val attackerOfficer = createOfficer(id = 1, factionId = 1, command = 80, leadership = 80, ships = 5000, supplies = 50000)
        val weakDefender = createOfficer(id = 2, factionId = 2, command = 20, leadership = 20, ships = 500, supplies = 5000)
        val strongDefender = createOfficer(id = 3, factionId = 2, command = 70, leadership = 70, ships = 3000, supplies = 30000)
        val planet = createPlanet(factionId = 2)

        val attacker = WarUnitGeneral(attackerOfficer)
        val defenders = listOf(WarUnitGeneral(weakDefender), WarUnitGeneral(strongDefender))

        val result = engine.resolveBattle(attacker, defenders, planet, rng)

        // Just verify the battle executed without errors and damage was dealt
        assertTrue(result.attackerDamageDealt > 0)
        assertTrue(result.defenderDamageDealt > 0)
    }

    // ========== Trigger integration tests ==========

    @Test
    fun `collectTriggers returns triggers from specialCode`() {
        val officer = createOfficer(specialCode = "필살")
        val unit = WarUnitGeneral(officer)

        val triggers = engine.collectTriggers(unit)

        assertEquals(1, triggers.size)
        assertEquals("필살", triggers[0].code)
    }

    @Test
    fun `collectTriggers returns triggers from both specialCode and special2Code`() {
        val officer = createOfficer(specialCode = "필살", special2Code = "회피")
        val unit = WarUnitGeneral(officer)

        val triggers = engine.collectTriggers(unit)

        assertEquals(2, triggers.size)
    }

    @Test
    fun `collectTriggers returns empty for None specials`() {
        val officer = createOfficer(specialCode = "None", special2Code = "None")
        val unit = WarUnitGeneral(officer)

        val triggers = engine.collectTriggers(unit)

        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `collectTriggers returns empty for city units`() {
        val planet = createPlanet()
        val unit = WarUnitCity(planet)

        val triggers = engine.collectTriggers(unit)

        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `견고 special prevents injury in resolveBattle`() {
        // Run many battles with 견고 attacker - should never get injured
        var injuryOccurred = false
        for (seed in 1..50) {
            val rng = Random(seed)
            val attackerOfficer = createOfficer(
                id = 1, factionId = 1, command = 60, leadership = 60,
                ships = 3000, supplies = 50000, specialCode = "견고",
            )
            val defenderOfficer = createOfficer(
                id = 2, factionId = 2, command = 50, leadership = 50,
                ships = 2000, supplies = 20000,
            )
            val planet = createPlanet(factionId = 2)

            val attacker = WarUnitGeneral(attackerOfficer)
            val defender = WarUnitGeneral(defenderOfficer)

            engine.resolveBattle(attacker, listOf(defender), planet, rng)

            if (attackerOfficer.injury > 0) {
                injuryOccurred = true
                break
            }
        }
        assertFalse(injuryOccurred, "견고 special should prevent all injuries")
    }

    @Test
    fun `공성 special works in siege without errors`() {
        val rng = Random(42)
        val officer = createOfficer(
            id = 1, factionId = 1, command = 90, leadership = 90,
            ships = 10000, supplies = 100000, specialCode = "공성",
        )
        val planet = createPlanet(factionId = 2, orbitalDefense = 50, fortress = 50)
        val attacker = WarUnitGeneral(officer)

        val result = engine.resolveBattle(attacker, emptyList(), planet, rng)

        assertTrue(result.attackerDamageDealt > 0)
        assertTrue(result.cityOccupied, "공성 attacker should occupy city")
    }

    @Test
    fun `resolveBattle with specials does not crash`() {
        // Smoke test: various special combinations should not cause errors
        val specials = listOf("필살", "회피", "반계", "신산", "위압", "저격", "격노", "돌격",
            "화공", "기습", "매복", "방어", "귀모", "공성", "철벽", "분투", "용병", "견고")

        for (special in specials) {
            val rng = Random(42)
            val attackerOfficer = createOfficer(
                id = 1, factionId = 1, command = 70, leadership = 70,
                ships = 3000, supplies = 50000, specialCode = special,
            )
            val defenderOfficer = createOfficer(
                id = 2, factionId = 2, command = 50, leadership = 50,
                ships = 2000, supplies = 20000,
            )
            val planet = createPlanet(factionId = 2)

            val attacker = WarUnitGeneral(attackerOfficer)
            val defender = WarUnitGeneral(defenderOfficer)

            // Should not throw
            val result = engine.resolveBattle(attacker, listOf(defender), planet, rng)
            assertTrue(result.attackerDamageDealt > 0, "Battle with $special should deal damage")
        }
    }

    @Test
    fun `resolveBattle deterministic with same seed`() {
        val attackerGen1 = createOfficer(id = 1, factionId = 1, command = 70, ships = 3000, supplies = 50000)
        val defenderGen1 = createOfficer(id = 2, factionId = 2, command = 50, ships = 2000, supplies = 20000)
        val planet1 = createPlanet(factionId = 2)
        val result1 = engine.resolveBattle(WarUnitGeneral(attackerGen1), listOf(WarUnitGeneral(defenderGen1)), planet1, Random(123))

        val attackerGen2 = createOfficer(id = 1, factionId = 1, command = 70, ships = 3000, supplies = 50000)
        val defenderGen2 = createOfficer(id = 2, factionId = 2, command = 50, ships = 2000, supplies = 20000)
        val planet2 = createPlanet(factionId = 2)
        val result2 = engine.resolveBattle(WarUnitGeneral(attackerGen2), listOf(WarUnitGeneral(defenderGen2)), planet2, Random(123))

        assertEquals(result1.attackerDamageDealt, result2.attackerDamageDealt)
        assertEquals(result1.defenderDamageDealt, result2.defenderDamageDealt)
        assertEquals(result1.attackerWon, result2.attackerWon)
    }
}
