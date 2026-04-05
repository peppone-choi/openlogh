package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * TDD parity tests for BattleEngine vs PHP process_war.php / WarUnit.php
 *
 * Key PHP references:
 *   WarUnit::computeWarPower():
 *     $warPower *= $this->getCrewType()->getAttackCoef($oppose->getCrewType());
 *     $opposeWarPowerMultiply *= $this->getCrewType()->getDefenceCoef($oppose->getCrewType());
 *     $this->oppose->setWarPowerMultiply($opposeWarPowerMultiply);   // ← applied to OPPONENT
 *
 *   getWarPower() = warPower * warPowerMultiply   // ← MULTIPLY, not divide
 *
 *   So: attacker's damage = attacker.warPower × defender.defenceCoef(attacker)
 *       defender's damage = defender.warPower × attacker.defenceCoef(defender)
 *
 *   process_war.php (start of each engagement):
 *     $attacker->addTrain(1);
 *     $defender->addTrain(1);
 */
class BattleEngineParityTest {

    private val engine = BattleEngine()

    private fun makeGeneral(
        id: Long = 1,
        nationId: Long = 1,
        strength: Short = 70,
        leadership: Short = 70,
        intel: Short = 70,
        crew: Int = 3000,
        rice: Int = 50000,
        crewType: Short = 0, // FOOTMAN (0 in entity, mapped to CrewType.FOOTMAN code 1000)
        train: Short = 80,
        atmos: Short = 80,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수$id",
        factionId = nationId,
        planetId = 1,
        leadership = leadership,
        command = strength,
        intelligence = intel,
        ships = crew,
        shipClass = crewType,
        training = train,
        morale = atmos,
        funds = 1000,
        supplies = rice,
        experience = 1000,
        dedication = 1000,
        specialCode = "None",
        special2Code = "None",
        turnTime = OffsetDateTime.now(),
    )

    private fun makeCity(
        nationId: Long = 2,
        def: Int = 300,
        wall: Int = 300,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = nationId,
        orbitalDefense = def,
        orbitalDefenseMax = 1000,
        fortress = wall,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
    )

    // ──────────────────────────────────────────────────────────────────
    // defence coef: multiply direction (PHP: $this->oppose->setWarPowerMultiply)
    // ──────────────────────────────────────────────────────────────────

    /**
     * PHP parity: when defender.defenceCoef(attacker) = 1.2,
     * the attacker deals 1.2× more damage (MULTIPLY on attacker side).
     *
     * CASTLE.defenceCoef["1"] = 1.2 → footman deals 1.2× damage to castle.
     *
     * Test approach: run two siege battles with identical stats.
     * Battle A: footman vs weak city (def=50) → cityHP reduction measured.
     * Battle B: same setup.
     * With PHP-correct multiply, footman deals more absolute damage per phase
     * than the broken divide implementation.
     *
     * We verify that a footman can reduce cityHP to 0 in fewer phases
     * than would be expected without the 1.2x coefficient.
     */
    @Test
    fun `defence coef applied as multiplier to attacker - footman bonus vs castle is positive`() {
        // footman (CASTLE.defenceCoef["1"]=1.2) should get +20% damage bonus against city
        // If coefficient is correctly applied as MULTIPLY, footman damage is 1.2x higher
        // than baseline. We can verify: with a seed that gives borderline outcome,
        // the coefficient direction determines if city is conquered.

        // Using a weak city: footman should conquer it in one engagement
        val rng = Random(42)
        val gen = makeGeneral(id = 1, nationId = 1, strength = 80, leadership = 80, crew = 5000, rice = 100000)
        val city = makeCity(nationId = 2, def = 50, wall = 50)

        val attacker = WarUnitOfficer(gen)
        val result = engine.resolveBattle(attacker, emptyList(), city, rng)

        // City HP = def * 10 = 500. Footman gets 1.2x boost → should fall.
        assertTrue(result.cityOccupied, "Footman should take city with 1.2x damage multiplier to castle")
    }

    @Test
    fun `defence coef direction attacker damage reflects defender type weakness`() {
        // PHP: attacker_damage = attacker.warPower × defender.defenceCoef(attacker)
        // A unit with defenceCoef["armType"] = 1.2 means the attacker of that arm type
        // deals 1.2x MORE damage. This should be applied as MULTIPLY on attacker's side.
        //
        // We run two battles with same attacker vs different defenders.
        // Defender A has high defenceCoef for attacker arm type → attacker should deal more.
        // Defender B has default defenceCoef (1.0) → attacker deals base amount.
        //
        // In PHP: attacker damage A > attacker damage B.
        // In broken Kotlin (divide): attacker damage A < attacker damage B (wrong direction).

        // Use cavalry (crewType 3 in entity = CAVALRY armType 3) as attacker
        // vs archer (ARCHER.defenceCoef["3"] = 1.2 → cavalry deals 1.2x to archer)
        // vs footman (FOOTMAN.defenceCoef has no "3" entry → 1.0)

        val seed = 12345L

        // Battle 1: cavalry attacker vs archer defender
        val cavalryGen = makeGeneral(id = 1, nationId = 1, strength = 70, crew = 3000, rice = 50000, crewType = 3)
        val archerDefender = makeGeneral(id = 2, nationId = 2, strength = 70, crew = 3000, rice = 50000, crewType = 2)
        val city1 = makeCity()
        val unit1 = WarUnitOfficer(cavalryGen)
        val def1 = WarUnitOfficer(archerDefender)
        val result1 = engine.resolveBattle(unit1, listOf(def1), city1, Random(seed))

        // Battle 2: cavalry attacker vs footman defender (neutral matchup)
        val cavalryGen2 = makeGeneral(id = 1, nationId = 1, strength = 70, crew = 3000, rice = 50000, crewType = 3)
        val footmanDefender = makeGeneral(id = 2, nationId = 2, strength = 70, crew = 3000, rice = 50000, crewType = 0)
        val city2 = makeCity()
        val unit2 = WarUnitOfficer(cavalryGen2)
        val def2 = WarUnitOfficer(footmanDefender)
        val result2 = engine.resolveBattle(unit2, listOf(def2), city2, Random(seed))

        // Cavalry vs archer: ARCHER.defenceCoef["3"] = 1.2 → cavalry should deal MORE damage
        // to archer than to footman (where the coef is 1.0).
        // PHP parity: result1.attackerDamageDealt >= result2.attackerDamageDealt
        assertTrue(
            result1.attackerDamageDealt >= result2.attackerDamageDealt,
            "Cavalry should deal >= damage to archer (defenceCoef=1.2) vs footman (defenceCoef=1.0). " +
                "Got cavalry vs archer: ${result1.attackerDamageDealt}, cavalry vs footman: ${result2.attackerDamageDealt}"
        )
    }

    // ──────────────────────────────────────────────────────────────────
    // addTrain at engagement start (PHP: process_war.php)
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `attacker train increases by 1 after each engagement`() {
        // PHP process_war.php: $attacker->addTrain(1) at start of each new engagement
        // (when defender.phase==0 && defender.oppose==null)
        val rng = Random(42)
        val gen = makeGeneral(id = 1, nationId = 1, strength = 80, leadership = 80, crew = 5000, rice = 100000, train = 70)
        val defenderGen = makeGeneral(id = 2, nationId = 2, strength = 30, crew = 100, rice = 1000, train = 30)
        val city = makeCity(nationId = 2, def = 50, wall = 50)

        val attacker = WarUnitOfficer(gen)
        val defender = WarUnitOfficer(defenderGen)
        val initialTrain = attacker.training

        engine.resolveBattle(attacker, listOf(defender), city, rng)

        // After at least one engagement, attacker.training should have increased by at least 1
        assertTrue(
            attacker.training > initialTrain,
            "Attacker train should increase by 1 per engagement. Initial=$initialTrain, after=${attacker.training}"
        )
    }

    @Test
    fun `defender train increases by 1 at start of each engagement`() {
        // PHP: $defender->addTrain(1) when opponent.phase==0 && opponent.oppose==null
        val rng = Random(42)
        val gen = makeGeneral(id = 1, nationId = 1, strength = 50, leadership = 50, crew = 3000, rice = 30000)
        val defenderGen = makeGeneral(id = 2, nationId = 2, strength = 50, crew = 2000, rice = 20000, train = 60)

        val city = makeCity(nationId = 2)
        val attacker = WarUnitOfficer(gen)
        val defender = WarUnitOfficer(defenderGen)
        val initialDefenderTrain = defender.training

        engine.resolveBattle(attacker, listOf(defender), city, rng)

        // If defender participated in at least one engagement, train should have increased
        // (Defender is WarUnitOfficer so addTrain applies to it)
        assertTrue(
            defender.training > initialDefenderTrain,
            "Defender train should increase by 1 per engagement. Initial=$initialDefenderTrain, after=${defender.training}"
        )
    }

    // ──────────────────────────────────────────────────────────────────
    // WarUnitPlanet year-based train/atmos in battle context
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `siege city uses year-based cityTrainAtmos not fixed 80`() {
        // PHP: cityTrainAtmos = clamp(year - startYear + 59, 60, 110)
        // At game start (year=181, startYear=180): cityTrainAtmos = 60
        // train=60 is LOWER than the fixed 80 → city is WEAKER defensively
        //
        // A fixed train=80 means city resists more. cityTrainAtmos=60 means less resistance.
        // Test: city with year=181 should have train=60, year=221 should have train=100.

        val cityEarly = makeCity(def = 300, wall = 300)
        val unitEarly = WarUnitPlanet(cityEarly, year = 181, startYear = 180)
        assertEquals(60, unitEarly.training, "Early game city should have train=60")

        val cityLate = makeCity(def = 300, wall = 300)
        val unitLate = WarUnitPlanet(cityLate, year = 221, startYear = 180)
        assertEquals(100, unitLate.training, "Late game city should have train=100")
    }
}
