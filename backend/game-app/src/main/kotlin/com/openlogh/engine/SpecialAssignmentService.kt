package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class SpecialAssignmentService {

    companion object {
        const val STAT_LEADERSHIP = 0x2
        const val STAT_STRENGTH = 0x4
        const val STAT_INTEL = 0x8
        const val STAT_NOT_LEADERSHIP = 0x20000
        const val STAT_NOT_STRENGTH = 0x40000
        const val STAT_NOT_INTEL = 0x80000

        private const val HIGH_THRESHOLD = 70
        private const val LOW_THRESHOLD = 40

        private val DOMESTIC_SPECIALS = listOf(
            "농업", "상업", "치안", "기술", "외교", "보급", "정보", "인사",
        )
        private val WAR_SPECIALS = listOf(
            "필살", "돌격", "매복", "화공", "수비", "기습", "연사", "저격",
        )
    }

    fun checkAndAssignSpecials(world: SessionState, officers: List<Officer>) {
        val startYear = (world.config["startYear"] as? Number)?.toInt() ?: 184
        val relYear = world.currentYear - startYear
        if (relYear < 3) return

        for (officer in officers) {
            assignDomesticSpecial(officer, relYear)
            assignWarSpecial(officer, relYear)
        }
    }

    private fun assignDomesticSpecial(officer: Officer, relYear: Int) {
        if (officer.specialCode != "None") return
        if (officer.age < officer.specAge) return

        val statCondition = calcStatCondition(officer)
        val candidates = DOMESTIC_SPECIALS.filter { matchesDomesticSpec(it, statCondition) }
        officer.specialCode = if (candidates.isEmpty()) DOMESTIC_SPECIALS.random() else candidates.random()
        officer.specAge = calcDomesticSpecAge(officer.age.toInt(), relYear)
    }

    private fun assignWarSpecial(officer: Officer, relYear: Int) {
        if (officer.special2Code != "None") return
        if (officer.age < officer.spec2Age) return

        val inherited = officer.meta["inheritSpecificSpecialWar"] as? String
        if (inherited != null) {
            officer.special2Code = inherited
            officer.meta.remove("inheritSpecificSpecialWar")
        } else {
            val statCondition = calcStatCondition(officer)
            val candidates = WAR_SPECIALS.filter { matchesWarSpec(it, statCondition) }
            officer.special2Code = if (candidates.isEmpty()) WAR_SPECIALS.random() else candidates.random()
        }
        officer.spec2Age = calcWarSpecAge(officer.age.toInt(), relYear)
    }

    private fun matchesDomesticSpec(spec: String, condition: Int): Boolean {
        return when (spec) {
            "농업", "상업" -> (condition and STAT_INTEL) != 0
            "치안" -> (condition and STAT_LEADERSHIP) != 0
            "기술" -> (condition and STAT_INTEL) != 0
            else -> true
        }
    }

    private fun matchesWarSpec(spec: String, condition: Int): Boolean {
        return when (spec) {
            "필살", "돌격" -> (condition and STAT_STRENGTH) != 0
            "매복", "화공" -> (condition and STAT_INTEL) != 0
            "수비" -> (condition and STAT_LEADERSHIP) != 0
            else -> true
        }
    }

    internal fun calcStatCondition(officer: Officer): Int {
        var result = 0
        val leadership = officer.leadership.toInt()
        val strength = officer.command.toInt()
        val intel = officer.intelligence.toInt()

        if (leadership >= HIGH_THRESHOLD) result = result or STAT_LEADERSHIP
        if (strength >= HIGH_THRESHOLD) result = result or STAT_STRENGTH
        if (intel >= HIGH_THRESHOLD) result = result or STAT_INTEL

        val hasHighStat = (result and (STAT_LEADERSHIP or STAT_STRENGTH or STAT_INTEL)) != 0
        if (hasHighStat) {
            if (leadership < LOW_THRESHOLD) result = result or STAT_NOT_LEADERSHIP
            if (strength < LOW_THRESHOLD) result = result or STAT_NOT_STRENGTH
            if (intel < LOW_THRESHOLD) result = result or STAT_NOT_INTEL
        }
        return result
    }

    internal fun calcDomesticSpecAge(age: Int, relYear: Int): Short {
        val wait = maxOf(((80 - age) / 12.0 - relYear / 2.0).roundToInt(), 3)
        return (wait + age).toShort()
    }

    internal fun calcWarSpecAge(age: Int, relYear: Int): Short {
        val wait = maxOf(((80 - age) / 6.0 - relYear / 2.0).roundToInt(), 3)
        return (wait + age).toShort()
    }
}
