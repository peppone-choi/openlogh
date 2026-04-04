package com.openlogh.engine.modifier

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.log2

class MusangKillnumTest {

    @Test
    fun `StatContext has killnum field with default 0_0`() {
        val ctx = StatContext()
        assertEquals(0.0, ctx.killnum, 0.001)
    }

    @Test
    fun `che_무쌍 modifier exists in SpecialModifiers`() {
        val modifier = SpecialModifiers.get("che_무쌍")
        assertNotNull(modifier)
        assertEquals("무쌍", modifier?.name)
    }

    @Test
    fun `che_무쌍 with killnum=0 produces attackMultiplier of 1_05`() {
        val modifier = SpecialModifiers.get("che_무쌍")!!
        val stat = StatContext(killnum = 0.0, isAttacker = true, warPower = 1.0)
        val result = modifier.onCalcStat(stat)

        // killnum=0: log2(max(1.0, 0/5)) = log2(1) = 0
        // attackMultiplier = 1.05 + 0/20 = 1.05
        // warPower = 1.0 * 1.05 = 1.05
        assertEquals(1.05, result.warPower, 0.001)
    }

    @Test
    fun `che_무쌍 with killnum=50 produces higher warPower than killnum=0`() {
        val modifier = SpecialModifiers.get("che_무쌍")!!
        val stat50 = StatContext(killnum = 50.0, isAttacker = true, warPower = 1.0)
        val result50 = modifier.onCalcStat(stat50)

        // killnum=50: log2(max(1.0, 50/5)) = log2(10) ≈ 3.3219
        // attackMultiplier = 1.05 + 3.3219/20 ≈ 1.05 + 0.1661 = 1.2161
        val expectedLogVal = log2(maxOf(1.0, 50.0 / 5.0))
        val expectedMultiplier = 1.05 + expectedLogVal / 20.0
        assertEquals(expectedMultiplier, result50.warPower, 0.001)

        // Must be higher than killnum=0
        val stat0 = StatContext(killnum = 0.0, isAttacker = true, warPower = 1.0)
        val result0 = modifier.onCalcStat(stat0)
        assertTrue(result50.warPower > result0.warPower,
            "killnum=50 warPower (${result50.warPower}) should be greater than killnum=0 (${result0.warPower})")
    }

    @Test
    fun `che_무쌍 with killnum=100 produces expected warPower`() {
        val modifier = SpecialModifiers.get("che_무쌍")!!
        val stat = StatContext(killnum = 100.0, isAttacker = false, warPower = 1.0)
        val result = modifier.onCalcStat(stat)

        // killnum=100: log2(max(1.0, 100/5)) = log2(20) ≈ 4.3219
        // attackMultiplier = 1.05 + 4.3219/20 ≈ 1.05 + 0.2161 = 1.2661
        val expectedLogVal = log2(maxOf(1.0, 100.0 / 5.0))
        val expectedMultiplier = 1.05 + expectedLogVal / 20.0
        assertEquals(expectedMultiplier, result.warPower, 0.001)
    }

    @Test
    fun `che_무쌍 isAttacker=true adds criticalChance plus 0_1`() {
        val modifier = SpecialModifiers.get("che_무쌍")!!
        val stat = StatContext(killnum = 0.0, isAttacker = true, criticalChance = 0.05)
        val result = modifier.onCalcStat(stat)

        // isAttacker=true: criticalChance += 0.1 → 0.05 + 0.1 = 0.15
        assertEquals(0.15, result.criticalChance, 0.001)
    }

    @Test
    fun `che_무쌍 isAttacker=false does NOT add criticalChance`() {
        val modifier = SpecialModifiers.get("che_무쌍")!!
        val stat = StatContext(killnum = 0.0, isAttacker = false, criticalChance = 0.05)
        val result = modifier.onCalcStat(stat)

        // isAttacker=false: criticalChance stays at 0.05
        assertEquals(0.05, result.criticalChance, 0.001)
    }

    @Test
    fun `che_무쌍 killnum affects dodgeChance reduction`() {
        val modifier = SpecialModifiers.get("che_무쌍")!!

        // killnum=0: defenceMultiplier = 0.98 - 0/50 = 0.98; dodgeChance -= (1 - 0.98) = -0.02
        val stat0 = StatContext(killnum = 0.0, isAttacker = false, dodgeChance = 0.10)
        val result0 = modifier.onCalcStat(stat0)

        // killnum=100: defenceMultiplier = 0.98 - log2(20)/50 ≈ 0.98 - 0.0864 = 0.8936
        // dodgeChance -= (1 - 0.8936) = -0.1064
        val stat100 = StatContext(killnum = 100.0, isAttacker = false, dodgeChance = 0.10)
        val result100 = modifier.onCalcStat(stat100)

        // Higher killnum should reduce dodgeChance more
        assertTrue(result100.dodgeChance < result0.dodgeChance,
            "killnum=100 dodgeChance (${result100.dodgeChance}) should be less than killnum=0 (${result0.dodgeChance})")
    }
}
