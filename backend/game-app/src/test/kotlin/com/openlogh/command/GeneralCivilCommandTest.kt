package com.openlogh.command

import com.openlogh.command.general.휴식
import com.openlogh.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Civil command tests.
 * Legacy samguk domestic commands (숙련전환, 물자조달, 군량매매) removed
 * along with the dead code classes they tested.
 */
class GeneralCivilCommandTest {

    @Test
    fun `휴식 always succeeds`() {
        val general = General(
            id = 1, worldId = 1, name = "테스트장수",
            nationId = 1, cityId = 1,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(year = 200, month = 1, startYear = 190, worldId = 1, realtimeMode = false)
        val result = runBlocking { 휴식(general, env).run(Random(42)) }
        assertTrue(result.success)
    }
}
