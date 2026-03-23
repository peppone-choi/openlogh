package com.openlogh.engine

import com.openlogh.entity.Officer
import org.springframework.stereotype.Service

@Service
class StatChangeService {

    companion object {
        const val UPGRADE_LIMIT: Short = 30
        const val MAX_LEVEL: Short = 255
    }

    data class StatChange(val statName: String, val delta: Int)
    data class StatChangeResult(val hasChanges: Boolean, val changes: List<StatChange>)

    fun checkStatChange(officer: Officer): StatChangeResult {
        val changes = mutableListOf<StatChange>()

        changes.addAll(processStat(officer, "leadership",
            { it.leadership }, { o, v -> o.leadership = v },
            { it.leadershipExp }, { o, v -> o.leadershipExp = v }))

        changes.addAll(processStat(officer, "command",
            { it.command }, { o, v -> o.command = v },
            { it.commandExp }, { o, v -> o.commandExp = v }))

        changes.addAll(processStat(officer, "intelligence",
            { it.intelligence }, { o, v -> o.intelligence = v },
            { it.intelligenceExp }, { o, v -> o.intelligenceExp = v }))

        changes.addAll(processStat(officer, "politics",
            { it.politics }, { o, v -> o.politics = v },
            { it.politicsExp }, { o, v -> o.politicsExp = v }))

        changes.addAll(processStat(officer, "administration",
            { it.administration }, { o, v -> o.administration = v },
            { it.administrationExp }, { o, v -> o.administrationExp = v }))

        return StatChangeResult(changes.isNotEmpty(), changes)
    }

    private fun processStat(
        officer: Officer,
        name: String,
        getStat: (Officer) -> Short,
        setStat: (Officer, Short) -> Unit,
        getExp: (Officer) -> Short,
        setExp: (Officer, Short) -> Unit,
    ): List<StatChange> {
        val stat = getStat(officer)
        val exp = getExp(officer)

        if (exp >= UPGRADE_LIMIT) {
            setExp(officer, 0)
            if (stat < MAX_LEVEL) {
                setStat(officer, (stat + 1).toShort())
                return listOf(StatChange(name, +1))
            }
        } else if (exp < 0) {
            setExp(officer, (exp + UPGRADE_LIMIT).toShort())
            if (stat > 0) {
                setStat(officer, (stat - 1).toShort())
                return listOf(StatChange(name, -1))
            }
        }
        return emptyList()
    }
}
