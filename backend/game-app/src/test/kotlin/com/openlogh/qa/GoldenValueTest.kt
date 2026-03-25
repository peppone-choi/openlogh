package com.openlogh.qa

import com.openlogh.engine.war.BattleEngine
import com.openlogh.engine.war.getDexLog
import com.openlogh.engine.war.WarUnitGeneral
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.entity.General
import com.openlogh.entity.City
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Golden value regression tests.
 * These lock in exact expected outputs derived from PHP parity analysis.
 *
 * Legacy samguk command golden values (징병, 모병 blend formulas) removed
 * along with the dead code classes they tested.
 * Battle engine golden values retained.
 */
@DisplayName("Golden Value Regression Tests")
class GoldenValueTest {

    // ──────────────────────────────────────────────────
    // Battle Engine dex golden values
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BattleEngine dex damage golden values")
    inner class DexDamage {

        @Test
        @DisplayName("dex5 advantage yields higher dexLog ratio")
        fun `dex5 advantage`() {
            val log = getDexLog(1000, 200)
            assertTrue(log > 1.0, "Higher dex should produce log > 1.0, got $log")
        }

        @Test
        @DisplayName("equal dex produces dexLog = 1.0")
        fun `equal dex`() {
            val log = getDexLog(500, 500)
            assertEquals(1.0, log, 0.0001)
        }

        @Test
        @DisplayName("lower dex produces dexLog < 1.0")
        fun `lower dex`() {
            val log = getDexLog(200, 1000)
            assertTrue(log < 1.0, "Lower dex should produce log < 1.0, got $log")
        }
    }

    @Nested
    @DisplayName("BattleEngine determinism")
    inner class BattleDeterminism {

        @Test
        @DisplayName("same seed produces identical battle result")
        fun `battle determinism`() {
            val engine = BattleEngine()

            fun makeAttacker() = WarUnitGeneral(General(
                id = 1, worldId = 1, name = "공격자",
                nationId = 1, cityId = 1,
                strength = 80, crew = 1000, crewType = 0,
                train = 70, atmos = 70,
                turnTime = OffsetDateTime.now(),
            ).apply { dex5 = 500 })

            fun makeDefender() = WarUnitGeneral(General(
                id = 2, worldId = 1, name = "방어자",
                nationId = 2, cityId = 2,
                strength = 70, crew = 1000, crewType = 0,
                train = 60, atmos = 60,
                turnTime = OffsetDateTime.now(),
            ).apply { dex5 = 400 })

            fun makeCity() = City(
                id = 2, worldId = 1, name = "행성",
                nationId = 2, supplyState = 1,
            )

            val r1 = engine.resolveBattle(
                attacker = makeAttacker(),
                defenders = listOf(makeDefender()),
                city = makeCity(),
                rng = LiteHashDRBG.build("golden_battle_det"),
            )
            val r2 = engine.resolveBattle(
                attacker = makeAttacker(),
                defenders = listOf(makeDefender()),
                city = makeCity(),
                rng = LiteHashDRBG.build("golden_battle_det"),
            )

            assertEquals(r1.attackerDamageDealt, r2.attackerDamageDealt)
            assertEquals(r1.defenderDamageDealt, r2.defenderDamageDealt)
            assertEquals(r1.attackerWon, r2.attackerWon)
        }
    }
}
