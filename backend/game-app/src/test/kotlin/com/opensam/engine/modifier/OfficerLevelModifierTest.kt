package com.opensam.engine.modifier

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * OfficerLevelModifier parity test.
 *
 * Legacy: TriggerOfficerLevel.php (13 grades 0-12)
 * Current: 21 grades (0-20), proportionally scaled via round(legacy * 20/12)
 *
 * Key: max bonus values preserved, intermediate thresholds scaled.
 */
@DisplayName("OfficerLevelModifier Parity")
class OfficerLevelModifierTest {

    @Nested
    @DisplayName("Leadership Bonus — threshold >= 8 (scaled from legacy >= 5)")
    inner class LeadershipBonus {

        @Test
        fun `level 20 gives nationLevel x2`() {
            val mod = OfficerLevelModifier(officerLevel = 20, nationLevel = 5)
            val result = mod.onCalcStat(StatContext(leadership = 80.0))
            assertThat(result.leadership).isEqualTo(90.0) // 80 + 5*2
        }

        @Test
        fun `level 8 gives nationLevel x1`() {
            val mod = OfficerLevelModifier(officerLevel = 8, nationLevel = 5)
            val result = mod.onCalcStat(StatContext(leadership = 80.0))
            assertThat(result.leadership).isEqualTo(85.0) // 80 + 5
        }

        @Test
        fun `level 7 gives no bonus — below threshold`() {
            val mod = OfficerLevelModifier(officerLevel = 7, nationLevel = 5)
            val result = mod.onCalcStat(StatContext(leadership = 80.0))
            assertThat(result.leadership).isEqualTo(80.0) // no change
        }

        @Test
        fun `level 0 gives no bonus`() {
            val mod = OfficerLevelModifier(officerLevel = 0, nationLevel = 7)
            val result = mod.onCalcStat(StatContext(leadership = 100.0))
            assertThat(result.leadership).isEqualTo(100.0)
        }
    }

    @Nested
    @DisplayName("War Power Multiplier — max 1.10 preserved")
    inner class WarPower {

        @Test
        fun `level 20 self x1_07`() {
            val mod = OfficerLevelModifier(officerLevel = 20, nationLevel = 1)
            assertThat(mod.getWarPowerMultiplier()).isEqualTo(1.07)
        }

        @Test
        fun `level 10 self x1_10`() {
            val mod = OfficerLevelModifier(officerLevel = 10, nationLevel = 1)
            assertThat(mod.getWarPowerMultiplier()).isEqualTo(1.10)
        }

        @Test
        fun `level 0 no multiplier`() {
            val mod = OfficerLevelModifier(officerLevel = 0, nationLevel = 1)
            assertThat(mod.getWarPowerMultiplier()).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("Opponent War Power — min 0.90 preserved")
    inner class OpponentStat {

        @Test
        fun `level 8 opponent x0_90`() {
            val mod = OfficerLevelModifier(officerLevel = 8, nationLevel = 1)
            val result = mod.onCalcOpposeStat(StatContext(warPower = 100.0))
            assertThat(result.warPower).isEqualTo(90.0)
        }

        @Test
        fun `level 20 opponent x0_93`() {
            val mod = OfficerLevelModifier(officerLevel = 20, nationLevel = 1)
            val result = mod.onCalcOpposeStat(StatContext(warPower = 100.0))
            assertThat(result.warPower).isEqualTo(93.0)
        }
    }
}
