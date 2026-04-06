package com.openlogh.engine.tactical

import com.openlogh.engine.war.CaptureProcessingInput
import com.openlogh.engine.war.CaptureProcessingResult
import com.openlogh.engine.war.PlanetCaptureProcessor
import kotlin.random.Random

/**
 * 6종 행성 점령 커맨드 (gin7 스펙).
 *
 * | 커맨드     | 미사일  | 공작포인트        | 지지율  | 경제력  | 방위력  | 공적  |
 * |-----------|---------|------------------|---------|---------|---------|------|
 * | 항복권고   | -       | -                | -       | -       | -       | 높음 |
 * | 정밀폭격   | 200     | -                | -       | -       | ↓(0.6) | 낮음 |
 * | 무차별폭격 | 500     | -                | -30     | ↓(0.5) | ↓(0.3) | 최저 |
 * | 육전대강하 | -       | -                | -       | -       | ↓(0.8) | 보통 |
 * | 점거       | -       | 군사 4000        | -       | -       | -       | 최고 |
 * | 선동       | -       | 정보 1000        | -50     | -       | -       | 보통 |
 */
enum class ConquestCommand(
    val displayNameKo: String,
    val missileCost: Int,
    /** "MILITARY", "INTEL", or null */
    val workPointType: String?,
    val workPointCost: Int,
    /** 지지율 변화 */
    val approvalEffect: Int,
    /** 경제력 배율 (1.0 = 변화 없음) */
    val economyEffect: Double,
    /** 방위력 배율 */
    val defenseEffect: Double,
    /** 공적 포인트 */
    val meritPoints: Int,
) {
    SURRENDER_DEMAND(
        "항복권고",
        missileCost = 0, workPointType = null, workPointCost = 0,
        approvalEffect = 0, economyEffect = 1.0, defenseEffect = 1.0, meritPoints = 500,
    ),
    PRECISION_BOMBING(
        "정밀폭격",
        missileCost = 200, workPointType = null, workPointCost = 0,
        approvalEffect = 0, economyEffect = 1.0, defenseEffect = 0.6, meritPoints = 200,
    ),
    CARPET_BOMBING(
        "무차별폭격",
        missileCost = 500, workPointType = null, workPointCost = 0,
        approvalEffect = -30, economyEffect = 0.5, defenseEffect = 0.3, meritPoints = 50,
    ),
    GROUND_ASSAULT(
        "육전대강하",
        missileCost = 0, workPointType = null, workPointCost = 0,
        approvalEffect = 0, economyEffect = 1.0, defenseEffect = 0.8, meritPoints = 300,
    ),
    INFILTRATION(
        "점거",
        missileCost = 0, workPointType = "MILITARY", workPointCost = 4000,
        approvalEffect = 0, economyEffect = 1.0, defenseEffect = 1.0, meritPoints = 800,
    ),
    SUBVERSION(
        "선동",
        missileCost = 0, workPointType = "INTEL", workPointCost = 1000,
        approvalEffect = -50, economyEffect = 1.0, defenseEffect = 1.0, meritPoints = 300,
    ),
}

/** 점령 커맨드 실행 요청 */
data class ConquestRequest(
    val command: ConquestCommand,
    val attackerOfficerId: Long,
    val attackerFactionId: Long,
    /** "empire", "alliance", "fezzan", "rebel" */
    val attackerFactionType: String,
    val defenderFactionId: Long,
    val planetId: Long,
    val planetName: String,
    /** 현재 요새 방위력 */
    val planetDefense: Int,
    val garrisonUnits: Int,
    val warehouseSupplies: Int,
    val shipyardShipsInProgress: Int,
    val shipyardShipsStored: Int,
    val isFortress: Boolean,
    val defeatedOfficerIds: List<Long>,
    val planetPositionCards: List<String>,
    /** 공격자 보유 미사일 수 */
    val attackerMissileCount: Int,
    /** 군사공작포인트 (점거 커맨드 조건) */
    val militaryWorkPoint: Int = 0,
    /** 정보공작포인트 (선동 커맨드 조건) */
    val intelWorkPoint: Int = 0,
)

/** 점령 커맨드 실행 결과 */
data class ConquestResult(
    val success: Boolean,
    val command: ConquestCommand,
    val reason: String,
    val logs: List<String>,
    val captureResult: CaptureProcessingResult? = null,
    val missilesConsumed: Int = 0,
    val approvalChange: Int = 0,
    val economyMultiplier: Double = 1.0,
    val defenseMultiplier: Double = 1.0,
)

/**
 * 행성 점령 6종 커맨드 실행 서비스.
 *
 * 점령 성공 시 [PlanetCaptureProcessor.processCaptureAftermath] 위임.
 */
class PlanetConquestService(
    private val captureProcessor: PlanetCaptureProcessor = PlanetCaptureProcessor(),
) {

    fun executeConquest(req: ConquestRequest, rng: Random = Random): ConquestResult {
        return when (req.command) {
            ConquestCommand.SURRENDER_DEMAND  -> executeSurrenderDemand(req, rng)
            ConquestCommand.PRECISION_BOMBING -> executePrecisionBombing(req)
            ConquestCommand.CARPET_BOMBING    -> executeCarpetBombing(req)
            ConquestCommand.GROUND_ASSAULT    -> executeGroundAssault(req)
            ConquestCommand.INFILTRATION      -> executeInfiltration(req)
            ConquestCommand.SUBVERSION        -> executeSubversion(req)
        }
    }

    // ── Private handlers ──

    private fun executeSurrenderDemand(req: ConquestRequest, rng: Random): ConquestResult {
        // gin7: 방위력 5000 이하 → 30%, 초과 → 5%
        val successChance = if (req.planetDefense <= 5000) 0.30 else 0.05
        if (rng.nextDouble() >= successChance) {
            return ConquestResult(
                false, req.command,
                "항복 거부 (방위력 ${req.planetDefense})",
                listOf("${req.planetName}: 항복 거부"),
            )
        }
        val captureResult = doCaptureAftermath(req)
        return ConquestResult(
            true, req.command, "항복 수락",
            captureResult.logs, captureResult,
        )
    }

    private fun executePrecisionBombing(req: ConquestRequest): ConquestResult {
        val cmd = ConquestCommand.PRECISION_BOMBING
        if (req.attackerMissileCount < cmd.missileCost) {
            return ConquestResult(
                false, req.command,
                "미사일 부족 (필요: ${cmd.missileCost}, 보유: ${req.attackerMissileCount})",
                emptyList(),
            )
        }
        val newDefense = (req.planetDefense * cmd.defenseEffect).toInt()
        val logs = mutableListOf("${req.planetName}: 정밀폭격 (방위력 ${req.planetDefense} → $newDefense)")
        val conquered = newDefense <= 1000
        val captureResult = if (conquered) doCaptureAftermath(req) else null
        if (conquered) logs.addAll(captureResult!!.logs)
        return ConquestResult(
            conquered, req.command,
            if (conquered) "폭격 성공" else "방위력 잔존",
            logs, captureResult,
            missilesConsumed = cmd.missileCost,
            defenseMultiplier = cmd.defenseEffect,
        )
    }

    private fun executeCarpetBombing(req: ConquestRequest): ConquestResult {
        val cmd = ConquestCommand.CARPET_BOMBING
        if (req.attackerMissileCount < cmd.missileCost) {
            return ConquestResult(
                false, req.command,
                "미사일 부족 (필요: ${cmd.missileCost}, 보유: ${req.attackerMissileCount})",
                emptyList(),
            )
        }
        val newDefense = (req.planetDefense * cmd.defenseEffect).toInt()
        val logs = mutableListOf(
            "${req.planetName}: 무차별폭격 (방위력 → $newDefense, 경제력 50%↓, 지지율 ${cmd.approvalEffect})"
        )
        val conquered = newDefense <= 1000
        val captureResult = if (conquered) doCaptureAftermath(req) else null
        if (conquered) logs.addAll(captureResult!!.logs)
        return ConquestResult(
            conquered, req.command,
            if (conquered) "폭격 성공" else "방위력 잔존",
            logs, captureResult,
            missilesConsumed = cmd.missileCost,
            approvalChange = cmd.approvalEffect,
            economyMultiplier = cmd.economyEffect,
            defenseMultiplier = cmd.defenseEffect,
        )
    }

    private fun executeGroundAssault(req: ConquestRequest): ConquestResult {
        // 지상전 개시 신호만 반환 — 실제 전투는 GroundBattleEngine이 틱마다 처리
        return ConquestResult(
            false, req.command,
            "지상전 개시 — 육전대 강하 진행 중",
            listOf("${req.planetName}: 육전대 강하 개시"),
        )
    }

    private fun executeInfiltration(req: ConquestRequest): ConquestResult {
        val cmd = ConquestCommand.INFILTRATION
        if (req.militaryWorkPoint < cmd.workPointCost) {
            return ConquestResult(
                false, req.command,
                "군사공작포인트 부족 (필요: ${cmd.workPointCost}, 보유: ${req.militaryWorkPoint})",
                emptyList(),
            )
        }
        val captureResult = doCaptureAftermath(req)
        return ConquestResult(
            true, req.command, "점거 성공",
            listOf("${req.planetName}: 군사공작으로 점거") + captureResult.logs,
            captureResult,
        )
    }

    private fun executeSubversion(req: ConquestRequest): ConquestResult {
        val cmd = ConquestCommand.SUBVERSION
        if (req.intelWorkPoint < cmd.workPointCost) {
            return ConquestResult(
                false, req.command,
                "정보공작포인트 부족 (필요: ${cmd.workPointCost}, 보유: ${req.intelWorkPoint})",
                emptyList(),
            )
        }
        val captureResult = doCaptureAftermath(req)
        return ConquestResult(
            true, req.command, "선동 성공",
            listOf("${req.planetName}: 선동으로 점령 (지지율 ${cmd.approvalEffect})") + captureResult.logs,
            captureResult,
            approvalChange = cmd.approvalEffect,
        )
    }

    private fun doCaptureAftermath(req: ConquestRequest): CaptureProcessingResult =
        captureProcessor.processCaptureAftermath(
            CaptureProcessingInput(
                capturedPlanetId = req.planetId,
                capturedPlanetName = req.planetName,
                captorFactionId = req.attackerFactionId,
                captorFactionType = req.attackerFactionType,
                defeatedFactionId = req.defenderFactionId,
                garrisonUnits = req.garrisonUnits,
                warehouseSupplies = req.warehouseSupplies,
                shipyardShipsInProgress = req.shipyardShipsInProgress,
                shipyardShipsStored = req.shipyardShipsStored,
                isFortress = req.isFortress,
                defeatedOfficerIds = req.defeatedOfficerIds,
                planetPositionCards = req.planetPositionCards,
            )
        )
}
