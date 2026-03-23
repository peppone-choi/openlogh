@file:Suppress("unused")

package com.openlogh.engine.ai

typealias GeneralAI = OfficerAI
typealias NationAI = FactionAI

enum class GeneralType(val flag: Int) {
    WARRIOR(1),
    STRATEGIST(2),
    COMMANDER(4),
}

enum class DiplomacyState {
    PEACE, AT_WAR, RECRUITING, DECLARED
}
