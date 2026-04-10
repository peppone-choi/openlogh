package com.openlogh.engine.modifier

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Modifier Stacking Parity")
class ModifierStackingParityTest {

    private val service = ModifierService()
    private val tolerance = Offset.offset(0.001)

    @Nested
    @DisplayName("AbsoluteSet overwrite")
    inner class AbsoluteSetOverwrite {

        @Test
        @DisplayName("Scenario 4: che_징병 special + recruit item on 징병 (last-writer wins)")
        fun `징병 special then recruit item overwrites train and morale`() {
            // che_징병: for "징병" -> trainMultiplier=70.0, atmosMultiplier=84.0
            // MiscItem(recruit): for "징병" -> trainMultiplier=70.0, atmosMultiplier=84.0
            // Both set same absolute values; item (step 5+) overwrites special (step 4)
            val modifiers = listOf(
                SpecialModifiers.get("che_징병")!!,
                MiscItem(code = "test_recruit", name = "모병부", triggerType = "recruit"),
            )
            val ctx = DomesticContext(trainMultiplier = 50.0, atmosMultiplier = 60.0, actionCode = "징병")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            assertThat(result.trainMultiplier).isEqualTo(70.0)
            assertThat(result.atmosMultiplier).isEqualTo(84.0)
        }

        @Test
        @DisplayName("Scenario 4b: recruit item alone on 모병")
        fun `recruit item sets absolute values on conscription`() {
            val modifiers = listOf(
                MiscItem(code = "test_recruit", name = "모병부", triggerType = "recruit"),
            )
            val ctx = DomesticContext(trainMultiplier = 30.0, atmosMultiplier = 40.0, actionCode = "모병")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            assertThat(result.trainMultiplier).isEqualTo(70.0)
            assertThat(result.atmosMultiplier).isEqualTo(84.0)
        }
    }

    // ── Edge Cases ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        @DisplayName("Scenario 6: empty modifiers leave context unchanged")
        fun `empty modifier list returns base context`() {
            val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "농업")
            val result = service.applyDomesticModifiers(emptyList(), ctx)
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
            assertThat(result.costMultiplier).isEqualTo(1.0)
            assertThat(result.successMultiplier).isEqualTo(1.0)
            assertThat(result.trainMultiplier).isEqualTo(1.0)
            assertThat(result.atmosMultiplier).isEqualTo(1.0)
        }

        @Test
        @DisplayName("Scenario 7: non-matching action leaves context unchanged")
        fun `modifiers present but action does not match`() {
            // 농업 special only fires for "농지개간"
            // OfficerLevel(20,5) only fires for "농업", "상업", "기술", etc.
            val modifiers = listOf(
                SpecialModifiers.get("농업")!!,
                OfficerLevelModifier(20, 5),
            )
            val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "계략")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }
}
