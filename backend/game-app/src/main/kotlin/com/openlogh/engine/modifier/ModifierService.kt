package com.openlogh.engine.modifier

import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import org.springframework.stereotype.Service

@Service
class ModifierService {

    fun getModifiers(general: Officer, nation: Faction? = null): List<ActionModifier> {
        val modifiers = mutableListOf<ActionModifier>()

        // 1. Nation type
        nation?.factionType?.let { NationTypeModifiers.get(it)?.let { m -> modifiers.add(m) } }

        // 2. Personality
        if (general.personalCode != "None") {
            PersonalityModifiers.get(general.personalCode)?.let { modifiers.add(it) }
        }

        // 3. War special (specialCode)
        if (general.specialCode != "None") {
            SpecialModifiers.get(general.specialCode)?.let { modifiers.add(it) }
        }

        // 4. Domestic special (special2Code)
        if (general.special2Code != "None") {
            SpecialModifiers.get(general.special2Code)?.let { modifiers.add(it) }
        }

        // 5. Items
        if (general.flagshipCode != "None") {
            ItemModifiers.get(general.flagshipCode)?.let { modifiers.add(it) }
        }
        if (general.equipCode != "None") {
            ItemModifiers.get(general.equipCode)?.let { modifiers.add(it) }
        }
        if (general.engineCode != "None") {
            ItemModifiers.get(general.engineCode)?.let { modifiers.add(it) }
        }
        if (general.accessoryCode != "None") {
            ItemModifiers.get(general.accessoryCode)?.let { modifiers.add(it) }
        }

        // 6. Officer level (legacy: TriggerOfficerLevel — leadership bonus, score bonus, war power)
        if (general.officerLevel > 0 && nation != null) {
            modifiers.add(OfficerLevelModifier(general.officerLevel.toInt(), nation.factionRank.toInt()))
        }

        return modifiers
    }

    fun applyStatModifiers(modifiers: List<ActionModifier>, baseStat: StatContext): StatContext {
        var stat = baseStat
        for (mod in modifiers) {
            stat = mod.onCalcStat(stat)
        }
        return stat
    }

    fun applyDomesticModifiers(modifiers: List<ActionModifier>, baseCtx: DomesticContext): DomesticContext {
        var ctx = baseCtx
        for (mod in modifiers) {
            ctx = mod.onCalcDomestic(ctx)
        }
        return ctx
    }

    fun applyStrategicModifiers(modifiers: List<ActionModifier>, baseCtx: StrategicContext): StrategicContext {
        var ctx = baseCtx
        for (mod in modifiers) {
            ctx = mod.onCalcStrategic(ctx)
        }
        return ctx
    }

    fun applyIncomeModifiers(modifiers: List<ActionModifier>, baseCtx: IncomeContext): IncomeContext {
        var ctx = baseCtx
        for (mod in modifiers) {
            ctx = mod.onCalcIncome(ctx)
        }
        return ctx
    }

    fun applyOpposeStatModifiers(modifiers: List<ActionModifier>, baseStat: StatContext): StatContext {
        var stat = baseStat
        for (mod in modifiers) {
            stat = mod.onCalcOpposeStat(stat)
        }
        return stat
    }

    fun getTotalWarPowerMultiplier(modifiers: List<ActionModifier>): Double {
        return modifiers.fold(1.0) { acc, mod -> acc * mod.getWarPowerMultiplier() }
    }
}
