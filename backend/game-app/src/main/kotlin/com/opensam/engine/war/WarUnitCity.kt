package com.opensam.engine.war

import com.opensam.entity.City

class WarUnitCity(
    val city: City,
) : WarUnit(city.name, city.nationId) {
    var wall: Int = city.wall


    init {
        hp = city.def * 10
        maxHp = hp
        crew = city.pop
        train = 80
        atmos = 100
        crewType = -1  // CREWTYPE_CASTLE
        leadership = 50
        strength = 30
        intel = 30
        experience = 0
        dedication = 0
        tech = 0f
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
