package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.entity.OldOfficer
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.GameConstService
import com.openlogh.service.HistoryService
import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

@Service
class OfficerMaintenanceService(
    private val gameConstService: GameConstService,
    private val hallOfFameRepository: HallOfFameRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val factionTurnRepository: FactionTurnRepository,
    private val fleetRepository: FleetRepository,
    private val oldOfficerRepository: OldOfficerRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val officerAccessLogRepository: OfficerAccessLogRepository,
    private val historyService: HistoryService,
) {
    companion object {
        private const val RETIREMENT_AGE = 80
        private const val REBIRTH_AGE: Short = 20
        private const val REBIRTH_STAT_PENALTY = 15
        private const val REBIRTH_MIN_STAT: Short = 10
        private const val EXP_GAIN_PER_TURN = 10
        private const val DEDICATION_DECAY_RATE = 0.99
    }

    fun processGeneralMaintenance(world: SessionState, officers: List<Officer>) =
        processOfficerMaintenance(world, officers)

    fun processOfficerMaintenance(world: SessionState, officers: List<Officer>) {
        val isJanuary = world.currentMonth.toInt() == 1
        val isUnited = (world.config["isUnited"] as? Number)?.toInt() ?: 0

        for (officer in officers) {
            // killTurn processing
            if (officer.killTurn != null) {
                if (officer.killTurn!! <= 0.toShort()) {
                    killOfficer(world, officer, officers)
                    continue
                } else {
                    officer.killTurn = (officer.killTurn!! - 1).toShort()
                }
            }

            // Age increment in January
            if (isJanuary) {
                officer.age = (officer.age + 1).toShort()
            }

            // Experience gain
            val oldExp = officer.experience
            officer.experience += EXP_GAIN_PER_TURN
            updateExpLevel(officer, oldExp)

            // Dedication decay
            val oldDed = officer.dedication
            officer.dedication = maxOf(0, floor(officer.dedication * DEDICATION_DECAY_RATE).toInt())
            updateDedLevel(officer, oldDed)

            // Injury recovery
            if (officer.injury > 0) {
                officer.injury = (officer.injury - 1).toShort()
            }

            // Retirement/rebirth when united and age > RETIREMENT_AGE
            if (isUnited > 0 && officer.age > RETIREMENT_AGE) {
                rebirthOfficer(officer)
            }
        }
    }

    private fun updateExpLevel(officer: Officer, oldExp: Int) {
        val newLevel = sqrt(officer.experience / 10.0).toInt().toShort()
        if (newLevel != officer.expLevel) {
            officer.expLevel = newLevel
        }
    }

    private fun updateDedLevel(officer: Officer, oldDed: Int) {
        val newLevel = ceil(sqrt(officer.dedication.toDouble()) / 10.0).toInt().toShort()
        if (newLevel != officer.dedLevel) {
            officer.dedLevel = newLevel
        }
    }

    private fun rebirthOfficer(officer: Officer) {
        // Save old officer record before rebirth
        val oldOfficer = oldOfficerRepository.findByServerIdAndOfficerNo(officer.sessionId.toString(), officer.id)
        // Rebirth: reset to young officer with reduced stats
        officer.age = REBIRTH_AGE
        officer.npcState = 0
        officer.leadership = maxOf(REBIRTH_MIN_STAT, (officer.leadership - REBIRTH_STAT_PENALTY).toShort())
        officer.command = maxOf(REBIRTH_MIN_STAT, (officer.command - REBIRTH_STAT_PENALTY).toShort())
        officer.intelligence = maxOf(REBIRTH_MIN_STAT, (officer.intelligence - REBIRTH_STAT_PENALTY).toShort())
        officer.injury = 0
        officer.experience = 500
        officer.dedication = 500
        officer.specAge = 0
        officer.spec2Age = 0
        officer.dex1 /= 2
        officer.dex2 /= 2
        officer.dex3 /= 2
        officer.dex4 /= 2
        officer.dex5 /= 2

        @Suppress("UNCHECKED_CAST")
        val rank = officer.meta["rank"] as? MutableMap<String, Any>
        if (rank != null) {
            rank["warnum"] = 0
            rank["killnum"] = 0
        }
        officer.meta.remove("rebirth_available")
    }

    private fun killOfficer(world: SessionState, dead: Officer, allOfficers: List<Officer>) {
        val sessionId = world.id.toLong()
        val factionId = dead.factionId
        val deadRank = dead.rank

        // Archive old officer
        val yearMonth = world.currentYear.toInt() * 100 + world.currentMonth.toInt()
        val existing = oldOfficerRepository.findByServerIdAndOfficerNo(world.name ?: "", dead.id)
        if (existing == null) {
            oldOfficerRepository.save(OldOfficer(
                serverId = world.name ?: "",
                officerNo = dead.id,
                name = dead.name,
                lastYearMonth = yearMonth,
            ))
        }

        // Delete officer turns
        officerTurnRepository.deleteByOfficerId(dead.id)

        // Find successor in same faction (highest rank, excluding dead)
        val sameNationOfficers = allOfficers.filter {
            it.id != dead.id && it.factionId == factionId && it.factionId != 0L
        }
        val successor = sameNationOfficers
            .sortedWith(compareByDescending<Officer> { it.rank }.thenByDescending { it.dedication })
            .firstOrNull()

        // Disband fleet if dead was in one
        val deadFleetId = dead.fleetId
        if (deadFleetId != 0L) {
            val fleet = fleetRepository.findById(deadFleetId)
            if (fleet.isPresent) {
                val fleetEntity = fleet.get()
                // Remove all members from fleet
                for (officer in allOfficers) {
                    if (officer.fleetId == deadFleetId) {
                        officer.fleetId = 0
                    }
                }
                fleetRepository.delete(fleetEntity)
            }
        }

        // Mark dead officer
        dead.npcState = 5
        dead.factionId = 0
        dead.rank = 0
        dead.stationedSystem = 0
        dead.killTurn = null
        dead.fleetId = 0

        if (factionId != 0L) {
            val faction = factionRepository.findById(factionId).orElse(null)
            if (faction != null) {
                faction.officerCount = maxOf(0, faction.officerCount - 1)

                if (successor != null) {
                    // Promote successor
                    successor.rank = deadRank
                    successor.stationedSystem = 0
                    if (faction.supremeCommanderId == dead.id) {
                        faction.supremeCommanderId = successor.id
                    }
                    factionRepository.save(faction)

                    historyService.logNationHistory(
                        sessionId, factionId,
                        "${dead.name} 사망, ${successor.name} 후임",
                        world.currentYear.toInt(), world.currentMonth.toInt(),
                    )
                } else {
                    // No successor - collapse the faction
                    collapseFaction(world, faction, dead)
                }
            }
        }
    }

    private fun collapseFaction(world: SessionState, faction: com.openlogh.entity.Faction, dead: Officer) {
        val sessionId = world.id.toLong()
        val factionId = faction.id

        // Reset faction
        faction.factionRank = 0
        faction.officerCount = 0
        faction.militaryPower = 0
        faction.supremeCommanderId = 0
        faction.capitalPlanetId = null
        factionRepository.save(faction)

        // Release all planets
        val planets = planetRepository.findByFactionId(factionId)
        for (planet in planets) {
            planet.factionId = 0
            planet.frontState = 0
            planetRepository.save(planet)
        }

        // Disband all fleets
        val fleets = fleetRepository.findByFactionId(factionId)
        for (fleet in fleets) {
            fleetRepository.delete(fleet)
        }

        // Kill all diplomacy
        val diplomacies = diplomacyRepository.findBySessionId(sessionId)
        for (d in diplomacies) {
            if (d.srcFactionId == factionId || d.destFactionId == factionId) {
                d.isDead = true
                d.isShowing = false
                diplomacyRepository.save(d)
            }
        }

        // Delete faction turns
        val factionTurns = factionTurnRepository.findBySessionId(sessionId)
        for (ft in factionTurns) {
            if (ft.factionId == factionId) {
                factionTurnRepository.delete(ft)
            }
        }

        historyService.logWorldHistory(
            sessionId,
            "${faction.name} 멸망",
            world.currentYear.toInt(), world.currentMonth.toInt(),
        )
        historyService.logNationHistory(
            sessionId, factionId,
            "${dead.name} 사망으로 멸망",
            world.currentYear.toInt(), world.currentMonth.toInt(),
        )
    }
}
