package com.openlogh.engine.tactical

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
    /** Faction type of the defender ("empire"/"alliance"/"fezzan"/"rebel"). */
    val defeatedFactionType: String = "neutral",
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

/**
 * Phase 24-16 (gap A4, gin7 manual p40):
 * フェザーン自治領は中立条約で保護되어 있어, 帝国/同盟이 페잔 영토를 점령하면
 * 국제적 비난과 제재를 받는다. 본 데이터는 순수 엔진에서 산출되며, 실제 DB 적용은
 * TacticalBattleService가 수행한다.
 */
data class NeutralityViolationPenalty(
    /** Aggressor faction that captured a neutral (fezzan) planet. */
    val violatorFactionId: Long,
    /** Per-planet approval decrement applied on every planet owned by the violator. */
    val approvalPenalty: Int,
    /** Flat tech-level penalty applied to the violator faction. */
    val techLevelPenalty: Float,
    /** Multiplier applied to the violator's military_power (e.g., 0.95 = -5%). */
    val militaryPowerMultiplier: Float,
    /** Diplomatic shock: non-aggression pacts the violator holds with other factions break. */
    val breakNonAggressionPacts: Boolean,
    /** Human-readable log line describing the violation. */
    val logLine: String,
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
    /** Phase 24-16: non-null when the captor violated Fezzan neutrality. */
    val neutralityViolation: NeutralityViolationPenalty? = null,
)

class PlanetCaptureProcessor {

    companion object {
        /** Phase 24-16: approval decrement per violator planet on Fezzan capture. */
        const val FEZZAN_APPROVAL_PENALTY: Int = 10
        /** Phase 24-16: flat tech-level decrement (simulates arms-embargo fallout). */
        const val FEZZAN_TECH_LEVEL_PENALTY: Float = 0.5f
        /** Phase 24-16: military power multiplier (-5% simulating sanction). */
        const val FEZZAN_MILITARY_POWER_MULTIPLIER: Float = 0.95f
    }

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

        // Phase 24-16 (gap A4, gin7 manual p40): 페잔 자치령 점령 페널티.
        //
        // 페잔은 제국·동맹 양쪽 모두와 거래하는 교역 중계국이라, 한 진영이 페잔 영토를
        // 점령하면 페잔 통상망이 붕괴되고 페잔 상인 가문들이 자본을 회수한다. 기술·물자
        // 교역선이 끊기고, 페잔을 통해 유지되던 상대 진영과의 불가침 조약 역시 파기된다.
        // 페잔 자신이 점령의 주체인 경우(드문 사례)는 예외.
        val neutralityViolation: NeutralityViolationPenalty? = if (
            input.defeatedFactionType == "fezzan" && input.captorFactionType != "fezzan"
        ) {
            val line = "${input.capturedPlanetName}: 페잔 통상망 붕괴! " +
                "자본 이탈로 민심 -$FEZZAN_APPROVAL_PENALTY, " +
                "기술 교류 단절 -$FEZZAN_TECH_LEVEL_PENALTY, 군사력 -5%, 불가침 조약 파기"
            logs.add(line)
            NeutralityViolationPenalty(
                violatorFactionId = input.captorFactionId,
                approvalPenalty = FEZZAN_APPROVAL_PENALTY,
                techLevelPenalty = FEZZAN_TECH_LEVEL_PENALTY,
                militaryPowerMultiplier = FEZZAN_MILITARY_POWER_MULTIPLIER,
                breakNonAggressionPacts = true,
                logLine = line,
            )
        } else null

        return CaptureProcessingResult(
            logs = logs,
            removedPositionCards = removedCards,
            sortiedOfficerIds = sortiedOfficers,
            suppliesSeized = suppliesSeized,
            shipsDestroyed = shipsDestroyed,
            empireDirectRule = empireDirectRule,
            garrisonAnnihilated = garrisonAnnihilated,
            neutralityViolation = neutralityViolation,
        )
    }
}
