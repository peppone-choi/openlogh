package com.opensam.service

import com.opensam.dto.AuctionUniqueRequest
import com.opensam.dto.BuyInheritBuffRequest
import com.opensam.dto.CheckOwnerRequest
import com.opensam.dto.CheckOwnerResponse
import com.opensam.dto.CurrentStat
import com.opensam.dto.InheritanceActionCost
import com.opensam.dto.InheritanceActionResult
import com.opensam.dto.InheritanceInfo
import com.opensam.dto.InheritanceLogEntry
import com.opensam.dto.ResetStatsRequest
import com.opensam.dto.SpecialWarOption
import com.opensam.dto.UniqueItemOption
import com.opensam.engine.modifier.TraitSpecRegistry
import com.opensam.entity.AppUser
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import org.springframework.stereotype.Service

@Service
class InheritanceService(
    private val appUserRepository: AppUserRepository,
    private val cityRepository: CityRepository,
    private val generalRepository: GeneralRepository,
    private val gameConstService: GameConstService,
) {
    companion object {
        // Cumulative buff level costs: index = level (0 = free, 1..5)
        private val BUFF_LEVEL_COSTS = listOf(0, 200, 600, 1200, 2000, 3000)
        private const val MAX_BUFF_LEVEL = 5

        // Valid combat buff types
        val COMBAT_BUFF_TYPES = setOf(
            "warAvoidRatio", "warCriticalRatio", "warMagicTrialProb",
            "warAvoidRatioOppose", "warCriticalRatioOppose", "warMagicTrialProbOppose",
            "domesticSuccessProb", "domesticFailProb",
        )

        // Available unique items (should come from game config/static data in production)
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

        private fun fibonacciCost(base: Int, count: Int): Int {
            if (count <= 0) return base
            var a = base
            var b = base
            for (i in 0 until count) {
                val next = a + b
                a = b
                b = next
            }
            return b
        }
    }

    private val availableSpecialWar: Map<String, SpecialWarOption> = TraitSpecRegistry.war
        .associate { it.key to SpecialWarOption(it.name, it.info) }

    private fun inheritSpecialCost(): Int = gameConstService.getInt("inheritSpecificSpecialPoint")

    private fun inheritCityCost(): Int = gameConstService.getInt("inheritBornCityPoint")

    private fun randomUniqueCost(): Int = gameConstService.getInt("inheritItemRandomPoint")

    private fun checkOwnerCost(): Int = gameConstService.getInt("inheritCheckOwnerPoint")

    private fun minSpecificUniqueCost(): Int = gameConstService.getInt("inheritItemUniqueMinPoint")

    private fun bornStatPointCost(): Int = gameConstService.getInt("inheritBornStatPoint")

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

        // Point breakdown
        val rawBreakdown = readStringAnyMap(user.meta["inheritPointBreakdown"])
        val pointBreakdown = rawBreakdown.mapValues { (it.value as? Number)?.toInt() ?: 0 }

        val turnResetCount = (user.meta["inheritTurnResetCount"] as? Number)?.toInt() ?: 0
        val specialWarResetCount = (user.meta["inheritSpecialWarResetCount"] as? Number)?.toInt() ?: 0

        val actionCost = InheritanceActionCost(
            buff = BUFF_LEVEL_COSTS,
            resetTurnTime = fibonacciCost(100, turnResetCount),
            resetSpecialWar = fibonacciCost(200, specialWarResetCount),
            randomUnique = randomUniqueCost(),
            nextSpecial = inheritSpecialCost(),
            minSpecificUnique = minSpecificUniqueCost(),
            checkOwner = checkOwnerCost(),
            bornStatPoint = bornStatPointCost(),
        )

        // Get current general's stat if exists
        val general = generalRepository.findByWorldIdAndUserId(worldId, user.id!!).firstOrNull()
        val currentStat = general?.let {
            CurrentStat(
                leadership = it.leadership.toInt(),
                strength = it.strength.toInt(),
                intel = it.intel.toInt(),
                statMax = 100,
                statMin = 10,
            )
        }

        // Available target generals for owner check (all non-NPC generals in world)
        val allGenerals = generalRepository.findByWorldId(worldId)
        val availableTargetGeneral = allGenerals
            .filter { it.npcState < 2 && it.id != general?.id }
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
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        if (specialCode !in availableSpecialWar) return InheritanceActionResult(error = "잘못된 전투 특기")
        val cost = inheritSpecialCost()
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        user.meta["inheritSpecificSpecialWar"] = specialCode
        addInheritLog(user, "전투특기 지정: $specialCode", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun setInheritCity(worldId: Long, loginId: String, cityId: Long): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val cost = inheritCityCost()
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        val city = cityRepository.findById(cityId).orElse(null) ?: return InheritanceActionResult(error = "존재하지 않는 도시")

        user.meta["inheritPoints"] = points - cost
        user.meta["inheritCity"] = cityId
        addInheritLog(user, "시작 도시 지정: ${city.name}", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun resetTurn(worldId: Long, loginId: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        val resetCount = (user.meta["inheritTurnResetCount"] as? Number)?.toInt() ?: 0
        val cost = fibonacciCost(100, resetCount)

        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        user.meta["inheritTurnResetCount"] = resetCount + 1
        user.meta["inheritResetTurn"] = true
        addInheritLog(user, "턴 시간 초기화 (${resetCount + 1}회차)", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun buyRandomUnique(worldId: Long, loginId: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val cost = randomUniqueCost()
        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        user.meta["inheritRandomUnique"] = true
        addInheritLog(user, "랜덤 유니크 획득", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun resetSpecialWar(worldId: Long, loginId: String): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        val resetCount = (user.meta["inheritSpecialWarResetCount"] as? Number)?.toInt() ?: 0
        val cost = fibonacciCost(200, resetCount)

        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        user.meta["inheritPoints"] = points - cost
        user.meta["inheritSpecialWarResetCount"] = resetCount + 1
        // Actual reset of special war would be done by game engine
        addInheritLog(user, "전투특기 초기화 (${resetCount + 1}회차)", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun resetStats(worldId: Long, loginId: String, request: ResetStatsRequest): InheritanceActionResult? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val hasBonusStat = request.inheritBonusStat?.any { it > 0 } == true
        val cost = if (hasBonusStat) bornStatPointCost() else 0

        if (points < cost) return InheritanceActionResult(error = "포인트 부족 (필요: $cost)")

        // Check if already reset this season
        if (user.meta["inheritStatResetDone"] == true) {
            return InheritanceActionResult(error = "이미 이번 시즌에 능력치를 초기화했습니다")
        }

        val general = generalRepository.findByWorldIdAndUserId(worldId, user.id!!).firstOrNull()
            ?: return InheritanceActionResult(error = "장수를 찾을 수 없습니다")

        // Update general stats
        general.leadership = request.leadership.toShort()
        general.strength = request.strength.toShort()
        general.intel = request.intel.toShort()

        // Apply bonus stat if present
        request.inheritBonusStat?.takeIf { hasBonusStat }?.let { bonusStat ->
            general.leadership = (general.leadership + bonusStat[0]).toShort()
            general.strength = (general.strength + bonusStat[1]).toShort()
            general.intel = (general.intel + bonusStat[2]).toShort()
        }

        generalRepository.save(general)

        if (cost > 0) {
            user.meta["inheritPoints"] = points - cost
        }
        user.meta["inheritStatResetDone"] = true
        addInheritLog(user, "능력치 초기화 (통${request.leadership}/무${request.strength}/지${request.intel})", -cost)

        appUserRepository.save(user)
        return InheritanceActionResult(remainingPoints = points - cost)
    }

    fun checkOwner(worldId: Long, loginId: String, request: CheckOwnerRequest): CheckOwnerResponse? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val points = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0

        val cost = checkOwnerCost()
        if (points < cost) return CheckOwnerResponse(found = false)

        val general = when {
            request.destGeneralID != null -> generalRepository.findById(request.destGeneralID).orElse(null)
            request.generalName != null -> generalRepository.findByNameAndWorldId(request.generalName, worldId)
            else -> null
        } ?: return CheckOwnerResponse(found = false)

        val ownerUser = general.userId?.let { appUserRepository.findById(it).orElse(null) }

        user.meta["inheritPoints"] = points - cost
        addInheritLog(user, "소유주 확인: ${general.name}", -cost)
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

    /**
     * Accrue inheritance points for a user based on their general's actions.
     */
    fun accruePoints(general: com.opensam.entity.General, key: String, amount: Int) {
        if (general.npcState >= 2) return
        val userId = general.userId ?: return
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

    private fun addInheritLog(user: AppUser, action: String, amount: Int) {
        val log = getOrCreateMutableStringAnyMapList(user.meta, "inheritLog")
        log.add(mapOf(
            "action" to action,
            "amount" to amount,
            "date" to java.time.OffsetDateTime.now().toString(),
        ))
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
