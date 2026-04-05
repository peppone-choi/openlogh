package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.model.ArmType

class WarUnitPlanet(
    val city: Planet,
    year: Int = 200,
    startYear: Int = 180,
) : WarUnit(city.name, city.factionId) {
    var wall: Int = city.fortress

    /**
     * Legacy PHP WarUnitPlanet::__construct():
     *   cityTrainAtmos = Util::clamp(year - startYear + 59, 60, 110)
     * Used as both train and atmos for the city unit.
     */
    val cityTrainAtmos: Int = (year - startYear + 59).coerceIn(60, 110)

    init {
        hp = city.orbitalDefense * 10
        maxHp = hp
        ships = city.population
        // PHP: getComputedTrain() = cityTrainAtmos + trainBonus
        //      getComputedAtmos() = cityTrainAtmos + atmosBonus
        training = cityTrainAtmos
        morale = cityTrainAtmos
        shipClass = -1  // CREWTYPE_CASTLE
        leadership = 50
        command = 30
        intelligence = 30
        experience = 0
        dedication = 0
        techLevel = 0f

        // PHP: level==1 or level==3 → trainBonus += 5
        if (city.level == 1.toShort() || city.level == 3.toShort()) {
            training += 5
        }
    }

    /**
     * Legacy PHP WarUnitPlanet::getDex():
     *   return ($this->cityTrainAtmos - 60) * 7200;
     * Ignores the crewType argument — always uses cityTrainAtmos.
     */
    override fun getDexForArmType(armType: ArmType): Int {
        return (cityTrainAtmos - 60) * 7200
    }

    override fun getBaseAttack(): Double {
        val base = (city.orbitalDefense + wall * 9) / 500.0 + 200.0
        return base * attackMultiplier
    }

    override fun getBaseDefence(): Double {
        val base = (city.orbitalDefense + wall * 9) / 500.0 + 200.0
        return base * defenceMultiplier
    }

    override fun takeDamage(damage: Int) {
        super.takeDamage(damage)
        wall = (wall - damage / 20).coerceAtLeast(0)
    }

    fun applyResults() {
        city.orbitalDefense = (hp / 10).coerceAtLeast(0)
        city.fortress = wall.coerceAtLeast(0)
    }
}
