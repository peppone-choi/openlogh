package com.openlogh.engine.ai

data class NpcGeneralPolicy(
    val minWarCrew: Int = 1500,
    val properWarTrainAtmos: Int = 90,
    val priority: List<String> = DEFAULT_GENERAL_PRIORITY,
    private val disabledActions: Set<String> = setOf("한계징병", "고급병종"),
) {
    companion object {
        val DEFAULT_GENERAL_PRIORITY = listOf(
            "긴급내정", "요양", "징병", "훈련", "일반내정", "전방워프", "출병", "중립",
        )
    }

    fun canDo(action: String): Boolean = action !in disabledActions
}

data class NpcNationPolicy(
    val minNPCWarLeadership: Int = 40,
    val minNPCRecruitCityPopulation: Int = 50000,
    val safeRecruitCityPopulationRatio: Double = 0.5,
    val reqNationGold: Int = 10000,
    val reqNationRice: Int = 12000,
    val cureThreshold: Int = 10,
    val priority: List<String> = DEFAULT_NATION_PRIORITY,
    private val disabledActions: Set<String> = emptySet(),
) {
    companion object {
        val DEFAULT_NATION_PRIORITY = listOf(
            "부대전방발령", "NPC포상", "선전포고", "발령", "증축", "포상", "전시전략",
        )
    }

    fun canDo(action: String): Boolean = action !in disabledActions
}

object NpcPolicyBuilder {
    @Suppress("UNCHECKED_CAST")
    fun buildGeneralPolicy(meta: Map<String, Any>): NpcGeneralPolicy {
        val raw = meta["npcGeneralPolicy"] as? Map<String, Any> ?: return NpcGeneralPolicy()
        return NpcGeneralPolicy(
            minWarCrew = (raw["minWarCrew"] as? Number)?.toInt() ?: 1500,
            properWarTrainAtmos = (raw["properWarTrainAtmos"] as? Number)?.toInt() ?: 90,
            priority = (raw["priority"] as? List<String>) ?: NpcGeneralPolicy.DEFAULT_GENERAL_PRIORITY,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun buildNationPolicy(meta: Map<String, Any>): NpcNationPolicy {
        val raw = meta["npcNationPolicy"] as? Map<String, Any> ?: return NpcNationPolicy()
        return NpcNationPolicy(
            minNPCWarLeadership = (raw["minNPCWarLeadership"] as? Number)?.toInt() ?: 40,
            minNPCRecruitCityPopulation = (raw["minNPCRecruitCityPopulation"] as? Number)?.toInt() ?: 50000,
            safeRecruitCityPopulationRatio = (raw["safeRecruitCityPopulationRatio"] as? Number)?.toDouble() ?: 0.5,
            reqNationGold = (raw["reqNationGold"] as? Number)?.toInt() ?: 10000,
            reqNationRice = (raw["reqNationRice"] as? Number)?.toInt() ?: 12000,
            priority = (raw["priority"] as? List<String>) ?: NpcNationPolicy.DEFAULT_NATION_PRIORITY,
        )
    }
}
