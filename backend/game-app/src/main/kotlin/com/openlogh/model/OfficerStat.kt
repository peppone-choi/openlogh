package com.openlogh.model

enum class StatCategory { PCP, MCP }

enum class OfficerStat(val category: StatCategory, val koreanName: String) {
    LEADERSHIP(StatCategory.PCP, "통솔"),
    POLITICS(StatCategory.PCP, "정치"),
    ADMINISTRATION(StatCategory.PCP, "운영"),
    INTELLIGENCE(StatCategory.PCP, "정보"),
    COMMAND(StatCategory.MCP, "지휘"),
    MOBILITY(StatCategory.MCP, "기동"),
    ATTACK(StatCategory.MCP, "공격"),
    DEFENSE(StatCategory.MCP, "방어");

    companion object {
        fun pcpStats(): List<OfficerStat> = entries.filter { it.category == StatCategory.PCP }
        fun mcpStats(): List<OfficerStat> = entries.filter { it.category == StatCategory.MCP }
    }
}
