package com.openlogh.engine.ai

import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import kotlin.random.Random

/**
 * Port interface for faction AI decision-making.
 * Extracted to allow test-time substitution without mockito-inline.
 */
interface FactionAIPort {
    fun decideNationAction(nation: Faction, world: SessionState, rng: Random): String
}
