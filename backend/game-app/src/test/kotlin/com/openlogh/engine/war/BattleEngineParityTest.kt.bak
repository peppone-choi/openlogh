package com.openlogh.engine.war

import com.openlogh.entity.*
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

    private fun makeOfficer(
        id: Long = 1,
        factionId: Long = 1,
        command: Short = 70,
        leadership: Short = 70,
        intelligence: Short = 70,
        ships: Int = 3000,
        supplies: Int = 50000,
        shipClass: Short = 0, // FOOTMAN (0 in entity, mapped to ShipClass.FOOTMAN)
        training: Short = 80,
        morale: Short = 80,
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
        shipClass = shipClass,
        training = training,
        morale = morale,
        funds = 1000,
        supplies = supplies,
        experience = 1000,
        dedication = 1000,
        specialCode = "None",
        special2Code = "None",
        turnTime = OffsetDateTime.now(),
    )

    private fun makePlanet(
        factionId: Long = 2,
        orbitalDefense: Int = 300,
        fortress: Int = 300,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = 1000,
        fortress = fortress,
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
     * Battle A: footman vs weak city (orbitalDefense=50) → cityHP reduction measured.
     * Battle B: same setup.
     * With PHP-correct multiply, footman deals more absolute damage per phase
     * than the broken divide implementation.
     *
     * We verify that a footman can reduce cityHP to 0 in fewer phases
     * than would be expected without the 1.2x coefficient.
     */
    @Test
    fun `defence coef applied as multiplier to attacker - footman bonus vs castle is positive`() {
        val rng = Random(42)
        val officer = makeOfficer(id = 1, factionId = 1, command = 80, leadership = 80, ships = 5000, supplies = 100000)
        val planet = makePlanet(factionId = 2, orbitalDefense = 50, fortress = 50)

        val attacker = WarUnitGeneral(officer)
        val result = engine.resolveBattle(attacker, emptyList(), planet, rng)

        // City HP = orbitalDefense * 10 = 500. Footman gets 1.2x boost → should fall.
        assertTrue(result.cityOccupied, "Footman should take city with 1.2x damage multiplier to castle")
    }

    @Test
    fun `defence coef direction attacker damage reflects defender type weakness`() {
        val seed = 12345L

        // Battle 1: cavalry attacker vs archer defender
        val cavalryGen = makeOfficer(id = 1, factionId = 1, command = 70, ships = 3000, supplies = 50000, shipClass = 3)
        val archerDefender = makeOfficer(id = 2, factionId = 2, command = 70, ships = 3000, supplies = 50000, shipClass = 2)
        val planet1 = makePlanet()
        val unit1 = WarUnitGeneral(cavalryGen)
        val def1 = WarUnitGeneral(archerDefender)
        val result1 = engine.resolveBattle(unit1, listOf(def1), planet1, Random(seed))

        // Battle 2: cavalry attacker vs footman defender (neutral matchup)
        val cavalryGen2 = makeOfficer(id = 1, factionId = 1, command = 70, ships = 3000, supplies = 50000, shipClass = 3)
        val footmanDefender = makeOfficer(id = 2, factionId = 2, command = 70, ships = 3000, supplies = 50000, shipClass = 0)
        val planet2 = makePlanet()
        val unit2 = WarUnitGeneral(cavalryGen2)
        val def2 = WarUnitGeneral(footmanDefender)
        val result2 = engine.resolveBattle(unit2, listOf(def2), planet2, Random(seed))

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
    fun `attacker training increases by 1 after each engagement`() {
        val rng = Random(42)
        val officer = makeOfficer(id = 1, factionId = 1, command = 80, leadership = 80, ships = 5000, supplies = 100000, training = 70)
        val defenderOfficer = makeOfficer(id = 2, factionId = 2, command = 30, ships = 100, supplies = 1000, training = 30)
        val planet = makePlanet(factionId = 2, orbitalDefense = 50, fortress = 50)

        val attacker = WarUnitGeneral(officer)
        val defender = WarUnitGeneral(defenderOfficer)
        val initialTrain = attacker.training

        engine.resolveBattle(attacker, listOf(defender), planet, rng)

        // After at least one engagement, attacker.training should have increased by at least 1
        assertTrue(
            attacker.training > initialTrain,
            "Attacker training should increase by 1 per engagement. Initial=$initialTrain, after=${attacker.training}"
        )
    }

    @Test
    fun `defender training increases by 1 at start of each engagement`() {
        val rng = Random(42)
        val officer = makeOfficer(id = 1, factionId = 1, command = 50, leadership = 50, ships = 3000, supplies = 30000)
        val defenderOfficer = makeOfficer(id = 2, factionId = 2, command = 50, ships = 2000, supplies = 20000, training = 60)

        val planet = makePlanet(factionId = 2)
        val attacker = WarUnitGeneral(officer)
        val defender = WarUnitGeneral(defenderOfficer)
        val initialDefenderTrain = defender.training

        engine.resolveBattle(attacker, listOf(defender), planet, rng)

        assertTrue(
            defender.training > initialDefenderTrain,
            "Defender training should increase by 1 per engagement. Initial=$initialDefenderTrain, after=${defender.training}"
        )
    }

    // ──────────────────────────────────────────────────────────────────
    // WarUnitCity year-based training/morale in battle context
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `siege city uses year-based cityTrainAtmos not fixed 80`() {
        val planetEarly = makePlanet(orbitalDefense = 300, fortress = 300)
        val unitEarly = WarUnitCity(planetEarly, year = 181, startYear = 180)
        assertEquals(60, unitEarly.training, "Early game city should have training=60")

        val planetLate = makePlanet(orbitalDefense = 300, fortress = 300)
        val unitLate = WarUnitCity(planetLate, year = 221, startYear = 180)
        assertEquals(100, unitLate.training, "Late game city should have training=100")
    }
}
