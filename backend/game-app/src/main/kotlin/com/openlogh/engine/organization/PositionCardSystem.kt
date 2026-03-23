package com.openlogh.engine.organization

/**
 * 직무권한카드 기반 커맨드 게이팅 시스템.
 *
 * gin7 매뉴얼:
 * - 모든 커맨드는 직무권한카드를 통해 실행
 * - 각 카드는 실행 가능한 커맨드 목록을 보유
 * - 1인 최대 16장 보유 가능
 * - 겸임 시 복수 카드 보유
 * - 기본: [개인] + [함장] 카드는 모든 캐릭터 보유
 */

// ===== 직무권한카드 타입 =====

enum class PositionCardType(
    val code: String,
    val displayName: String,
    val jpName: String,
    val category: CardCategory,
    /** 이 카드로 실행 가능한 커맨드 그룹 */
    val grantedCommands: Set<String>,
    /** 최소 계급 (0~10) */
    val minRank: Int = 0,
) {
    // === 기본 카드 (모든 캐릭터 보유) ===
    PERSONAL("personal", "개인", "個人", CardCategory.BASIC,
        setOf("move", "retire", "volunteer", "defect", "meeting", "study",
            "war_game", "rebellion_intent", "conspiracy", "persuade",
            "invest_funds", "buy_flagship", "return_setting"),
        minRank = 0),

    CAPTAIN("captain", "함장", "艦長", CardCategory.BASIC,
        setOf("warp", "inter_system", "refuel", "recon", "discipline",
            "navigation_training", "ground_training", "fighter_training",
            "patrol_deploy", "suppress", "parade", "requisition", "special_guard",
            "ground_deploy", "ground_withdraw"),
        minRank = 0),

    // === 함대 카드 ===
    FLEET_COMMANDER("fleet_commander", "함대사령관", "艦隊司令官", CardCategory.FLEET,
        setOf("operations", "logistics", "formation", "energy", "repair",
            "resupply", "reorganize", "transfer"),
        minRank = 7),

    FLEET_VICE_COMMANDER("fleet_vice_commander", "함대부사령관", "艦隊副司令官", CardCategory.FLEET,
        setOf("operations", "logistics", "formation"),
        minRank = 6),

    FLEET_CHIEF_OF_STAFF("fleet_chief_of_staff", "함대참모장", "艦隊参謀長", CardCategory.FLEET,
        setOf("operations", "logistics", "recon", "formation"),
        minRank = 5),

    FLEET_STAFF("fleet_staff", "함대참모", "艦隊参謀", CardCategory.FLEET,
        setOf("recon", "formation"),
        minRank = 3),

    // === 순찰대/수송함대/지상부대 카드 ===
    PATROL_COMMANDER("patrol_commander", "순찰대사령", "巡察隊司令", CardCategory.FLEET,
        setOf("operations", "logistics", "recon"),
        minRank = 5),

    TRANSPORT_COMMANDER("transport_commander", "수송함대사령관", "輸送艦隊司令官", CardCategory.FLEET,
        setOf("logistics", "transfer", "resupply"),
        minRank = 4),

    GROUND_FORCE_COMMANDER("ground_commander", "지상부대지휘관", "地上部隊指揮官", CardCategory.FLEET,
        setOf("ground_operations", "ground_deploy", "ground_withdraw"),
        minRank = 3),

    // === 행성/요새 카드 ===
    PLANET_GOVERNOR("planet_governor", "행성총독", "惑星総督", CardCategory.ADMINISTRATION,
        setOf("politics", "economy", "diplomacy", "govern", "budget"),
        minRank = 5),

    DEFENSE_COMMANDER("defense_commander", "행성수비대지휘관", "惑星守備隊指揮官", CardCategory.ADMINISTRATION,
        setOf("defense", "ground_operations", "patrol_deploy", "suppress"),
        minRank = 4),

    FORTRESS_COMMANDER("fortress_commander", "요새사령관", "要塞司令官", CardCategory.ADMINISTRATION,
        setOf("defense", "operations", "fortress_cannon"),
        minRank = 7),

    // === 중앙 지휘 카드 ===
    SUPREME_COMMANDER("supreme_commander", "최고사령관", "最高司令官", CardCategory.HIGH_COMMAND,
        setOf("operations", "personnel", "politics", "diplomacy",
            "economy", "command_all", "war_plan"),
        minRank = 9),

    CHIEF_OF_STAFF("chief_of_staff", "참모총장", "統帥本部総長", CardCategory.HIGH_COMMAND,
        setOf("operations", "personnel", "war_plan", "formation"),
        minRank = 8),

    OPERATIONS_CHIEF("operations_chief", "작전과장", "作戦課長", CardCategory.HIGH_COMMAND,
        setOf("operations", "war_plan", "recon"),
        minRank = 7),

    // === 인사/정치 카드 ===
    PERSONNEL_CHIEF("personnel_chief", "인사국장", "人事局長", CardCategory.ADMINISTRATION,
        setOf("personnel", "promote", "demote", "appoint", "dismiss"),
        minRank = 6),

    MILITARY_MINISTER("military_minister", "군무상서", "軍務尚書", CardCategory.HIGH_COMMAND,
        setOf("personnel", "politics", "budget", "promote", "demote",
            "appoint", "dismiss"),
        minRank = 8),

    // === 첩보 카드 ===
    INTELLIGENCE_CHIEF("intelligence_chief", "정보국장", "情報局長", CardCategory.INTELLIGENCE,
        setOf("espionage", "mass_search", "surveillance", "infiltrate",
            "intel_gathering", "sabotage"),
        minRank = 6),

    MILITARY_POLICE_CHIEF("mp_chief", "헌병총감", "憲兵総監", CardCategory.INTELLIGENCE,
        setOf("arrest_permit", "execute_order", "arrest_order",
            "inspection", "mass_search", "raid"),
        minRank = 7),

    // === 제국 전용 ===
    EMPEROR("emperor", "황제", "皇帝", CardCategory.SOVEREIGN,
        setOf("all"), // 모든 커맨드 실행 가능
        minRank = 10),

    GRENADIER_CHIEF("grenadier_chief", "장갑척탄병총감", "装甲擲弾兵総監", CardCategory.HIGH_COMMAND,
        setOf("ground_operations", "personnel", "formation"),
        minRank = 8),

    // === 동맹 전용 ===
    CHAIRMAN("chairman", "최고평의회의장", "最高評議会議長", CardCategory.SOVEREIGN,
        setOf("politics", "diplomacy", "economy", "personnel", "budget",
            "war_declaration"),
        minRank = 0), // 선출직

    GROUND_FORCE_INSPECTOR("ground_inspector", "육전총감부장", "陸戦総監部長", CardCategory.HIGH_COMMAND,
        setOf("ground_operations", "personnel", "training"),
        minRank = 7),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): PositionCardType? = byCode[code]
    }
}

enum class CardCategory(val displayName: String) {
    BASIC("기본"),
    FLEET("함대"),
    ADMINISTRATION("행정"),
    HIGH_COMMAND("중앙 지휘"),
    INTELLIGENCE("첩보"),
    SOVEREIGN("최고 권력"),
}

// ===== 커맨드 게이팅 =====

object CommandGating {

    /** 1인 최대 카드 보유 수 */
    const val MAX_CARDS_PER_OFFICER = 16

    /**
     * 장교가 특정 커맨드를 실행할 수 있는지 확인.
     * @param heldCards 보유 카드 코드 목록
     * @param commandGroup 실행하려는 커맨드 그룹
     */
    fun canExecuteCommand(heldCards: List<String>, commandGroup: String): Boolean {
        return heldCards.any { cardCode ->
            val cardType = PositionCardType.fromCode(cardCode) ?: return@any false
            "all" in cardType.grantedCommands || commandGroup in cardType.grantedCommands
        }
    }

    /**
     * 장교의 기본 카드 목록 (개인 + 함장).
     */
    fun defaultCards(): List<String> = listOf("personal", "captain")

    /**
     * 겸임 가능 여부 확인.
     * @param currentCards 현재 보유 카드 수
     */
    fun canAddCard(currentCards: Int): Boolean = currentCards < MAX_CARDS_PER_OFFICER

    /**
     * 계급에 따라 임명 가능한 카드 목록.
     */
    fun availablePositionsForRank(rank: Int): List<PositionCardType> =
        PositionCardType.entries.filter { it.minRank <= rank && it.category != CardCategory.BASIC }
}

// ===== 제안/명령 시스템 =====

/**
 * 상하급 간 제안/명령 체계.
 *
 * gin7:
 * - 제안 (하급→상급): 수락 확률 = f(계급, 공적, 상성, 우호도)
 * - 명령 (상급→하급): 복종 확률 = f(충성도, 계급차, 우호도)
 * - 제안 공작: 정치공작 1,000 소모 시 강제 수락
 */
object ProposalSystem {

    /**
     * 제안 수락 확률 계산.
     * @param proposerRank 제안자 계급
     * @param targetRank 대상 계급
     * @param proposerMerit 제안자 공적
     * @param friendship 우호도 (0~100)
     */
    fun calculateAcceptanceChance(
        proposerRank: Int,
        targetRank: Int,
        proposerMerit: Int,
        friendship: Int,
    ): Double {
        val rankFactor = (proposerRank - targetRank + 3) * 0.05
        val meritFactor = proposerMerit * 0.001
        val friendFactor = friendship * 0.003
        return (0.3 + rankFactor + meritFactor + friendFactor).coerceIn(0.05, 0.95)
    }

    /** 제안 공작 비용 */
    const val FORCED_ACCEPTANCE_COST = 1000 // 정치공작

    /**
     * 명령 복종 확률 계산.
     */
    fun calculateObedienceChance(
        commanderRank: Int,
        subordinateRank: Int,
        loyalty: Int,
        friendship: Int,
    ): Double {
        val rankDiff = commanderRank - subordinateRank
        if (rankDiff <= 0) return 0.3 // 동급 이하는 복종 확률 낮음
        val rankFactor = rankDiff * 0.1
        val loyaltyFactor = loyalty * 0.005
        val friendFactor = friendship * 0.002
        return (0.5 + rankFactor + loyaltyFactor + friendFactor).coerceIn(0.20, 0.99)
    }
}
