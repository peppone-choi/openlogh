package com.openlogh.engine.war

import com.openlogh.entity.Planet
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * FieldBattleService — resolves field battles (야전/요격) between two generals.
 *
 * Unlike a siege battle, field combat takes place on a road segment with no city walls.
 * Ambush (isAmbush=true) grants the interceptor an attack bonus, crit bonus, and
 * reduces the target's morale and defence.
 */
@Service
class FieldBattleService {

    fun resolve(
        interceptor: WarUnitOfficer,
        target: WarUnitOfficer,
        city: Planet,
        rng: Random,
        isAmbush: Boolean,
        year: Int,
        startYear: Int,
    ): BattleResult {
        if (isAmbush) {
            interceptor.attackMultiplier *= 1.2
            interceptor.criticalChance += 0.15
        }
        target.morale = (target.morale - 10).coerceAtLeast(0)
        target.defenceMultiplier *= 0.85

        // Field battle uses a dummy city with no walls (pure field, no siege phase)
        val fieldCity = Planet()
        fieldCity.orbitalDefense = 0
        fieldCity.fortress = 0
        fieldCity.level = city.level

        val engine = BattleEngine()
        return engine.resolveBattle(
            attacker = interceptor,
            defenders = listOf(target),
            city = fieldCity,
            rng = rng,
            year = year,
            startYear = startYear,
        )
    }
}
