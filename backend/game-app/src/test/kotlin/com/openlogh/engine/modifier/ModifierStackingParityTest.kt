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

    // ── Score Multiplier Stacking ────────────────────────────────────────────

    @Nested
    @DisplayName("ScoreMultiplier stacking")
    inner class ScoreMultiplierStacking {

        @Test
        @DisplayName("Scenario 1: 왕도 + 온후 + 농업special on 농지개간 (3-source)")
        fun `nationtype + personality + domestic special on agriculture`() {
            // 왕도: score *= 1.15 (unconditional)
            // 온후: score *= 1.1 (unconditional)
            // 농업 special: score *= 1.2 (when actionCode == "농지개간")
            val modifiers = listOf(
                NationTypeModifiers.get("che_왕도")!!,
                PersonalityModifiers.get("온후")!!,
                SpecialModifiers.get("농업")!!,
            )
            val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "농지개간")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            // 1.0 * 1.15 * 1.1 * 1.2 = 1.518
            assertThat(result.scoreMultiplier).isCloseTo(1.518, tolerance)
        }

        @Test
        @DisplayName("Scenario 1b: 왕도 + 온후 + 정치special + OfficerLevel on 농업 (4-source)")
        fun `nationtype + personality + unconditional special + officer on agriculture`() {
            // 왕도: score *= 1.15 (unconditional)
            // 온후: score *= 1.1 (unconditional)
            // 정치 special: score *= 1.1 (unconditional)
            // OfficerLevel(20, 5): score *= 1.05 (농업 in AGR_COM_LEVELS for level 20)
            val modifiers = listOf(
                NationTypeModifiers.get("che_왕도")!!,
                PersonalityModifiers.get("온후")!!,
                SpecialModifiers.get("정치")!!,
                OfficerLevelModifier(20, 5),
            )
            val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "농업")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            // 1.0 * 1.15 * 1.1 * 1.1 * 1.05 = 1.460925
            assertThat(result.scoreMultiplier).isCloseTo(1.460925, tolerance)
        }

        @Test
        @DisplayName("Scenario 2: 패도 penalty + 상업 special on 상업투자")
        fun `pado penalty + commerce special on commerce`() {
            // 패도: score *= 0.95 (unconditional)
            // 상업 special: score *= 1.2 (when actionCode == "상업투자")
            val modifiers = listOf(
                NationTypeModifiers.get("che_패도")!!,
                SpecialModifiers.get("상업")!!,
            )
            val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "상업투자")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            // 1.0 * 0.95 * 1.2 = 1.14
            assertThat(result.scoreMultiplier).isCloseTo(1.14, tolerance)
        }

        @Test
        @DisplayName("Scenario 5: 명가 + 발명special + OfficerLevel on 기술연구 (triple stack)")
        fun `myeongga + invention special + officer on tech`() {
            // 명가: for "기술연구" -> score *= 1.1, cost *= 0.8
            // 발명 special: for "기술연구" -> score *= 1.5
            // OfficerLevel(12, 5): 기술 NOT in actionCode "기술연구" -> no bonus
            //   (OfficerLevelModifier checks actionCode == "기술", not "기술연구")
            val modifiers = listOf(
                NationTypeModifiers.get("che_명가")!!,
                SpecialModifiers.get("발명")!!,
                OfficerLevelModifier(12, 5),
            )
            val ctx = DomesticContext(scoreMultiplier = 1.0, costMultiplier = 1.0, actionCode = "기술연구")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            // score: 1.0 * 1.1 * 1.5 = 1.65 (officer doesn't match "기술연구")
            assertThat(result.scoreMultiplier).isCloseTo(1.65, tolerance)
            // cost: 1.0 * 0.8 = 0.8 (명가 for 기술연구)
            assertThat(result.costMultiplier).isCloseTo(0.8, tolerance)
        }

        @Test
        @DisplayName("Scenario 5b: 명가 + OfficerLevel on 기술 (officer matches short-form)")
        fun `myeongga + officer on tech shortform`() {
            // 명가: for "기술" -> score *= 1.1, cost *= 0.8
            // OfficerLevel(12, 5): "기술" in TECH_LEVELS -> score *= 1.05
            val modifiers = listOf(
                NationTypeModifiers.get("che_명가")!!,
                OfficerLevelModifier(12, 5),
            )
            val ctx = DomesticContext(scoreMultiplier = 1.0, costMultiplier = 1.0, actionCode = "기술")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            // score: 1.0 * 1.1 * 1.05 = 1.155
            assertThat(result.scoreMultiplier).isCloseTo(1.155, tolerance)
            // cost: 1.0 * 0.8 = 0.8
            assertThat(result.costMultiplier).isCloseTo(0.8, tolerance)
        }
    }

    // ── Cost Multiplier Stacking ─────────────────────────────────────────────

    @Nested
    @DisplayName("CostMultiplier stacking")
    inner class CostMultiplierStacking {

        @Test
        @DisplayName("Scenario 3: 황건 + 징수 on 물자조달 (cost reduction stack)")
        fun `hwanggeon + jingsu on supply`() {
            // 황건: cost *= 0.8 (unconditional)
            // 징수 special: cost *= 0.8 (when actionCode == "물자조달")
            val modifiers = listOf(
                NationTypeModifiers.get("che_황건")!!,
                SpecialModifiers.get("징수")!!,
            )
            val ctx = DomesticContext(costMultiplier = 1.0, actionCode = "물자조달")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            // 1.0 * 0.8 * 0.8 = 0.64
            assertThat(result.costMultiplier).isCloseTo(0.64, tolerance)
        }
    }

    // ── Absolute-Set Overwrite ───────────────────────────────────────────────

    @Nested
    @DisplayName("AbsoluteSet overwrite")
    inner class AbsoluteSetOverwrite {

        @Test
        @DisplayName("Scenario 4: che_징병 special + recruit item on 징병 (last-writer wins)")
        fun `징병 special then recruit item overwrites train and atmos`() {
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

    // ── Additive + Multiplicative Order ──────────────────────────────────────

    @Nested
    @DisplayName("Additive+Multiplicative pipeline order")
    inner class AdditiveMultiplicativeOrder {

        @Test
        @DisplayName("Scenario 8a: 종교(mult) first, then che_경작(add) -- pipeline order")
        fun `nation multiplicative then domestic additive`() {
            // 종교: successMultiplier *= 1.1 (nation type, step 1)
            // che_경작: for "농지개간" -> successMultiplier += 0.1 (domestic special, step 4)
            // Pipeline: 1.0 * 1.1 = 1.1, then 1.1 + 0.1 = 1.2
            val modifiers = listOf(
                NationTypeModifiers.get("che_종교")!!,
                SpecialModifiers.get("che_경작")!!,
            )
            val ctx = DomesticContext(successMultiplier = 1.0, actionCode = "농지개간")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            assertThat(result.successMultiplier).isCloseTo(1.2, tolerance)
        }

        @Test
        @DisplayName("Scenario 8b: che_경작(add) first, then 종교(mult) -- reversed order")
        fun `domestic additive then nation multiplicative proves order matters`() {
            // If passed in reverse order: che_경작 before 종교
            // Pipeline: 1.0 + 0.1 = 1.1, then 1.1 * 1.1 = 1.21
            val modifiers = listOf(
                SpecialModifiers.get("che_경작")!!,
                NationTypeModifiers.get("che_종교")!!,
            )
            val ctx = DomesticContext(successMultiplier = 1.0, actionCode = "농지개간")
            val result = service.applyDomesticModifiers(modifiers, ctx)
            assertThat(result.successMultiplier).isCloseTo(1.21, tolerance)
        }

        @Test
        @DisplayName("Purely multiplicative order does not matter")
        fun `multiplicative-only stacking is commutative`() {
            // 왕도: score *= 1.15, 온후: score *= 1.1 -- both multiplicative
            val forward = service.applyDomesticModifiers(
                listOf(NationTypeModifiers.get("che_왕도")!!, PersonalityModifiers.get("온후")!!),
                DomesticContext(scoreMultiplier = 1.0, actionCode = "농업"),
            )
            val reversed = service.applyDomesticModifiers(
                listOf(PersonalityModifiers.get("온후")!!, NationTypeModifiers.get("che_왕도")!!),
                DomesticContext(scoreMultiplier = 1.0, actionCode = "농업"),
            )
            assertThat(forward.scoreMultiplier).isCloseTo(reversed.scoreMultiplier, tolerance)
            // Both should be 1.15 * 1.1 = 1.265
            assertThat(forward.scoreMultiplier).isCloseTo(1.265, tolerance)
        }
    }
}
