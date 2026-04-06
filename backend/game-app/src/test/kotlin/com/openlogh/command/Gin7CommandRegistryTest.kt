package com.openlogh.command

import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Verifies Gin7CommandRegistry:
 * 1. 82 commands registered (81 gin7 + 대기)
 * 2. All 81 Korean command codes exist in the registry
 * 3. 대기 command can be created (non-null)
 */
class Gin7CommandRegistryTest {

    private val registry = Gin7CommandRegistry()

    private val ALL_81_COMMAND_CODES = listOf(
        // 작전커맨드 (16종)
        "워프항행", "연료보급", "성계내항행", "군기유지", "항공훈련", "육전훈련",
        "공전훈련", "육전전술훈련", "공전전술훈련", "경계출동", "무력진압",
        "분열행진", "징발", "특별경비", "육전대출격", "육전대철수",
        // 개인커맨드 (15종)
        "원거리이동", "근거리이동", "퇴역", "지원", "망명", "회견", "수강",
        "병기연습", "반의", "모의", "설득", "반란", "참가", "자금투입", "기함구매",
        // 지휘커맨드 (8종)
        "작전계획", "작전철회", "발령", "부대결성", "부대해산", "강의", "수송계획", "수송중지",
        // 병참커맨드 (6종)
        "완전수리", "완전보급", "재편성", "보충", "반출입", "할당",
        // 인사커맨드 (10종)
        "승진", "발탁", "강등", "서작", "서훈", "임명", "파면", "사임", "봉토수여", "봉토직할",
        // 정치커맨드 (12종)
        "야회", "수렵", "회담", "담화", "연설", "국가목표", "납입률변경", "관세율변경",
        "분배", "처단", "외교", "통치목표",
        // 첩보커맨드 (14종)
        "일제수색", "체포허가", "집행명령", "체포명령", "사열", "습격",
        "감시", "잠입공작", "탈출공작", "정보공작", "파괴공작", "선동공작",
        "침입공작", "귀환공작",
    )

    private fun makeOfficer(): Officer = Officer(
        id = 1L,
        sessionId = 1L,
        name = "테스트제독",
        factionId = 1L,
        planetId = 10L,
        positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
        turnTime = OffsetDateTime.now(),
    )

    private fun makeEnv(): CommandEnv = CommandEnv(
        sessionId = 1L,
        year = 796,
        month = 1,
        startYear = 796,
        realtimeMode = true,
        gameStor = mutableMapOf(),
    )

    @Test
    fun `registry has 82 commands (81 gin7 plus 대기)`() {
        val names = registry.getGeneralCommandNames()
        assertEquals(82, names.size,
            "Expected 82 commands (81 gin7 + 대기) but got ${names.size}: $names")
    }

    @Test
    fun `all 81 gin7 command codes are registered`() {
        val names = registry.getGeneralCommandNames()
        val missing = ALL_81_COMMAND_CODES.filter { it !in names }
        assertTrue(missing.isEmpty(), "Missing commands: $missing")
    }

    @Test
    fun `대기 command can be created`() {
        val officer = makeOfficer()
        val env = makeEnv()
        val command = registry.createOfficerCommand("대기", officer, env, null)
        assertNotNull(command)
    }
}
