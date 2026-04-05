package com.openlogh.service

import com.openlogh.dto.AuctionUniqueRequest
import com.openlogh.dto.BuyInheritBuffRequest
import com.openlogh.dto.CheckOwnerRequest
import com.openlogh.dto.CheckOwnerResponse
import com.openlogh.dto.CurrentStat
import com.openlogh.dto.InheritanceActionCost
import com.openlogh.dto.InheritanceActionResult
import com.openlogh.dto.InheritanceInfo
import com.openlogh.dto.InheritanceLogEntry
import com.openlogh.dto.ResetStatsRequest
import com.openlogh.dto.SpecialWarOption
import com.openlogh.dto.UniqueItemOption
import com.openlogh.engine.DeterministicRng
import com.openlogh.engine.modifier.TraitSpecRegistry
import com.openlogh.entity.AppUser
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.RankDataRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.math.floor
import kotlin.math.max

@Service
class InheritanceService(
    private val appUserRepository: AppUserRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val rankDataRepository: RankDataRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val gameConstService: GameConstService,
) {
    companion object {
        private val BUFF_LEVEL_COSTS = listOf(0, 200, 600, 1200, 2000, 3000)
        private const val MAX_BUFF_LEVEL = 5
        private const val RESET_ATTR_BASE = 1000

        val COMBAT_BUFF_TYPES = setOf(
            "warAvoidRatio",
            "warCriticalRatio",
            "warMagicTrialProb",
            "warAvoidRatioOppose",
            "warCriticalRatioOppose",
            "warMagicTrialProbOppose",
            "domesticSuccessProb",
            "domesticFailProb",
        )

        val AVAILABLE_UNIQUE = mapOf(
            "적토마" to UniqueItemOption("적토마", "적토마", "이동력 +3, 회피 확률 증가"),
            "의천검" to UniqueItemOption("의천검", "의천검", "무력 +7"),
            "청룡언월도" to UniqueItemOption("청룡언월도", "청룡언월도", "무력 +10"),
            "방천화극" to UniqueItemOption("방천화극", "방천화극", "무력 +12"),
            "태평요술서" to UniqueItemOption("태평요술서", "태평요술서", "지력 +10"),
            "전국옥새" to UniqueItemOption("전국옥새", "전국옥새", "매력 +15"),
            "맹덕신서" to UniqueItemOption("맹덕신서", "맹덕신서", "통솔 +7"),
            "둔갑천서" to UniqueItemOption("둔갑천서", "둔갑천서", "계략 성공률 대폭 증가"),
        )
    }

    private val availableSpecialWar: Map<String, SpecialWarOption> = TraitSpecRegistry.war
        .associate { it.key to SpecialWarOption(it.name, it.info) }

    private fun inheritSpecialCost(): Int = gameConstService.getInt("inheritSpecificSpecialPoint")

    private fun inheritCityCost(): Int = gameConstService.getInt("inheritBornCityPoint")

    private fun randomUniqueCost(): Int = gameConstService.getInt("inheritItemRandomPoint")

    private fun checkOwnerCost(): Int = gameConstService.getInt("inheritCheckOwnerPoint")

    private fun minSpecificUniqueCost(): Int = gameConstService.getInt("inheritItemUniqueMinPoint")

    private fun bornStatPointCost(): Int = gameConstService.getInt("inheritBornStatPoint")

    @Transactional
    fun forceReapplyMergedPoints(worldId: Long): Int {
        val mergeEntries = rankDataRepository.findBySessionIdAndCategory(worldId, "inherit_earned_dyn")
            .associateBy { (it.meta["generalId"] as? Number)?.toLong() ?: 0L }
        val totalEntries = rankDataRepository.findBySessionIdAndCategory(worldId, "inherit_earned")
            .associateBy { (it.meta["generalId"] as? Number)?.toLong() ?: 0L }

        var updatedUsers = 0
        val users = mutableMapOf<Long, AppUser>()

        officerRepository.findBySessionId(worldId)
            .asSequence()
            .filter { it.npcState.toInt() == 0 && it.userId != null }
            .forEach { officer ->
                val userId = officer.userId ?: return@forEach
                val user = users.getOrPut(userId) { appUserRepository.findById(userId).orElse(null) ?: return@forEach }
                val targetScore = totalEntries[officer.id]?.score ?: mergeEntries[officer.id]?.score ?: 0
                val breakdown = getOrCreateMutableStringAnyMap(user.meta, "inheritPointBreakdown")
                val previousScore = (breakdown["forceRehallMerged"] as? Number)?.toInt() ?: 0
                val appliedScore = max(previousScore, targetScore)
                val delta = appliedScore - previousScore

                if (delta > 0) {
                    val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
                    user.meta["inheritPoints"] = currentPoints + delta
                    addInheritLog(user, "관리자 리홀 재정산", delta)
                    updatedUsers++
                }

                breakdown["forceRehallMerged"] = appliedScore
                user.meta["inheritPointBreakdown"] = breakdown
                appUserRepository.save(user)
            }

        return updatedUsers
    }

    fun getInheritance(worldId: Long, loginId: String): InheritanceInfo? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val rawBuffs = readStringAnyMap(user.meta["inheritBuffs"])
        val buffs = rawBuffs.mapValues { (it.value as? Number)?.toInt() ?: 0 }

        val rawInheritBuff = readStringAnyMap(user.meta["inheritCombatBuffs"])
        val inheritBuff = rawInheritBuff.mapValues { (it.value as? Number)?.toInt() ?: 0 }

        val rawLog = readStringAnyMapList(user.meta["inheritLog"])
        var logId = rawLog.size.toLong()
        val log = rawLog.map { entry ->
            logId--
            InheritanceLogEntry(
                id = logId + 1,
                action = entry["action"] as? String ?: "",
                amount = (entry["amount"] as? Number)?.toInt() ?: 0,
                date = entry["date"] as? String ?: "",
                text = entry["text"] as? String,
            )
        }

        val rawBreakdown = readStringAnyMap(user.meta["inheritPointBreakdown"])
        val pointBreakdown = rawBreakdown.mapValues { (it.value as? Number)?.toInt() ?: 0 }

        val officer = officerRepository.findBySessionIdAndUserId(worldId, user.id!!).firstOrNull { it.npcState.toInt() < 5 }
        val turnResetCount = readResetCount(officer?.meta?.get("inheritResetTurnTime"))
        val specialWarResetCount = readResetCount(officer?.meta?.get("inheritResetSpecialWar"))

        val actionCost = InheritanceActionCost(
            buff = BUFF_LEVEL_COSTS,
            resetTurnTime = resetAttrCost(turnResetCount),
            resetSpecialWar = resetAttrCost(specialWarResetCount),
            randomUnique = randomUniqueCost(),
            nextSpecial = inheritSpecialCost(),
            minSpecificUnique = minSpecificUniqueCost(),
            checkOwner = checkOwnerCost(),
            bornStatPoint = bornStatPointCost(),
        )

        val currentStat = officer?.let {
            CurrentStat(
                leadership = it.leadership.toInt(),
                strength = it.command.toInt(),
                intel = it.intelligence.toInt(),
                statMax = 100,
                statMin = 10,
            )
        }

        val allGenerals = officerRepository.findBySessionId(worldId)
        val availableTargetGeneral = allGenerals
            .filter { it.npcState < 2 && it.id != officer?.id }
            .associate { it.id!! to it.name }

        return InheritanceInfo(
            points = points,
            pointBreakdown = pointBreakdown,
            buffs = buffs,
            inheritBuff = inheritBuff,
            maxInheritBuff = MAX_BUFF_LEVEL,
            log = log,
            turnResetCount = turnResetCount,
            specialWarResetCount = specialWarResetCount,
            inheritActionCost = actionCost,
            availableSpecialWar = availableSpecialWar,
            availableUnique = AVAILABLE_UNIQUE,
            availableTargetGeneral = availableTargetGeneral,
            currentStat = currentStat,
        )
    }

    fun buyInheritBuff(worldId: Long, loginId: String, request: BuyInheritBuffRequest): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        if (request.type !in COMBAT_BUFF_TYPES) return InheritanceActionResult(error = "잘못된 전투 버프 타입")
        if (request.level < 1 || request.level > MAX_BUFF_LEVEL) return InheritanceActionResult(error = "잘못된 레벨")

        val combatBuffs = getOrCreateMutableStringAnyMap(user.meta, "inheritCombatBuffs")
        val currentLevel = (combatBuffs[request.type] as? Number)?.toInt() ?: 0
        if (request.level <= currentLevel) return InheritanceActionResult(error = "현재 레벨보다 높아야 합니다")

        val cost = BUFF_LEVEL_COSTS[request.level] - BUFF_LEVEL_COSTS[currentLevel]
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        combatBuffs[request.type] = request.level
        user.meta["inheritCombatBuffs"] = combatBuffs

        addInheritLog(user, "전투버프 구매: ${request.type} Lv.${request.level}", -cost)
        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost, newLevel = request.level)
    }

    fun setInheritSpecial(worldId: Long, loginId: String, specialCode: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val officer = findOwnedOfficer(worldId, user) ?: return InheritanceActionResult(error = "장수를 찾을 수 없습니다")
        val world = findWorld(worldId) ?: return InheritanceActionResult(error = "월드를 찾을 수 없습니다")
        if (isWorldUnited(world)) return InheritanceActionResult(error = "이미 천하가 통일되었습니다.")

        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        if (specialCode !in availableSpecialWar) return InheritanceActionResult(error = "잘못된 전투 특기")
        if (officer.special2Code == specialCode) return InheritanceActionResult(error = "이미 그 특기를 보유하고 있습니다.")

        val pending = officer.meta["inheritSpecificSpecialWar"] as? String
        if (pending == specialCode) return InheritanceActionResult(error = "이미 그 특기를 예약하였습니다.")
        if (pending != null) return InheritanceActionResult(error = "이미 예약한 특기가 있습니다.")

        val cost = inheritSpecialCost()
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        officer.meta["inheritSpecificSpecialWar"] = specialCode

        addInheritLog(user, "전투특기 지정: ${availableSpecialWar[specialCode]?.title ?: specialCode}", -cost)
        officerRepository.save(officer)
        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun setInheritPlanet(worldId: Long, loginId: String, cityId: Long): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val cost = inheritCityCost()
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        val planet = planetRepository.findById(cityId).orElse(null) ?: return InheritanceActionResult(error = "존재하지 않는 도시")

        user.meta["inheritPoints"] = points - cost
        user.meta["inheritCity"] = cityId
        addInheritLog(user, "시작 도시 지정: ${planet.name}", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun resetTurn(worldId: Long, loginId: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val officer = findOwnedOfficer(worldId, user) ?: return InheritanceActionResult(error = "장수를 찾을 수 없습니다")
        val world = findWorld(worldId) ?: return InheritanceActionResult(error = "월드를 찾을 수 없습니다")
        if (isWorldUnited(world)) return InheritanceActionResult(error = "이미 천하가 통일되었습니다.")

        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        val resetCount = readResetCount(officer.meta["inheritResetTurnTime"])
        val cost = resetAttrCost(resetCount)
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        val turnBaseSeconds = rollResetTurnBaseSeconds(world, user.id!!, officer)
        user.meta["inheritPoints"] = points - cost
        officer.meta["inheritResetTurnTime"] = resetCount
        officer.meta["nextTurnTimeBase"] = turnBaseSeconds

        addInheritLog(user, "턴 시간 초기화 (${resetCount + 1}회차)", -cost)
        officerRepository.save(officer)
        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun buyRandomUnique(worldId: Long, loginId: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val officer = findOwnedOfficer(worldId, user) ?: return InheritanceActionResult(error = "장수를 찾을 수 없습니다")
        val world = findWorld(worldId) ?: return InheritanceActionResult(error = "월드를 찾을 수 없습니다")
        if (isWorldUnited(world)) return InheritanceActionResult(error = "이미 천하가 통일되었습니다.")

        if (officer.meta["inheritRandomUnique"] != null) {
            return InheritanceActionResult(error = "이미 구입 명령을 내렸습니다. 다음 턴까지 기다려주세요.")
        }

        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        val cost = randomUniqueCost()
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        officer.meta["inheritRandomUnique"] = OffsetDateTime.now().toString()
        addInheritLog(user, "랜덤 유니크 획득", -cost)

        officerRepository.save(officer)
        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun refundRandomUniquePurchase(officer: Officer): Boolean {
        val userId = officer.userId ?: return false
        val user = appUserRepository.findById(userId).orElse(null) ?: return false
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        val refund = randomUniqueCost()
        user.meta["inheritPoints"] = currentPoints + refund
        addInheritLog(user, "랜덤 유니크 구매 환불", refund)
        appUserRepository.save(user)
        return true
    }

    fun resetSpecialWar(worldId: Long, loginId: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val officer = findOwnedOfficer(worldId, user) ?: return InheritanceActionResult(error = "장수를 찾을 수 없습니다")
        val world = findWorld(worldId) ?: return InheritanceActionResult(error = "월드를 찾을 수 없습니다")
        if (isWorldUnited(world)) return InheritanceActionResult(error = "이미 천하가 통일되었습니다.")

        if (officer.special2Code == "None") {
            return InheritanceActionResult(error = "이미 전투 특기가 공란입니다.")
        }

        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        val resetCount = readResetCount(officer.meta["inheritResetSpecialWar"])
        val cost = resetAttrCost(resetCount)
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        val prevSpecials = getOrCreateMutableStringList(officer.meta, "prev_special2")
        prevSpecials.add(officer.special2Code)
        officer.meta["prev_special2"] = prevSpecials
        officer.meta["inheritResetSpecialWar"] = resetCount
        officer.special2Code = "None"

        user.meta["inheritPoints"] = points - cost
        addInheritLog(user, "전투특기 초기화 (${resetCount + 1}회차)", -cost)

        officerRepository.save(officer)
        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun resetStats(worldId: Long, loginId: String, request: ResetStatsRequest): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val hasBonusStat = request.inheritBonusStat?.any { it > 0 } == true
        val cost = if (hasBonusStat) bornStatPointCost() else 0

        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        if (user.meta["inheritStatResetDone"] == true) {
            return InheritanceActionResult(error = "이미 이번 시즌에 능력치를 초기화했습니다")
        }

        val officer = officerRepository.findBySessionIdAndUserId(worldId, user.id!!).firstOrNull { it.npcState.toInt() < 5 }
            ?: return InheritanceActionResult(error = "장수를 찾을 수 없습니다")

        officer.leadership = request.leadership.toShort()
        officer.command = request.command.toShort()
        officer.intelligence = request.intelligence.toShort()
        officer.politics = request.politics.toShort()
        officer.administration = request.administration.toShort()

        request.inheritBonusStat?.takeIf { hasBonusStat }?.let { bonusStat ->
            officer.leadership = (officer.leadership + bonusStat[0]).toShort()
            officer.command = (officer.command + bonusStat[1]).toShort()
            officer.intelligence = (officer.intelligence + bonusStat[2]).toShort()
            officer.politics = (officer.politics + bonusStat[3]).toShort()
            officer.administration = (officer.administration + bonusStat[4]).toShort()
        }

        officerRepository.save(officer)

        if (cost > 0) {
            user.meta["inheritPoints"] = points - cost
        }
        user.meta["inheritStatResetDone"] = true
        addInheritLog(user, "능력치 초기화 (통${request.leadership}/무${request.command}/지${request.intelligence}/정${request.politics}/매${request.administration})", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun checkOwner(worldId: Long, loginId: String, request: CheckOwnerRequest): CheckOwnerResponse? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val cost = checkOwnerCost()
        if (points < cost) return CheckOwnerResponse(found = false)

        val officer = when {
            request.destGeneralID != null -> officerRepository.findById(request.destGeneralID).orElse(null)
            request.generalName != null -> officerRepository.findByNameAndSessionId(request.generalName, worldId)
            else -> null
        } ?: return CheckOwnerResponse(found = false)

        val ownerUser = officer.userId?.let { appUserRepository.findById(it).orElse(null) }

        user.meta["inheritPoints"] = points - cost
        addInheritLog(user, "소유주 확인: ${officer.name}", -cost)
        appUserRepository.save(user)

        return if (ownerUser != null) {
            CheckOwnerResponse(ownerName = ownerUser.loginId, found = true)
        } else {
            CheckOwnerResponse(found = false)
        }
    }

    fun getMoreLog(worldId: Long, loginId: String, lastID: Long): List<InheritanceLogEntry> {
        val user = appUserRepository.findByLoginId(loginId) ?: return emptyList()

        val rawLog = readStringAnyMapList(user.meta["inheritLog"])

        var logId = rawLog.size.toLong()
        val allLogs = rawLog.map { entry ->
            logId--
            InheritanceLogEntry(
                id = logId + 1,
                action = entry["action"] as? String ?: "",
                amount = (entry["amount"] as? Number)?.toInt() ?: 0,
                date = entry["date"] as? String ?: "",
                text = entry["text"] as? String,
            )
        }

        return allLogs.filter { (it.id ?: Long.MAX_VALUE) < lastID }.take(20)
    }

    fun auctionUnique(worldId: Long, loginId: String, request: AuctionUniqueRequest): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val minBid = minSpecificUniqueCost()
        if (request.bidAmount < minBid) {
            return InheritanceActionResult(error = "최소 입찰금: ${minBid}P")
        }
        if (points < request.bidAmount) {
            return InheritanceActionResult(error = "포인트 부족 (필요: ${request.bidAmount})")
        }

        user.meta["inheritPoints"] = points - request.bidAmount
        user.meta["inheritAuctionUnique"] = request.uniqueCode
        user.meta["inheritAuctionBid"] = request.bidAmount

        val uniqueName = AVAILABLE_UNIQUE[request.uniqueCode]?.title ?: request.uniqueCode
        addInheritLog(user, "유니크 입찰: $uniqueName (${request.bidAmount}P)", -request.bidAmount)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - request.bidAmount)
    }

    fun accruePoints(officer: Officer, key: String, amount: Int) {
        if (officer.npcState >= 2) return
        val userId = officer.userId ?: return
        val user = appUserRepository.findById(userId).orElse(null) ?: return

        val coefficient = when (key) {
            "lived_month" -> 1
            "active_action" -> 3
            "combat" -> 5
            "sabotage" -> 20
            "max_belong" -> 10
            "unifier" -> 1
            "dex" -> 1
            "tournament" -> 1
            "betting" -> 10
            "max_domestic_critical" -> 1
            else -> 1
        }

        val points = amount * coefficient
        val current = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        user.meta["inheritPoints"] = current + points
        appUserRepository.save(user)
    }

    private fun findOwnedOfficer(worldId: Long, user: AppUser): Officer? {
        return officerRepository.findBySessionIdAndUserId(worldId, user.id!!).firstOrNull { it.npcState.toInt() < 5 }
    }

    private fun findWorld(worldId: Long): SessionState? {
        return sessionStateRepository.findById(worldId.toShort()).orElse(null)
    }

    private fun isWorldUnited(world: SessionState): Boolean {
        val unified = (world.config["isunited"] as? Number)?.toInt()
            ?: (world.config["isUnited"] as? Number)?.toInt()
            ?: 0
        return unified != 0
    }

    private fun readResetCount(raw: Any?): Int {
        val level = (raw as? Number)?.toInt() ?: return 0
        return level + 1
    }

    private fun resetAttrCost(resetCount: Int): Int {
        if (resetCount <= 1) {
            return RESET_ATTR_BASE
        }
        var prev = RESET_ATTR_BASE
        var current = RESET_ATTR_BASE
        for (index in 2..resetCount) {
            val next = prev + current
            prev = current
            current = next
            if (index == resetCount) {
                return current
            }
        }
        return current
    }

    private fun rollResetTurnBaseSeconds(world: SessionState, userId: Long, officer: Officer): Double {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        val seedSource = officer.meta["nextTurnTimeBase"] ?: officer.turnTime.toString()
        val rng = DeterministicRng.create(hiddenSeed, "ResetTurnTime", userId, seedSource.toString())
        return floor(rng.nextDouble() * world.tickSeconds.toDouble() * 1000.0) / 1000.0
    }

    private fun getOrCreateMutableStringList(container: MutableMap<String, Any>, key: String): MutableList<String> {
        val result = mutableListOf<String>()
        val current = container[key]
        if (current is Iterable<*>) {
            current.forEach { entry ->
                val value = entry as? String ?: return@forEach
                result.add(value)
            }
        }
        container[key] = result
        return result
    }

    private fun addInheritLog(user: AppUser, action: String, amount: Int) {
        val log = getOrCreateMutableStringAnyMapList(user.meta, "inheritLog")
        log.add(
            mapOf(
                "action" to action,
                "amount" to amount,
                "date" to OffsetDateTime.now().toString(),
            ),
        )
        if (log.size > 50) {
            user.meta["inheritLog"] = log.takeLast(50).toMutableList()
        } else {
            user.meta["inheritLog"] = log
        }
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun readStringAnyMapList(raw: Any?): List<Map<String, Any>> {
        if (raw !is Iterable<*>) return emptyList()
        return raw.mapNotNull { readStringAnyMapOrNull(it) }
    }

    private fun readStringAnyMapOrNull(raw: Any?): Map<String, Any>? {
        if (raw !is Map<*, *>) return null
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun getOrCreateMutableStringAnyMap(container: MutableMap<String, Any>, key: String): MutableMap<String, Any> {
        val current = container[key]
        val typed = mutableMapOf<String, Any>()
        if (current is Map<*, *>) {
            current.forEach { (k, v) ->
                if (k is String && v != null) {
                    typed[k] = v
                }
            }
        }
        container[key] = typed
        return typed
    }

    private fun getOrCreateMutableStringAnyMapList(
        container: MutableMap<String, Any>,
        key: String,
    ): MutableList<Map<String, Any>> {
        val current = container[key]
        val typed = mutableListOf<Map<String, Any>>()
        if (current is Iterable<*>) {
            current.forEach { entry ->
                val parsed = readStringAnyMapOrNull(entry)
                if (parsed != null) {
                    typed.add(parsed)
                }
            }
        }
        container[key] = typed
        return typed
    }
}
