package com.openlogh.service

import com.openlogh.dto.InheritanceActionResult
import com.openlogh.entity.AppUser
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.RankDataRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service

@Service
class InheritanceService(
    private val appUserRepository: AppUserRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val rankDataRepository: RankDataRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val gameConstService: GameConstService,
) {
    private data class Ownership(val user: AppUser, val officer: Officer, val world: SessionState)

    private fun findOwnership(sessionId: Long, loginId: String): Ownership? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val officers = officerRepository.findBySessionIdAndUserId(sessionId, user.id)
        val officer = officers.firstOrNull() ?: return null
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null) ?: return null
        return Ownership(user, officer, world)
    }

    private fun deductPoints(user: AppUser, cost: Int): Int {
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        user.meta["inheritPoints"] = currentPoints - cost
        return currentPoints - cost
    }

    fun getInheritanceScore(officer: Officer): Int {
        val statSum = officer.leadership + officer.command + officer.intelligence + officer.politics + officer.administration
        return statSum + officer.experience / 100 + officer.dedication / 100
    }

    fun processInheritance(world: SessionState) {}

    /**
     * Feature 2.9 - Character Inheritance 60-year cap.
     * Officers aged 60+ cannot be inherited. Throws if cap is exceeded.
     * Returns the proportional experience to carry over to the successor.
     */
    fun checkAgeCapForInheritance(officer: Officer): Int {
        if (officer.age >= 60) {
            throw IllegalStateException("60세 이상은 인계 불가 (현재 나이: ${officer.age})")
        }
        // Carry over experience proportional to remaining life: scale by (60 - age) / 40
        val ageRatio = (60 - officer.age).toDouble() / 40.0
        val carryOver = (officer.experience * ageRatio * 0.5).toInt()
        return maxOf(0, carryOver)
    }

    /**
     * Execute inheritance from dying officer to new character, respecting the 60-year cap.
     * Returns the experience points to award to the new officer, or null if inheritance is blocked.
     */
    fun executeInheritanceTransfer(fromOfficer: Officer): InheritanceTransfer? {
        return try {
            val carryOverExp = checkAgeCapForInheritance(fromOfficer)
            val inheritScore = getInheritanceScore(fromOfficer)
            InheritanceTransfer(
                carryOverExperience = carryOverExp,
                inheritanceScore = inheritScore,
            )
        } catch (e: IllegalStateException) {
            null
        }
    }

    data class InheritanceTransfer(
        val carryOverExperience: Int,
        val inheritanceScore: Int,
    )

    fun resetTurn(sessionId: Long, loginId: String): InheritanceActionResult? {
        val (user, officer, _) = findOwnership(sessionId, loginId) ?: return null

        val cost = gameConstService.getInt("inheritBornCityPoint")
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        if (currentPoints < cost) {
            return InheritanceActionResult(error = "계승 포인트가 부족합니다.")
        }

        val remaining = deductPoints(user, cost)
        officer.meta["inheritResetTurnTime"] = 0
        officer.meta["nextTurnTimeBase"] = officer.turnTime.toString()

        appUserRepository.save(user)
        officerRepository.save(officer)

        return InheritanceActionResult(remainingPoints = remaining)
    }

    fun setInheritSpecial(sessionId: Long, loginId: String, specialCode: String): InheritanceActionResult? {
        val (user, officer, _) = findOwnership(sessionId, loginId) ?: return null

        val cost = gameConstService.getInt("inheritSpecificSpecialPoint")
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        if (currentPoints < cost) {
            return InheritanceActionResult(error = "계승 포인트가 부족합니다.")
        }

        val remaining = deductPoints(user, cost)
        officer.meta["inheritSpecificSpecialWar"] = specialCode

        appUserRepository.save(user)
        officerRepository.save(officer)

        return InheritanceActionResult(remainingPoints = remaining)
    }

    fun buyRandomUnique(sessionId: Long, loginId: String): InheritanceActionResult? {
        val (user, officer, _) = findOwnership(sessionId, loginId) ?: return null

        if (officer.meta.containsKey("inheritRandomUnique")) {
            return InheritanceActionResult(error = "이미 구입 명령이 예약되어 있습니다.")
        }

        val cost = gameConstService.getInt("inheritItemRandomPoint")
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        if (currentPoints < cost) {
            return InheritanceActionResult(error = "계승 포인트가 부족합니다.")
        }

        val remaining = deductPoints(user, cost)
        officer.meta["inheritRandomUnique"] = true

        appUserRepository.save(user)
        officerRepository.save(officer)

        return InheritanceActionResult(remainingPoints = remaining)
    }

    fun resetSpecialWar(sessionId: Long, loginId: String): InheritanceActionResult? {
        val (user, officer, _) = findOwnership(sessionId, loginId) ?: return null

        val cost = gameConstService.getInt("inheritBornStatPoint")
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        if (currentPoints < cost) {
            return InheritanceActionResult(error = "계승 포인트가 부족합니다.")
        }

        val currentSpecial = officer.special2Code
        if (currentSpecial != "None") {
            @Suppress("UNCHECKED_CAST")
            val prevList = (officer.meta["prev_special2"] as? MutableList<String>) ?: mutableListOf()
            prevList.add(currentSpecial)
            officer.meta["prev_special2"] = prevList
        }

        officer.special2Code = "None"
        officer.meta["inheritResetSpecialWar"] = 0
        val remaining = deductPoints(user, cost)

        appUserRepository.save(user)
        officerRepository.save(officer)

        return InheritanceActionResult(remainingPoints = remaining)
    }
}
