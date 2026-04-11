package com.openlogh.model

/**
 * gin7 직무권한카드 집합.
 * Phase 24-17 (gap D1, gin7 매뉴얼 p58-65): 동맹 국방위원회 산하 11 부장직을
 * 각각 독립 카드로 분리하여 매뉴얼 별표와 1:1 정렬. 이전까지는 `DEFENSE_DEPT_CHIEF`
 * 한 장에 maxHolders=11로 뭉쳐 있었음.
 *
 * Each card grants access to specific command groups and has rank/faction constraints.
 *
 * @param code String identifier
 * @param nameKo Korean name
 * @param nameEn English name
 * @param department Organizational department
 * @param maxHolders How many officers can hold this card simultaneously (0 = unlimited)
 * @param minRank Minimum rank required (0-10)
 * @param maxRank Maximum rank allowed (0-10, use 10 for no upper limit)
 * @param factionType null = any faction, "empire"/"alliance" for faction-specific
 * @param commandGroups Set of CommandGroup this card grants access to
 */
enum class PositionCard(
    val code: String,
    val nameKo: String,
    val nameEn: String,
    val department: String,
    val maxHolders: Int,
    val minRank: Int,
    val maxRank: Int,
    val factionType: String?,
    val commandGroups: Set<CommandGroup>,
) {
    // ===== Universal (both factions, all officers) =====

    PERSONAL(
        "PERSONAL", "개인", "Personal", "공통", 0, 0, 10, null,
        setOf(CommandGroup.PERSONAL),
    ),
    CAPTAIN(
        "CAPTAIN", "함장", "Captain", "공통", 0, 0, 10, null,
        setOf(CommandGroup.OPERATIONS),
    ),

    // ===== Empire Imperial Court (황궁) =====

    EMPEROR(
        "EMPEROR", "황제", "Emperor", "황궁", 1, 10, 10, "empire",
        setOf(CommandGroup.POLITICS, CommandGroup.PERSONNEL, CommandGroup.COMMAND),
    ),
    SUPREME_COMMANDER(
        "SUPREME_COMMANDER", "제국군최고사령관", "Supreme Commander", "황궁", 1, 10, 10, "empire",
        setOf(CommandGroup.COMMAND, CommandGroup.PERSONNEL),
    ),
    CHIEF_OF_STAFF(
        "CHIEF_OF_STAFF", "막료총감", "Chief of Staff", "황궁", 1, 10, 10, "empire",
        setOf(CommandGroup.COMMAND, CommandGroup.PERSONNEL),
    ),
    HQ_STAFF(
        "HQ_STAFF", "대본영참모", "HQ Staff", "황궁", 10, 5, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    IMPERIAL_CHANCELLOR(
        "IMPERIAL_CHANCELLOR", "제국재상", "Imperial Chancellor", "황궁", 1, 10, 10, "empire",
        setOf(CommandGroup.POLITICS, CommandGroup.PERSONNEL),
    ),

    // ===== Empire Cabinet (내각) =====

    STATE_SECRETARY(
        "STATE_SECRETARY", "국무상서", "State Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),
    INTERIOR_SECRETARY(
        "INTERIOR_SECRETARY", "내무상서", "Interior Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS, CommandGroup.INTELLIGENCE),
    ),
    FINANCE_SECRETARY(
        "FINANCE_SECRETARY", "재무상서", "Finance Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),
    COURT_SECRETARY(
        "COURT_SECRETARY", "궁내상서", "Court Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),
    JUSTICE_SECRETARY(
        "JUSTICE_SECRETARY", "사법상서", "Justice Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS, CommandGroup.INTELLIGENCE),
    ),
    CEREMONIES_SECRETARY(
        "CEREMONIES_SECRETARY", "전례상서", "Ceremonies Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),
    SCIENCE_SECRETARY(
        "SCIENCE_SECRETARY", "과학상서", "Science Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),
    CABINET_SECRETARY(
        "CABINET_SECRETARY", "내각서기관장", "Cabinet Secretary", "내각", 1, 7, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),

    // ===== Empire Fezzan Embassy (주페잔) =====

    FEZZAN_HIGH_COMMISSIONER(
        "FEZZAN_HIGH_COMMISSIONER", "주페잔고등판무관", "Fezzan High Commissioner", "주페잔", 1, 6, 10, "empire",
        setOf(CommandGroup.POLITICS, CommandGroup.INTELLIGENCE),
    ),
    FEZZAN_DEPUTY(
        "FEZZAN_DEPUTY", "페잔주재보좌관", "Fezzan Deputy", "주페잔", 1, 5, 10, "empire",
        setOf(CommandGroup.POLITICS),
    ),
    FEZZAN_ATTACHE(
        "FEZZAN_ATTACHE", "페잔주재무관", "Fezzan Military Attache", "주페잔", 1, 5, 10, "empire",
        setOf(CommandGroup.INTELLIGENCE),
    ),

    // ===== Empire Military Affairs Ministry (군무성) =====

    MILITARY_AFFAIRS_SECRETARY(
        "MILITARY_AFFAIRS_SECRETARY", "군무상서", "Military Affairs Secretary", "군무성", 1, 10, 10, "empire",
        setOf(CommandGroup.PERSONNEL, CommandGroup.POLITICS),
    ),
    MILITARY_VICE_SECRETARY(
        "MILITARY_VICE_SECRETARY", "군무성차관", "Military Vice Secretary", "군무성", 1, 8, 10, "empire",
        setOf(CommandGroup.PERSONNEL),
    ),
    MILITARY_HR_CHIEF(
        "MILITARY_HR_CHIEF", "군무성인사국장", "Military HR Bureau Chief", "군무성", 1, 7, 10, "empire",
        setOf(CommandGroup.PERSONNEL),
    ),
    MILITARY_INVESTIGATION_CHIEF(
        "MILITARY_INVESTIGATION_CHIEF", "군무성조사국장", "Investigation Bureau Chief", "군무성", 1, 7, 10, "empire",
        setOf(CommandGroup.INTELLIGENCE),
    ),
    MILITARY_STAFF(
        "MILITARY_STAFF", "군무성참사관", "Military Ministry Staff", "군무성", 10, 5, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Empire High Command (통수본부) =====

    HIGH_COMMAND_CHIEF(
        "HIGH_COMMAND_CHIEF", "통수본부총장", "High Command Chief", "통수본부", 1, 10, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    HIGH_COMMAND_VICE(
        "HIGH_COMMAND_VICE", "통수본부차장", "High Command Vice Chief", "통수본부", 1, 8, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    OPS_1ST_CHIEF(
        "OPS_1ST_CHIEF", "통수본부작전1과장", "Ops 1st Chief", "통수본부", 1, 8, 10, "empire",
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    OPS_2ND_CHIEF(
        "OPS_2ND_CHIEF", "통수본부작전2과장", "Ops 2nd Chief", "통수본부", 1, 7, 10, "empire",
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    OPS_3RD_CHIEF(
        "OPS_3RD_CHIEF", "통수본부작전3과장", "Ops 3rd Chief", "통수본부", 1, 6, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    HIGH_COMMAND_INSPECTOR(
        "HIGH_COMMAND_INSPECTOR", "통수본부감찰관", "High Command Inspector", "통수본부", 10, 5, 10, "empire",
        setOf(CommandGroup.INTELLIGENCE),
    ),

    // ===== Empire Space Fleet Command (우주함대사령부) =====

    SPACE_FLEET_COMMANDER(
        "SPACE_FLEET_COMMANDER", "우주함대사령장관", "Space Fleet Commander", "우주함대사령부", 1, 10, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    SPACE_FLEET_VICE(
        "SPACE_FLEET_VICE", "우주함대부사령장관", "Space Fleet Vice Commander", "우주함대사령부", 1, 10, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    SPACE_FLEET_STAFF_CHIEF(
        "SPACE_FLEET_STAFF_CHIEF", "우주함대총참모장", "Space Fleet Staff Chief", "우주함대사령부", 1, 7, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    SPACE_FLEET_STAFF(
        "SPACE_FLEET_STAFF", "우주함대참모", "Space Fleet Staff", "우주함대사령부", 10, 5, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Empire Military Police (헌병본부) =====

    MP_COMMANDER(
        "MP_COMMANDER", "헌병총감", "MP Commander", "헌병본부", 1, 8, 10, "empire",
        setOf(CommandGroup.INTELLIGENCE, CommandGroup.PERSONNEL),
    ),
    MP_VICE_COMMANDER(
        "MP_VICE_COMMANDER", "헌병부총감", "MP Vice Commander", "헌병본부", 1, 7, 10, "empire",
        setOf(CommandGroup.INTELLIGENCE),
    ),

    // ===== Empire Ground Forces (장갑척탄병총감부) =====

    GROUND_FORCES_COMMANDER(
        "GROUND_FORCES_COMMANDER", "장갑척탄병총감", "Ground Forces Commander", "장갑척탄병총감부", 1, 8, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),
    GROUND_FORCES_VICE(
        "GROUND_FORCES_VICE", "장갑척탄병부총감", "Ground Forces Vice", "장갑척탄병총감부", 1, 7, 10, "empire",
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Empire Science & Tech =====

    SCIENCE_TECH_DIRECTOR(
        "SCIENCE_TECH_DIRECTOR", "과학기술총감", "Science & Tech Director", "과학기술총감부", 1, 8, 10, "empire",
        setOf(CommandGroup.LOGISTICS),
    ),

    // ===== Empire Officer Academy (사관학교) =====

    ACADEMY_DEAN(
        "ACADEMY_DEAN", "사관학교장", "Academy Dean", "사관학교", 1, 8, 10, "empire",
        setOf(CommandGroup.PERSONNEL),
    ),
    ACADEMY_INSTRUCTOR(
        "ACADEMY_INSTRUCTOR", "사관학교교관", "Academy Instructor", "사관학교", 10, 4, 10, "empire",
        setOf(CommandGroup.PERSONNEL),
    ),

    // ===== Unit-level (per fleet/patrol/transport/ground - faction neutral) =====

    FLEET_COMMANDER(
        "FLEET_COMMANDER", "함대사령관", "Fleet Commander", "함대", 1, 7, 10, null,
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    FLEET_VICE_COMMANDER(
        "FLEET_VICE_COMMANDER", "함대부사령관", "Fleet Vice Commander", "함대", 1, 6, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    FLEET_STAFF_CHIEF(
        "FLEET_STAFF_CHIEF", "함대참모장", "Fleet Staff Chief", "함대", 1, 6, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    FLEET_STAFF(
        "FLEET_STAFF", "함대참모", "Fleet Staff", "함대", 6, 4, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    FLEET_ADJUTANT(
        "FLEET_ADJUTANT", "함대사령관부관", "Fleet Adjutant", "함대", 1, 4, 10, null,
        setOf(CommandGroup.LOGISTICS),
    ),
    TRANSPORT_COMMANDER(
        "TRANSPORT_COMMANDER", "수송함대사령관", "Transport Commander", "수송함대", 1, 6, 10, null,
        setOf(CommandGroup.LOGISTICS),
    ),
    TRANSPORT_VICE(
        "TRANSPORT_VICE", "수송함대부사령관", "Transport Vice", "수송함대", 1, 5, 10, null,
        setOf(CommandGroup.LOGISTICS),
    ),
    TRANSPORT_ADJUTANT(
        "TRANSPORT_ADJUTANT", "수송함대사령관부관", "Transport Adjutant", "수송함대", 1, 4, 10, null,
        setOf(CommandGroup.LOGISTICS),
    ),
    PATROL_COMMANDER(
        "PATROL_COMMANDER", "순찰대사령", "Patrol Commander", "순찰대", 1, 5, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    PATROL_VICE(
        "PATROL_VICE", "순찰대부사령", "Patrol Vice Commander", "순찰대", 1, 5, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    PATROL_ADJUTANT(
        "PATROL_ADJUTANT", "순찰대사령부관", "Patrol Adjutant", "순찰대", 1, 4, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    GROUND_UNIT_COMMANDER(
        "GROUND_UNIT_COMMANDER", "지상부대지휘관", "Ground Unit Commander", "지상부대", 1, 5, 10, null,
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Fortress (faction neutral) =====

    FORTRESS_COMMANDER(
        "FORTRESS_COMMANDER", "요새사령관", "Fortress Commander", "요새", 1, 7, 10, null,
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    FORTRESS_GARRISON_COMMANDER(
        "FORTRESS_GARRISON_COMMANDER", "요새수비대지휘관", "Fortress Garrison Commander", "요새", 1, 6, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    FORTRESS_SECRETARY(
        "FORTRESS_SECRETARY", "요새사무총감", "Fortress Secretary", "요새", 1, 6, 10, null,
        setOf(CommandGroup.LOGISTICS, CommandGroup.POLITICS),
    ),

    // ===== Planet Governance (faction neutral) =====

    PLANET_GOVERNOR(
        "PLANET_GOVERNOR", "행성총독", "Planet Governor", "행성", 1, 6, 10, null,
        setOf(CommandGroup.POLITICS, CommandGroup.LOGISTICS),
    ),
    PLANET_GARRISON_COMMANDER(
        "PLANET_GARRISON_COMMANDER", "행성수비대지휘관", "Planet Garrison Commander", "행성", 1, 5, 10, null,
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Capital Planet (faction neutral) =====

    CAPITAL_DEFENSE_COMMANDER(
        "CAPITAL_DEFENSE_COMMANDER", "수도방위사령관", "Capital Defense Commander", "수도방위", 1, 8, 10, null,
        setOf(CommandGroup.COMMAND),
    ),
    CAPITAL_GUARD_COMMANDER(
        "CAPITAL_GUARD_COMMANDER", "근위병총감", "Capital Guard Commander", "수도방위", 1, 8, 10, null,
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Intelligence (faction neutral) =====

    INTELLIGENCE_AGENT(
        "INTELLIGENCE_AGENT", "첩보관", "Intelligence Agent", "첩보", 50, 4, 10, null,
        setOf(CommandGroup.INTELLIGENCE),
    ),

    // ===== Alliance Supreme Council (최고평의회) =====

    COUNCIL_CHAIRMAN(
        "COUNCIL_CHAIRMAN", "의장", "Council Chairman", "최고평의회", 1, 10, 10, "alliance",
        setOf(CommandGroup.POLITICS, CommandGroup.PERSONNEL),
    ),
    COUNCIL_VICE_CHAIRMAN(
        "COUNCIL_VICE_CHAIRMAN", "부의장", "Council Vice Chairman", "최고평의회", 1, 9, 10, "alliance",
        setOf(CommandGroup.POLITICS),
    ),
    COUNCIL_STATE_CHAIR(
        "COUNCIL_STATE_CHAIR", "국무위원장", "State Committee Chair", "최고평의회", 1, 8, 10, "alliance",
        setOf(CommandGroup.POLITICS),
    ),
    COUNCIL_DEFENSE_CHAIR(
        "COUNCIL_DEFENSE_CHAIR", "국방위원장", "Defense Committee Chair", "최고평의회", 1, 8, 10, "alliance",
        setOf(CommandGroup.PERSONNEL, CommandGroup.COMMAND),
    ),
    COUNCIL_FINANCE_CHAIR(
        "COUNCIL_FINANCE_CHAIR", "재정위원장", "Finance Committee Chair", "최고평의회", 1, 8, 10, "alliance",
        setOf(CommandGroup.POLITICS),
    ),
    COUNCIL_MEMBERS(
        "COUNCIL_MEMBERS", "평의회위원", "Council Members", "최고평의회", 6, 7, 10, "alliance",
        setOf(CommandGroup.POLITICS),
    ),
    COUNCIL_SECRETARY(
        "COUNCIL_SECRETARY", "서기", "Council Secretary", "최고평의회", 1, 7, 10, "alliance",
        setOf(CommandGroup.POLITICS),
    ),

    // ===== Alliance Defense Committee Departments =====
    //
    // Phase 24-17 (gap D1, gin7 매뉴얼 p61 동맹군 조직 구성표):
    // 매뉴얼 별표는 국방위원회 산하 11개 부장직을 각각 별도의 직무권한카드로 명시한다.
    // v2.4 까지는 `DEFENSE_DEPT_CHIEF` 한 장 + maxHolders=11로 뭉쳐져 있었으나,
    // 실제 gin7 권한 체계는 부서별로 발령 가능한 커맨드 그룹이 다르므로 11장 분리 구현.

    DEFENSE_INVESTIGATION_DEPT(
        "DEFENSE_INVESTIGATION_DEPT", "국방위원회 사문부장", "Defense Investigation Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.PERSONNEL, CommandGroup.INTELLIGENCE),
    ),
    DEFENSE_STRATEGY_DEPT(
        "DEFENSE_STRATEGY_DEPT", "국방위원회 전략부장", "Defense Strategy Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.COMMAND),
    ),
    DEFENSE_HR_DEPT(
        "DEFENSE_HR_DEPT", "국방위원회 인사부장", "Defense HR Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.PERSONNEL),
    ),
    DEFENSE_COUNTERINTEL_DEPT(
        "DEFENSE_COUNTERINTEL_DEPT", "국방위원회 방첩부장", "Defense Counter-Intelligence Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.INTELLIGENCE),
    ),
    DEFENSE_INTEL_DEPT(
        "DEFENSE_INTEL_DEPT", "국방위원회 정보부장", "Defense Intelligence Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.INTELLIGENCE),
    ),
    DEFENSE_COMMUNICATIONS_DEPT(
        "DEFENSE_COMMUNICATIONS_DEPT", "국방위원회 통신부장", "Defense Communications Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    DEFENSE_EQUIPMENT_DEPT(
        "DEFENSE_EQUIPMENT_DEPT", "국방위원회 장비부장", "Defense Equipment Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    DEFENSE_FACILITIES_DEPT(
        "DEFENSE_FACILITIES_DEPT", "국방위원회 시설부장", "Defense Facilities Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    DEFENSE_ACCOUNTING_DEPT(
        "DEFENSE_ACCOUNTING_DEPT", "국방위원회 경리부장", "Defense Accounting Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    DEFENSE_EDUCATION_DEPT(
        "DEFENSE_EDUCATION_DEPT", "국방위원회 교육부장", "Defense Education Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.PERSONNEL),
    ),
    DEFENSE_HEALTH_DEPT(
        "DEFENSE_HEALTH_DEPT", "국방위원회 위생부장", "Defense Health Dept Chief", "국방위원회", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),

    // ===== Alliance Joint Operations HQ (통합작전본부) =====

    JOINT_OPS_CHIEF(
        "JOINT_OPS_CHIEF", "통합작전본부장", "Joint Ops Chief", "통합작전본부", 1, 10, 10, "alliance",
        setOf(CommandGroup.COMMAND),
    ),
    JOINT_OPS_VICE_1ST(
        "JOINT_OPS_VICE_1ST", "통합작전본부제1차장", "Joint Ops 1st Vice", "통합작전본부", 1, 8, 10, "alliance",
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    JOINT_OPS_VICE_2ND(
        "JOINT_OPS_VICE_2ND", "통합작전본부제2차장", "Joint Ops 2nd Vice", "통합작전본부", 1, 7, 10, "alliance",
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    JOINT_OPS_VICE_3RD(
        "JOINT_OPS_VICE_3RD", "통합작전본부제3차장", "Joint Ops 3rd Vice", "통합작전본부", 1, 6, 10, "alliance",
        setOf(CommandGroup.COMMAND, CommandGroup.LOGISTICS),
    ),
    JOINT_OPS_STAFF(
        "JOINT_OPS_STAFF", "통합작전본부참사관", "Joint Ops Staff", "통합작전본부", 10, 5, 10, "alliance",
        setOf(CommandGroup.COMMAND),
    ),
    GROUND_WAR_SUPERVISOR(
        "GROUND_WAR_SUPERVISOR", "육전총감부장", "Ground War Supervisor", "통합작전본부", 1, 7, 10, "alliance",
        setOf(CommandGroup.COMMAND),
    ),

    // ===== Alliance Logistics HQ (후방근무본부) =====

    LOGISTICS_CHIEF(
        "LOGISTICS_CHIEF", "후방근무본부장", "Logistics Chief", "후방근무본부", 1, 8, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    LOGISTICS_VICE(
        "LOGISTICS_VICE", "후방근무본부차장", "Logistics Vice", "후방근무본부", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    LOGISTICS_STAFF(
        "LOGISTICS_STAFF", "후방근무본부참사관", "Logistics Staff", "후방근무본부", 10, 5, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    SCIENCE_TECH_CHIEF(
        "SCIENCE_TECH_CHIEF", "과학기술본부장", "Science Tech Chief", "후방근무본부", 1, 7, 10, "alliance",
        setOf(CommandGroup.LOGISTICS),
    ),
    ALLIANCE_MP_COMMANDER(
        "ALLIANCE_MP_COMMANDER", "헌병사령관", "Alliance MP Commander", "후방근무본부", 1, 7, 10, "alliance",
        setOf(CommandGroup.INTELLIGENCE),
    ),

    // ===== Alliance Officer Academy =====

    ALLIANCE_ACADEMY_DEAN(
        "ALLIANCE_ACADEMY_DEAN", "동맹군사관학교장", "Alliance Academy Dean", "동맹군사관학교", 1, 8, 10, "alliance",
        setOf(CommandGroup.PERSONNEL),
    ),
    ALLIANCE_ACADEMY_INSTRUCTOR(
        "ALLIANCE_ACADEMY_INSTRUCTOR", "동맹군사관학교교관", "Alliance Academy Instructor", "동맹군사관학교", 10, 4, 10, "alliance",
        setOf(CommandGroup.PERSONNEL),
    ),

    // ===== Alliance Strategic Operations (전략작전국) =====

    STRATEGIC_OPS_AGENTS(
        "STRATEGIC_OPS_AGENTS", "전략작전국첩보관", "Strategic Ops Agents", "전략작전국", 50, 4, 10, "alliance",
        setOf(CommandGroup.INTELLIGENCE),
    ),
    ;

    companion object {
        /** Returns all cards applicable to a given faction type (faction-specific + neutral cards). */
        fun forFaction(factionType: String): List<PositionCard> =
            entries.filter { it.factionType == null || it.factionType == factionType }

        /** Returns the default cards every officer receives. */
        fun defaults(): List<PositionCard> = listOf(PERSONAL, CAPTAIN)
    }
}
