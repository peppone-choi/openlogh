@file:Suppress("unused")

package com.openlogh.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openlogh.engine.CommandPointService
import com.openlogh.engine.CoupExecutionService
import com.openlogh.engine.organization.CommandGating
import com.openlogh.engine.organization.PositionCardType
import com.openlogh.entity.*
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class CommandExecutor(
    private val defaultRegistry: CommandRegistry = CommandRegistry(),
    private val factionRepository: FactionRepository? = null,
    private val planetRepository: PlanetRepository? = null,
    private val officerRepository: OfficerRepository? = null,
    private val commandPointService: CommandPointService? = null,
    private val coupExecutionService: CoupExecutionService? = null,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandExecutor::class.java)
    }

    private val mapper = jacksonObjectMapper()

    /**
     * Execute an officer-mutating action with optimistic lock retry.
     * Re-reads the officer on conflict, up to 3 attempts.
     * HARD-01: Prevents CP race condition by catching version conflicts.
     */
    fun <T> withOptimisticRetry(officerId: Long, action: (Officer) -> T): T {
        val repo = officerRepository
            ?: throw IllegalStateException("OfficerRepository not available")
        repeat(3) { attempt ->
            try {
                val officer = repo.findById(officerId)
                    .orElseThrow { IllegalArgumentException("Officer not found: $officerId") }
                val result = action(officer)
                repo.save(officer)
                return result
            } catch (e: OptimisticLockingFailureException) {
                if (attempt == 2) throw e
                log.warn("Optimistic lock conflict on officer {}, retry {}/3", officerId, attempt + 2)
            }
        }
        throw IllegalStateException("Optimistic lock retry exhausted for officer $officerId")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun executeGeneralCommand(
        actionCode: String,
        general: General,
        env: CommandEnv,
        city: City? = null,
        nation: Nation? = null,
        arg: Map<String, Any>? = null,
        rng: Random = Random,
        registry: CommandRegistry? = null,
        services: CommandServices? = null,
    ): CommandResult {
        // Cooldown check
        val nextExecuteMap = general.meta["next_execute"] as? Map<String, Any>
        if (nextExecuteMap != null) {
            val cooldownEnd = (nextExecuteMap[actionCode] as? Number)?.toInt()
            if (cooldownEnd != null) {
                val currentYearMonth = env.year * 12 + env.month
                if (currentYearMonth < cooldownEnd) {
                    val remainMonths = cooldownEnd - currentYearMonth
                    return CommandResult(
                        success = false,
                        logs = listOf("${formatDate(env)} <R>${actionCode}</R> 쿨다운 중입니다. (${remainMonths}턴 후 가능)"),
                    )
                }
            }
        }

        // Position card check: 직무카드 기반 커맨드 권한 확인
        val heldCards = (general.meta["positionCards"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: CommandGating.defaultCards()

        val commandGroup = resolveCommandGroup(actionCode)
        if (commandGroup != null && !CommandGating.canExecuteCommand(heldCards, commandGroup)) {
            return CommandResult(
                success = false,
                logs = listOf("${formatDate(env)} <R>${actionCode}</R> 해당 직무권한이 없습니다."),
            )
        }

        // CP consumption: 커맨드 포인트 소비
        if (commandPointService != null) {
            val cpType = resolveCpType(actionCode)
            val cpCost = resolveCpCost(actionCode)
            if (cpCost > 0 && !commandPointService.consume(general, cpType, cpCost)) {
                return CommandResult(
                    success = false,
                    logs = listOf("${formatDate(env)} <R>${actionCode}</R> 커맨드 포인트가 부족합니다. (필요: $cpCost ${cpType.name})"),
                )
            }
        }

        // Create command via registry
        val reg = registry ?: defaultRegistry
        val cmd = reg.createGeneralCommand(actionCode, general, env, arg)
        cmd.city = city
        cmd.nation = nation
        cmd.services = services

        val result = cmd.run(rng)
        if (!result.success) return result

        // Apply stat changes from message
        if (result.message != null) {
            applyMessageChanges(result.message, general, city, nation, env)
        }

        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyMessageChanges(
        message: String,
        general: General,
        city: City?,
        nation: Nation?,
        env: CommandEnv,
    ) {
        val parsed: Map<String, Any> = try {
            mapper.readValue(message)
        } catch (_: Exception) {
            return
        }

        // Apply general stat changes
        val statChanges = parsed["statChanges"] as? Map<String, Any>
        if (statChanges != null) applyGeneralStatMap(general, statChanges)

        // Apply city changes
        val cityChanges = parsed["cityChanges"] as? Map<String, Any>
        if (cityChanges != null && city != null) applyCityStatMap(city, cityChanges)

        // Apply nation changes
        val nationChanges = parsed["nationChanges"] as? Map<String, Any>
        if (nationChanges != null && nation != null) applyNationStatMap(nation, nationChanges)

        // Handle special: createWanderingNation (거병)
        if (parsed["createWanderingNation"] == true) {
            handleCreateWanderingNation(general, env)
        }

        // Handle special: coup execution (반란 성공 시)
        if (parsed["joinCoup"] == true || parsed["becomeCoupLeader"] == true) {
            handleCoupExecution(general, env, parsed)
        }

        // Handle special: nation foundation
        if (parsed["nationFoundation"] == true) {
            val foundNation = parsed["foundNation"] as? Map<String, Any>
            handleNationFoundation(general, city, env, foundNation, placeAtCurrentCity = true)
        } else if (parsed.containsKey("foundNation") && parsed["nationFoundation"] != true) {
            val foundNation = parsed["foundNation"] as? Map<String, Any>
            handleNationFoundation(general, city, env, foundNation, placeAtCurrentCity = false)
        }
    }

    private fun applyGeneralStatMap(general: General, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            val value = (rawValue as? Number)?.toInt() ?: continue
            when (key) {
                "funds", "gold" -> general.funds += value
                "supplies", "rice" -> general.supplies += value
                "ships", "crew" -> general.ships += value
                "shipClass", "crewType" -> general.shipClass = value.toShort()
                "training", "train" -> general.training = (general.training + value).toShort()
                "morale", "atmos" -> general.morale = (general.morale + value).toShort()
                "injury" -> general.injury = (general.injury + value).coerceAtLeast(0).toShort()
                "experience" -> general.experience += value
                "dedication" -> general.dedication += value
                "officerLevel" -> general.rank = (general.rank + value).toShort()
                "betray" -> general.betray = (general.betray + value).toShort()
                "leadershipExp" -> general.leadershipExp = (general.leadershipExp + value).toShort()
                "strengthExp", "commandExp" -> general.commandExp = (general.commandExp + value).toShort()
                "intelExp", "intelligenceExp" -> general.intelligenceExp = (general.intelligenceExp + value).toShort()
                "politicsExp" -> general.politicsExp = (general.politicsExp + value).toShort()
                "charmExp", "administrationExp" -> general.administrationExp = (general.administrationExp + value).toShort()
            }
        }
    }

    private fun applyCityStatMap(city: City, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            when (key) {
                "trust" -> {
                    val delta = (rawValue as? Number)?.toFloat() ?: continue
                    city.trust += delta
                }
                else -> {
                    val value = (rawValue as? Number)?.toInt() ?: continue
                    when (key) {
                        "agri" -> city.agri += value
                        "comm" -> city.comm += value
                        "secu" -> city.secu += value
                        "def" -> city.def += value
                        "wall" -> city.wall += value
                        "pop" -> city.pop += value
                    }
                }
            }
        }
    }

    private fun applyNationStatMap(nation: Nation, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            val value = (rawValue as? Number)?.toInt() ?: continue
            when (key) {
                "funds", "gold" -> nation.funds += value
                "supplies", "rice" -> nation.supplies += value
            }
        }
    }

    private fun handleCreateWanderingNation(general: General, env: CommandEnv) {
        val repo = factionRepository ?: return
        val wanderingNation = Faction(
            sessionId = env.worldId,
            name = "${general.name}군",
            color = "#808080",
            factionRank = 0,
            supremeCommanderId = general.id,
            officerCount = 1,
        )
        val saved = repo.save(wanderingNation)
        general.nationId = saved.id
        general.officerLevel = 20
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleNationFoundation(
        general: General,
        city: City?,
        env: CommandEnv,
        foundNation: Map<String, Any>?,
        placeAtCurrentCity: Boolean,
    ) {
        val nationName = foundNation?.get("nationName") as? String ?: "신국"
        val nationType = foundNation?.get("nationType") as? String ?: "도적"

        if (general.nationId != 0L) {
            // Already has a wandering nation - upgrade it
            val existingNation = factionRepository?.findById(general.nationId)?.orElse(null) ?: return
            existingNation.name = nationName
            existingNation.typeCode = "che_$nationType"
            existingNation.level = 1

            if (placeAtCurrentCity && city != null) {
                existingNation.capitalCityId = city.id
                city.nationId = general.nationId
            } else {
                // 무작위건국: pick a random neutral city
                val allCities = planetRepository?.findBySessionId(env.worldId) ?: return
                val neutralCities = allCities.filter { it.nationId == 0L && it.id != (city?.id ?: -1) }
                if (neutralCities.isNotEmpty()) {
                    val target = neutralCities.random()
                    existingNation.capitalCityId = target.id
                    target.nationId = general.nationId

                    // Move all nation generals to the target city
                    val nationGenerals = officerRepository?.findByNationId(general.nationId) ?: emptyList()
                    for (ng in nationGenerals) {
                        ng.cityId = target.id
                    }
                    planetRepository?.save(target)
                }
            }
            factionRepository?.save(existingNation)
        } else {
            // Unaffiliated general founding a new nation
            val repo = factionRepository ?: return
            val newNation = Faction(
                sessionId = env.worldId,
                name = nationName,
                color = "#FF0000",
                factionRank = 1,
                supremeCommanderId = general.id,
                officerCount = 1,
                factionType = "che_$nationType",
            )
            val saved = repo.save(newNation)
            general.nationId = saved.id
            general.officerLevel = 20

            if (city != null) {
                saved.capitalPlanetId = city.id
                city.nationId = saved.id
                repo.save(saved)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleCoupExecution(general: General, env: CommandEnv, parsed: Map<String, Any>) {
        val service = coupExecutionService ?: return
        // Only trigger coup when explicitly becoming coup leader with enough support
        if (parsed["becomeCoupLeader"] != true) return
        val coupStep = (parsed["coupStep"] as? Number)?.toInt() ?: 0
        if (coupStep < 1) return

        // Mark rebellion intent in officer meta; actual coup execution requires
        // enough conspirators (checked on subsequent turns by the turn engine)
        general.meta["rebellionIntent"] = coupStep
        general.meta["coupLeader"] = true
    }

    private fun formatDate(env: CommandEnv): String =
        "${env.year}년 ${"%02d".format(env.month)}월"

    /** 커맨드 코드 → 직무카드 커맨드 그룹 매핑 */
    private fun resolveCommandGroup(actionCode: String): String? {
        // 전략 커맨드 레지스트리에서 찾기
        val strategicDef = com.openlogh.engine.strategic.StrategicCommandRegistry.findByCode(actionCode)
        if (strategicDef != null) return strategicDef.requiredCommandGroup

        // 기존 커맨드: 접두사 기반 매핑
        return when {
            actionCode.startsWith("che_") -> when {
                actionCode.contains("외교") || actionCode.contains("선전포고") -> "diplomacy"
                actionCode.contains("모병") || actionCode.contains("징병") -> "logistics"
                actionCode.contains("공격") || actionCode.contains("출병") -> "operations"
                actionCode.contains("발탁") || actionCode.contains("강등") || actionCode.contains("서작") -> "personnel"
                actionCode.contains("세율") || actionCode.contains("분배") -> "politics"
                else -> null // 기본 카드로 실행 가능
            }
            else -> null // 기본 카드로 실행 가능
        }
    }

    /** 커맨드 코드 → CP 타입 결정 (정략 vs 군사) */
    private fun resolveCpType(actionCode: String): CommandPointService.CpType {
        val strategicDef = com.openlogh.engine.strategic.StrategicCommandRegistry.findByCode(actionCode)
        if (strategicDef != null) return strategicDef.cpType

        // 군사 관련 커맨드는 MCP, 나머지는 PCP
        val militaryKeywords = listOf("공격", "출병", "이동", "훈련", "모병", "징병", "정찰", "연료",
            "워프", "항행", "군기", "경계", "진압", "행진", "징발", "부대", "보충", "할당",
            "통신", "위장", "병기", "수색", "습격", "감시", "잠입", "탈출", "정보공작", "파괴", "선동", "귀환공작",
            "체포명령", "사열", "특별경비")
        return if (militaryKeywords.any { actionCode.contains(it) }) {
            CommandPointService.CpType.MCP
        } else {
            CommandPointService.CpType.PCP
        }
    }

    /** 커맨드 코드 → CP 비용 결정 */
    private fun resolveCpCost(actionCode: String): Int {
        val strategicDef = com.openlogh.engine.strategic.StrategicCommandRegistry.findByCode(actionCode)
        if (strategicDef != null) return strategicDef.cpCost

        // 기존 커맨드 기본 비용 (0 = CP 소비 없음)
        return 0 // 기존 커맨드는 현재 CP 체크 없이 유지 (점진적 이행)
    }
}
