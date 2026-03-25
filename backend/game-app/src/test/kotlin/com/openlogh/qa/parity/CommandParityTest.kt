package com.openlogh.qa.parity

import com.openlogh.command.CommandEnv
import com.openlogh.command.general.휴식
import com.openlogh.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Command parity tests verifying Kotlin commands match legacy PHP logic.
 * Legacy samguk command parity tests (훈련, 사기진작, DomesticCommand family)
 * removed along with the dead code classes they tested.
 */
@DisplayName("Command Logic Legacy Parity")
class CommandParityTest {

    @Test
    fun `휴식 determinism`() {
        val gen = General(
            id = 1, worldId = 1, name = "테스트장수",
            nationId = 1, cityId = 1,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(year = 200, month = 1, startYear = 190, worldId = 1, realtimeMode = false)

        val first = runBlocking { 휴식(gen, env).run(Random(42)) }
        val second = runBlocking { 휴식(gen, env).run(Random(42)) }

        assertTrue(first.success)
        assertTrue(first.logs == second.logs)
    }
}
