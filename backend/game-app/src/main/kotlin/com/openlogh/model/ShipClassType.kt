package com.openlogh.model

/**
 * Ship class types for the LOGH universe (함종).
 * Maps to warehouse columns and fleet composition.
 */
enum class ShipClassType(
    val code: Int,
    val displayName: String,
    val shipsPerUnit: Int,
    val warehouseColumn: String,
) {
    BATTLESHIP(0, "전함", 300, "battleship"),
    CRUISER(1, "순양함", 300, "cruiser"),
    DESTROYER(2, "구축함", 300, "destroyer"),
    CARRIER(3, "항공모함", 300, "carrier"),
    TRANSPORT(4, "수송함", 300, "transport"),
    HOSPITAL(5, "병원선", 300, "hospital"),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Int): ShipClassType? = byCode[code]

        fun fromName(name: String): ShipClassType? = entries.firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.displayName == name
        }
    }
}
