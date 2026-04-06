package com.openlogh.engine.ai

/**
 * NPC personality traits that influence AI decision-making.
 * Each trait adjusts stat weights and action probabilities to produce
 * observably different behavior patterns.
 *
 * - AGGRESSIVE: Favors attack, offensive operations, early engagement
 * - DEFENSIVE: Favors fortification, training, cautious positioning
 * - BALANCED: No bias, default for most officers
 * - POLITICAL: Favors diplomacy, proposals, administrative actions
 * - CAUTIOUS: Avoids risky actions, prefers intelligence gathering before acting
 */
enum class PersonalityTrait(val korean: String) {
    AGGRESSIVE("공격적"),
    DEFENSIVE("방어적"),
    BALANCED("균형"),
    POLITICAL("정치적"),
    CAUTIOUS("신중"),
    ;

    companion object {
        fun fromString(value: String?): PersonalityTrait {
            if (value == null) return BALANCED
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: BALANCED
        }
    }
}

/**
 * Stat weight multipliers per personality trait.
 * Used by AI to bias decision-making based on officer personality.
 */
data class PersonalityWeights(
    val leadership: Double = 1.0,
    val command: Double = 1.0,
    val intelligence: Double = 1.0,
    val politics: Double = 1.0,
    val administration: Double = 1.0,
    val mobility: Double = 1.0,
    val attack: Double = 1.0,
    val defense: Double = 1.0,
) {
    companion object {
        fun forTrait(trait: PersonalityTrait): PersonalityWeights = when (trait) {
            PersonalityTrait.AGGRESSIVE -> PersonalityWeights(
                attack = 1.5, command = 1.3, mobility = 1.2,
                defense = 0.7, administration = 0.8,
            )
            PersonalityTrait.DEFENSIVE -> PersonalityWeights(
                defense = 1.5, administration = 1.3, leadership = 1.1,
                attack = 0.7, mobility = 0.9,
            )
            PersonalityTrait.BALANCED -> PersonalityWeights()
            PersonalityTrait.POLITICAL -> PersonalityWeights(
                politics = 1.5, intelligence = 1.3, administration = 1.2,
                command = 0.7, attack = 0.8,
            )
            PersonalityTrait.CAUTIOUS -> PersonalityWeights(
                defense = 1.2, intelligence = 1.2,
                attack = 0.8, mobility = 0.8,
            )
        }

        /**
         * Infer personality from officer stats for offline player AI takeover.
         * Uses the dominant stat pattern to pick the closest personality.
         */
        fun inferFromStats(
            leadership: Int, command: Int, intelligence: Int,
            politics: Int, administration: Int, mobility: Int,
            attack: Int, defense: Int,
        ): PersonalityTrait {
            val militaryScore = attack + command + mobility
            val defensiveScore = defense + administration + leadership
            val politicalScore = politics + intelligence + administration

            return when {
                politicalScore > militaryScore && politicalScore > defensiveScore -> PersonalityTrait.POLITICAL
                militaryScore > defensiveScore * 1.3 -> PersonalityTrait.AGGRESSIVE
                defensiveScore > militaryScore * 1.3 -> PersonalityTrait.DEFENSIVE
                intelligence > command && defense > attack -> PersonalityTrait.CAUTIOUS
                else -> PersonalityTrait.BALANCED
            }
        }
    }
}
