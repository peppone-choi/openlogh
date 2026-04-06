package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Offline location classification for officers.
 *
 * Determines an officer's offline behavior based on their current location:
 *
 * 1. HOME (자택/호텔/거주구 거실):
 *    - Cannot die in combat
 *    - CAN be arrested
 *    - Subject to personnel commands (인사 커맨드)
 *    - No movement to other locations
 *
 * 2. FLAGSHIP (기함유닛 내):
 *    - If planet/fortress is in anchored state: no death even if tactical game occurs
 *    - If planet/fortress falls during offline tactical: may lose anchored state
 *    - If flagship is in space and tactical occurs: AI controls automatically
 *    - CAN be arrested, subject to personnel commands
 *
 * 3. OTHER (기타 위치):
 *    - Character stays at that location
 */
@Service
class OfflineLocationService(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val fleetRepository: FleetRepository,
) {
    private val logger = LoggerFactory.getLogger(OfflineLocationService::class.java)

    enum class OfflineLocation {
        HOME,       // 자택 - safe from combat death
        FLAGSHIP,   // 기함유닛 내 - depends on anchored state
        OTHER,      // 기타 - stays at location
    }

    data class OfflineStatus(
        val location: OfflineLocation,
        val canDieInCombat: Boolean,
        val canBeArrested: Boolean,
        val subjectToPersonnel: Boolean,
        val canMove: Boolean,
        val aiControlled: Boolean,
        val anchored: Boolean,
        val description: String,
    )

    /**
     * Determine the offline status for an officer.
     */
    fun getOfflineStatus(officer: Officer): OfflineStatus {
        // Check if officer is at home (no fleet, at a friendly planet)
        if (isAtHome(officer)) {
            return OfflineStatus(
                location = OfflineLocation.HOME,
                canDieInCombat = false,
                canBeArrested = true,
                subjectToPersonnel = true,
                canMove = false,
                aiControlled = false,
                anchored = true,
                description = "자택 대기 중 - 전투 사망 없음, 체포/인사 커맨드 대상",
            )
        }

        // Check if officer is in a flagship unit
        if (isInFlagship(officer)) {
            val anchoredAtPlanet = isAnchoredAtPlanet(officer)
            return if (anchoredAtPlanet) {
                OfflineStatus(
                    location = OfflineLocation.FLAGSHIP,
                    canDieInCombat = false,
                    canBeArrested = true,
                    subjectToPersonnel = true,
                    canMove = false,
                    aiControlled = false,
                    anchored = true,
                    description = "기함유닛 내 (행성 정박) - 전투 사망 없음, 행성 함락 시 정박 해제 가능",
                )
            } else {
                OfflineStatus(
                    location = OfflineLocation.FLAGSHIP,
                    canDieInCombat = true,
                    canBeArrested = true,
                    subjectToPersonnel = true,
                    canMove = false,
                    aiControlled = true,
                    anchored = false,
                    description = "기함유닛 내 (우주) - 전술전 발생 시 AI 자동 조종, 사망 가능",
                )
            }
        }

        // Other location
        return OfflineStatus(
            location = OfflineLocation.OTHER,
            canDieInCombat = false,
            canBeArrested = true,
            subjectToPersonnel = true,
            canMove = false,
            aiControlled = false,
            anchored = false,
            description = "기타 위치 대기 - 현재 위치에 머무름",
        )
    }

    /**
     * Process offline officers for a tactical battle event.
     * Returns list of officers that should be AI-controlled.
     */
    fun getAIControlledOfficers(sessionId: Long, planetId: Long): List<Officer> {
        val officers = officerRepository.findBySessionIdAndPlanetId(sessionId, planetId)
        return officers.filter { officer ->
            val status = getOfflineStatus(officer)
            status.aiControlled && isOffline(officer)
        }
    }

    /**
     * Handle planet fall for offline officers at that planet.
     * Officers in anchored flagship state may lose anchored status.
     */
    fun handlePlanetFall(sessionId: Long, planetId: Long, newFactionId: Long) {
        val officers = officerRepository.findBySessionIdAndPlanetId(sessionId, planetId)
        for (officer in officers) {
            if (officer.factionId != newFactionId && isOffline(officer)) {
                // Officer's planet fell to enemy - they need to be handled
                val status = getOfflineStatus(officer)
                if (status.location == OfflineLocation.HOME || status.location == OfflineLocation.FLAGSHIP) {
                    // Move to no-faction state or captured state depending on game rules
                    logger.info(
                        "Offline officer {} ({}) at fallen planet {} - location: {}",
                        officer.id, officer.name, planetId, status.location
                    )
                }
            }
        }
    }

    /**
     * Check if officer is at home (자택).
     * At home = no fleet assignment, stationed at a planet owned by their faction.
     */
    private fun isAtHome(officer: Officer): Boolean {
        if (officer.fleetId != 0L) return false
        if (officer.factionId == 0L) return true // Unaffiliated officers are "at home"

        val planet = planetRepository.findById(officer.planetId).orElse(null) ?: return false
        return planet.factionId == officer.factionId
    }

    /**
     * Check if officer is in a flagship unit.
     */
    private fun isInFlagship(officer: Officer): Boolean {
        return officer.fleetId != 0L
    }

    /**
     * Check if officer's fleet is anchored at a planet (not in space).
     */
    private fun isAnchoredAtPlanet(officer: Officer): Boolean {
        if (officer.fleetId == 0L) return false

        val fleet = fleetRepository.findById(officer.fleetId).orElse(null) ?: return false
        // A fleet is anchored if it has a valid planetId (not traveling in space)
        val isMoving = fleet.meta["moving"] as? Boolean ?: false
        return !isMoving && officer.planetId != 0L
    }

    /**
     * Check if an officer is currently offline (no recent activity).
     */
    private fun isOffline(officer: Officer): Boolean {
        // Player characters with userId who haven't accessed recently
        if (officer.userId == null) return true // NPC
        if (officer.npcState.toInt() != 0) return true // NPC-controlled

        val lastAccess = officer.lastAccessAt ?: officer.turnTime
        val threshold = java.time.OffsetDateTime.now().minusMinutes(5)
        return lastAccess.isBefore(threshold)
    }
}
