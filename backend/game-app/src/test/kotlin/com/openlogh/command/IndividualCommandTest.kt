package com.openlogh.command

import com.openlogh.command.general.휴식
import com.openlogh.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Individual command tests.
 * Legacy samguk command tests (훈련, 사기진작, 소집해제, 헌납, etc.) removed
 * along with the dead code classes they tested.
 */
class IndividualCommandTest {

    @Test
    fun `휴식 returns success for any officer`() {
        val general = General(
            id = 1, worldId = 1, name = "테스트장수",
            nationId = 0, cityId = 1,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(year = 200, month = 1, startYear = 190, worldId = 1, realtimeMode = false)
        val result = runBlocking { 휴식(general, env).run(Random(42)) }
        assertTrue(result.success)
    }
}
