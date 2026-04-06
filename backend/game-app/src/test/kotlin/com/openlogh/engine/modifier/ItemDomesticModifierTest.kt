package com.openlogh.engine.modifier

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Golden value tests for ItemModifiers domestic effects.
 *
 * MiscItem supports: domesticSuccess, domesticSabotageSuccess,
 * domesticSupplySuccess, domesticSupplyScore, and triggerType="recruit".
 * StatItem and ConsumableItem have no domestic overrides.
 */
@DisplayName("ItemModifiers Domestic Parity")
class ItemDomesticModifierTest {

    @Nested
    @DisplayName("MiscItem domestic stat effects")
    inner class MiscItemDomesticEffects {

        @Test
        fun `domesticSuccess adds to successMultiplier for any action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSuccess" to 0.1))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.successMultiplier).isEqualTo(1.1)
        }

        @Test
        fun `domesticSabotageSuccess adds to successMultiplier on sabotage action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSabotageSuccess" to 0.2))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "계략"))
            assertThat(result.successMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `domesticSabotageSuccess does not apply to non-sabotage action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSabotageSuccess" to 0.2))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.successMultiplier).isEqualTo(1.0)
        }

        @Test
        fun `domesticSupplySuccess adds to successMultiplier on supply action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSupplySuccess" to 0.15))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "조달"))
            assertThat(result.successMultiplier).isEqualTo(1.15)
        }

        @Test
        fun `domesticSupplySuccess does not apply to non-supply action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSupplySuccess" to 0.15))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.successMultiplier).isEqualTo(1.0)
        }

        @Test
        fun `domesticSupplyScore multiplies scoreMultiplier on supply action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSupplyScore" to 1.5))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "조달"))
            assertThat(result.scoreMultiplier).isEqualTo(1.5)
        }

        @Test
        fun `domesticSupplyScore does not apply to non-supply action`() {
            val item = MiscItem(code = "test_item", name = "test", statMods = mapOf("domesticSupplyScore" to 1.5))
            val result = item.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("MiscItem recruit triggerType")
    inner class RecruitTriggerType {

        @Test
        fun `recruit triggerType on conscription sets train and morale`() {
            val item = MiscItem(code = "test_recruit", name = "test", triggerType = "recruit")
            val result = item.onCalcDomestic(DomesticContext(actionCode = "징병"))
            assertThat(result.trainMultiplier).isEqualTo(70.0)
            assertThat(result.atmosMultiplier).isEqualTo(84.0)
        }

        @Test
        fun `recruit triggerType on volunteer sets train and morale`() {
            val item = MiscItem(code = "test_recruit", name = "test", triggerType = "recruit")
            val result = item.onCalcDomestic(DomesticContext(actionCode = "모병"))
            assertThat(result.trainMultiplier).isEqualTo(70.0)
            assertThat(result.atmosMultiplier).isEqualTo(84.0)
        }

        @Test
        fun `recruit triggerType on population conscription zeroes score`() {
            val item = MiscItem(code = "test_recruit", name = "test", triggerType = "recruit")
            val result = item.onCalcDomestic(DomesticContext(actionCode = "징집인구"))
            assertThat(result.scoreMultiplier).isEqualTo(0.0)
        }

        @Test
        fun `recruit triggerType on non-recruit action returns unchanged`() {
            val item = MiscItem(code = "test_recruit", name = "test", triggerType = "recruit")
            val result = item.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.trainMultiplier).isEqualTo(1.0)
            assertThat(result.atmosMultiplier).isEqualTo(1.0)
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("StatItem has no domestic effect")
    inner class StatItemNoDomesticEffect {

        @Test
        fun `StatItem returns unchanged context`() {
            val item = StatItem(code = "test_weapon", name = "test", command = 5.0)
            val ctx = DomesticContext(actionCode = "농업", scoreMultiplier = 1.0, costMultiplier = 1.0)
            val result = item.onCalcDomestic(ctx)
            assertThat(result).isEqualTo(ctx)
        }
    }

    @Nested
    @DisplayName("ConsumableItem has no domestic effect")
    inner class ConsumableItemNoDomesticEffect {

        @Test
        fun `ConsumableItem returns unchanged context`() {
            val item = ConsumableItem(code = "test_cons", name = "test", maxUses = 1, effect = "heal", value = 10)
            val ctx = DomesticContext(actionCode = "농업", scoreMultiplier = 1.0, costMultiplier = 1.0)
            val result = item.onCalcDomestic(ctx)
            assertThat(result).isEqualTo(ctx)
        }
    }
}
