package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Character lifecycle operations: deletion, injury, death, and cross-session inheritance.
 *
 * Consolidates lifecycle behaviors that affect an officer's active status:
 * - CHAR-10: Deletion (rank <= colonel, on planet)
 * - CHAR-11: Injury (stat penalty + recovery)
 * - CHAR-12: Death (flagship destruction)
 * - CHAR-09: Cross-session inheritance (famePoints > 0, age <= 60)
 */
@Service
class CharacterLifecycleService(
    private val officerRepository: OfficerRepository,
) {
    companion object {
        /** Colonel (rank 4) is the maximum rank that allows voluntary deletion */
        const val MAX_DELETION_RANK: Short = 4

        /** Officers older than 60 cannot be inherited to a new session */
        const val MAX_INHERITANCE_AGE: Short = 60

        /** Injury value is divided by this to compute stat penalty */
        const val INJURY_STAT_DIVISOR = 10
    }

    /**
     * CHAR-10: Check if officer can be deleted.
     * Conditions: rank <= colonel (4), currently on a planet, not already dead.
     * The specific facility check (residence vs hotel) is deferred to CharacterDeletionService.
     */
    fun canDeleteOfficer(officer: Officer): Boolean {
        if (officer.rank > MAX_DELETION_RANK) return false
        if (officer.locationState != "planet") return false
        if (officer.killTurn != null) return false
        return true
    }

    /**
     * CHAR-10: Execute officer deletion.
     * Sets killTurn to mark as dead, clears userId to release the slot.
     */
    @Transactional
    fun deleteOfficer(officer: Officer, currentTurn: Short): Officer {
        require(canDeleteOfficer(officer)) { "삭제 조건을 충족하지 않습니다." }
        officer.killTurn = currentTurn
        officer.userId = null
        return officerRepository.save(officer)
    }

    /**
     * CHAR-11: Apply injury after battle defeat.
     * Injury value determines stat penalty: each stat reduced by injury / INJURY_STAT_DIVISOR.
     * Stats cannot go below 1. Pre-injury stats stored in meta for recovery.
     */
    @Transactional
    fun applyInjury(officer: Officer, injuryLevel: Int): Officer {
        require(injuryLevel > 0) { "부상 레벨은 1 이상이어야 합니다." }

        // Store pre-injury stats in meta for recovery
        officer.meta["preInjuryLeadership"] = officer.leadership.toInt()
        officer.meta["preInjuryCommand"] = officer.command.toInt()
        officer.meta["preInjuryIntelligence"] = officer.intelligence.toInt()
        officer.meta["preInjuryPolitics"] = officer.politics.toInt()
        officer.meta["preInjuryAdministration"] = officer.administration.toInt()
        officer.meta["preInjuryMobility"] = officer.mobility.toInt()
        officer.meta["preInjuryAttack"] = officer.attack.toInt()
        officer.meta["preInjuryDefense"] = officer.defense.toInt()

        officer.injury = injuryLevel.toShort()
        val penalty = injuryLevel / INJURY_STAT_DIVISOR
        officer.leadership = (officer.leadership - penalty).coerceAtLeast(1).toShort()
        officer.command = (officer.command - penalty).coerceAtLeast(1).toShort()
        officer.intelligence = (officer.intelligence - penalty).coerceAtLeast(1).toShort()
        officer.politics = (officer.politics - penalty).coerceAtLeast(1).toShort()
        officer.administration = (officer.administration - penalty).coerceAtLeast(1).toShort()
        officer.mobility = (officer.mobility - penalty).coerceAtLeast(1).toShort()
        officer.attack = (officer.attack - penalty).coerceAtLeast(1).toShort()
        officer.defense = (officer.defense - penalty).coerceAtLeast(1).toShort()

        return officerRepository.save(officer)
    }

    /**
     * CHAR-11: Recover from injury. Restores pre-injury stats from meta.
     */
    @Transactional
    fun recoverFromInjury(officer: Officer): Officer {
        if (officer.injury.toInt() == 0) return officer

        officer.leadership = (officer.meta["preInjuryLeadership"] as? Number)?.toShort() ?: officer.leadership
        officer.command = (officer.meta["preInjuryCommand"] as? Number)?.toShort() ?: officer.command
        officer.intelligence = (officer.meta["preInjuryIntelligence"] as? Number)?.toShort() ?: officer.intelligence
        officer.politics = (officer.meta["preInjuryPolitics"] as? Number)?.toShort() ?: officer.politics
        officer.administration = (officer.meta["preInjuryAdministration"] as? Number)?.toShort() ?: officer.administration
        officer.mobility = (officer.meta["preInjuryMobility"] as? Number)?.toShort() ?: officer.mobility
        officer.attack = (officer.meta["preInjuryAttack"] as? Number)?.toShort() ?: officer.attack
        officer.defense = (officer.meta["preInjuryDefense"] as? Number)?.toShort() ?: officer.defense

        // Clear injury state
        officer.injury = 0
        officer.meta.keys.removeAll { it.startsWith("preInjury") }

        return officerRepository.save(officer)
    }

    /**
     * CHAR-12: Trigger officer death on flagship destruction.
     * Sets killTurn, moves officer to home planet (or faction capital if no home set).
     */
    @Transactional
    fun triggerDeath(officer: Officer, currentTurn: Short, factionCapitalPlanetId: Long): Officer {
        officer.killTurn = currentTurn
        val returnPlanetId = officer.homePlanetId ?: factionCapitalPlanetId
        officer.planetId = returnPlanetId
        officer.locationState = "planet"
        return officerRepository.save(officer)
    }

    /**
     * CHAR-09: Check if officer can be inherited to a new session.
     * Conditions: has fame/evaluation points, age <= 60, not dead.
     */
    fun canInherit(officer: Officer): Boolean {
        if (officer.killTurn != null) return false
        if (officer.age > MAX_INHERITANCE_AGE) return false
        if (officer.famePoints <= 0) return false
        return true
    }

    /**
     * CHAR-09: Create an inherited officer in a new session.
     * Copies identity and fame; stats carry over at 80% of original.
     */
    @Transactional
    fun inheritOfficer(
        sourceOfficer: Officer,
        targetSessionId: Long,
        userId: Long,
        planetId: Long,
        stationedSystem: Int,
    ): Officer {
        require(canInherit(sourceOfficer)) { "인계 조건을 충족하지 않습니다." }
        val inherited = Officer(
            sessionId = targetSessionId,
            userId = userId,
            factionId = sourceOfficer.factionId,
            name = sourceOfficer.name,
            picture = sourceOfficer.picture,
            leadership = (sourceOfficer.leadership * 0.8).toInt().toShort(),
            command = (sourceOfficer.command * 0.8).toInt().toShort(),
            intelligence = (sourceOfficer.intelligence * 0.8).toInt().toShort(),
            politics = (sourceOfficer.politics * 0.8).toInt().toShort(),
            administration = (sourceOfficer.administration * 0.8).toInt().toShort(),
            mobility = (sourceOfficer.mobility * 0.8).toInt().toShort(),
            attack = (sourceOfficer.attack * 0.8).toInt().toShort(),
            defense = (sourceOfficer.defense * 0.8).toInt().toShort(),
            originType = sourceOfficer.originType,
            careerType = sourceOfficer.careerType,
            rank = 0, // start at sub-lieutenant in new session
            planetId = planetId,
            stationedSystem = stationedSystem,
            locationState = "planet",
            homePlanetId = planetId,
            famePoints = sourceOfficer.famePoints,
            npcState = 0, // player-controlled
        )
        return officerRepository.save(inherited)
    }
}
