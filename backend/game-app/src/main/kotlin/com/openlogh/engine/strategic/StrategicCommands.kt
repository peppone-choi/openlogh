package com.openlogh.engine.strategic

import com.openlogh.engine.CommandPointService.CpType

/**
 * 미구현 전략 커맨드 정의.
 *
 * gin7 매뉴얼 기반 미구현 커맨드 전체 등록.
 * 각 커맨드의 CP 비용, 타입, 실행 조건을 정의.
 */

// ===== 커맨드 메타데이터 =====

data class StrategicCommandDef(
    val code: String,
    val displayName: String,
    val jpName: String,
    val category: CommandCategory,
    val cpType: CpType,
    val cpCost: Int,
    /** 실행 대기 시간 (게임시간, 분 단위) */
    val waitTime: Int = 0,
    /** 실행 소요 시간 (게임시간, 분 단위) */
    val executionTime: Int = 0,
    /** 필요한 직무카드 커맨드 그룹 */
    val requiredCommandGroup: String,
    val description: String,
)

enum class CommandCategory(val displayName: String) {
    OPERATIONS("작전"),
    PERSONAL("개인"),
    COMMAND("지휘"),
    LOGISTICS("병참"),
    PERSONNEL("인사"),
    POLITICS("정치"),
    ESPIONAGE("첩보"),
}

// ===== 미구현 커맨드 전체 등록 =====

object StrategicCommandRegistry {

    val commands: List<StrategicCommandDef> = listOf(
        // === 작전 코맨드군 (#1-11) ===
        StrategicCommandDef("inter_system_nav", "성계내 항행", "星系内航行",
            CommandCategory.OPERATIONS, CpType.MCP, 20,
            waitTime = 5, executionTime = 30,
            requiredCommandGroup = "move",
            description = "같은 성계 내 행성 간 이동"),

        StrategicCommandDef("discipline", "군기 유지", "軍紀維持",
            CommandCategory.OPERATIONS, CpType.MCP, 40,
            executionTime = 60,
            requiredCommandGroup = "discipline",
            description = "혼란 발생률 감소. 함대 내 질서 유지"),

        StrategicCommandDef("ground_training", "육전 훈련", "陸戦訓練",
            CommandCategory.OPERATIONS, CpType.MCP, 60,
            executionTime = 120,
            requiredCommandGroup = "ground_training",
            description = "육전대 훈련도 향상. groundCombat 경험치 획득"),

        StrategicCommandDef("fighter_training", "공전 훈련", "空戦訓練",
            CommandCategory.OPERATIONS, CpType.MCP, 60,
            executionTime = 120,
            requiredCommandGroup = "fighter_training",
            description = "함재기 훈련도 향상. fighterSkill 경험치 획득"),

        StrategicCommandDef("ground_tactics", "육전전술훈련", "陸戦戦術訓練",
            CommandCategory.OPERATIONS, CpType.MCP, 100,
            executionTime = 180,
            requiredCommandGroup = "training",
            description = "육전 전술 스킬 습득"),

        StrategicCommandDef("fighter_tactics", "공전전술훈련", "空戦戦術訓練",
            CommandCategory.OPERATIONS, CpType.MCP, 100,
            executionTime = 180,
            requiredCommandGroup = "training",
            description = "공전 전술 스킬 습득"),

        StrategicCommandDef("patrol_deploy", "경계 출동", "警戒出動",
            CommandCategory.OPERATIONS, CpType.MCP, 30,
            executionTime = 60,
            requiredCommandGroup = "ground_deploy",
            description = "주둔 육전대로 행성 치안 증가"),

        StrategicCommandDef("suppress", "무력 진압", "武力鎮圧",
            CommandCategory.OPERATIONS, CpType.MCP, 50,
            executionTime = 30,
            requiredCommandGroup = "ground_deploy",
            description = "치안 대폭 증가, 지지율 하락"),

        StrategicCommandDef("parade", "분열 행진", "分列行進",
            CommandCategory.OPERATIONS, CpType.MCP, 30,
            executionTime = 60,
            requiredCommandGroup = "ground_deploy",
            description = "주둔 육전대 분열행진으로 지지율 증가"),

        StrategicCommandDef("requisition", "징발", "徴発",
            CommandCategory.OPERATIONS, CpType.MCP, 40,
            executionTime = 30,
            requiredCommandGroup = "logistics",
            description = "점령 적 행성 군수물자 징발. 지지율 하락"),

        StrategicCommandDef("special_guard", "특별 경비", "特別警備",
            CommandCategory.OPERATIONS, CpType.MCP, 60,
            executionTime = 120,
            requiredCommandGroup = "defense",
            description = "특정 스팟 경계 강화. 습격/잠입 방어"),

        // === 개인 코맨드군 (#12-15) ===
        StrategicCommandDef("long_range_move", "원거리 이동", "遠距離移動",
            CommandCategory.PERSONAL, CpType.PCP, 10,
            waitTime = 5, executionTime = 60,
            requiredCommandGroup = "move",
            description = "다른 행성의 시설로 이동"),

        StrategicCommandDef("short_range_move", "근거리 이동", "近距離移動",
            CommandCategory.PERSONAL, CpType.PCP, 0,
            executionTime = 5,
            requiredCommandGroup = "move",
            description = "같은 행성 내 시설(스팟) 간 이동"),

        StrategicCommandDef("war_game", "병기연습", "兵棋演習",
            CommandCategory.PERSONAL, CpType.MCP, 20,
            executionTime = 60,
            requiredCommandGroup = "training",
            description = "시뮬레이터 전술 훈련. 지휘/기동 경험치"),

        StrategicCommandDef("return_setting", "귀환 설정", "帰還設定",
            CommandCategory.PERSONAL, CpType.PCP, 0,
            requiredCommandGroup = "personal",
            description = "기함 격침 시 귀환 행성 설정"),

        // === 병참 코맨드군 (#16-17) ===
        StrategicCommandDef("assign_units", "할당", "割当",
            CommandCategory.LOGISTICS, CpType.MCP, 40,
            executionTime = 30,
            requiredCommandGroup = "logistics",
            description = "행성 창고에서 부대 창고로 유닛 이동"),

        StrategicCommandDef("replenish", "보충", "補充",
            CommandCategory.LOGISTICS, CpType.MCP, 60,
            executionTime = 60,
            requiredCommandGroup = "logistics",
            description = "동일 서브타입 함선으로 유닛 보충"),

        // === 인사 코맨드군 (#18) ===
        StrategicCommandDef("dismiss", "파면", "罷免",
            CommandCategory.PERSONNEL, CpType.PCP, 80,
            requiredCommandGroup = "dismiss",
            description = "직무권한카드 회수. 해당 직책에서 해임"),

        // === 정치 코맨드군 (#19-22) ===
        StrategicCommandDef("budget", "예산 편성", "予算編成",
            CommandCategory.POLITICS, CpType.PCP, 100,
            executionTime = 120,
            requiredCommandGroup = "budget",
            description = "국가 예산 관리. 부문별 예산 배분"),

        StrategicCommandDef("propose", "제안", "提案",
            CommandCategory.POLITICS, CpType.PCP, 20,
            requiredCommandGroup = "personal",
            description = "하급→상급 제안. 수락 확률은 계급/공적/우호도에 의존"),

        StrategicCommandDef("order", "명령", "命令",
            CommandCategory.POLITICS, CpType.PCP, 10,
            requiredCommandGroup = "command_all",
            description = "상급→하급 명령 하달. 복종 확률은 계급차/충성도에 의존"),

        StrategicCommandDef("forced_proposal", "제안 공작", "提案工作",
            CommandCategory.POLITICS, CpType.PCP, 50,
            requiredCommandGroup = "politics",
            description = "정치공작 1,000 소모하여 제안 강제 수락"),
    )

    private val byCode = commands.associateBy { it.code }
    fun findByCode(code: String): StrategicCommandDef? = byCode[code]
    fun findByCategory(category: CommandCategory): List<StrategicCommandDef> =
        commands.filter { it.category == category }
}

// ===== 세션/기타 (#72-74) =====

/**
 * 상세 승리 조건 (gin7).
 */
enum class VictoryLevel(val displayName: String, val description: String) {
    DECISIVE("결정적 승리", "인구 90% 이상 지배, 함선비 10:1 이상, 쿠데타 없음"),
    LIMITED("한정적 승리", "결정적 승리 조건 일부 미충족"),
    LOCAL("국지적 승리", "적 수도 점령 또는 영토 3성계 이하"),
    DRAW("무승부", "시간 만료 시 인구 비교"),
}

/**
 * 전술전 채팅 3채널.
 */
enum class TacticalChatChannel(val code: String, val displayName: String) {
    ALL("all", "전체"),       // 적 포함 모두
    FLEET("fleet", "함대"),   // 같은 함대 멤버만
    FACTION("faction", "동진영"), // 같은 진영 전체
}

/**
 * 페잔 중립 침범 페널티.
 */
object FezzanNeutralityPenalty {
    /** 중립 침범 시 적용되는 페널티 */
    data class Penalty(
        val approvalLoss: Float = 15f,
        val diplomacyPenalty: Int = -50,
        val commerceLoss: Double = 0.3, // 교역 30% 감소
        val description: String = "페잔 중립 침범! 국제적 비난으로 지지도/외교/교역 대폭 하락",
    )

    fun calculatePenalty(isArmedInvasion: Boolean): Penalty {
        return if (isArmedInvasion) {
            Penalty(approvalLoss = 25f, diplomacyPenalty = -100, commerceLoss = 0.5,
                description = "페잔 무력 점령! 극심한 국제적 고립")
        } else {
            Penalty()
        }
    }
}
