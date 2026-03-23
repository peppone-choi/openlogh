package com.openlogh.engine.tactical

enum class TacticalShipClass(
    val code: String,
    val displayName: String,
    val shipsPerUnit: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    /** Base movement distance per turn */
    val baseSpeed: Double,
    /** BEAM attack range (distance units) */
    val beamRange: Double,
    /** GUN attack range (distance units) */
    val gunRange: Double,
) {
    BATTLESHIP("battleship", "전함", 300, 120, 100, 50.0, 300.0, 150.0),
    FAST_BATTLESHIP("fast_battleship", "고속전함", 300, 110, 80, 80.0, 280.0, 140.0),
    CRUISER("cruiser", "순양함", 300, 90, 80, 70.0, 350.0, 180.0),
    STRIKE_CRUISER("strike_cruiser", "타격순양함", 300, 100, 60, 65.0, 200.0, 100.0),
    DESTROYER("destroyer", "구축함", 300, 60, 50, 100.0, 200.0, 120.0),
    CARRIER("carrier", "항공모함", 300, 110, 70, 50.0, 400.0, 100.0),
    TORPEDO_CARRIER("torpedo_carrier", "뇌격정모함", 300, 100, 60, 55.0, 350.0, 100.0),
    ASSAULT_SHIP("assault_ship", "강습양륙함", 300, 50, 60, 55.0, 120.0, 80.0),
    TRANSPORT("transport", "수송함", 300, 20, 40, 60.0, 100.0, 50.0),
    ENGINEERING("engineering_ship", "공작함", 300, 15, 70, 55.0, 50.0, 30.0),
    HOSPITAL("hospital", "병원선", 300, 10, 60, 60.0, 50.0, 30.0),
    FORTRESS("fortress", "요새", 1, 300, 400, 0.0, 500.0, 300.0),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): TacticalShipClass? = byCode[code]
    }
}
