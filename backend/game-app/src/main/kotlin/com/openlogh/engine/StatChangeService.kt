package com.openlogh.engine

import com.openlogh.entity.Officer
import org.springframework.stereotype.Service

/**
 * Checks and applies stat level-ups/level-downs based on accumulated experience.
 *
 * Legacy parity: Officer::checkStatChange() in legacy/hwe/sammo/General.php
 *
 * When statExp reaches upgradeLimit (default 30), the stat increases by 1.
 * When statExp goes below 0, the stat decreases by 1.
 * Stats are capped at maxLevel (default 255).
 */
@Service
class StatChangeService {

    companion object {
        /** Experience threshold for stat level change. Legacy: GameConst::$upgradeLimit */
        const val UPGRADE_LIMIT: Int = 30

        /** Maximum stat value. Legacy: GameConst::$maxLevel */
        const val MAX_LEVEL: Int = 255
    }

    data class StatChange(
        val statName: String,
        val displayName: String,
        val oldValue: Int,
        val newValue: Int,
        val delta: Int, // +1 or -1
    )

    data class StatChangeResult(
        val changes: List<StatChange>,
        val logs: List<String>,
    ) {
        val hasChanges: Boolean get() = changes.isNotEmpty()
    }

    private data class StatEntry(
        val displayName: String,
        val statName: String,
        val expName: String,
    )

    // Phase 24-29 (gap A10, gin7 매뉴얼 p14): 캐릭터 경험치 자동 성장 —
    // OpenLOGH 8-stat(통솔·지휘·정보·정치·운영·기동·공격·방어) 전부에 대해
    // {statExp} ≥ UPGRADE_LIMIT 이면 stat +1, statExp < 0 이면 stat -1.
    // v2.4 까지는 legacy 삼국지 3-stat(leadership/strength/intel) 만 다뤘고
    // 나머지 5 항목은 누적되지만 stat level-up 경로가 없었다.
    private val statTable = listOf(
        StatEntry("통솔", "leadership",     "leadershipExp"),
        StatEntry("무력", "strength",       "strengthExp"),     // alias → command
        StatEntry("지력", "intel",          "intelExp"),        // alias → intelligence
        StatEntry("정치", "politics",       "politicsExp"),
        StatEntry("운영", "administration", "administrationExp"),
        StatEntry("기동", "mobility",       "mobilityExp"),
        StatEntry("공격", "attack",         "attackExp"),
        StatEntry("방어", "defense",        "defenseExp"),
    )

    /**
     * Check all three stats for level changes and apply them to the general entity.
     *
     * Legacy: Officer::checkStatChange()
     * Called after command execution when statExp values have been modified.
     */
    fun checkStatChange(general: Officer, upgradeLimit: Int = UPGRADE_LIMIT): StatChangeResult {
        val changes = mutableListOf<StatChange>()
        val logs = mutableListOf<String>()

        for (entry in statTable) {
            val exp = getExp(general, entry.expName)
            val stat = getStat(general, entry.statName)

            if (exp < 0) {
                // Stat decreases
                val newStat = maxOf(0, stat - 1)
                if (newStat != stat) {
                    changes.add(StatChange(entry.statName, entry.displayName, stat, newStat, -1))
                    logs.add("<R>${entry.displayName}</>이 <C>1</> 떨어졌습니다!")
                }
                setStat(general, entry.statName, newStat)
                setExp(general, entry.expName, (exp + upgradeLimit).coerceIn(0, 1000).toShort())
            } else if (exp >= upgradeLimit) {
                // Stat increases (capped at maxLevel)
                if (stat < MAX_LEVEL) {
                    val newStat = stat + 1
                    changes.add(StatChange(entry.statName, entry.displayName, stat, newStat, 1))
                    logs.add("<S>${entry.displayName}</>이 <C>1</> 올랐습니다!")
                    setStat(general, entry.statName, newStat)
                }
                // Exp is always consumed even if stat is at max
                setExp(general, entry.expName, (exp - upgradeLimit).coerceIn(0, 1000).toShort())
            }
        }

        return StatChangeResult(changes, logs)
    }

    private fun getExp(general: Officer, expName: String): Int = when (expName) {
        "leadershipExp"     -> general.leadershipExp.toInt()
        "strengthExp"       -> general.commandExp.toInt()        // alias
        "intelExp"          -> general.intelligenceExp.toInt()   // alias
        "politicsExp"       -> general.politicsExp.toInt()
        "administrationExp" -> general.administrationExp.toInt()
        "mobilityExp"       -> general.mobilityExp.toInt()
        "attackExp"         -> general.attackExp.toInt()
        "defenseExp"        -> general.defenseExp.toInt()
        else -> 0
    }

    private fun setExp(general: Officer, expName: String, value: Short) {
        when (expName) {
            "leadershipExp"     -> general.leadershipExp = value
            "strengthExp"       -> general.commandExp = value        // alias
            "intelExp"          -> general.intelligenceExp = value   // alias
            "politicsExp"       -> general.politicsExp = value
            "administrationExp" -> general.administrationExp = value
            "mobilityExp"       -> general.mobilityExp = value
            "attackExp"         -> general.attackExp = value
            "defenseExp"        -> general.defenseExp = value
        }
    }

    private fun getStat(general: Officer, statName: String): Int = when (statName) {
        "leadership"     -> general.leadership.toInt()
        "strength"       -> general.command.toInt()       // alias
        "intel"          -> general.intelligence.toInt()  // alias
        "politics"       -> general.politics.toInt()
        "administration" -> general.administration.toInt()
        "mobility"       -> general.mobility.toInt()
        "attack"         -> general.attack.toInt()
        "defense"        -> general.defense.toInt()
        else -> 0
    }

    private fun setStat(general: Officer, statName: String, value: Int) {
        when (statName) {
            "leadership"     -> general.leadership     = value.coerceIn(0, 100).toShort()
            "strength"       -> general.command        = value.coerceIn(0, 100).toShort()
            "intel"          -> general.intelligence   = value.coerceIn(0, 100).toShort()
            "politics"       -> general.politics       = value.coerceIn(0, 100).toShort()
            "administration" -> general.administration = value.coerceIn(0, 100).toShort()
            "mobility"       -> general.mobility       = value.coerceIn(0, 100).toShort()
            "attack"         -> general.attack         = value.coerceIn(0, 100).toShort()
            "defense"        -> general.defense        = value.coerceIn(0, 100).toShort()
        }
    }
}
