package com.openlogh.engine.strategic

import com.openlogh.engine.CommandPointService.CpType

/**
 * gin7 매뉴얼 원본 기반 레거시 커맨드 CP 비용 레지스트리.
 *
 * 112개 커맨드(General 78 + Nation 34)의 CP 타입/비용을 순수 데이터로 관리.
 * 밸런스 조정 시 이 파일만 수정하면 됨.
 *
 * Design Ref: §3.1 — CpEntry data structure
 * Design Ref: §Appendix A — Full CP cost data
 */
object LegacyCommandCpRegistry {

    data class CpEntry(
        val actionCode: String,
        val cpType: CpType,
        val baseCost: Int,
        val isVariable: Boolean = false,
        val costPerUnit: Int = 0,
        val minCost: Int = baseCost,
        val maxCost: Int = baseCost,
        val commandGroup: String? = null,
    )

    // ===== General Commands (78) =====

    // --- Operations / MCP (25) ---
    private val operations = listOf(
        CpEntry("워프항행", CpType.MCP, 40, commandGroup = "operations"),
        CpEntry("성계내항행", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("연료보급", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("정찰", CpType.MCP, 40, commandGroup = "recon"),
        CpEntry("군기유지", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("기본훈련", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("특수훈련", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("맹훈련", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("육전훈련", CpType.MCP, 80, commandGroup = "ground_operations"),
        CpEntry("공전훈련", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("육전전술훈련", CpType.MCP, 80, commandGroup = "ground_operations"),
        CpEntry("공전전술훈련", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("경계출동", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("무력진압", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("분열행진", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("징발", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("특별경비", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("정비", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("지상작전개시", CpType.MCP, 80, commandGroup = "ground_operations"),
        CpEntry("지상전투개시", CpType.MCP, 80, commandGroup = "ground_operations"),
        CpEntry("점령", CpType.MCP, 80, commandGroup = "ground_operations"),
        CpEntry("철수", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("후퇴", CpType.MCP, 80, commandGroup = "operations"),
        CpEntry("육전대출격", CpType.MCP, 80, commandGroup = "ground_operations"),
        CpEntry("육전대철수", CpType.MCP, 80, commandGroup = "ground_operations"),
    )

    // --- Personal / PCP (16) ---
    private val personal = listOf(
        CpEntry("퇴역", CpType.PCP, 160, commandGroup = "move"),
        CpEntry("지원전환", CpType.PCP, 160, commandGroup = "move"),
        CpEntry("망명", CpType.PCP, 320, commandGroup = "move"),
        CpEntry("회견", CpType.PCP, 10, commandGroup = "move"),
        CpEntry("수강", CpType.PCP, 160, commandGroup = "move"),
        CpEntry("기함구매", CpType.PCP, 80, commandGroup = "move"),
        CpEntry("자금투입", CpType.PCP, 80, commandGroup = "move"),
        CpEntry("귀환설정", CpType.PCP, 0, commandGroup = "move"),
        CpEntry("원거리이동", CpType.PCP, 10, commandGroup = "move"),
        CpEntry("근거리이동", CpType.PCP, 5, commandGroup = "move"),
        CpEntry("병기연습", CpType.MCP, 10, commandGroup = "move"),
        CpEntry("반의", CpType.PCP, 640, commandGroup = "move"),
        CpEntry("모의", CpType.PCP, 640, commandGroup = "move"),
        CpEntry("설득", CpType.PCP, 640, commandGroup = "move"),
        CpEntry("반란참가", CpType.PCP, 160, commandGroup = "move"),
        CpEntry("반란", CpType.PCP, 640, commandGroup = "move"),
    )

    // --- Command / MCP (8) ---
    private val command = listOf(
        CpEntry("작전계획", CpType.MCP, 10, isVariable = true, costPerUnit = 10, minCost = 10, maxCost = 1280, commandGroup = "operations"),
        CpEntry("작전철회", CpType.MCP, 5, isVariable = true, costPerUnit = 5, minCost = 5, maxCost = 320, commandGroup = "operations"),
        CpEntry("장수발령", CpType.MCP, 1, isVariable = true, costPerUnit = 1, minCost = 1, maxCost = 320, commandGroup = "personnel"),
        CpEntry("부대결성", CpType.MCP, 320, commandGroup = "operations"),
        CpEntry("부대해산", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("강의", CpType.MCP, 160, commandGroup = "operations"),
        CpEntry("수송계획", CpType.MCP, 80, commandGroup = "logistics"),
        CpEntry("수송중지", CpType.MCP, 80, commandGroup = "logistics"),
    )

    // --- Logistics / MCP (6) ---
    private val logistics = listOf(
        CpEntry("재편성", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("완전수리", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("완전보급", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("반출입", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("보충", CpType.MCP, 160, commandGroup = "logistics"),
        CpEntry("할당", CpType.MCP, 160, commandGroup = "logistics"),
    )

    // --- Influence / PCP (5) ---
    private val influence = listOf(
        CpEntry("야회", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("수렵", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("회담", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("담화", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("연설", CpType.PCP, 320, commandGroup = "politics"),
    )

    // --- Proposal-Order / PCP (2) ---
    private val proposalOrder = listOf(
        CpEntry("제안", CpType.PCP, 20, commandGroup = "politics"),
        CpEntry("명령", CpType.PCP, 10, commandGroup = "politics"),
    )

    // --- Espionage / Mixed (15) ---
    private val espionage = listOf(
        CpEntry("일제수색", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("체포허가", CpType.PCP, 800, commandGroup = "espionage"),
        CpEntry("집행명령", CpType.PCP, 800, commandGroup = "espionage"),
        CpEntry("체포명령", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("사열", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("습격", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("감시", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("잠입공작", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("탈출공작", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("정보공작", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("파괴공작", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("선동공작", CpType.PCP, 160, commandGroup = "espionage"),
        CpEntry("귀환공작", CpType.PCP, 320, commandGroup = "espionage"),
        CpEntry("통신방해", CpType.MCP, 160, commandGroup = "espionage"),
        CpEntry("위장함대", CpType.MCP, 160, commandGroup = "espionage"),
    )

    // --- Default (1) ---
    private val defaultCmd = listOf(
        CpEntry("휴식", CpType.PCP, 0),
    )

    // ===== Nation Commands (34) =====

    // --- Personnel / PCP (10) ---
    private val nationPersonnel = listOf(
        CpEntry("승진", CpType.PCP, 160, commandGroup = "personnel"),
        CpEntry("발탁", CpType.PCP, 640, commandGroup = "personnel"),
        CpEntry("강등", CpType.PCP, 320, commandGroup = "personnel"),
        CpEntry("서작", CpType.PCP, 160, commandGroup = "personnel"),
        CpEntry("서훈", CpType.PCP, 160, commandGroup = "personnel"),
        CpEntry("임명", CpType.PCP, 160, commandGroup = "personnel"),
        CpEntry("파면", CpType.PCP, 160, commandGroup = "personnel"),
        CpEntry("사임", CpType.PCP, 80, commandGroup = "personnel"),
        CpEntry("봉토수여", CpType.PCP, 640, commandGroup = "personnel"),
        CpEntry("봉토직할", CpType.PCP, 640, commandGroup = "personnel"),
    )

    // --- Political / PCP (9) ---
    private val nationPolitical = listOf(
        CpEntry("국가목표설정", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("납입률변경", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("관세율변경", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("분배", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("처단", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("외교", CpType.PCP, 320, commandGroup = "diplomacy"),
        CpEntry("통치목표", CpType.PCP, 80, commandGroup = "politics"),
        CpEntry("예산편성", CpType.PCP, 100, commandGroup = "economy"),
        CpEntry("제안공작", CpType.PCP, 50, commandGroup = "politics"),
    )

    // --- Diplomacy / PCP (7) ---
    private val nationDiplomacy = listOf(
        CpEntry("선전포고", CpType.PCP, 320, commandGroup = "diplomacy"),
        CpEntry("불가침제의", CpType.PCP, 160, commandGroup = "diplomacy"),
        CpEntry("불가침수락", CpType.PCP, 80, commandGroup = "diplomacy"),
        CpEntry("불가침파기제의", CpType.PCP, 320, commandGroup = "diplomacy"),
        CpEntry("불가침파기수락", CpType.PCP, 160, commandGroup = "diplomacy"),
        CpEntry("종전제의", CpType.PCP, 320, commandGroup = "diplomacy"),
        CpEntry("종전수락", CpType.PCP, 160, commandGroup = "diplomacy"),
    )

    // --- Resource-Admin / PCP (7) ---
    private val nationResource = listOf(
        CpEntry("감축", CpType.PCP, 160, commandGroup = "economy"),
        CpEntry("주민동원", CpType.PCP, 160, commandGroup = "economy"),
        CpEntry("외교공작", CpType.PCP, 320, commandGroup = "diplomacy"),
        CpEntry("세율변경", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("징병률변경", CpType.PCP, 320, commandGroup = "politics"),
        CpEntry("국가해산", CpType.PCP, 640, commandGroup = "politics"),
        CpEntry("항복", CpType.PCP, 640, commandGroup = "diplomacy"),
    )

    // --- Nation Default (1) ---
    private val nationDefault = listOf(
        CpEntry("Nation휴식", CpType.PCP, 0),
    )

    // ===== Lookup =====

    private val registry: Map<String, CpEntry> = buildMap {
        val allEntries = operations + personal + command + logistics + influence +
            proposalOrder + espionage + defaultCmd +
            nationPersonnel + nationPolitical + nationDiplomacy + nationResource + nationDefault
        for (entry in allEntries) {
            put(entry.actionCode, entry)
        }
    }

    fun findByCode(actionCode: String): CpEntry? = registry[actionCode]

    fun allEntries(): Collection<CpEntry> = registry.values

    fun size(): Int = registry.size
}
