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
 *
 * Design Ref: §3.2 — PositionCardType Enum Extension
 * Design Ref: §Appendix B — 77 Cards
 */

// ===== 진영 구분 =====

enum class Faction {
    EMPIRE,    // 은하제국
    ALLIANCE,  // 자유행성동맹
}

// ===== 카드 카테고리 =====

enum class CardCategory(val displayName: String) {
    BASIC("기본"),
    FLEET("함대"),
    ADMINISTRATION("행정"),
    HIGH_COMMAND("중앙 지휘"),
    INTELLIGENCE("첩보"),
    SOVEREIGN("최고 권력"),
    CABINET("내각"),       // Design Ref: §3.4
    STAFF("참모"),         // Design Ref: §3.4
    ACADEMY("사관학교"),   // Design Ref: §3.4
    FIEF("봉토"),          // Design Ref: §3.4
}

// ===== 직무권한카드 타입 (77종) =====

enum class PositionCardType(
    val code: String,
    val displayName: String,
    val jpName: String,
    val category: CardCategory,
    /** 진영 귀속: null = 공통 */
    val faction: Faction? = null,
    /** 최소 계급 (0~10) */
    val minRank: Int = 0,
    /** 임명 권한자 카드 코드 */
    val appointedBy: String? = null,
) {
    // ========================================
    // Common Cards (19) — 양 진영 공통
    // ========================================

    // --- 기본 카드 (모든 캐릭터 보유) ---
    PERSONAL("personal", "개인", "個人", CardCategory.BASIC),
    CAPTAIN("captain", "함장", "艦長", CardCategory.BASIC),

    // --- 함대 카드 ---
    FLEET_COMMANDER("fleet_commander", "함대사령관", "艦隊司令官",
        CardCategory.FLEET, minRank = 7, appointedBy = "supreme_commander"),
    FLEET_VICE_COMMANDER("fleet_vice_commander", "함대부사령관", "艦隊副司令官",
        CardCategory.FLEET, minRank = 6, appointedBy = "fleet_commander"),
    FLEET_CHIEF_OF_STAFF("fleet_chief_of_staff", "함대참모장", "艦隊参謀長",
        CardCategory.FLEET, minRank = 5, appointedBy = "fleet_commander"),
    FLEET_STAFF("fleet_staff", "함대참모", "艦隊参謀",
        CardCategory.FLEET, minRank = 3, appointedBy = "fleet_chief_of_staff"),
    FLEET_ADJUTANT("fleet_adjutant", "함대부관", "艦隊副官",
        CardCategory.FLEET, minRank = 1, appointedBy = "fleet_commander"),

    // --- 순찰대 ---
    PATROL_COMMANDER("patrol_commander", "순찰대사령", "巡察隊司令",
        CardCategory.FLEET, minRank = 5, appointedBy = "fleet_commander"),
    PATROL_VICE("patrol_vice", "순찰대부사령", "巡察隊副司令",
        CardCategory.FLEET, minRank = 4, appointedBy = "patrol_commander"),
    PATROL_ADJUTANT("patrol_adjutant", "순찰대부관", "巡察隊副官",
        CardCategory.FLEET, minRank = 1, appointedBy = "patrol_commander"),

    // --- 수송함대 ---
    TRANSPORT_COMMANDER("transport_commander", "수송함대사령관", "輸送艦隊司令官",
        CardCategory.FLEET, minRank = 4, appointedBy = "fleet_commander"),
    TRANSPORT_VICE("transport_vice", "수송함대부사령관", "輸送艦隊副司令官",
        CardCategory.FLEET, minRank = 3, appointedBy = "transport_commander"),
    TRANSPORT_ADJUTANT("transport_adjutant", "수송함대부관", "輸送艦隊副官",
        CardCategory.FLEET, minRank = 1, appointedBy = "transport_commander"),

    // --- 지상부대/행성/요새 ---
    GROUND_COMMANDER("ground_commander", "지상부대지휘관", "地上部隊指揮官",
        CardCategory.FLEET, minRank = 3, appointedBy = "fleet_commander"),
    PLANET_GOVERNOR("planet_governor", "행성총독", "惑星総督",
        CardCategory.ADMINISTRATION, minRank = 5, appointedBy = "supreme_commander"),
    DEFENSE_COMMANDER("defense_commander", "행성수비대지휘관", "惑星守備隊指揮官",
        CardCategory.ADMINISTRATION, minRank = 4, appointedBy = "planet_governor"),
    FORTRESS_COMMANDER("fortress_commander", "요새사령관", "要塞司令官",
        CardCategory.ADMINISTRATION, minRank = 7, appointedBy = "supreme_commander"),
    FORTRESS_GARRISON("fortress_garrison", "요새수비대장", "要塞守備隊指揮官",
        CardCategory.ADMINISTRATION, minRank = 5, appointedBy = "fortress_commander"),
    FORTRESS_ADMIN("fortress_admin", "요새사무총감", "要塞事務総監",
        CardCategory.ADMINISTRATION, minRank = 5, appointedBy = "fortress_commander"),

    // ========================================
    // Empire Cards (41) — 은하제국 전용
    // ========================================

    // --- 최고 권력 ---
    EMPEROR("emperor", "황제", "皇帝",
        CardCategory.SOVEREIGN, faction = Faction.EMPIRE, minRank = 10),

    // --- 내각 ---
    IMPERIAL_CHANCELLOR("imperial_chancellor", "제국재상", "帝国宰相",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 9, appointedBy = "emperor"),
    STATE_MINISTER("state_minister", "국무상서", "国務尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    INTERIOR_MINISTER("interior_minister", "내무상서", "内務尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    FINANCE_MINISTER("finance_minister", "재무상서", "財務尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    COURT_MINISTER("court_minister", "궁내상서", "宮内尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    JUSTICE_MINISTER("justice_minister", "사법상서", "司法尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    CEREMONY_MINISTER("ceremony_minister", "전례상서", "典礼尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    SCIENCE_MINISTER("science_minister", "과학기술상서", "科学尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    CABINET_SECRETARY("cabinet_secretary", "내각서기관장", "内閣書記官長",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 7, appointedBy = "imperial_chancellor"),

    // --- 중앙 지휘 ---
    CAPITAL_DEFENSE("capital_defense", "제도방위사령관", "帝都防衛司令官",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 9, appointedBy = "emperor"),
    IMPERIAL_GUARD_CHIEF("imperial_guard_chief", "근위병총감", "近衛兵総監",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    SUPREME_COMMANDER("supreme_commander", "최고사령관", "帝国軍最高司令官",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 9, appointedBy = "emperor"),
    CHIEF_OF_STAFF("chief_of_staff", "참모총장", "幕僚総監",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 8, appointedBy = "supreme_commander"),

    // --- 통수본부 ---
    JMC_VICE("jmc_vice", "통수본부차장", "統帥本部次長",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 7, appointedBy = "chief_of_staff"),
    OPS_DIV1("ops_div1", "작전일과장", "作戦一課長",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 6, appointedBy = "chief_of_staff"),
    OPS_DIV2("ops_div2", "작전이과장", "作戦二課長",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 6, appointedBy = "chief_of_staff"),
    OPS_DIV3("ops_div3", "작전삼과장", "作戦三課長",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 6, appointedBy = "chief_of_staff"),
    JMC_INSPECTOR("jmc_inspector", "통수본부감찰관", "統帥本部監察官",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 5, appointedBy = "chief_of_staff"),

    // --- 우주함대사령부 ---
    SF_COMMANDER("sf_commander", "우주함대사령장관", "宇宙艦隊司令長官",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 9, appointedBy = "supreme_commander"),
    SF_VICE("sf_vice", "우주함대부사령장관", "宇宙艦隊副司令長官",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 8, appointedBy = "sf_commander"),
    SF_CHIEF_STAFF("sf_chief_staff", "우주함대총참모장", "宇宙艦隊総参謀長",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 7, appointedBy = "sf_commander"),
    SF_STAFF("sf_staff", "우주함대참모", "宇宙艦隊参謀",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 5, appointedBy = "sf_chief_staff"),

    // --- 군무성 ---
    MILITARY_MINISTER("military_minister", "군무상서", "軍務尚書",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 8, appointedBy = "emperor"),
    MILITARY_VICE("military_vice", "군무차관", "軍務省次官",
        CardCategory.CABINET, faction = Faction.EMPIRE, minRank = 7, appointedBy = "military_minister"),
    PERSONNEL_CHIEF("personnel_chief", "인사국장", "人事局長",
        CardCategory.ADMINISTRATION, faction = Faction.EMPIRE, minRank = 6, appointedBy = "military_minister"),
    INVESTIGATION_CHIEF("investigation_chief", "조사국장", "調査局長",
        CardCategory.INTELLIGENCE, faction = Faction.EMPIRE, minRank = 6, appointedBy = "military_minister"),
    MILITARY_COUNSELOR("military_counselor", "군무참사관", "軍務省参事官",
        CardCategory.STAFF, faction = Faction.EMPIRE, minRank = 5, appointedBy = "military_minister"),

    // --- 장갑척탄병 ---
    GRENADIER_CHIEF("grenadier_chief", "장갑척탄병총감", "装甲擲弾兵総監",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 8, appointedBy = "supreme_commander"),
    GRENADIER_VICE("grenadier_vice", "장갑척탄병부총감", "装甲擲弾兵副総監",
        CardCategory.HIGH_COMMAND, faction = Faction.EMPIRE, minRank = 7, appointedBy = "grenadier_chief"),

    // --- 헌병/정보/사관학교 ---
    MP_CHIEF("mp_chief", "헌병총감", "憲兵総監",
        CardCategory.INTELLIGENCE, faction = Faction.EMPIRE, minRank = 7, appointedBy = "emperor"),
    MP_VICE("mp_vice", "헌병부총감", "憲兵副総監",
        CardCategory.INTELLIGENCE, faction = Faction.EMPIRE, minRank = 6, appointedBy = "mp_chief"),
    SCIENCE_INSPECTOR("science_inspector", "과학기술총감", "科学技術総監",
        CardCategory.ADMINISTRATION, faction = Faction.EMPIRE, minRank = 7, appointedBy = "science_minister"),
    SPY_CHIEF("spy_chief", "정찰국장", "総合偵察局長",
        CardCategory.INTELLIGENCE, faction = Faction.EMPIRE, minRank = 6, appointedBy = "chief_of_staff"),
    SPY_OFFICER("spy_officer", "첩보관", "諜報官",
        CardCategory.INTELLIGENCE, faction = Faction.EMPIRE, minRank = 1, appointedBy = "spy_chief"),
    ACADEMY_COMMANDANT("academy_commandant", "사관학교장", "士官学校長",
        CardCategory.ACADEMY, faction = Faction.EMPIRE, minRank = 7, appointedBy = "military_minister"),
    ACADEMY_INSTRUCTOR("academy_instructor", "사관학교교관", "士官学校教官",
        CardCategory.ACADEMY, faction = Faction.EMPIRE, minRank = 3, appointedBy = "academy_commandant"),

    // --- 페잔/봉토 ---
    FEZZAN_ENVOY("fezzan_envoy", "페잔주재변무관", "フェザーン駐在高等弁務官",
        CardCategory.ADMINISTRATION, faction = Faction.EMPIRE, minRank = 6, appointedBy = "state_minister"),
    FEZZAN_AIDE("fezzan_aide", "페잔주재보좌관", "フェザーン駐在補佐官",
        CardCategory.ADMINISTRATION, faction = Faction.EMPIRE, minRank = 4, appointedBy = "fezzan_envoy"),
    FEZZAN_ATTACHE("fezzan_attache", "페잔주재무관", "フェザーン駐在武官",
        CardCategory.ADMINISTRATION, faction = Faction.EMPIRE, minRank = 4, appointedBy = "fezzan_envoy"),
    FIEF_LORD("fief_lord", "봉토영주", "封土領主",
        CardCategory.FIEF, faction = Faction.EMPIRE, minRank = 0, appointedBy = "emperor"),

    // ========================================
    // Alliance Cards (35) — 자유행성동맹 전용
    // ========================================

    // --- 최고평의회 ---
    CHAIRMAN("chairman", "최고평의회의장", "最高評議会議長",
        CardCategory.SOVEREIGN, faction = Faction.ALLIANCE, minRank = 0), // 선출직
    VICE_CHAIRMAN("vice_chairman", "부의장", "副議長",
        CardCategory.SOVEREIGN, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    STATE_COMMITTEE("state_committee", "국무위원장", "国務委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    DEFENSE_COMMITTEE("defense_committee", "국방위원장", "国防委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    FINANCE_COMMITTEE("finance_committee", "재정위원장", "財政委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    LAW_COMMITTEE("law_committee", "법질서위원장", "法秩序委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    RESOURCE_COMMITTEE("resource_committee", "천연자원위원장", "天然資源委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    HR_COMMITTEE("hr_committee", "인적자원위원장", "人的資源委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    ECONOMY_COMMITTEE("economy_committee", "경제개발위원장", "経済開発委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    COMMUNITY_COMMITTEE("community_committee", "지역사회개발위원장", "地域社会開発委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    INFO_COMMITTEE("info_committee", "정보교통위원장", "情報交通委員長",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),
    COUNCIL_SECRETARY("council_secretary", "서기", "書記",
        CardCategory.CABINET, faction = Faction.ALLIANCE, minRank = 0, appointedBy = "chairman"),

    // --- 통합작전본부 ---
    JOC_CHIEF("joc_chief", "통합작전본부장", "統合作戦本部長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 9, appointedBy = "defense_committee"),
    JOC_VICE1("joc_vice1", "통작본제1차장", "統合作戦本部第一次長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 8, appointedBy = "joc_chief"),
    JOC_VICE2("joc_vice2", "통작본제2차장", "統合作戦本部第二次長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "joc_chief"),
    JOC_VICE3("joc_vice3", "통작본제3차장", "統合作戦本部第三次長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "joc_chief"),
    JOC_COUNSELOR("joc_counselor", "통작본참사관", "統合作戦本部参事官",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 5, appointedBy = "joc_chief"),

    // --- 국방위원회 부서 ---
    DEFENSE_STRATEGY("defense_strategy", "전략부장", "戦略部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "defense_committee"),
    DEFENSE_PERSONNEL("defense_personnel", "인사부장", "人事部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "defense_committee"),
    DEFENSE_PROTECTION("defense_protection", "방위부장", "防衛部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "defense_committee"),
    DEFENSE_INTEL("defense_intel", "정보부장", "情報部長",
        CardCategory.INTELLIGENCE, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "defense_committee"),
    DEFENSE_COMM("defense_comm", "통신부장", "通信部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_committee"),
    DEFENSE_EQUIPMENT("defense_equipment", "장비부장", "装備部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_committee"),
    DEFENSE_FACILITIES("defense_facilities", "시설부장", "施設部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_committee"),
    DEFENSE_ACCOUNTING("defense_accounting", "경리부장", "経理部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_committee"),
    DEFENSE_EDUCATION("defense_education", "교육부장", "教育部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_committee"),
    DEFENSE_MEDICAL("defense_medical", "위생부장", "衛生部長",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_committee"),

    // --- 후방근무본부 ---
    REAR_CHIEF("rear_chief", "후방근무본부장", "後方勤務本部長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 8, appointedBy = "joc_chief"),
    REAR_VICE("rear_vice", "후방근무차장", "後方勤務本部次長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "rear_chief"),
    REAR_COUNSELOR("rear_counselor", "후방근무참사관", "後方勤務本部参事官",
        CardCategory.STAFF, faction = Faction.ALLIANCE, minRank = 5, appointedBy = "rear_chief"),

    // --- 특수 ---
    GROUND_INSPECTOR("ground_inspector", "육전총감부장", "陸戦総監部長",
        CardCategory.HIGH_COMMAND, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "defense_committee"),
    ALLIANCE_MP_CHIEF("alliance_mp_chief", "헌병사령관", "憲兵司令官",
        CardCategory.INTELLIGENCE, faction = Faction.ALLIANCE, minRank = 7, appointedBy = "law_committee"),
    INQUIRY_CHIEF("inquiry_chief", "사문부장", "査問部長",
        CardCategory.INTELLIGENCE, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "law_committee"),
    ALLIANCE_SPY_CHIEF("alliance_spy_chief", "전략작전국장", "戦略作戦局長",
        CardCategory.INTELLIGENCE, faction = Faction.ALLIANCE, minRank = 6, appointedBy = "defense_intel"),
    ALLIANCE_SPY_OFFICER("alliance_spy_officer", "첩보관", "諜報官",
        CardCategory.INTELLIGENCE, faction = Faction.ALLIANCE, minRank = 1, appointedBy = "alliance_spy_chief"),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): PositionCardType? = byCode[code]
    }
}

// ===== 커맨드 게이팅 =====

object CommandGating {

    /** 1인 최대 카드 보유 수 */
    const val MAX_CARDS_PER_OFFICER = 16

    /**
     * 장교가 특정 커맨드를 실행할 수 있는지 확인.
     * grantedCommands는 PositionCardGrantMap에서 외부 조회.
     * Design Ref: §9.2 — Dependency Rules
     */
    fun canExecuteCommand(heldCards: List<String>, commandGroup: String): Boolean {
        return heldCards.any { cardCode ->
            val grants = PositionCardGrantMap.getGrantedCommands(cardCode)
            "all" in grants || commandGroup in grants
        }
    }

    /** 장교의 기본 카드 목록 (개인 + 함장). */
    fun defaultCards(): List<String> = listOf("personal", "captain")

    /** 겸임 가능 여부 확인. */
    fun canAddCard(currentCards: Int): Boolean = currentCards < MAX_CARDS_PER_OFFICER

    /** 계급에 따라 임명 가능한 카드 목록. */
    fun availablePositionsForRank(rank: Int): List<PositionCardType> =
        PositionCardType.entries.filter { it.minRank <= rank && it.category != CardCategory.BASIC }

    /** 진영별 카드 필터링. */
    fun cardsForFaction(faction: Faction?): List<PositionCardType> =
        PositionCardType.entries.filter { it.faction == null || it.faction == faction }
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

    const val FORCED_ACCEPTANCE_COST = 1000

    fun calculateObedienceChance(
        commanderRank: Int,
        subordinateRank: Int,
        loyalty: Int,
        friendship: Int,
    ): Double {
        val rankDiff = commanderRank - subordinateRank
        if (rankDiff <= 0) return 0.3
        val rankFactor = rankDiff * 0.1
        val loyaltyFactor = loyalty * 0.005
        val friendFactor = friendship * 0.002
        return (0.5 + rankFactor + loyaltyFactor + friendFactor).coerceIn(0.20, 0.99)
    }
}
