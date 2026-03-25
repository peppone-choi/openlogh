package com.openlogh.command

import com.openlogh.command.general.휴식
import com.openlogh.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Parity tests for commands registered in CommandRegistry.
 * Legacy samguk domestic/military command parity tests removed
 * along with the dead code classes they tested.
 */
class CommandParityTest {

    @Test
    fun `휴식 parity and determinism`() {
        val general = General(
            id = 1, worldId = 1, name = "테스트장수",
            nationId = 1, cityId = 1,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(year = 200, month = 1, startYear = 190, worldId = 1, realtimeMode = false)

        val first = runBlocking { 휴식(general, env).run(Random(42)) }
        val second = runBlocking { 휴식(general, env).run(Random(42)) }

        assertTrue(first.success)
        assertEquals(first.logs, second.logs)
    }
}
