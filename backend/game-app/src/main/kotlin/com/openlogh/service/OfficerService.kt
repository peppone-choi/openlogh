package com.openlogh.service

import com.openlogh.dto.BuildPoolOfficerRequest
import com.openlogh.dto.CreateOfficerRequest
import com.openlogh.entity.AppUser
import com.openlogh.entity.Officer
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class OfficerService(
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val gameConstService: GameConstService,
) {
    fun createOfficer(
        sessionId: Long,
        loginId: String,
        request: CreateOfficerRequest,
    ): Officer? {
        val user = findUser(loginId)
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null)
            ?: throw IllegalArgumentException("서버를 찾을 수 없습니다.")

        val existing = officerRepository.findBySessionIdAndUserId(sessionId, user.id)
        if (existing.isNotEmpty()) return null

        val maxOfficer = (world.config["maxGeneral"] as? Number)?.toInt()
            ?: gameConstService.getInt("defaultMaxGeneral")
        val allOfficers = officerRepository.findBySessionId(sessionId)
        if (allOfficers.size >= maxOfficer) return null

        val name = request.name ?: return null
        if (officerRepository.findByNameAndWorldId(name, sessionId) != null) return null

        // Handle pre-purchased inherit city
        val inheritCity = user.meta["inheritCity"] as? Number
        val targetPlanetId = inheritCity?.toLong() ?: request.planetId ?: return null
        val planet = planetRepository.findById(targetPlanetId).orElse(null) ?: return null

        // Handle pre-purchased inherit special
        val inheritSpecificSpecialWar = user.meta["inheritSpecificSpecialWar"] as? String
        val special2Code = inheritSpecificSpecialWar ?: request.inheritSpecial ?: "None"

        // Handle inherit bonus stats
        val bonusStat = request.inheritBonusStat ?: emptyList()
        val leadershipBonus = bonusStat.getOrElse(0) { 0 }
        val commandBonus = bonusStat.getOrElse(1) { 0 }
        val intelligenceBonus = bonusStat.getOrElse(2) { 0 }

        // Calculate inherit cost (only charge for newly requested, not pre-purchased)
        var cost = 0
        if (inheritSpecificSpecialWar == null && request.inheritSpecial != null) {
            cost += gameConstService.getInt("inheritBornSpecialPoint")
        }
        if (bonusStat.isNotEmpty() && bonusStat.any { it > 0 }) {
            cost += gameConstService.getInt("inheritBornStatPoint")
        }

        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        user.meta["inheritPoints"] = currentPoints - cost

        // Clean up consumed pre-purchased inherit items
        if (inheritSpecificSpecialWar != null) {
            user.meta.remove("inheritSpecificSpecialWar")
        }
        if (inheritCity != null) {
            user.meta.remove("inheritCity")
        }
        appUserRepository.save(user)

        // Picture & imageServer
        val picture = if (request.pic == true && user.grade >= 2) {
            (user.meta["picture"] as? String) ?: ""
        } else ""
        val imageServer = if (request.pic == true && user.grade >= 2) {
            (user.meta["imageServer"] as? Number)?.toShort() ?: 0
        } else 0

        val personalCode = request.personality ?: "None"
        val killTurn = computeKillTurn(world)
        val turnTime = computeTurnTime(world)

        val startYear = (world.config["startYear"] as? Number)?.toInt()
            ?: gameConstService.getInt("defaultStartYear")
        val age: Short = 20
        val birthYear = (startYear - age).toShort()

        val officer = Officer(
            sessionId = sessionId,
            userId = user.id,
            name = name,
            factionId = planet.factionId,
            planetId = targetPlanetId,
            leadership = (request.leadership + leadershipBonus).toShort(),
            command = (request.command + commandBonus).toShort(),
            intelligence = (request.intelligence + intelligenceBonus).toShort(),
            politics = request.politics,
            administration = request.administration,
            shipClass = request.shipClass,
            personalCode = personalCode,
            special2Code = special2Code,
            picture = picture,
            imageServer = imageServer,
            killTurn = killTurn,
            turnTime = turnTime,
            birthYear = birthYear,
            funds = gameConstService.getInt("defaultGold"),
            supplies = gameConstService.getInt("defaultRice"),
        )

        val saved = officerRepository.save(officer)

        val maxTurn = gameConstService.getInt("maxTurn")
        val turns = (0 until maxTurn).map { idx ->
            OfficerTurn(
                sessionId = sessionId,
                officerId = saved.id,
                turnIdx = idx.toShort(),
                actionCode = "휴식",
            )
        }
        officerTurnRepository.saveAll(turns)

        return saved
    }

    fun buildPoolOfficer(
        sessionId: Long,
        loginId: String,
        request: BuildPoolOfficerRequest,
    ): Officer? {
        val user = findUser(loginId)
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null) ?: return null

        val existing = officerRepository.findBySessionIdAndUserId(sessionId, user.id)
        if (existing.isNotEmpty()) return null

        val picture = if (user.grade >= 2) {
            (user.meta["picture"] as? String) ?: ""
        } else ""
        val imageServer = if (user.grade >= 2) {
            (user.meta["imageServer"] as? Number)?.toShort() ?: 0
        } else 0

        val personalCode = request.personality ?: "None"

        val officer = Officer(
            sessionId = sessionId,
            userId = user.id,
            name = request.name,
            npcState = 5,
            leadership = request.leadership,
            command = request.command,
            intelligence = request.intelligence,
            politics = request.politics,
            administration = request.administration,
            personalCode = personalCode,
            picture = picture,
            imageServer = imageServer,
        )

        return officerRepository.save(officer)
    }

    private fun findUser(loginId: String): AppUser {
        return appUserRepository.findByLoginId(loginId)
            ?: appUserRepository.findByLoginIdIgnoreCase(loginId)
            ?: throw IllegalArgumentException("계정 정보를 찾을 수 없습니다. 다시 로그인해주세요.")
    }

    private fun computeKillTurn(world: SessionState): Short {
        val configKillTurn = (world.config["killturn"] as? Number)?.toShort()
        if (configKillTurn != null) return configKillTurn

        val turnterm = world.tickSeconds / 60
        return if (turnterm > 0) (4800 / turnterm).toShort() else 960
    }

    private fun computeTurnTime(world: SessionState): OffsetDateTime {
        val base = world.updatedAt
        val maxOffset = 2L * world.tickSeconds
        val randomOffset = (Math.random() * maxOffset).toLong()
        return base.plusSeconds(randomOffset)
    }
}
