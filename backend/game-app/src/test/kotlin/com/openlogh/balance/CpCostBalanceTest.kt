package com.openlogh.balance

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.slf4j.LoggerFactory

/**
 * CP 비용 밸런스 검증 테스트 (TEST-02)
 *
 * commands.json을 직접 파싱하여 모든 커맨드의 cpCost >= 0이고
 * waitTime(존재 시) >= 0 임을 검증한다.
 *
 * 밸런스 이슈 발견 시 이 블록에 @Disabled + 코멘트로 문서화:
 * (현재 발견된 이슈 없음)
 */
class CpCostBalanceTest {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    data class CommandEntry(
        val group: String,
        val id: String,
        val cpCost: Int,
        val waitTime: Int?,
    )

    @Suppress("UNCHECKED_CAST")
    private fun loadAllCommands(): List<CommandEntry> {
        val stream = javaClass.classLoader.getResourceAsStream("data/commands.json")
            ?: fail<Nothing>("Resource not found: data/commands.json")
        val root = mapper.readValue(stream, Map::class.java) as Map<String, Any>
        val commandsRoot = root["commands"] as? Map<*, *>
            ?: fail<Nothing>("Missing 'commands' root key in commands.json")

        val result = mutableListOf<CommandEntry>()
        for ((groupKey, groupVal) in commandsRoot) {
            val groupMap = groupVal as? Map<*, *> ?: continue
            val commandList = groupMap["commands"] as? List<*> ?: continue

            for (cmd in commandList) {
                val cmdMap = cmd as? Map<*, *> ?: continue
                val id      = cmdMap["id"] as? String ?: continue
                val cpCost  = (cmdMap["cpCost"] as? Number)?.toInt() ?: -1
                val waitTime = (cmdMap["waitTime"] as? Number)?.toInt()

                result += CommandEntry(
                    group    = groupKey.toString(),
                    id       = id,
                    cpCost   = cpCost,
                    waitTime = waitTime,
                )
            }
        }
        return result
    }

    @Test
    fun `전체 커맨드 수가 75개 이상이다`() {
        val commands = loadAllCommands()
        assertThat(commands.size)
            .withFailMessage("커맨드 총 수: ${commands.size} (기대값 >= 75)")
            .isGreaterThanOrEqualTo(75)
        logger.info("총 커맨드 수: ${commands.size}개")
    }

    @TestFactory
    fun `각 커맨드의 cpCost가 0 이상이다`(): List<DynamicTest> {
        val commands = loadAllCommands()
        return commands.map { cmd ->
            DynamicTest.dynamicTest("[${cmd.group}] ${cmd.id}: cpCost=${cmd.cpCost}") {
                if (cmd.cpCost > 1000) {
                    logger.warn("경고: [${cmd.group}] ${cmd.id} cpCost=${cmd.cpCost} (1000 초과)")
                }
                assertThat(cmd.cpCost)
                    .withFailMessage("[${cmd.group}] ${cmd.id}: cpCost가 음수 (${cmd.cpCost})")
                    .isGreaterThanOrEqualTo(0)
            }
        }
    }

    @TestFactory
    fun `각 커맨드의 waitTime이 존재하면 0 이상이다`(): List<DynamicTest> {
        val commands = loadAllCommands().filter { it.waitTime != null }
        return commands.map { cmd ->
            DynamicTest.dynamicTest("[${cmd.group}] ${cmd.id}: waitTime=${cmd.waitTime}") {
                assertThat(cmd.waitTime!!)
                    .withFailMessage("[${cmd.group}] ${cmd.id}: waitTime이 음수 (${cmd.waitTime})")
                    .isGreaterThanOrEqualTo(0)
            }
        }
    }

    @Test
    fun `cpCost 1000 초과 커맨드를 로그에 출력한다 (실패 아님)`() {
        val commands = loadAllCommands()
        val highCost = commands.filter { it.cpCost > 1000 }
        if (highCost.isNotEmpty()) {
            logger.warn("cpCost > 1000 커맨드 목록:")
            highCost.forEach { logger.warn("  [${it.group}] ${it.id} = ${it.cpCost}") }
        }
        // 이 테스트는 경고 출력만 하고 실패하지 않는다
        assertThat(true).isTrue()
    }
}
