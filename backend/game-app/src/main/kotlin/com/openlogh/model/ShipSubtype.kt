package com.openlogh.model

/**
 * Ship subtypes (함종 세부형식) based on gin7 manual.
 *
 * Each ship class has multiple subtypes with different stat profiles.
 * Some subtypes are faction-specific:
 * - 고속전함 (Fast Battleship): Empire only
 * - 타격순항함 (Strike Cruiser): Alliance only
 * - 뇌격정모함 (Torpedo Boat Carrier): Empire only
 */
enum class ShipSubtype(
    val code: Int,
    val shipClass: ShipClass,
    val displayNameKo: String,
    val displayNameEn: String,
    val beamPower: Int,
    val gunPower: Int,
    val shieldPower: Int,
    val enginePower: Int,
    val sensorPower: Int,
    val hullPoints: Int,
    val factionRestriction: String? = null,
) {
    // -- Battleship (전함) subtypes --
    BATTLESHIP_I(2001, ShipClass.BATTLESHIP, "전함I형", "Battleship Type I",
        beamPower = 80, gunPower = 70, shieldPower = 80, enginePower = 50, sensorPower = 40, hullPoints = 100),
    BATTLESHIP_II(2002, ShipClass.BATTLESHIP, "전함II형", "Battleship Type II",
        beamPower = 90, gunPower = 80, shieldPower = 85, enginePower = 55, sensorPower = 45, hullPoints = 110),
    BATTLESHIP_III(2003, ShipClass.BATTLESHIP, "전함III형", "Battleship Type III",
        beamPower = 100, gunPower = 90, shieldPower = 90, enginePower = 60, sensorPower = 50, hullPoints = 120),
    FAST_BATTLESHIP(2004, ShipClass.BATTLESHIP, "고속전함", "Fast Battleship",
        beamPower = 85, gunPower = 75, shieldPower = 70, enginePower = 85, sensorPower = 50, hullPoints = 95,
        factionRestriction = "empire"),

    // -- Cruiser (순양함) subtypes --
    CRUISER_I(2101, ShipClass.CRUISER, "순양함I형", "Cruiser Type I",
        beamPower = 60, gunPower = 60, shieldPower = 60, enginePower = 70, sensorPower = 50, hullPoints = 70),
    CRUISER_II(2102, ShipClass.CRUISER, "순양함II형", "Cruiser Type II",
        beamPower = 70, gunPower = 65, shieldPower = 65, enginePower = 75, sensorPower = 55, hullPoints = 80),
    CRUISER_III(2103, ShipClass.CRUISER, "순양함III형", "Cruiser Type III",
        beamPower = 75, gunPower = 70, shieldPower = 70, enginePower = 80, sensorPower = 60, hullPoints = 85),
    STRIKE_CRUISER(2104, ShipClass.CRUISER, "타격순항함", "Strike Cruiser",
        beamPower = 85, gunPower = 80, shieldPower = 55, enginePower = 75, sensorPower = 55, hullPoints = 75,
        factionRestriction = "alliance"),

    // -- Destroyer (구축함) subtypes --
    DESTROYER_I(2201, ShipClass.DESTROYER, "구축함I형", "Destroyer Type I",
        beamPower = 40, gunPower = 50, shieldPower = 40, enginePower = 90, sensorPower = 60, hullPoints = 50),
    DESTROYER_II(2202, ShipClass.DESTROYER, "구축함II형", "Destroyer Type II",
        beamPower = 50, gunPower = 55, shieldPower = 45, enginePower = 95, sensorPower = 65, hullPoints = 55),
    DESTROYER_III(2203, ShipClass.DESTROYER, "구축함III형", "Destroyer Type III",
        beamPower = 55, gunPower = 60, shieldPower = 50, enginePower = 100, sensorPower = 70, hullPoints = 60),

    // -- Carrier (항공모함) subtypes --
    CARRIER_I(2301, ShipClass.CARRIER, "항공모함I형", "Carrier Type I",
        beamPower = 30, gunPower = 20, shieldPower = 60, enginePower = 60, sensorPower = 80, hullPoints = 80),
    CARRIER_II(2302, ShipClass.CARRIER, "항공모함II형", "Carrier Type II",
        beamPower = 35, gunPower = 25, shieldPower = 65, enginePower = 65, sensorPower = 85, hullPoints = 90),
    TORPEDO_BOAT_CARRIER(2303, ShipClass.CARRIER, "뇌격정모함", "Torpedo Boat Carrier",
        beamPower = 25, gunPower = 40, shieldPower = 50, enginePower = 70, sensorPower = 75, hullPoints = 75,
        factionRestriction = "empire"),

    // -- Transport (수송함) subtypes --
    TRANSPORT_I(2401, ShipClass.TRANSPORT, "수송함I형", "Transport Type I",
        beamPower = 10, gunPower = 10, shieldPower = 50, enginePower = 70, sensorPower = 30, hullPoints = 60),
    TRANSPORT_II(2402, ShipClass.TRANSPORT, "수송함II형", "Transport Type II",
        beamPower = 15, gunPower = 15, shieldPower = 55, enginePower = 75, sensorPower = 35, hullPoints = 70),

    // -- Hospital (병원선) subtypes --
    HOSPITAL_I(2501, ShipClass.HOSPITAL, "병원선I형", "Hospital Ship Type I",
        beamPower = 5, gunPower = 5, shieldPower = 70, enginePower = 65, sensorPower = 40, hullPoints = 65),

    // -- Fortress (요새) --
    FORTRESS(2601, ShipClass.FORTRESS, "이동요새", "Mobile Fortress",
        beamPower = 200, gunPower = 150, shieldPower = 200, enginePower = 10, sensorPower = 100, hullPoints = 500);

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Int): ShipSubtype? = byCode[code]

        fun byShipClass(shipClass: ShipClass): List<ShipSubtype> =
            entries.filter { it.shipClass == shipClass }

        fun availableForFaction(factionType: String): List<ShipSubtype> =
            entries.filter { it.factionRestriction == null || it.factionRestriction == factionType }
    }
}

/**
 * Ship class categories matching CLAUDE.md ship classes.
 */
enum class ShipClass(
    val code: Int,
    val displayNameKo: String,
    val shipsPerUnit: Int,
    val description: String,
) {
    BATTLESHIP(1, "전함", 300, "주력 전투함"),
    CRUISER(2, "순양함", 300, "범용 전투함"),
    DESTROYER(3, "구축함", 300, "고속 전투함"),
    CARRIER(4, "항공모함", 300, "스파르타니안 운용"),
    TRANSPORT(5, "수송함", 300, "물자/병력 수송"),
    HOSPITAL(6, "병원선", 300, "부상자 치료"),
    FORTRESS(7, "요새", 1, "이동 요새 (이제르론/가이에스부르크)");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): ShipClass? = byCode[code]
    }
}
