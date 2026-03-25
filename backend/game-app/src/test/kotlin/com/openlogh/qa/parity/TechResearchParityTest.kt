package com.openlogh.qa.parity

import com.openlogh.command.CommandEnv
import com.openlogh.command.isTechLimited
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tech research legacy parity tests.
 * Only the isTechLimited utility is tested here.
 * The che_기술연구 command tests were removed along with the dead code class.
 */
@DisplayName("Tech Research Legacy Parity")
class TechResearchParityTest {

    @Test
    fun `TechLimit follows legacy five-year bands`() {
        val beforeFiveYears = createEnv(year = 184)
        val atFiveYears = createEnv(year = 185)

        assertFalse(beforeFiveYears.isTechLimited(999.0))
        assertTrue(beforeFiveYears.isTechLimited(1000.0))
        assertFalse(atFiveYears.isTechLimited(1000.0))
        assertTrue(atFiveYears.isTechLimited(2000.0))
    }

    private fun createEnv(year: Int = 180): CommandEnv {
        return CommandEnv(
            year = year,
            month = 1,
            startYear = 180,
            worldId = 1,
            realtimeMode = false,
        )
    }
}
