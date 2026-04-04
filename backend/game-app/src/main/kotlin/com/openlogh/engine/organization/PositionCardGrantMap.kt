package com.openlogh.engine.organization

/**
 * 직무권한카드별 실행 가능 커맨드 그룹 매핑.
 *
 * PositionCardType enum에서 분리하여 SRP 준수.
 * 밸런스/권한 조정 시 이 파일만 수정.
 *
 * Design Ref: §4.1 — PositionCardGrantMap API
 * Design Ref: §9.3 — File Import Rules
 */
object PositionCardGrantMap {

    private val grantMap: Map<String, Set<String>> = mapOf(
        // ========== Common Basic ==========
        "personal" to setOf("move", "retire", "volunteer", "defect", "meeting", "study",
            "war_game", "rebellion_intent", "conspiracy", "persuade",
            "invest_funds", "buy_flagship", "return_setting"),

        "captain" to setOf("warp", "inter_system", "refuel", "recon", "discipline",
            "navigation_training", "ground_training", "fighter_training",
            "patrol_deploy", "suppress", "parade", "requisition", "special_guard",
            "ground_deploy", "ground_withdraw"),

        // ========== Common Fleet ==========
        "fleet_commander" to setOf("operations", "logistics", "formation", "energy",
            "repair", "resupply", "reorganize", "transfer", "personnel"),
        "fleet_vice_commander" to setOf("operations", "logistics", "formation"),
        "fleet_chief_of_staff" to setOf("operations", "logistics", "recon", "formation"),
        "fleet_staff" to setOf("recon", "formation"),
        "fleet_adjutant" to setOf("recon"),

        "patrol_commander" to setOf("operations", "logistics", "recon"),
        "patrol_vice" to setOf("operations", "recon"),
        "patrol_adjutant" to setOf("recon"),

        "transport_commander" to setOf("logistics", "transfer", "resupply"),
        "transport_vice" to setOf("logistics", "transfer"),
        "transport_adjutant" to setOf("logistics"),

        "ground_commander" to setOf("ground_operations", "ground_deploy", "ground_withdraw"),

        // ========== Common Planet/Fortress ==========
        "planet_governor" to setOf("politics", "economy", "diplomacy", "govern", "budget"),
        "defense_commander" to setOf("defense", "ground_operations", "patrol_deploy", "suppress"),
        "fortress_commander" to setOf("defense", "operations", "fortress_cannon"),
        "fortress_garrison" to setOf("defense", "ground_operations"),
        "fortress_admin" to setOf("economy", "logistics"),

        // ========== Empire — Sovereign ==========
        "emperor" to setOf("all"),

        // ========== Empire — Cabinet ==========
        "imperial_chancellor" to setOf("politics", "economy", "diplomacy", "personnel", "budget"),
        "state_minister" to setOf("politics", "diplomacy"),
        "interior_minister" to setOf("politics", "economy", "govern"),
        "finance_minister" to setOf("economy", "budget"),
        "court_minister" to setOf("politics"),
        "justice_minister" to setOf("politics", "espionage"),
        "ceremony_minister" to setOf("politics"),
        "science_minister" to setOf("economy"),
        "cabinet_secretary" to setOf("politics", "economy"),

        // ========== Empire — High Command ==========
        "capital_defense" to setOf("operations", "defense", "ground_operations"),
        "imperial_guard_chief" to setOf("operations", "defense", "ground_operations"),
        "supreme_commander" to setOf("operations", "personnel", "politics", "diplomacy",
            "economy", "command_all", "war_plan"),
        "chief_of_staff" to setOf("operations", "personnel", "war_plan", "formation"),

        // ========== Empire — Staff ==========
        "jmc_vice" to setOf("operations", "war_plan"),
        "ops_div1" to setOf("operations", "war_plan", "recon"),
        "ops_div2" to setOf("operations", "war_plan"),
        "ops_div3" to setOf("operations", "war_plan"),
        "jmc_inspector" to setOf("operations", "recon"),

        // ========== Empire — Space Fleet HQ ==========
        "sf_commander" to setOf("operations", "personnel", "war_plan", "formation"),
        "sf_vice" to setOf("operations", "formation"),
        "sf_chief_staff" to setOf("operations", "recon", "formation"),
        "sf_staff" to setOf("recon", "formation"),

        // ========== Empire — Military Ministry ==========
        "military_minister" to setOf("personnel", "politics", "budget", "promote", "demote",
            "appoint", "dismiss"),
        "military_vice" to setOf("personnel", "budget"),
        "personnel_chief" to setOf("personnel", "promote", "demote", "appoint", "dismiss"),
        "investigation_chief" to setOf("espionage", "mass_search"),
        "military_counselor" to setOf("personnel"),

        // ========== Empire — Grenadier ==========
        "grenadier_chief" to setOf("ground_operations", "personnel", "formation"),
        "grenadier_vice" to setOf("ground_operations", "formation"),

        // ========== Empire — Intelligence/Special ==========
        "mp_chief" to setOf("arrest_permit", "execute_order", "arrest_order",
            "inspection", "mass_search", "raid"),
        "mp_vice" to setOf("arrest_order", "inspection", "mass_search"),
        "science_inspector" to setOf("economy"),
        "spy_chief" to setOf("espionage", "mass_search", "surveillance", "infiltrate",
            "intel_gathering", "sabotage"),
        "spy_officer" to setOf("espionage", "infiltrate", "intel_gathering"),
        "academy_commandant" to setOf("personnel", "training"),
        "academy_instructor" to setOf("training"),

        // ========== Empire — Fezzan/Fief ==========
        "fezzan_envoy" to setOf("diplomacy", "politics"),
        "fezzan_aide" to setOf("diplomacy"),
        "fezzan_attache" to setOf("recon"),
        "fief_lord" to setOf("economy", "govern", "politics"),

        // ========== Alliance — Sovereign ==========
        "chairman" to setOf("politics", "diplomacy", "economy", "personnel", "budget",
            "war_declaration"),
        "vice_chairman" to setOf("politics", "diplomacy", "economy"),

        // ========== Alliance — Cabinet ==========
        "state_committee" to setOf("politics", "diplomacy"),
        "defense_committee" to setOf("operations", "personnel", "war_plan", "budget"),
        "finance_committee" to setOf("economy", "budget"),
        "law_committee" to setOf("politics", "espionage"),
        "resource_committee" to setOf("economy"),
        "hr_committee" to setOf("personnel", "economy"),
        "economy_committee" to setOf("economy"),
        "community_committee" to setOf("politics", "economy"),
        "info_committee" to setOf("politics"),
        "council_secretary" to setOf("politics"),

        // ========== Alliance — JOC ==========
        "joc_chief" to setOf("operations", "personnel", "war_plan", "formation"),
        "joc_vice1" to setOf("operations", "war_plan", "formation"),
        "joc_vice2" to setOf("operations", "war_plan"),
        "joc_vice3" to setOf("operations", "war_plan"),
        "joc_counselor" to setOf("operations", "recon"),

        // ========== Alliance — Defense Departments ==========
        "defense_strategy" to setOf("operations", "war_plan"),
        "defense_personnel" to setOf("personnel"),
        "defense_protection" to setOf("defense", "operations"),
        "defense_intel" to setOf("espionage", "recon", "mass_search", "surveillance"),
        "defense_comm" to setOf("recon"),
        "defense_equipment" to setOf("logistics"),
        "defense_facilities" to setOf("economy"),
        "defense_accounting" to setOf("economy", "budget"),
        "defense_education" to setOf("training", "personnel"),
        "defense_medical" to setOf("logistics"),

        // ========== Alliance — Rear Services ==========
        "rear_chief" to setOf("logistics", "economy"),
        "rear_vice" to setOf("logistics"),
        "rear_counselor" to setOf("logistics"),

        // ========== Alliance — Special ==========
        "ground_inspector" to setOf("ground_operations", "personnel", "training"),
        "alliance_mp_chief" to setOf("arrest_permit", "execute_order", "arrest_order",
            "inspection", "mass_search", "raid"),
        "inquiry_chief" to setOf("espionage", "inspection"),
        "alliance_spy_chief" to setOf("espionage", "mass_search", "surveillance", "infiltrate",
            "intel_gathering", "sabotage"),
        "alliance_spy_officer" to setOf("espionage", "infiltrate", "intel_gathering"),
    )

    /** 카드 코드 → 실행 가능한 커맨드 그룹 세트 */
    fun getGrantedCommands(cardCode: String): Set<String> =
        grantMap[cardCode] ?: emptySet()

    /** 모든 카드의 grantedCommands가 비어있지 않은지 검증 */
    fun validateAll(): Boolean =
        PositionCardType.entries.all { getGrantedCommands(it.code).isNotEmpty() }

    fun size(): Int = grantMap.size
}
