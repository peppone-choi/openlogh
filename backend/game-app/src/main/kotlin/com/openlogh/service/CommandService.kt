package com.openlogh.service

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.command.CommandResult
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.dto.CommandTableEntry
import com.openlogh.engine.RealtimeService
import com.openlogh.model.PositionCardRegistry
import com.openlogh.repository.*
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommandService(
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
        val officerCards = officer.getPositionCardEnums()
        if (actionCode != "Nation휴식" && !PositionCardRegistry.canExecute(officerCards, actionCode)) {
            return CommandResult(success = false, logs = listOf("해당 직무권한카드가 없습니다."))
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
        val officerCards = officer.getPositionCardEnums()

        val categories = linkedMapOf<String, MutableList<CommandTableEntry>>()
        val actionCodes = commandRegistry.getGeneralCommandNames().toList().sortedWith(
            compareBy<String>({ generalCategoryOrder(generalCategory(it)) }, { it })
        )

        for (actionCode in actionCodes) {
            if (allowedCommands != null && actionCode !in allowedCommands) continue
            // NPC/CR commands are scenario-specific; hide when no whitelist is configured
            if (allowedCommands == null && (actionCode.startsWith("NPC") || actionCode.startsWith("CR"))) continue
            // Position card authority filter
            if (actionCode !in ALWAYS_ALLOWED_OFFICER_COMMANDS && !PositionCardRegistry.canExecute(officerCards, actionCode)) continue

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
                    commandGroup = PositionCardRegistry.getCommandGroup(actionCode).name,
                )
            )
        }

        return categories
    }

    fun getNationCommandTable(generalId: Long): Map<String, List<CommandTableEntry>>? {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return null
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null) ?: return null

        val officerCards = officer.getPositionCardEnums()
        // Card-based check: show faction commands if officer has any
        // non-PERSONAL/CAPTAIN card granting faction-level groups
        val hasFactionCardAccess = officerCards.any { card ->
            card.commandGroups.any { it != com.openlogh.model.CommandGroup.PERSONAL && it != com.openlogh.model.CommandGroup.OPERATIONS }
        }
        if (!hasFactionCardAccess) {
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
            // Position card authority filter — Phase 2에서 gin7 81종 PositionCard 매핑으로 완성 예정
            if (actionCode != "Nation휴식" && !PositionCardRegistry.canExecute(officerCards, actionCode)) continue

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
                    commandGroup = PositionCardRegistry.getCommandGroup(actionCode).name,
                )
            )
        }

        return categories
    }

    private fun generalCategory(actionCode: String): String = when (actionCode) {
        "휴식", "요양", "단련", "숙련전환", "견문", "은퇴", "장비매매", "군량매매", "내정특기초기화", "전투특기초기화" -> "개인"
        "농지개간", "상업투자", "기술연구", "수비강화", "성벽보수", "치안강화", "정착장려", "주민선정", "생산감독" -> "내정"
        "물자배분", "함선보급", "함대재편" -> "병참"
        "징병", "모병", "훈련", "사기진작", "출병", "집합", "소집해제", "첩보", "전투태세", "작전수립", "워프항행", "장거리워프", "점거" -> "군사"
        "이동", "강행", "인재탐색", "등용", "등용수락", "귀환", "접경귀환", "임관", "랜덤임관", "장수대상임관" -> "인사"
        "선동", "탈취", "파괴", "화계", "정찰", "통신방해" -> "계략"
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
        "필사즉생", "백성동원", "수몰", "허보", "의병모집", "이호경식", "급습", "피장파장", "작전지시" -> "전략"
        "예산편성" -> "재정"
        "국기변경", "국호변경", "인구이동", "무작위수도이전" -> "기타"
        "극병연구", "대검병연구", "무희연구", "산저병연구", "상병연구", "원융노병연구", "음귀병연구", "화륜차연구", "화시병연구" -> "연구"
        else -> "기타"
    }

    private fun nationCategoryOrder(category: String): Int = when (category) {
        "휴식" -> 1
        "인사" -> 2
        "재정" -> 3
        "외교" -> 4
        "황제" -> 5
        "특수" -> 6
        "전략" -> 7
        "기타" -> 8
        "연구" -> 9
        else -> 99
    }

    companion object {
        private val ALWAYS_ALLOWED_OFFICER_COMMANDS = setOf("휴식", "NPC능동", "CR건국", "CR맹훈련")
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
        val mapName = (world.config["mapCode"] as? String)?.trim().orEmpty().ifBlank { "logh" }
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
