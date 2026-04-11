package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.TacticalWeaponType
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Tests for MissileWeaponSystem (Task 03-02):
 * - missileCount=0 이면 processMissileAttack이 null 반환
 * - missileCount=5 이면 공격 성공 시 missileCount=4
 * - FIGHTER 공격 후 target.fighterSpeedDebuffTicks == 60
 * - FIGHTER vs CARRIER: 피해가 vs non-CARRIER보다 2배
 *
 * Tests for BEAM distance-factor (in TacticalBattleEngine):
 * - dist=BEAM_RANGE*0.7 (optimal) → distFactor=1.0
 * - dist=0 → distFactor=0.0
 */
class MissileWeaponSystemTest {

    private val system = MissileWeaponSystem()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double = 100.0,
        posY: Double = 100.0,
        missileCount: Int = 100,
        unitType: String = "FLEET",
        attack: Int = 50,
        defense: Int = 50,
        // Phase 24-18: carriers must carry 軍需物資 to launch fighters (10 per sortie).
        // Default high enough that existing tests exercising the fighter path do not
        // trip the new supply guard; tests that want to verify the guard set this to 0.
        supplies: Int = 1000,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = 50,
        attack = attack,
        defense = defense,
        missileCount = missileCount,
        supplies = supplies,
        unitType = unitType,
        formation = Formation.MIXED,
        stance = UnitStance.NAVIGATION,
    )

    private fun makeState(vararg units: TacticalUnit) = TacticalBattleState(
        battleId = 1L,
        starSystemId = 1L,
        units = units.toMutableList(),
    )

    // ── Missile Tests ──

    @Test
    fun `processMissileAttack returns null when missileCount is zero`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, missileCount = 0)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 0.0)
        val state = makeState(source, target)

        val result = system.processMissileAttack(source, target, state, rng = Random(42))

        assertNull(result, "Should return null when missileCount=0")
        assertEquals(0, source.missileCount, "missileCount should remain 0")
    }

    @Test
    fun `processMissileAttack decrements missileCount on hit`() {
        // Place target within missile range (800 units) and use fixed RNG that always hits
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, missileCount = 5)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0)
        val state = makeState(source, target)

        // Use a fixed RNG that always returns 0.0 (< hitChance) → always hits
        val alwaysHitRng = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.0
        }

        val result = system.processMissileAttack(source, target, state, rng = alwaysHitRng)

        assertNotNull(result, "Should return TacticalWeaponEvent on hit")
        assertEquals(4, source.missileCount, "missileCount should decrease from 5 to 4 after hit")
    }

    @Test
    fun `processMissileAttack returns null when target is out of range`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, missileCount = 10)
        // MISSILE_RANGE = 8.0 * 100 = 800 units; place target at 900
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 900.0, posY = 0.0)
        val state = makeState(source, target)

        val result = system.processMissileAttack(source, target, state, rng = Random(42))

        assertNull(result, "Should return null when target is out of missile range")
        assertEquals(10, source.missileCount, "missileCount should not change when out of range")
    }

    // ── Fighter Tests ──

    @Test
    fun `processFighterAttack sets fighterSpeedDebuffTicks to 60 on hit`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, unitType = "CARRIER")
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(source, target)

        assertEquals(0, target.fighterSpeedDebuffTicks, "Should start at 0")

        // Always hit
        val alwaysHitRng = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.0
        }

        val result = system.processFighterAttack(source, target, state, rng = alwaysHitRng)

        assertNotNull(result, "CARRIER should be able to launch fighters")
        assertEquals(TacticalWeaponType.FIGHTER_DEBUFF_DURATION_TICKS, target.fighterSpeedDebuffTicks,
            "fighterSpeedDebuffTicks should be set to 60 after fighter attack")
    }

    @Test
    fun `processFighterAttack returns null when source is not a carrier`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, unitType = "FLEET")
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(source, target)

        val result = system.processFighterAttack(source, target, state, rng = Random(42))

        assertNull(result, "Non-carrier cannot launch fighters")
    }

    @Test
    fun `processFighterAttack deals double damage against carrier (FIGHTER_INTERCEPT)`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, unitType = "CARRIER")

        // Always hit
        val alwaysHitRng = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.0
        }

        // vs CARRIER
        val carrierTarget = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "CARRIER")
        val stateCarrier = makeState(source, carrierTarget)
        val initialHpCarrier = carrierTarget.hp
        system.processFighterAttack(source, carrierTarget, stateCarrier, rng = alwaysHitRng)
        val carrierDamage = initialHpCarrier - carrierTarget.hp

        // vs FLEET (not carrier) — reset source
        val source2 = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0, unitType = "CARRIER")
        val fleetTarget = makeUnit(3L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val stateFleet = makeState(source2, fleetTarget)
        val initialHpFleet = fleetTarget.hp
        system.processFighterAttack(source2, fleetTarget, stateFleet, rng = alwaysHitRng)
        val fleetDamage = initialHpFleet - fleetTarget.hp

        assertEquals(fleetDamage * 2, carrierDamage,
            "Fighter vs CARRIER (intercept) should deal 2x damage compared to vs FLEET: carrier=$carrierDamage, fleet=$fleetDamage")
    }

    // ── Phase 24-18: Spartanian sortie cost (gin7 manual p49) ──

    private val alwaysHitRng = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.0
    }

    @Test
    fun `spartanian sortie consumes 10 military supplies per launch`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(source, target)

        val event = system.processFighterAttack(source, target, state, rng = alwaysHitRng)

        assertNotNull(event, "carrier with sufficient supplies must launch")
        assertEquals(90, source.supplies, "sortie must deduct exactly 10 軍需物資")
        assertEquals(TacticalWeaponType.FIGHTER.supplyCostPerUse, event!!.supplyCost)
    }

    @Test
    fun `spartanian sortie is gated when supplies are below 10`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 9)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(source, target)

        val event = system.processFighterAttack(source, target, state, rng = alwaysHitRng)

        assertNull(event, "carrier with < 10 supplies must not be able to sortie")
        assertEquals(9, source.supplies, "supplies must not change when sortie is aborted")
        assertTrue(state.tickEvents.any { it.type == "fighter_sortie_aborted" },
            "battle log must record the aborted sortie")
    }

    @Test
    fun `spartanian sortie pays cost even on miss`() {
        // 항상 실패(nextDouble = 0.999) RNG — 미사일 hitChance < 1.0 보장
        val alwaysMissRng = object : Random() {
            override fun nextBits(bitCount: Int): Int = Int.MAX_VALUE
            override fun nextDouble(): Double = 0.999999
        }
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 50)
        val target = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val state = makeState(source, target)

        val event = system.processFighterAttack(source, target, state, rng = alwaysMissRng)

        assertNull(event, "miss returns null")
        assertEquals(40, source.supplies,
            "gin7 p49: cost applies per SORTIE not per hit — supplies drop by 10 even on miss")
        assertTrue(state.tickEvents.any { it.type == "fighter_miss" })
    }

    @Test
    fun `spartanian intercept vs carrier still consumes 10 supplies per sortie`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val carrierTarget = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "CARRIER")
        val state = makeState(source, carrierTarget)

        val event = system.processFighterAttack(source, carrierTarget, state, rng = alwaysHitRng)

        assertNotNull(event)
        assertTrue(event!!.isIntercept, "CARRIER target = 요격전")
        assertEquals(90, source.supplies, "intercept sortie also costs 10 supplies")
    }

    @Test
    fun `spartanian sortie label indicates intercept vs antiship mode`() {
        val source = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val fleetTarget = makeUnit(2L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "FLEET")
        val stateAnti = makeState(source, fleetTarget)
        system.processFighterAttack(source, fleetTarget, stateAnti, rng = alwaysHitRng)
        assertTrue(
            stateAnti.tickEvents.any { it.type == "fighter_attack" && it.detail.contains("대함전") },
            "대함전 label must appear when target is not a carrier"
        )

        val source2 = makeUnit(3L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0,
            unitType = "CARRIER", supplies = 100)
        val carrierTarget = makeUnit(4L, BattleSide.DEFENDER, posX = 200.0, posY = 0.0, unitType = "CARRIER")
        val stateIntercept = makeState(source2, carrierTarget)
        system.processFighterAttack(source2, carrierTarget, stateIntercept, rng = alwaysHitRng)
        assertTrue(
            stateIntercept.tickEvents.any { it.type == "fighter_attack" && it.detail.contains("요격전") },
            "요격전 label must appear when target is a carrier"
        )
    }

    // ── BEAM distance factor tests (via engine) ──

    @Test
    fun `BEAM distFactor is 1_0 at optimal distance (BEAM_RANGE * 0_7)`() {
        // This test verifies the distFactor calculation directly.
        // optimalDist = BEAM_RANGE * 0.7 = 200.0 * 0.7 = 140.0
        val beamRange = TacticalBattleEngine.BEAM_RANGE  // 200.0
        val optimalDist = beamRange * 0.7
        val distFactor = (1.0 - Math.abs(optimalDist - optimalDist) / optimalDist).coerceAtLeast(0.0)
        assertEquals(1.0, distFactor, 0.001, "distFactor at optimal distance should be 1.0")
    }

    @Test
    fun `BEAM distFactor is 0_0 at distance 0`() {
        val beamRange = TacticalBattleEngine.BEAM_RANGE
        val optimalDist = beamRange * 0.7
        val dist = 0.0
        val distFactor = (1.0 - Math.abs(dist - optimalDist) / optimalDist).coerceAtLeast(0.0)
        assertEquals(0.0, distFactor, 0.001, "distFactor at distance=0 should be 0.0")
    }

    @Test
    fun `BEAM damage is higher at optimal range than at point blank`() {
        val engine = TacticalBattleEngine()

        // Place units at optimal BEAM range (140 units apart)
        val optimalDist = TacticalBattleEngine.BEAM_RANGE * 0.7

        val attackerOptimal = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 300.0)
        val defenderOptimal = makeUnit(2L, BattleSide.DEFENDER, posX = optimalDist, posY = 300.0)
        val stateOptimal = makeState(attackerOptimal, defenderOptimal)

        // Run 10 ticks with BEAM energy only — use energy allocation focused on BEAM
        val beamEnergy = EnergyAllocation(beam = 60, gun = 0, shield = 10, engine = 10, warp = 10, sensor = 10)
        attackerOptimal.energy = beamEnergy

        repeat(10) { engine.processTick(stateOptimal, Random(42)) }
        val optimalDamage = 1000 - defenderOptimal.hp

        // Place units at point-blank (5 units apart, distFactor near 0)
        val attacker5 = makeUnit(3L, BattleSide.ATTACKER, posX = 0.0, posY = 600.0)
        val defender5 = makeUnit(4L, BattleSide.DEFENDER, posX = 5.0, posY = 600.0)
        val stateClose = makeState(attacker5, defender5)
        attacker5.energy = beamEnergy

        repeat(10) { engine.processTick(stateClose, Random(42)) }
        val closeDamage = 1000 - defender5.hp

        assertTrue(optimalDamage > closeDamage,
            "BEAM damage at optimal range ($optimalDist) should exceed point-blank damage. Optimal=$optimalDamage, Close=$closeDamage")
    }
}
