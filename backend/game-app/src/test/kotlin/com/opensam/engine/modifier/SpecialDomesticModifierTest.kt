package com.opensam.engine.modifier

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Golden value tests for all 13 original domestic specials in SpecialModifiers.
 *
 * MOD-02: 8 specials were broken due to actionCode mismatch (only long-form matched).
 * After fix, both short-form and long-form actionCodes must fire correctly.
 */
@DisplayName("SpecialModifiers Domestic Parity")
class SpecialDomesticModifierTest {

    @Nested
    @DisplayName("농업 special -- scoreMultiplier x1.2")
    inner class Agriculture {

        @Test
        fun `short-form actionCode fires`() {
            val mod = SpecialModifiers.get("농업")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `long-form actionCode fires`() {
            val mod = SpecialModifiers.get("농업")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농지개간"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("농업")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "상업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("상업 special -- scoreMultiplier x1.2")
    inner class Commerce {

        @Test
        fun `short-form actionCode fires`() {
            val mod = SpecialModifiers.get("상업")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "상업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `long-form actionCode fires`() {
            val mod = SpecialModifiers.get("상업")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "상업투자"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("상업")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("징수 special -- costMultiplier x0.8")
    inner class Taxation {

        @Test
        fun `short-form actionCode fires`() {
            val mod = SpecialModifiers.get("징수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "조달"))
            assertThat(result.costMultiplier).isEqualTo(0.8)
        }

        @Test
        fun `long-form actionCode fires`() {
            val mod = SpecialModifiers.get("징수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "물자조달"))
            assertThat(result.costMultiplier).isEqualTo(0.8)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("징수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.costMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("보수 special -- scoreMultiplier x1.3")
    inner class Repair {

        @Test
        fun `short-form actionCode fires for defence`() {
            val mod = SpecialModifiers.get("보수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "수비"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `short-form actionCode fires for wall`() {
            val mod = SpecialModifiers.get("보수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "성벽"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `long-form actionCode fires for defence`() {
            val mod = SpecialModifiers.get("보수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "수비강화"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `long-form actionCode fires for wall`() {
            val mod = SpecialModifiers.get("보수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "성벽보수"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("보수")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("발명 special -- scoreMultiplier x1.5")
    inner class Invention {

        @Test
        fun `short-form actionCode fires`() {
            val mod = SpecialModifiers.get("발명")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "기술"))
            assertThat(result.scoreMultiplier).isEqualTo(1.5)
        }

        @Test
        fun `long-form actionCode fires`() {
            val mod = SpecialModifiers.get("발명")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "기술연구"))
            assertThat(result.scoreMultiplier).isEqualTo(1.5)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("발명")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("인덕 special -- scoreMultiplier x1.2")
    inner class Virtue {

        @Test
        fun `short-form actionCode fires for popularity`() {
            val mod = SpecialModifiers.get("인덕")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "민심"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `short-form actionCode fires for population`() {
            val mod = SpecialModifiers.get("인덕")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "인구"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `long-form actionCode fires for popularity`() {
            val mod = SpecialModifiers.get("인덕")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "주민선정"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `long-form actionCode fires for population`() {
            val mod = SpecialModifiers.get("인덕")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "정착장려"))
            assertThat(result.scoreMultiplier).isEqualTo(1.2)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("인덕")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("건축 special -- scoreMultiplier x1.3")
    inner class Construction {

        @Test
        fun `short-form actionCode fires for defence`() {
            val mod = SpecialModifiers.get("건축")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "수비"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `short-form actionCode fires for wall`() {
            val mod = SpecialModifiers.get("건축")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "성벽"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `long-form actionCode fires for defence`() {
            val mod = SpecialModifiers.get("건축")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "수비강화"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `long-form actionCode fires for wall`() {
            val mod = SpecialModifiers.get("건축")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "성벽보수"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("건축")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("의술 special -- successMultiplier x1.3")
    inner class Medicine {

        @Test
        fun `matching actionCode fires`() {
            val mod = SpecialModifiers.get("의술")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "요양"))
            assertThat(result.successMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("의술")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.successMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("치료 special -- successMultiplier x1.5")
    inner class Healing {

        @Test
        fun `matching actionCode fires`() {
            val mod = SpecialModifiers.get("치료")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "요양"))
            assertThat(result.successMultiplier).isEqualTo(1.5)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("치료")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.successMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("등용 special -- successMultiplier x1.5")
    inner class Recruitment {

        @Test
        fun `matching actionCode fires`() {
            val mod = SpecialModifiers.get("등용")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "등용"))
            assertThat(result.successMultiplier).isEqualTo(1.5)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("등용")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.successMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("정치 special -- unconditional scoreMultiplier x1.1 + costMultiplier x0.9")
    inner class Politics {

        @Test
        fun `any actionCode gets bonus`() {
            val mod = SpecialModifiers.get("정치")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.1)
            assertThat(result.costMultiplier).isEqualTo(0.9)
        }

        @Test
        fun `empty actionCode gets bonus`() {
            val mod = SpecialModifiers.get("정치")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = ""))
            assertThat(result.scoreMultiplier).isEqualTo(1.1)
            assertThat(result.costMultiplier).isEqualTo(0.9)
        }
    }

    @Nested
    @DisplayName("훈련_특기 special -- trainMultiplier x1.3")
    inner class Training {

        @Test
        fun `matching actionCode fires`() {
            val mod = SpecialModifiers.get("훈련_특기")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "훈련"))
            assertThat(result.trainMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("훈련_특기")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.trainMultiplier).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("모병_특기 special -- scoreMultiplier x1.3")
    inner class Conscription {

        @Test
        fun `징병 actionCode fires`() {
            val mod = SpecialModifiers.get("모병_특기")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "징병"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `모병 actionCode fires`() {
            val mod = SpecialModifiers.get("모병_특기")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "모병"))
            assertThat(result.scoreMultiplier).isEqualTo(1.3)
        }

        @Test
        fun `non-matching actionCode returns unchanged`() {
            val mod = SpecialModifiers.get("모병_특기")!!
            val result = mod.onCalcDomestic(DomesticContext(actionCode = "농업"))
            assertThat(result.scoreMultiplier).isEqualTo(1.0)
        }
    }
}
