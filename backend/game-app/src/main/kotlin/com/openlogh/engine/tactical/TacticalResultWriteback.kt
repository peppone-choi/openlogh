package com.openlogh.engine.tactical

import com.openlogh.entity.BattleRecord
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.BattleRecordRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * 전술 전투 결과를 DB 엔티티(Officer, Fleet, Planet)에 반영하고 전투 기록을 저장.
 * 전투 종료 후 TacticalWebSocketController에서 호출.
 */
@Service
class TacticalResultWriteback(
    private val officerRepository: OfficerRepository,
    private val fleetRepository: FleetRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val battleRecordRepository: BattleRecordRepository,
    private val gameEventService: GameEventService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TacticalResultWriteback::class.java)
    }

    /**
     * 전술 전투 결과를 DB에 반영.
     *
     * @param session 완료된 전투 세션
     * @param victory 승리 결과
     * @param attackerFactionId 공격측 진영 ID
     * @param defenderFactionId 방어측 진영 ID
     */
    @Transactional
    fun applyResult(
        session: TacticalBattleSession,
        victory: VictoryResult,
        attackerFactionId: Long,
        defenderFactionId: Long,
    ) {
        log.info("Applying tactical battle result: planet={}, winner={}, type={}",
            session.planetId, victory.winnerFactionId, victory.victoryType)

        // 1. 함대별 피해 반영
        for (fleet in session.attackerFleets + session.defenderFleets) {
            applyFleetDamage(fleet)
        }

        // 2. 행성 점령 판정
        val attackerWon = victory.winnerFactionId == attackerFactionId
        if (attackerWon) {
            applyPlanetOccupation(session.planetId, attackerFactionId)
        }

        // 3. 전투 기록 DB 저장
        saveBattleRecord(session, victory, attackerFactionId, defenderFactionId, attackerWon)

        // 4. 전투 결과 이벤트 브로드캐스트
        gameEventService.broadcastEvent(
            worldId = session.sessionId,
            eventType = "tactical_battle_ended",
            payload = mapOf(
                "planetId" to session.planetId,
                "winnerFactionId" to victory.winnerFactionId,
                "victoryType" to victory.victoryType.name,
                "description" to victory.description,
                "turns" to session.currentTurn,
                "attackerWon" to attackerWon,
            ),
        )
    }

    /**
     * 함대 피해를 Fleet/Officer 엔티티에 반영.
     * - 유닛 HP 비율로 함선 수 감소
     * - 사기 반영
     * - 전멸 시 함대 해산
     */
    private fun applyFleetDamage(tacticalFleet: TacticalFleet) {
        val officer = officerRepository.findById(tacticalFleet.officerId).orElse(null) ?: run {
            log.warn("Officer {} not found for writeback", tacticalFleet.officerId)
            return
        }

        val fleet = if (officer.fleetId > 0) {
            fleetRepository.findById(officer.fleetId).orElse(null)
        } else null

        if (fleet != null) {
            applyFleetShipLosses(fleet, tacticalFleet)
            fleet.morale = tacticalFleet.morale.toShort()
            fleet.formation = tacticalFleet.formation.code
            fleetRepository.save(fleet)
            log.debug("Fleet {} updated: morale={}", fleet.id, fleet.morale)
        } else {
            // Fleet 엔티티 없이 Officer.ships로 직접 관리
            val totalMaxHp = tacticalFleet.units.sumOf { it.maxHp }
            val totalHp = tacticalFleet.units.sumOf { it.hp }
            if (totalMaxHp > 0) {
                val survivalRate = totalHp.toDouble() / totalMaxHp
                officer.ships = (officer.ships * survivalRate).toInt()
            }
        }

        // Officer 사기 반영
        officer.morale = tacticalFleet.morale.toShort()

        // 경험치 부여: 턴당 +1, 격침 유닛당 +2
        val destroyedEnemies = tacticalFleet.units.count { !it.isAlive() }
        officer.experience += destroyedEnemies * 2

        officerRepository.save(officer)
        log.debug("Officer {} ({}) updated: ships={}, morale={}", officer.id, officer.name, officer.ships, officer.morale)
    }

    /**
     * Fleet 엔티티의 함종별 함선 수를 전투 결과에 따라 감소.
     * 각 함종 유닛의 HP 잔존율을 적용.
     */
    private fun applyFleetShipLosses(fleet: Fleet, tacticalFleet: TacticalFleet) {
        for (unit in tacticalFleet.units) {
            if (unit.maxHp <= 0) continue
            val survivalRate = unit.hp.toDouble() / unit.maxHp

            when (unit.shipClass) {
                TacticalShipClass.BATTLESHIP -> fleet.battleships = (fleet.battleships * survivalRate).toInt()
                TacticalShipClass.FAST_BATTLESHIP -> fleet.fastBattleships = (fleet.fastBattleships * survivalRate).toInt()
                TacticalShipClass.CRUISER -> fleet.cruisers = (fleet.cruisers * survivalRate).toInt()
                TacticalShipClass.STRIKE_CRUISER -> fleet.strikeCruisers = (fleet.strikeCruisers * survivalRate).toInt()
                TacticalShipClass.DESTROYER -> fleet.destroyers = (fleet.destroyers * survivalRate).toInt()
                TacticalShipClass.CARRIER -> fleet.carriers = (fleet.carriers * survivalRate).toInt()
                TacticalShipClass.ASSAULT_SHIP -> fleet.assaultShips = (fleet.assaultShips * survivalRate).toInt()
                TacticalShipClass.TRANSPORT -> fleet.transports = (fleet.transports * survivalRate).toInt()
                TacticalShipClass.TORPEDO_CARRIER -> fleet.torpedoCarriers = (fleet.torpedoCarriers * survivalRate).toInt()
                TacticalShipClass.ENGINEERING -> fleet.engineeringShips = (fleet.engineeringShips * survivalRate).toInt()
                TacticalShipClass.HOSPITAL -> fleet.hospitalShips = (fleet.hospitalShips * survivalRate).toInt()
                TacticalShipClass.FORTRESS -> { /* 요새는 Fleet에서 관리하지 않음 */ }
            }
        }
    }

    /**
     * 행성 점령 처리: 공격측 승리 시 행성 소유권 변경.
     */
    private fun applyPlanetOccupation(planetId: Long, attackerFactionId: Long) {
        val planet = planetRepository.findById(planetId).orElse(null) ?: run {
            log.warn("Planet {} not found for occupation", planetId)
            return
        }

        if (planet.factionId == attackerFactionId) return // 이미 아군 행성

        log.info("Planet {} ({}) occupied by faction {}", planet.id, planet.name, attackerFactionId)
        planet.factionId = attackerFactionId
        planet.approval = 0f
        planet.security = (planet.security * 0.7).toInt()
        planetRepository.save(planet)
    }

    /**
     * 전투 기록을 DB에 영구 저장.
     */
    private fun saveBattleRecord(
        session: TacticalBattleSession,
        victory: VictoryResult,
        attackerFactionId: Long,
        defenderFactionId: Long,
        attackerWon: Boolean,
    ) {
        val planet = planetRepository.findById(session.planetId).orElse(null)
        val attackerFaction = factionRepository.findById(attackerFactionId).orElse(null)
        val defenderFaction = factionRepository.findById(defenderFactionId).orElse(null)

        // 장교 요약 정보
        val attackerOfficerSummaries = session.attackerFleets.map { fleet ->
            mapOf<String, Any>(
                "officerId" to fleet.officerId,
                "name" to fleet.officer.name,
                "fleetId" to fleet.fleetId,
                "unitsInitial" to fleet.units.size,
                "unitsAlive" to fleet.aliveUnits().size,
                "hpInitial" to fleet.totalMaxHp(),
                "hpFinal" to fleet.totalHp(),
                "morale" to fleet.morale,
            )
        }
        val defenderOfficerSummaries = session.defenderFleets.map { fleet ->
            mapOf<String, Any>(
                "officerId" to fleet.officerId,
                "name" to fleet.officer.name,
                "fleetId" to fleet.fleetId,
                "unitsInitial" to fleet.units.size,
                "unitsAlive" to fleet.aliveUnits().size,
                "hpInitial" to fleet.totalMaxHp(),
                "hpFinal" to fleet.totalHp(),
                "morale" to fleet.morale,
            )
        }

        // 피해 통계
        val attackerInitialHp = session.attackerFleets.sumOf { it.totalMaxHp() }
        val defenderInitialHp = session.defenderFleets.sumOf { it.totalMaxHp() }
        val attackerLost = attackerInitialHp - session.attackerFleets.sumOf { it.totalHp() }
        val defenderLost = defenderInitialHp - session.defenderFleets.sumOf { it.totalHp() }

        // 이벤트 로그 → JSON (null 값 제거)
        val eventLog = session.battleLog.map { event -> event.toDto().let { dto ->
            val data = mutableMapOf<String, Any>("type" to dto.type, "turn" to dto.turn)
            dto.data.forEach { (k, v) -> if (v != null) data[k] = v }
            data.toMap()
        }}

        // 초기 배치 스냅샷
        val initialState = mapOf<String, Any>(
            "fieldSize" to session.grid.fieldSize,
            "obstacles" to session.grid.obstacles.map { obs ->
                mapOf("cx" to obs.center.x, "cy" to obs.center.y, "cz" to obs.center.z,
                    "radius" to obs.radius, "type" to obs.type.name)
            },
        )

        val record = BattleRecord(
            sessionId = session.sessionId,
            sessionCode = session.sessionCode,
            planetId = session.planetId,
            planetName = planet?.name ?: "Unknown",
            attackerFactionId = attackerFactionId,
            attackerFactionName = attackerFaction?.name ?: "Unknown",
            attackerOfficers = attackerOfficerSummaries,
            defenderFactionId = defenderFactionId,
            defenderFactionName = defenderFaction?.name ?: "Unknown",
            defenderOfficers = defenderOfficerSummaries,
            winnerFactionId = victory.winnerFactionId,
            victoryType = victory.victoryType.name,
            totalTurns = session.currentTurn,
            attackerWon = attackerWon,
            planetCaptured = attackerWon,
            attackerShipsInitial = attackerInitialHp,
            defenderShipsInitial = defenderInitialHp,
            attackerShipsLost = attackerLost,
            defenderShipsLost = defenderLost,
            battleLog = eventLog,
            initialState = initialState,
            endedAt = OffsetDateTime.now(),
        )

        battleRecordRepository.save(record)
        log.info("Battle record saved: id={}, session={}, turns={}", record.id, record.sessionCode, record.totalTurns)
    }
}
