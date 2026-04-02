package com.opensam.engine.ai

/**
 * Diplomacy state matching PHP 5-state model.
 * PHP: d평화=0, d선포=1, d징병=2, d직전=3, d전쟁=4
 */
enum class DiplomacyState(val code: Int) {
    PEACE(0),      // d평화
    DECLARED(1),   // d선포
    RECRUITING(2), // d징병
    IMMINENT(3),   // d직전
    AT_WAR(4),     // d전쟁
}

enum class GeneralType(val flag: Int) {
    WARRIOR(1),
    STRATEGIST(2),
    COMMANDER(4),
}
