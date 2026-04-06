package com.openlogh.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Integer Division Parity Test (TYPE-03)
 *
 * Verifies that Kotlin's `/` operator on Int values matches PHP's `intdiv()`
 * for all sign combinations. Both languages truncate toward zero for integer
 * division, so no `phpIntdiv()` utility should be needed (per D-09).
 *
 * PHP reference:
 *   intdiv(7, 2) == 3
 *   intdiv(-7, 2) == -3  (truncate toward zero, NOT floor)
 *   intdiv(7, -2) == -3
 *   intdiv(-7, -2) == 3
 *
 * Kotlin reference:
 *   7 / 2 == 3
 *   (-7) / 2 == -3  (truncate toward zero)
 *   7 / (-2) == -3
 *   (-7) / (-2) == 3
 */
@DisplayName("Integer Division Parity (PHP intdiv vs Kotlin /)")
class IntegerDivisionParityTest {

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource(
        "7, 2, 3",
        "-7, 2, -3",
        "7, -2, -3",
        "-7, -2, 3",
        "10, 3, 3",
        "-10, 3, -3",
        "10, -3, -3",
        "-10, -3, 3",
        "0, 5, 0",
        "3, 10, 0",
        "-500, 3, -166",
        "10000, 6, 1666",
        "1, 1, 1",
        "-1, 1, -1",
        "100, 100, 1",
    )
    fun `Kotlin integer division matches PHP intdiv`(
        dividend: Int,
        divisor: Int,
        expected: Int,
    ) {
        assertEquals(expected, dividend / divisor)
    }

    @Test
    @DisplayName("Kotlin truncates toward zero, not toward negative infinity (floor)")
    fun `negative division truncates toward zero`() {
        // Key verification: (-7) / 2 == -3 (truncate toward zero)
        // If it were floor division, result would be -4
        assertEquals(-3, (-7) / 2, "Must truncate toward zero, not floor")
        assertEquals(-3, 7 / (-2), "Must truncate toward zero, not floor")
    }

    @Test
    @DisplayName("Game-relevant: negative gold divided by officer count")
    fun `negative gold division matches PHP`() {
        // Nation gold can go negative (debt scenario)
        // PHP: intdiv(-500, 3) == -166
        assertEquals(-166, (-500) / 3)

        // General salary distribution with negative treasury
        assertEquals(-83, (-500) / 6)
        assertEquals(-50, (-500) / 10)
    }

    @Test
    @DisplayName("Game-relevant: population and economy divisions")
    fun `population division matches PHP`() {
        // Large population divided by city count
        assertEquals(1666, 10000 / 6)
        assertEquals(2500, 10000 / 4)

        // Economy formula: gold income base
        assertEquals(333, 1000 / 3)
        assertEquals(142, 1000 / 7)
    }

    @Test
    @DisplayName("Edge case: dividend equals divisor")
    fun `dividend equals divisor`() {
        assertEquals(1, 5 / 5)
        assertEquals(1, (-5) / (-5))
        assertEquals(-1, (-5) / 5)
        assertEquals(-1, 5 / (-5))
    }

    @Test
    @DisplayName("Edge case: large values within game range")
    fun `large game values`() {
        // Max ships * train scenario
        assertEquals(11000, 1100000 / 100)

        // Population / max cities
        assertEquals(1250, 50000 / 40)
    }

    @Test
    @DisplayName("Per D-09: no phpIntdiv utility needed since Kotlin matches PHP")
    fun `confirms no phpIntdiv utility needed`() {
        // All sign combinations match -- Kotlin / operator IS PHP intdiv
        val testCases = listOf(
            Triple(7, 2, 3),
            Triple(-7, 2, -3),
            Triple(7, -2, -3),
            Triple(-7, -2, 3),
        )
        for ((a, b, expected) in testCases) {
            assertEquals(expected, a / b, "intdiv($a, $b) should equal $expected")
        }
    }
}
