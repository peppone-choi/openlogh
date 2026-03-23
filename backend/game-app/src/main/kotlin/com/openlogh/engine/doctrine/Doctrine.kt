package com.openlogh.engine.doctrine

/**
 * 국가 체제 3축 시스템 (TFR 레퍼런스 기반).
 *
 * 1축: Ideology (이데올로기) — 정치 사상/철학
 * 2축: GovernmentType (정부 형태) — 권력 구조
 * 3축: EconomicSystem (경제 체제) — 생산/분배 방식
 *
 * 조합 예시:
 * - 은하제국 = 보수주의 + 전제군주정 + 국가자본주의
 * - 자유행성동맹 = 자유주의 + 대통령공화제 + 자유시장경제
 * - 페잔 = 자유지상주의 + 과두제 + 교역주의
 */

// ===== 1축: 이데올로기 =====

enum class Ideology(
    val code: String,
    val displayName: String,
    val description: String,
    val approvalModifier: Double = 0.0,
    val loyaltyModifier: Double = 0.0,
    val warFatigueRate: Double = 1.0,
    val espionageModifier: Double = 0.0,
) {
    SOCIAL_DEMOCRACY("social_democracy", "사회민주주의",
        "복지와 평등 중시. 시민 지지 높으나 군사 의지 약함",
        approvalModifier = 0.15, warFatigueRate = 1.3),

    LIBERALISM("liberalism", "자유주의",
        "개인 자유와 인권 중시. 균형 잡힌 발전",
        approvalModifier = 0.10, warFatigueRate = 1.1),

    CONSERVATISM("conservatism", "보수주의",
        "전통과 질서 중시. 충성도 높고 안정적",
        loyaltyModifier = 0.15, approvalModifier = -0.05),

    NATIONALISM("nationalism", "국가주의",
        "국가 이익 최우선. 결속력 강하나 외교 경직",
        loyaltyModifier = 0.10, warFatigueRate = 0.9),

    MILITARISM("militarism", "군국주의",
        "군사력 최우선. 전쟁 의지 극대화",
        loyaltyModifier = 0.05, warFatigueRate = 0.7),

    FASCISM("fascism", "파시즘",
        "전체주의 극우. 극단적 동원, 내부 억압",
        loyaltyModifier = 0.10, warFatigueRate = 0.6,
        espionageModifier = 0.15, approvalModifier = -0.10),

    COMMUNISM("communism", "공산주의",
        "프롤레타리아 독재. 이념적 결속, 자유 제한",
        loyaltyModifier = 0.10, warFatigueRate = 0.8,
        espionageModifier = 0.10, approvalModifier = -0.05),

    LIBERTARIANISM("libertarianism", "자유지상주의",
        "최소 국가, 최대 자유. 교역 활성, 군사 약함",
        approvalModifier = 0.10, warFatigueRate = 1.4),

    THEOCRATISM("theocratism", "신정주의",
        "종교/이념 절대 권위. 충성 극대, 과학 억제",
        loyaltyModifier = 0.25, warFatigueRate = 0.7),

    ANARCHISM("anarchism", "아나키즘",
        "무정부. 극단적 자유, 조직력 부재",
        approvalModifier = 0.15, loyaltyModifier = -0.15, warFatigueRate = 1.5),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): Ideology? = byCode[code]
    }
}

// ===== 2축: 정부 형태 =====

enum class GovernmentType(
    val code: String,
    val displayName: String,
    val description: String,
    val politicalPowerGain: Double = 0.0,
    val stabilityModifier: Double = 0.0,
    val militaryModifier: Double = 0.0,
    val conscriptionModifier: Double = 0.0,
) {
    ABSOLUTE_MONARCHY("absolute_monarchy", "전제군주정",
        "1인 절대 권력. 의사결정 빠르나 폭정 위험",
        politicalPowerGain = 0.10, stabilityModifier = 0.10,
        militaryModifier = 0.10),

    CONSTITUTIONAL_MONARCHY("constitutional_monarchy", "입헌군주정",
        "군주 + 의회. 안정적이나 의사결정 느림",
        stabilityModifier = 0.05, politicalPowerGain = -0.05),

    PRESIDENTIAL_REPUBLIC("presidential_republic", "대통령공화제",
        "선출 대통령 통치. 균형, 연구 약간 저하",
        politicalPowerGain = 0.05),

    PARLIAMENTARY_REPUBLIC("parliamentary_republic", "의원내각제",
        "의회 중심 통치. 민의 반영, 리더십 분산",
        stabilityModifier = -0.03, politicalPowerGain = -0.05),

    MILITARY_DICTATORSHIP("military_dictatorship", "군사독재",
        "군부 통치. 군사력 극대화, 시민 불만",
        militaryModifier = 0.15, conscriptionModifier = 0.15,
        politicalPowerGain = 0.10, stabilityModifier = -0.05),

    PARTY_STATE("party_state", "일당독재",
        "당 조직이 국가 지배. 이념 통제, 동원력",
        politicalPowerGain = 0.08, conscriptionModifier = 0.10,
        stabilityModifier = 0.05),

    OLIGARCHY("oligarchy", "과두제",
        "소수 엘리트 합의. 안정적이나 부패 위험",
        politicalPowerGain = 0.05, stabilityModifier = 0.03),

    CORPORATE_COUNCIL("corporate_council", "기업 평의회",
        "기업체 합의 통치. 경제 효율적, 군사 약함",
        militaryModifier = -0.10),

    THEOCRATIC_STATE("theocratic_state", "신정국가",
        "종교 지도자 통치. 결속력 강하나 과학 정체",
        stabilityModifier = 0.10, politicalPowerGain = -0.05),

    PROVISIONAL_GOVERNMENT("provisional_government", "임시정부",
        "과도기 정부. 불안정하나 유연함",
        stabilityModifier = -0.10, politicalPowerGain = -0.10),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): GovernmentType? = byCode[code]
    }
}

// ===== 3축: 경제 체제 =====

enum class EconomicSystem(
    val code: String,
    val displayName: String,
    val description: String,
    val commerceModifier: Double = 0.0,
    val taxModifier: Double = 0.0,
    val techModifier: Double = 0.0,
    val productionModifier: Double = 0.0,
    val supplyModifier: Double = 0.0,
) {
    FREE_MARKET("free_market", "자유시장경제",
        "시장 자율. 교역과 혁신 활성, 빈부격차",
        commerceModifier = 0.20, techModifier = 0.10,
        productionModifier = 0.05),

    MIXED_ECONOMY("mixed_economy", "혼합경제",
        "시장 + 정부 개입 균형. 뾰족함 없이 안정",
        commerceModifier = 0.05, productionModifier = 0.05,
        techModifier = 0.05, supplyModifier = 0.05),

    STATE_CAPITALISM("state_capitalism", "국가자본주의",
        "정부가 주요 산업 운영. 전략 산업 집중 가능",
        productionModifier = 0.10, supplyModifier = 0.10,
        commerceModifier = -0.05, techModifier = 0.05),

    PLANNED_ECONOMY("planned_economy", "계획경제",
        "전면 국가 통제. 군수 안정, 혁신 저하",
        supplyModifier = 0.20, productionModifier = 0.10,
        commerceModifier = -0.20, techModifier = -0.10),

    MERCANTILISM("mercantilism", "교역주의",
        "중개 교역 극대화. 자체 생산 약함",
        commerceModifier = 0.30, taxModifier = 0.10,
        productionModifier = -0.15),

    WAR_ECONOMY("war_economy", "전시경제",
        "군수 생산 집중. 민간 경제 위축",
        supplyModifier = 0.25, productionModifier = 0.15,
        commerceModifier = -0.15, taxModifier = -0.10),

    CORPORATISM("corporatism", "조합주의",
        "국가-기업-노동 3자 협력. 균형적이나 경직",
        productionModifier = 0.10, supplyModifier = 0.05,
        taxModifier = 0.05, techModifier = -0.05),

    FEUDAL_ECONOMY("feudal_economy", "봉건경제",
        "영지 기반 자급자족. 보급 안정, 성장 정체",
        supplyModifier = 0.10, taxModifier = 0.15,
        commerceModifier = -0.20, techModifier = -0.15),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): EconomicSystem? = byCode[code]
    }
}

// ===== 보조 정책 =====

enum class SubPolicy(
    val code: String,
    val displayName: String,
    val description: String,
    val modifiers: Map<String, Double>,
) {
    TOTAL_WAR("total_war", "총력전 체제",
        "징병+20%, 교역-10%",
        mapOf("conscription" to 0.20, "commerce" to -0.10)),
    FREE_TRADE("free_trade", "개방 교역",
        "교역+15%, 첩보방어-10%",
        mapOf("commerce" to 0.15, "espionage" to -0.10)),
    ARMS_EXPANSION("arms_expansion", "군비 확장",
        "군사+10%, 기술+5%, 세수-15%",
        mapOf("military" to 0.10, "tech" to 0.05, "tax" to -0.15)),
    TECH_FOCUS("tech_focus", "기술 입국",
        "기술+15%, 군사-5%",
        mapOf("tech" to 0.15, "military" to -0.05)),
    SECRET_POLICE("secret_police", "비밀경찰",
        "첩보+15%, 충성+10%, 지지-10%",
        mapOf("espionage" to 0.15, "loyalty" to 0.10, "approval" to -0.10)),
    PROPAGANDA("propaganda", "선전 선동",
        "지지+15%, 전쟁피로 감소",
        mapOf("approval" to 0.15, "warFatigue" to -0.15)),
    ISOLATIONISM("isolationism", "고립주의",
        "지지+10%, 교역-20%, 첩보방어+15%",
        mapOf("approval" to 0.10, "commerce" to -0.20, "espionage" to 0.15)),
    WELFARE_STATE("welfare_state", "복지국가",
        "지지+10%, 생산-5%, 징병-10%",
        mapOf("approval" to 0.10, "production" to -0.05, "conscription" to -0.10)),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): SubPolicy? = byCode[code]
    }
}

// ===== 진영 체제 구성 (3축 + 보조 정책) =====

data class FactionDoctrine(
    val ideology: Ideology,
    val government: GovernmentType,
    val economy: EconomicSystem,
    val subPolicies: List<SubPolicy> = emptyList(),
) {
    init {
        require(subPolicies.size <= 2) { "최대 2개의 보조 정책만 채택 가능" }
    }

    // === 합산 보정값 ===

    // 이데올로기 축
    fun getApprovalModifier(): Double = ideology.approvalModifier + sub("approval")
    fun getLoyaltyModifier(): Double = ideology.loyaltyModifier + sub("loyalty")
    fun getWarFatigueRate(): Double = ideology.warFatigueRate * (1.0 + sub("warFatigue"))
    fun getEspionageModifier(): Double = ideology.espionageModifier + sub("espionage")

    // 정부 형태 축
    fun getPoliticalPowerGain(): Double = government.politicalPowerGain
    fun getStabilityModifier(): Double = government.stabilityModifier
    fun getMilitaryModifier(): Double = government.militaryModifier + sub("military")
    fun getConscriptionModifier(): Double = government.conscriptionModifier + sub("conscription")

    // 경제 체제 축
    fun getCommerceModifier(): Double = economy.commerceModifier + sub("commerce")
    fun getTaxModifier(): Double = economy.taxModifier + sub("tax")
    fun getTechModifier(): Double = economy.techModifier + sub("tech")
    fun getProductionModifier(): Double = economy.productionModifier + sub("production")
    fun getSupplyModifier(): Double = economy.supplyModifier + sub("supply")

    private fun sub(key: String): Double = subPolicies.sumOf { it.modifiers[key] ?: 0.0 }

    companion object {
        /** factionType별 기본 조합 */
        fun defaultFor(factionType: String): FactionDoctrine = when (factionType) {
            "empire" -> FactionDoctrine(
                Ideology.CONSERVATISM,
                GovernmentType.ABSOLUTE_MONARCHY,
                EconomicSystem.STATE_CAPITALISM,
                listOf(SubPolicy.ARMS_EXPANSION),
            )
            "alliance" -> FactionDoctrine(
                Ideology.LIBERALISM,
                GovernmentType.PRESIDENTIAL_REPUBLIC,
                EconomicSystem.FREE_MARKET,
                listOf(SubPolicy.FREE_TRADE),
            )
            "fezzan" -> FactionDoctrine(
                Ideology.LIBERTARIANISM,
                GovernmentType.OLIGARCHY,
                EconomicSystem.MERCANTILISM,
                listOf(SubPolicy.FREE_TRADE),
            )
            // 제국측 반란 (귀족 반란/왕위 찬탈)
            "rebel_empire" -> FactionDoctrine(
                Ideology.CONSERVATISM,
                GovernmentType.ABSOLUTE_MONARCHY,
                EconomicSystem.FEUDAL_ECONOMY,
            )
            // 동맹측 반란 (군부 쿠데타)
            "rebel_alliance" -> FactionDoctrine(
                Ideology.MILITARISM,
                GovernmentType.MILITARY_DICTATORSHIP,
                EconomicSystem.WAR_ECONOMY,
            )
            // 독립/해방 세력
            "rebel", "independence" -> FactionDoctrine(
                Ideology.NATIONALISM,
                GovernmentType.PROVISIONAL_GOVERNMENT,
                EconomicSystem.WAR_ECONOMY,
            )
            else -> FactionDoctrine(
                Ideology.LIBERALISM,
                GovernmentType.PARLIAMENTARY_REPUBLIC,
                EconomicSystem.MIXED_ECONOMY,
            )
        }

        fun fromMeta(meta: Map<String, Any>): FactionDoctrine? {
            val ideo = Ideology.fromCode(meta["ideology"] as? String ?: return null) ?: return null
            val gov = GovernmentType.fromCode(meta["government"] as? String ?: return null) ?: return null
            val eco = EconomicSystem.fromCode(meta["economy"] as? String ?: return null) ?: return null
            val policies = (meta["subPolicies"] as? List<*>)
                ?.mapNotNull { SubPolicy.fromCode(it as? String ?: return@mapNotNull null) }
                ?: emptyList()
            return FactionDoctrine(ideo, gov, eco, policies)
        }

        fun toMeta(doctrine: FactionDoctrine): Map<String, Any> = mapOf(
            "ideology" to doctrine.ideology.code,
            "government" to doctrine.government.code,
            "economy" to doctrine.economy.code,
            "subPolicies" to doctrine.subPolicies.map { it.code },
        )
    }
}
