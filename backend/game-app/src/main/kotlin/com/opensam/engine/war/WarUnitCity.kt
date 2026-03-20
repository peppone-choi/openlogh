package com.opensam.engine.war

import com.opensam.entity.City
import com.opensam.model.ArmType

class WarUnitCity(
    val city: City,
    year: Int = 200,
    startYear: Int = 180,
) : WarUnit(city.name, city.nationId) {
    var wall: Int = city.wall

    /**
     * Legacy PHP WarUnitCity::__construct():
     *   cityTrainAtmos = Util::clamp(year - startYear + 59, 60, 110)
     * Used as both train and atmos for the city unit.
     */
    val cityTrainAtmos: Int = (year - startYear + 59).coerceIn(60, 110)

    init {
        hp = city.def * 10
        maxHp = hp
        crew = city.pop
        // PHP: getComputedTrain() = cityTrainAtmos + trainBonus
        //      getComputedAtmos() = cityTrainAtmos + atmosBonus
        train = cityTrainAtmos
        atmos = cityTrainAtmos
        crewType = -1  // CREWTYPE_CASTLE
        leadership = 50
        strength = 30
        intel = 30
        experience = 0
        dedication = 0
        tech = 0f

        // PHP: level==1 or level==3 → trainBonus += 5
        if (city.level == 1.toShort() || city.level == 3.toShort()) {
            train += 5
        }
    }

    /**
     * Legacy PHP WarUnitCity::getDex():
     *   return ($this->cityTrainAtmos - 60) * 7200;
     * Ignores the crewType argument — always uses cityTrainAtmos.
     */
    override fun getDexForArmType(armType: ArmType): Int {
        return (cityTrainAtmos - 60) * 7200
    }

    override fun getBaseAttack(): Double {
        val base = (city.def + wall * 9) / 500.0 + 200.0
        return base * attackMultiplier
    }

    override fun getBaseDefence(): Double {
        val base = (city.def + wall * 9) / 500.0 + 200.0
        return base * defenceMultiplier
    }

    override fun takeDamage(damage: Int) {
        super.takeDamage(damage)
        wall = (wall - damage / 20).coerceAtLeast(0)
    }

    fun applyResults() {
        city.def = (hp / 10).coerceAtLeast(0)
        city.wall = wall.coerceAtLeast(0)
    }
}
