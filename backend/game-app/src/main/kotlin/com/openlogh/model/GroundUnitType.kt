package com.openlogh.model

/**
 * Ground combat unit types (지상전 병종).
 *
 * Based on gin7 manual:
 * - Normal planet: all three types available
 * - Gas planet: GRENADIER and LIGHT_INFANTRY only
 * - Fortress: GRENADIER and LIGHT_INFANTRY only
 */
enum class GroundUnitType(
    val displayNameKo: String,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val allowedOnGas: Boolean,
    val allowedOnFortress: Boolean,
) {
    /** 장갑병 - Armored infantry. Heavy ground unit, normal planets only. */
    ARMORED_INFANTRY(
        displayNameKo = "장갑병",
        attack = 200,
        defense = 250,
        speed = 3,
        allowedOnGas = false,
        allowedOnFortress = false,
    ),

    /** 장갑유탄병 - Armored grenadier. Medium ground unit, all terrain. */
    GRENADIER(
        displayNameKo = "장갑유탄병",
        attack = 180,
        defense = 180,
        speed = 5,
        allowedOnGas = true,
        allowedOnFortress = true,
    ),

    /** 경장육전병 - Light infantry. Fast ground unit, all terrain. */
    LIGHT_INFANTRY(
        displayNameKo = "경장육전병",
        attack = 120,
        defense = 100,
        speed = 8,
        allowedOnGas = true,
        allowedOnFortress = true,
    );

    companion object {
        /** Returns ground unit types allowed for a given planet type. */
        fun allowedFor(isGasPlanet: Boolean, isFortress: Boolean): List<GroundUnitType> {
            return entries.filter { type ->
                when {
                    isFortress -> type.allowedOnFortress
                    isGasPlanet -> type.allowedOnGas
                    else -> true // normal planet allows all
                }
            }
        }
    }
}
