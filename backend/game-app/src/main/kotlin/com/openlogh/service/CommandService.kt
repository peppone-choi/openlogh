package com.openlogh.service

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.command.CommandResult
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.dto.CommandTableEntry
import com.openlogh.dto.TurnEntry
import com.openlogh.engine.RealtimeService
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.FactionTurn
import com.openlogh.repository.*
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommandService(
    private val officerTurnRepository: OfficerTurnRepository,
    private val factionTurnRepository: FactionTurnRepository,
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val appUserRepository: AppUserRepository,
    private val commandExecutor: CommandExecutor,
    private val commandRegistry: CommandRegistry,
    private val realtimeService: RealtimeService,
    private val gameConstService: GameConstService,
    private val gameEventService: GameEventService? = null,
) {
    fun verifyOwnership(generalId: Long, loginId: String): Boolean {
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false
        return officer.userId == user.id
    }

    fun listGeneralTurns(generalId: Long): List<OfficerTurn> {
        return officerTurnRepository.findByOfficerIdOrderByTurnIdx(generalId)
    }

    @Transactional
    fun reserveGeneralTurns(generalId: Long, turns: List<TurnEntry>): List<OfficerTurn> {
        val officer = officerRepository.findById(generalId).orElseThrow {
            IllegalArgumentException("General not found: $generalId")
        }
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElseThrow {
            IllegalArgumentException("World not found: ${officer.sessionId}")
        }
        if (world.realtimeMode) {
            throw IllegalStateException("실시간 모드에서는 예턴 예약을 사용할 수 없습니다.")
        }

        val turnIdxList = turns.map { it.turnIdx }
        officerTurnRepository.deleteByOfficerIdAndTurnIdxIn(generalId, turnIdxList)
        officerTurnRepository.flush()
        val saved = turns.map { entry ->
            officerTurnRepository.save(
                OfficerTurn(
                    sessionId = officer.sessionId,
                    officerId = generalId,
                    turnIdx = entry.turnIdx,
                    actionCode = entry.actionCode,
                    arg = entry.arg?.toMutableMap() ?: mutableMapOf(),
                )
            )
        }
        gameEventService?.fireCommand(
            worldId = officer.sessionId,
            year = world.currentYear,
            month = world.currentMonth,
            generalId = generalId,
            commandEventType = "reserved",
            detail = mapOf("turnCount" to saved.size),
        )
        return saved
    }

    @Transactional
    fun executeCommand(generalId: Long, actionCode: String, arg: Map<String, Any>?): CommandResult? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null

        if (world.realtimeMode) {
            return realtimeService.submitCommand(generalId, actionCode, arg)
        }

        val planet = planetRepository.findById(officer.planetId).orElse(null)
        val faction = if (officer.factionId != 0L) {
            factionRepository.findById(officer.factionId).orElse(null)
        } else null

        val env = createCommandEnv(world)

        val result = runBlocking {
            commandExecutor.executeOfficerCommand(
                actionCode = actionCode,
                general = officer,
                env = env,
                arg = arg,
                city = planet,
                nation = faction,
            )
        }
        officerRepository.save(officer)
        if (faction != null) factionRepository.save(faction)
        if (planet != null) planetRepository.save(planet)
        return result
    }

    @Transactional
    fun executeFactionCommand(generalId: Long, actionCode: String, arg: Map<String, Any>?): CommandResult? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        if (officer.officerLevel < 5) {
            return CommandResult(success = false, logs = listOf("국가 명령 권한이 없습니다."))
        }
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null

        if (world.realtimeMode) {
            return realtimeService.submitNationCommand(generalId, actionCode, arg)
        }

        val planet = planetRepository.findById(officer.planetId).orElse(null)
        val faction = if (officer.factionId != 0L) {
            factionRepository.findById(officer.factionId).orElse(null)
        } else null

        val env = createCommandEnv(world)

        val result = runBlocking {
            commandExecutor.executeFactionCommand(
                actionCode = actionCode,
                general = officer,
                env = env,
                arg = arg,
                city = planet,
                nation = faction,
            )
        }
        officerRepository.save(officer)
        if (faction != null) factionRepository.save(faction)
        if (planet != null) planetRepository.save(planet)
        return result
    }

    fun getCommandTable(generalId: Long): Map<String, List<CommandTableEntry>>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        val planet = planetRepository.findById(officer.planetId).orElse(null)
        val faction = if (officer.factionId != 0L) factionRepository.findById(officer.factionId).orElse(null) else null
        val env = createCommandEnv(world)

        val allowedCommands = extractAllowedCommands(world.config, "availableGeneralCommand")

        val categories = linkedMapOf<String, MutableList<CommandTableEntry>>()
        val actionCodes = commandRegistry.getGeneralCommandNames().toList().sortedWith(
            compareBy<String>({ generalCategoryOrder(generalCategory(it)) }, { it })
        )

        for (actionCode in actionCodes) {
            if (allowedCommands != null && actionCode !in allowedCommands) continue
            // NPC/CR commands are scenario-specific; hide when no whitelist is configured
            if (allowedCommands == null && (actionCode.startsWith("NPC") || actionCode.startsWith("CR"))) continue
            val command = commandRegistry.createOfficerCommand(actionCode, officer, env, null)
            command.city = planet
            command.nation = faction

            val minCheck = command.checkMinCondition()
            val enabled = minCheck is ConstraintResult.Pass
            val reason = if (minCheck is ConstraintResult.Fail) minCheck.reason else null

            val category = generalCategory(actionCode)

            categories.getOrPut(category) { mutableListOf() }.add(
                CommandTableEntry(
                    actionCode = actionCode,
                    name = command.actionName,
                    category = category,
                    enabled = enabled,
                    reason = reason,
                    durationSeconds = command.getDuration(),
                    commandPointCost = command.getCommandPointCost(),
                    poolType = command.getCommandPoolType().name,
                )
            )
        }

        return categories
    }

    fun getNationCommandTable(generalId: Long): Map<String, List<CommandTableEntry>>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        if (officer.officerLevel < 5) {
            return linkedMapOf()
        }

        val planet = planetRepository.findById(officer.planetId).orElse(null)
        val faction = if (officer.factionId != 0L) factionRepository.findById(officer.factionId).orElse(null) else null
        val env = createCommandEnv(world)

        val allowedCommands = extractAllowedCommands(world.config, "availableChiefCommand")

        val categories = linkedMapOf<String, MutableList<CommandTableEntry>>()
        val actionCodes = commandRegistry.getNationCommandNames().toList().sortedWith(
            compareBy<String>({ nationCategoryOrder(nationCategory(it)) }, { it })
        )

        for (actionCode in actionCodes) {
            if (allowedCommands != null && actionCode !in allowedCommands) continue
            val command = commandRegistry.createFactionCommand(actionCode, officer, env, null) ?: continue
            command.city = planet
            command.nation = faction

            val check = command.checkMinCondition()
            val enabled = check is ConstraintResult.Pass
            val reason = if (check is ConstraintResult.Fail) check.reason else null
            val category = nationCategory(actionCode)

            categories.getOrPut(category) { mutableListOf() }.add(
                CommandTableEntry(
                    actionCode = actionCode,
                    name = command.actionName,
                    category = category,
                    enabled = enabled,
                    reason = reason,
                    durationSeconds = command.getDuration(),
                    commandPointCost = command.getCommandPointCost(),
                    poolType = command.getCommandPoolType().name,
                )
            )
        }

        return categories
    }

    @Transactional
    fun repeatTurns(generalId: Long, count: Int): List<OfficerTurn>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        if (world.realtimeMode) {
            throw IllegalStateException("실시간 모드에서는 예턴 반복을 사용할 수 없습니다.")
        }

        val existing = officerTurnRepository.findByOfficerIdOrderByTurnIdx(generalId)
        if (existing.isEmpty()) return null
        val maxTurn = gameConstService.getInt("maxTurn")
        if (count <= 0 || count >= maxTurn) {
            return existing
        }

        val queue = buildFullOfficerTurnQueue(officer, existing, maxTurn)
        val reqTurn = if (count * 2 > maxTurn) maxTurn - count else count

        for (turnIdx in 0 until reqTurn) {
            val source = queue[turnIdx]
            var target = turnIdx + count
            while (target < maxTurn) {
                queue[target].actionCode = source.actionCode
                queue[target].arg = source.arg.toMutableMap()
                queue[target].brief = source.brief
                target += count
            }
        }

        return replaceOfficerTurns(officer, queue)
    }

    @Transactional
    fun pushTurns(generalId: Long, amount: Int): List<OfficerTurn>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        if (world.realtimeMode) {
            throw IllegalStateException("실시간 모드에서는 예턴 밀기/당기기를 사용할 수 없습니다.")
        }

        val existing = officerTurnRepository.findByOfficerIdOrderByTurnIdx(generalId)
        if (existing.isEmpty()) return null
        val maxTurn = gameConstService.getInt("maxTurn")
        if (amount == 0 || kotlin.math.abs(amount) >= maxTurn) {
            return existing
        }

        val queue = buildFullOfficerTurnQueue(officer, existing, maxTurn)
        val nextQueue = MutableList(maxTurn) { idx -> defaultOfficerTurn(officer, idx.toShort()) }

        if (amount > 0) {
            for (idx in 0 until maxTurn) {
                nextQueue[idx] = if (idx < amount) {
                    defaultOfficerTurn(officer, idx.toShort())
                } else {
                    queue[idx - amount].copyTurn(idx.toShort())
                }
            }
        } else {
            val pullCount = -amount
            for (idx in 0 until maxTurn) {
                nextQueue[idx] = if (idx >= maxTurn - pullCount) {
                    defaultOfficerTurn(officer, idx.toShort())
                } else {
                    queue[idx + pullCount].copyTurn(idx.toShort())
                }
            }
        }

        return replaceOfficerTurns(officer, nextQueue)
    }

    private fun buildFullOfficerTurnQueue(officer: com.openlogh.entity.Officer, existing: List<OfficerTurn>, maxTurn: Int): MutableList<OfficerTurn> {
        val indexed = existing.associateBy { it.turnIdx.toInt() }
        return MutableList(maxTurn) { idx ->
            indexed[idx]?.copyTurn(idx.toShort()) ?: defaultOfficerTurn(officer, idx.toShort())
        }
    }

    private fun replaceOfficerTurns(officer: com.openlogh.entity.Officer, turns: List<OfficerTurn>): List<OfficerTurn> {
        officerTurnRepository.deleteByOfficerId(officer.id)
        return officerTurnRepository.saveAll(turns.map { it.copyTurn(it.turnIdx) }).sortedBy { it.turnIdx }
    }

    private fun defaultOfficerTurn(officer: com.openlogh.entity.Officer, turnIdx: Short): OfficerTurn {
        return OfficerTurn(
            sessionId = officer.sessionId,
            officerId = officer.id,
            turnIdx = turnIdx,
            actionCode = "휴식",
            arg = mutableMapOf(),
            brief = "휴식",
        )
    }

    private fun OfficerTurn.copyTurn(turnIdx: Short): OfficerTurn {
        return OfficerTurn(
            sessionId = sessionId,
            officerId = officerId,
            turnIdx = turnIdx,
            actionCode = actionCode,
            arg = arg.toMutableMap(),
            brief = brief,
        )
    }

    fun listNationTurns(nationId: Long, officerLevel: Short): List<FactionTurn> {
        return factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
    }

    @Transactional
    fun repeatNationTurns(generalId: Long, nationId: Long, count: Int): List<FactionTurn>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        if (officer.factionId == 0L || officer.factionId != nationId || officer.officerLevel < 5) {
            return null
        }
        if (world.realtimeMode) {
            throw IllegalStateException("실시간 모드에서는 국가 예턴 반복을 사용할 수 없습니다.")
        }

        val existing = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(officer.factionId, officer.officerLevel)
        if (existing.isEmpty()) return null
        val maxChiefTurn = gameConstService.getInt("maxChiefTurn")
        if (count <= 0 || count >= maxChiefTurn) {
            return existing
        }

        val queue = buildFullFactionTurnQueue(officer, existing, maxChiefTurn)
        val reqTurn = if (count * 2 > maxChiefTurn) maxChiefTurn - count else count

        for (turnIdx in 0 until reqTurn) {
            val source = queue[turnIdx]
            var target = turnIdx + count
            while (target < maxChiefTurn) {
                queue[target].actionCode = source.actionCode
                queue[target].arg = source.arg.toMutableMap()
                queue[target].brief = source.brief
                target += count
            }
        }

        return replaceFactionTurns(officer, queue)
    }

    @Transactional
    fun pushNationTurns(generalId: Long, nationId: Long, amount: Int): List<FactionTurn>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        if (officer.factionId == 0L || officer.factionId != nationId || officer.officerLevel < 5) {
            return null
        }
        if (world.realtimeMode) {
            throw IllegalStateException("실시간 모드에서는 국가 예턴 밀기/당기기를 사용할 수 없습니다.")
        }

        val existing = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(officer.factionId, officer.officerLevel)
        if (existing.isEmpty()) return null
        val maxChiefTurn = gameConstService.getInt("maxChiefTurn")
        if (amount == 0 || kotlin.math.abs(amount) >= maxChiefTurn) {
            return existing
        }

        val queue = buildFullFactionTurnQueue(officer, existing, maxChiefTurn)
        val nextQueue = MutableList(maxChiefTurn) { idx -> defaultFactionTurn(officer, idx.toShort()) }

        if (amount > 0) {
            for (idx in 0 until maxChiefTurn) {
                nextQueue[idx] = if (idx < amount) {
                    defaultFactionTurn(officer, idx.toShort())
                } else {
                    queue[idx - amount].copyFactionTurn(idx.toShort())
                }
            }
        } else {
            val pullCount = -amount
            for (idx in 0 until maxChiefTurn) {
                nextQueue[idx] = if (idx >= maxChiefTurn - pullCount) {
                    defaultFactionTurn(officer, idx.toShort())
                } else {
                    queue[idx + pullCount].copyFactionTurn(idx.toShort())
                }
            }
        }

        return replaceFactionTurns(officer, nextQueue)
    }

    @Transactional
    fun reserveNationTurns(generalId: Long, nationId: Long, turns: List<TurnEntry>): List<FactionTurn>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        if (officer.factionId != nationId) {
            return null
        }
        if (officer.officerLevel < 5) {
            return null
        }

        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null
        if (world.realtimeMode) {
            throw IllegalStateException("실시간 모드에서는 국가 예턴 예약을 사용할 수 없습니다.")
        }

        val turnIdxList = turns.map { it.turnIdx }
        factionTurnRepository.deleteByFactionIdAndOfficerLevelAndTurnIdxIn(nationId, officer.officerLevel, turnIdxList)
        factionTurnRepository.flush()
        val saved = turns.map { entry ->
            factionTurnRepository.save(
                FactionTurn(
                    sessionId = officer.sessionId,
                    factionId = nationId,
                    officerLevel = officer.officerLevel,
                    turnIdx = entry.turnIdx,
                    actionCode = entry.actionCode,
                    arg = entry.arg?.toMutableMap() ?: mutableMapOf(),
                )
            )
        }
        gameEventService?.fireCommand(
            worldId = officer.sessionId,
            year = world.currentYear,
            month = world.currentMonth,
            generalId = generalId,
            commandEventType = "reserved",
            detail = mapOf("turnCount" to saved.size, "nationId" to nationId),
        )
        return saved
    }

    private fun buildFullFactionTurnQueue(officer: com.openlogh.entity.Officer, existing: List<FactionTurn>, maxChiefTurn: Int): MutableList<FactionTurn> {
        val indexed = existing.associateBy { it.turnIdx.toInt() }
        return MutableList(maxChiefTurn) { idx ->
            indexed[idx]?.copyFactionTurn(idx.toShort()) ?: defaultFactionTurn(officer, idx.toShort())
        }
    }

    private fun replaceFactionTurns(officer: com.openlogh.entity.Officer, turns: List<FactionTurn>): List<FactionTurn> {
        factionTurnRepository.deleteByFactionIdAndOfficerLevel(officer.factionId, officer.officerLevel)
        return factionTurnRepository.saveAll(turns.map { it.copyFactionTurn(it.turnIdx) }).sortedBy { it.turnIdx }
    }

    private fun defaultFactionTurn(officer: com.openlogh.entity.Officer, turnIdx: Short): FactionTurn {
        return FactionTurn(
            sessionId = officer.sessionId,
            factionId = officer.factionId,
            officerLevel = officer.officerLevel,
            turnIdx = turnIdx,
            actionCode = "휴식",
            arg = mutableMapOf(),
            brief = "휴식",
        )
    }

    private fun FactionTurn.copyFactionTurn(turnIdx: Short): FactionTurn {
        return FactionTurn(
            sessionId = sessionId,
            factionId = factionId,
            officerLevel = officerLevel,
            turnIdx = turnIdx,
            actionCode = actionCode,
            arg = arg.toMutableMap(),
            brief = brief,
        )
    }

    private fun generalCategory(actionCode: String): String = when (actionCode) {
        "휴식", "요양", "단련", "숙련전환", "견문", "은퇴", "장비매매", "군량매매", "내정특기초기화", "전투특기초기화" -> "개인"
        "농지개간", "상업투자", "기술연구", "수비강화", "성벽보수", "치안강화", "정착장려", "주민선정" -> "내정"
        "징병", "모병", "훈련", "사기진작", "출병", "집합", "소집해제", "첩보", "전투태세" -> "군사"
        "이동", "강행", "인재탐색", "등용", "등용수락", "귀환", "접경귀환", "임관", "랜덤임관", "장수대상임관" -> "인사"
        "선동", "탈취", "파괴", "화계" -> "계략"
        "증여", "헌납", "물자조달", "하야", "거병", "건국", "무작위건국", "모반시도", "선양", "해산", "방랑" -> "국가"
        else -> "기타"
    }

    private fun generalCategoryOrder(category: String): Int = when (category) {
        "개인" -> 1
        "내정" -> 2
        "군사" -> 3
        "인사" -> 4
        "계략" -> 5
        "국가" -> 6
        else -> 99
    }

    private fun nationCategory(actionCode: String): String = when (actionCode) {
        "Nation휴식" -> "휴식"
        "발령", "포상", "몰수", "부대탈퇴지시" -> "인사"
        "물자원조", "불가침제의", "불가침수락", "선전포고", "종전제의", "종전수락", "불가침파기제의", "불가침파기수락" -> "외교"
        "칭제", "천자맞이", "선양요구", "신속", "독립선언" -> "황제"
        "초토화", "천도", "증축", "감축" -> "특수"
        "필사즉생", "백성동원", "수몰", "허보", "의병모집", "이호경식", "급습", "피장파장" -> "전략"
        "국기변경", "국호변경", "인구이동", "무작위수도이전" -> "기타"
        "극병연구", "대검병연구", "무희연구", "산저병연구", "상병연구", "원융노병연구", "음귀병연구", "화륜차연구", "화시병연구" -> "연구"
        else -> "기타"
    }

    private fun nationCategoryOrder(category: String): Int = when (category) {
        "휴식" -> 1
        "인사" -> 2
        "외교" -> 3
        "황제" -> 4
        "특수" -> 5
        "전략" -> 6
        "기타" -> 7
        "연구" -> 8
        else -> 99
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractAllowedCommands(config: Map<String, Any>, key: String): Set<String>? {
        val raw = config[key] ?: return null
        val categoryMap = raw as? Map<String, List<String>> ?: return null
        return categoryMap.values.flatten()
            .map { it.removePrefix("che_").removePrefix("cr_") }
            .toSet()
    }

    private fun createCommandEnv(world: com.openlogh.entity.SessionState): CommandEnv {
        val gameStor = mutableMapOf<String, Any>()
        val mapName = (world.config["mapCode"] as? String)?.trim().orEmpty().ifBlank { "che" }
        gameStor["mapName"] = mapName
        gameStor["maxGeneral"] = (world.config["maxGeneral"] as? Number)?.toInt() ?: 500
        gameStor["openingPartYears"] = (world.config["openingPartYears"] as? Number)?.toInt()
            ?: gameConstService.getInt("openingPartYear")
        gameStor["joinActionLimit"] = gameConstService.getInt("joinActionLimit")
        gameStor["emperorSystem"] = (world.meta["emperorSystem"] as? Boolean) ?: false
        (world.meta["emperorGeneralId"] as? Number)?.toLong()?.let { gameStor["emperorGeneralId"] = it }

        return CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt(),
            sessionId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
            gameStor = gameStor,
            trainDelta = gameConstService.getDouble("trainDelta"),
            atmosDelta = gameConstService.getDouble("atmosDelta"),
            maxTrainByCommand = gameConstService.getInt("maxTrainByCommand"),
            maxAtmosByCommand = gameConstService.getInt("maxAtmosByCommand"),
            atmosSideEffectByTraining = gameConstService.getDouble("atmosSideEffectByTraining"),
            trainSideEffectByAtmosTurn = gameConstService.getDouble("trainSideEffectByAtmosTurn"),
        )
    }
}
