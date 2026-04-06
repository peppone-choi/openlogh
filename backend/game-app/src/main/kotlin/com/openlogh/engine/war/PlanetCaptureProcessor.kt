package com.openlogh.engine.war

/**
 * Planet/Star System Capture Processing (점령 후 처리).
 *
 * When capture completes (all enemy ground units eliminated):
 * 1. Position card loss: defeated faction loses planet/fortress-specific cards
 * 2. Garrison surrender: defeated garrison units are annihilated
 * 3. Emergency sortie: all defeated-faction characters sortie with flagships
 * 4. Supplies seized: all warehouse supplies become captor's
 * 5. Shipyard destruction: ships under construction + stored ships destroyed
 * 6. Facilities transfer: all facilities transfer to captor
 * 7. Empire direct rule: if Empire captures, becomes direct imperial territory
 */
data class CaptureProcessingInput(
    val capturedPlanetId: Long,
    val capturedPlanetName: String,
    val captorFactionId: Long,
    val captorFactionType: String,
    val defeatedFactionId: Long,
    /** Garrison unit count that surrendered */
    val garrisonUnits: Int,
    /** Warehouse supplies on the planet */
    val warehouseSupplies: Int,
    /** Ships under construction at shipyard */
    val shipyardShipsInProgress: Int,
    /** Ships stored at shipyard */
    val shipyardShipsStored: Int,
    /** Whether this is a fortress */
    val isFortress: Boolean,
    /** Officer IDs of defeated faction present on planet */
    val defeatedOfficerIds: List<Long>,
    /** Position cards associated with this planet */
    val planetPositionCards: List<String>,
)

data class CaptureProcessingResult(
    val logs: List<String>,
    /** Position cards removed from defeated faction */
    val removedPositionCards: List<String>,
    /** Officer IDs forced to emergency sortie */
    val sortiedOfficerIds: List<Long>,
    /** Supplies seized by captor */
    val suppliesSeized: Int,
    /** Ships destroyed (construction + stored) */
    val shipsDestroyed: Int,
    /** Whether empire direct rule was applied */
    val empireDirectRule: Boolean,
    /** Garrison units annihilated */
    val garrisonAnnihilated: Int,
)

class PlanetCaptureProcessor {

    /**
     * Process all capture aftermath effects.
     */
    fun processCaptureAftermath(input: CaptureProcessingInput): CaptureProcessingResult {
        val logs = mutableListOf<String>()

        // 1. Position card loss
        val removedCards = input.planetPositionCards.toList()
        if (removedCards.isNotEmpty()) {
            logs.add("${input.capturedPlanetName}: 패배 진영 직무권한카드 ${removedCards.size}종 박탈")
            for (card in removedCards) {
                logs.add("  - 직무권한카드 [$card] 소멸")
            }
        }

        // 2. Garrison surrender
        val garrisonAnnihilated = input.garrisonUnits
        if (garrisonAnnihilated > 0) {
            logs.add("${input.capturedPlanetName}: 수비대 ${garrisonAnnihilated}유닛 항복/소멸")
        }

        // 3. Emergency sortie
        val sortiedOfficers = input.defeatedOfficerIds.toList()
        if (sortiedOfficers.isNotEmpty()) {
            logs.add("${input.capturedPlanetName}: 패배 진영 장교 ${sortiedOfficers.size}명 기함과 함께 긴급 출격")
        }

        // 4. Supplies seized
        val suppliesSeized = input.warehouseSupplies
        if (suppliesSeized > 0) {
            logs.add("${input.capturedPlanetName}: 물자 창고 접수 (${suppliesSeized} 물자 획득)")
        }

        // 5. Shipyard destruction
        val shipsDestroyed = input.shipyardShipsInProgress + input.shipyardShipsStored
        if (shipsDestroyed > 0) {
            logs.add("${input.capturedPlanetName}: 조선소 파괴 (건조 중 ${input.shipyardShipsInProgress}척 + 보관 ${input.shipyardShipsStored}척 파괴)")
        }

        // 6. Facilities transfer (logged but actual transfer handled by caller)
        logs.add("${input.capturedPlanetName}: 모든 시설 점령 진영으로 이관")

        // 7. Empire direct rule
        val empireDirectRule = input.captorFactionType == "empire"
        if (empireDirectRule) {
            logs.add("${input.capturedPlanetName}: 제국 직할령으로 편입")
        }

        return CaptureProcessingResult(
            logs = logs,
            removedPositionCards = removedCards,
            sortiedOfficerIds = sortiedOfficers,
            suppliesSeized = suppliesSeized,
            shipsDestroyed = shipsDestroyed,
            empireDirectRule = empireDirectRule,
            garrisonAnnihilated = garrisonAnnihilated,
        )
    }
}
