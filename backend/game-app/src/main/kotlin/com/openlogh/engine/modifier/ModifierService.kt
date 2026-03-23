package com.openlogh.engine.modifier

import org.springframework.stereotype.Service

@Service
class ModifierService {
    fun applyModifiers(sessionId: Long) {}

    fun applyDomesticScoreModifier(officer: com.openlogh.entity.Officer, ctx: Map<String, Any>): Int? = null

    fun applyDomesticCostModifier(officer: com.openlogh.entity.Officer, ctx: Map<String, Any>): Int? = null

    fun onCalcDomesticCost(personalCode: String, cost: com.openlogh.command.CommandCost): com.openlogh.command.CommandCost {
        if (personalCode.contains("che_안전")) {
            return cost.copy(gold = (cost.gold * 0.8).toInt())
        }
        return cost
    }

    fun onCalcDomesticScore(personalCode: String, score: Int): Int {
        return when {
            personalCode.contains("온후") -> (score * 1.1).toInt()
            personalCode.contains("호전") -> (score * 0.95).toInt()
            else -> score
        }
    }

    fun onCalcDomesticSuccess(personalCode: String, successRatio: Double): Double {
        if (personalCode.contains("신중")) {
            return successRatio * 1.1
        }
        return successRatio
    }
}
