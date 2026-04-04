package com.openlogh.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Nation
import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.StatChangeService
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.repository.CityRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.NationRepository
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.service.MapService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class CommandExecutor @Autowired constructor(
    private val commandRegistry: CommandRegistry,
    private val worldPortFactory: JpaWorldPortFactory,
    private val generalRepository: GeneralRepository,
    private val cityRepository: CityRepository,
    private val nationRepository: NationRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val diplomacyService: DiplomacyService,
    private val mapService: MapService,
    private val statChangeService: StatChangeService,
    private val modifierService: ModifierService,
    private val messageService: com.openlogh.service.MessageService,
) {
    private val mapper = jacksonObjectMapper()

    constructor(
        commandRegistry: CommandRegistry,
        generalRepository: GeneralRepository,
        cityRepository: CityRepository,
        nationRepository: NationRepository,
        diplomacyRepository: DiplomacyRepository,
        diplomacyService: DiplomacyService,
        mapService: MapService,
        statChangeService: StatChangeService,
        modifierService: ModifierService,
        messageService: com.openlogh.service.MessageService,
    ) : this(
        commandRegistry = commandRegistry,
        worldPortFactory = JpaWorldPortFactory(
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
            diplomacyRepository = diplomacyRepository,
        ),
        generalRepository = generalRepository,
        cityRepository = cityRepository,
        nationRepository = nationRepository,
        diplomacyRepository = diplomacyRepository,
        diplomacyService = diplomacyService,
        mapService = mapService,
        statChangeService = statChangeService,
        modifierService = modifierService,
        messageService = messageService,
    )

    suspend fun executeGeneralCommand(
        actionCode: String,
        general: General,
        env: CommandEnv,
        arg: Map<String, Any>? = null,
        city: City? = null,
        nation: Nation? = null,
        rng: Random = Random.Default
    ): CommandResult {
        var effectiveArg = arg
        var effectiveNation = nation
        val schema = commandRegistry.getGeneralSchema(actionCode)
        if (schema != ArgSchema.NONE && !effectiveArg.isNullOrEmpty()) {
            val validated = schema.parse(effectiveArg)
            if (!validated.ok()) {
                val msg = validated.errors.joinToString("; ") { it.message }
                return CommandResult(success = false, logs = listOf("<R>$actionCode</>을(를) 실패하여 휴식합니다. - 인자 오류: $msg"))
            }
            effectiveArg = validated.toLegacyMap(schema)
        }

        val command = commandRegistry.createGeneralCommand(actionCode, general, env, effectiveArg)
        command.city = city
        command.nation = effectiveNation
        command.services = CommandServices(generalRepository, cityRepository, nationRepository, diplomacyService, messageService = messageService, modifierService = modifierService)
        hydrateCommandForConstraintCheck(command, general, env, effectiveArg)

        val cooldown = checkGeneralCooldown(actionCode, general, env)
        if (cooldown != null) {
            return cooldown
        }

        // Check constraints
        val conditionResult = command.checkFullCondition()
        if (conditionResult is ConstraintResult.Fail) {
            val altCode = command.getAlternativeCommand()
            if (altCode != null && altCode != actionCode) {
                return executeGeneralCommand(altCode, general, env, effectiveArg, city, effectiveNation, rng)
            }
            return CommandResult(success = false, logs = listOf("<R>${command.actionName}</>을(를) 실패하여 휴식합니다. - ${conditionResult.reason}"))
        }

        val preReq = command.getPreReqTurn()
        if (preReq > 0) {
            val lastTurn = LastTurn.fromMap(general.lastTurn)
            val stacked = lastTurn.addTermStack(actionCode, effectiveArg, preReq)

            if ((stacked.term ?: 0) < preReq) {
                general.lastTurn = stacked.toMap()
                return CommandResult(
                    success = true,
                    logs = listOf("${command.actionName} 수행중... (${stacked.term}/$preReq)"),
                )
            }
        }

        val result = command.run(rng)
        general.lastTurn = LastTurn(
            command = actionCode,
            arg = effectiveArg,
            term = if (preReq > 0) preReq else null,
        ).toMap()

        // JSON 델타를 엔티티에 적용.
        // 성공/실패 모두 적용 — 계략 실패 등에서도 비용/경험치 변동이 있으므로.
        var finalResult = result
        if (result.message != null) {
            effectiveNation = ensureNationContextForFounding(result.message, general, city, effectiveNation)
            command.nation = effectiveNation
            CommandResultApplicator.apply(
                result.copy(success = true), general, city, effectiveNation,
                destGeneral = command.destGeneral,
                destCity = command.destCity,
                destNation = command.destNation,
            )
        }

        // Post-command hook: check stat level changes (legacy: checkStatChange)
        val statChangeResult = runCatching { statChangeService.checkStatChange(general) }.getOrNull()
        if (statChangeResult?.hasChanges == true) {
            finalResult = result.copy(logs = result.logs + statChangeResult.logs)
        }

        saveModifiedEntities(general, city, effectiveNation, command)

        applyGeneralCooldown(actionCode, command.getPostReqTurn(), general, env)
        return finalResult
    }

    suspend fun executeNationCommand(
        actionCode: String,
        general: General,
        env: CommandEnv,
        arg: Map<String, Any>? = null,
        city: City? = null,
        nation: Nation? = null,
        rng: Random = Random.Default
    ): CommandResult {
        var effectiveArg = arg
        val schema = commandRegistry.getNationSchema(actionCode)
        if (schema != ArgSchema.NONE && !effectiveArg.isNullOrEmpty()) {
            val validated = schema.parse(effectiveArg)
            if (!validated.ok()) {
                val msg = validated.errors.joinToString("; ") { it.message }
                return CommandResult(success = false, logs = listOf("인자 오류: $msg"))
            }
            effectiveArg = validated.toLegacyMap(schema)
        }

        val command = commandRegistry.createNationCommand(actionCode, general, env, effectiveArg)
            ?: return CommandResult(success = false, logs = listOf("<R>$actionCode</> - 알 수 없는 국가 명령"))
        command.city = city
        command.nation = nation
        command.services = CommandServices(generalRepository, cityRepository, nationRepository, diplomacyService, messageService = messageService, modifierService = modifierService)
        hydrateCommandForConstraintCheck(command, general, env, effectiveArg)

        val cooldown = checkNationCooldown(actionCode, general, nation, env)
        if (cooldown != null) {
            return cooldown
        }

        val conditionResult = command.checkFullCondition()
        if (conditionResult is ConstraintResult.Fail) {
            return CommandResult(success = false, logs = listOf("<R>${command.actionName}</> 실패 - ${conditionResult.reason}"))
        }

        val preReq = command.getPreReqTurn()
        if (preReq > 0 && nation != null) {
            val lastTurn = getNationLastTurn(nation, general.officerLevel)
            val stacked = lastTurn.addTermStack(actionCode, effectiveArg, preReq)

            if ((stacked.term ?: 0) < preReq) {
                setNationLastTurn(nation, general.officerLevel, stacked)
                return CommandResult(
                    success = true,
                    logs = listOf("${command.actionName} 수행중... (${stacked.term}/$preReq)"),
                )
            }
        }

        // 국가 커맨드는 run()에서 직접 엔티티를 수정한다
        val result = command.run(rng)
        if (nation != null) {
            setNationLastTurn(
                nation,
                general.officerLevel,
                LastTurn(actionCode, effectiveArg, if (preReq > 0) preReq else null),
            )
        }

        // 수정된 엔티티 저장
        if (result.success) {
            saveModifiedEntities(general, city, nation, command)
        }

        applyNationCooldown(actionCode, command.getPostReqTurn(), general, nation, env)
        return result
    }

    /**
     * 커맨드 실행 후 수정된 엔티티들을 저장한다.
     * JPA dirty check가 불필요한 UPDATE를 방지한다.
     */
    private fun saveModifiedEntities(general: General, city: City?, nation: Nation?, command: BaseCommand) {
        val ports = worldPortFactory.create(general.worldId)
        ports.putGeneral(general.toSnapshot())
        if (city != null) ports.putCity(city.toSnapshot())
        if (nation != null) ports.putNation(nation.toSnapshot())
        if (command.destGeneral != null) ports.putGeneral(command.destGeneral!!.toSnapshot())
        if (command.destCity != null) ports.putCity(command.destCity!!.toSnapshot())
        if (command.destNation != null) ports.putNation(command.destNation!!.toSnapshot())
        // Save dest city generals (for sabotage injury effects)
        command.destCityGenerals?.forEach { ports.putGeneral(it.toSnapshot()) }
    }

    private fun toTurnIndex(env: CommandEnv): Int {
        return env.year * 12 + env.month
    }

    private fun checkGeneralCooldown(actionCode: String, general: General, env: CommandEnv): CommandResult? {
        val nextExecuteMap = parseIntMap(general.meta[GENERAL_NEXT_EXECUTE_KEY])
        val blockedUntil = nextExecuteMap[actionCode] ?: return null
        val nowTurn = toTurnIndex(env)
        if (nowTurn < blockedUntil) {
            val remain = blockedUntil - nowTurn
            return CommandResult(
                success = false,
                logs = listOf("<R>$actionCode</>을(를) 실패하여 휴식합니다. - 쿨다운 중 (${remain}턴 남음)"),
            )
        }
        return null
    }

    private fun applyGeneralCooldown(actionCode: String, postReqTurn: Int, general: General, env: CommandEnv) {
        if (postReqTurn <= 0) return
        val map = parseIntMap(general.meta[GENERAL_NEXT_EXECUTE_KEY])
        map[actionCode] = toTurnIndex(env) + postReqTurn
        general.meta[GENERAL_NEXT_EXECUTE_KEY] = mapToMetaValueMap(map)
    }

    private fun checkNationCooldown(
        actionCode: String,
        general: General,
        nation: Nation?,
        env: CommandEnv,
    ): CommandResult? {
        if (nation == null) return null
        val key = nationCooldownKey(general.officerLevel)
        val nextExecuteMap = parseIntMap(nation.meta[key])
        val blockedUntil = nextExecuteMap[actionCode] ?: return null
        val nowTurn = toTurnIndex(env)
        if (nowTurn < blockedUntil) {
            val remain = blockedUntil - nowTurn
            return CommandResult(
                success = false,
                logs = listOf("<R>$actionCode</> 실패 - 쿨다운 중 (${remain}턴 남음)"),
            )
        }
        return null
    }

    private fun applyNationCooldown(
        actionCode: String,
        postReqTurn: Int,
        general: General,
        nation: Nation?,
        env: CommandEnv,
    ) {
        if (nation == null || postReqTurn <= 0) return
        val key = nationCooldownKey(general.officerLevel)
        val map = parseIntMap(nation.meta[key])
        map[actionCode] = toTurnIndex(env) + postReqTurn
        nation.meta[key] = mapToMetaValueMap(map)
    }

    private fun getNationLastTurn(nation: Nation, officerLevel: Short): LastTurn {
        val key = nationLastTurnKey(officerLevel)
        val raw = readStringAnyMap(nation.meta[key])
        return LastTurn.fromMap(raw)
    }

    private fun setNationLastTurn(nation: Nation, officerLevel: Short, lastTurn: LastTurn) {
        val key = nationLastTurnKey(officerLevel)
        nation.meta[key] = lastTurn.toMap()
    }

    private fun nationLastTurnKey(officerLevel: Short): String {
        return "turn_last_$officerLevel"
    }

    private fun nationCooldownKey(officerLevel: Short): String {
        return "turn_next_$officerLevel"
    }

    private fun applyDestinationContext(command: BaseCommand, worldId: Long, arg: Map<String, Any>?) {
        if (arg == null) return
        val ports = worldPortFactory.create(worldId)

        var destCity = extractLong(
            arg,
            "destCityId",
            "destCityID",
            "cityId",
            "targetCityId",
        )?.let { ports.city(it)?.toEntity() }

        var destNation = extractLong(
            arg,
            "destNationId",
            "destNationID",
            "targetNationId",
            "nationId",
        )?.let { ports.nation(it)?.toEntity() }

        var destGeneral = extractLong(
            arg,
            "destGeneralID",
            "destGeneralId",
            "targetGeneralId",
            "generalId",
        )?.let { ports.general(it)?.toEntity() }

        if (destCity == null && destGeneral != null) {
            destCity = ports.city(destGeneral.cityId)?.toEntity()
        }
        if (destNation == null && destGeneral != null && destGeneral.nationId != 0L) {
            destNation = ports.nation(destGeneral.nationId)?.toEntity()
        }
        if (destNation == null && destCity != null && destCity.nationId != 0L) {
            destNation = ports.nation(destCity.nationId)?.toEntity()
        }

        if (destGeneral == null && destNation != null) {
            if (destNation.chiefGeneralId > 0L) {
                destGeneral = ports.general(destNation.chiefGeneralId)?.toEntity()
            }
            if (destGeneral == null) {
                destGeneral = ports.generalsByNation(destNation.id).map { it.toEntity() }
                    .maxByOrNull { it.officerLevel }
            }
        }

        command.destCity = destCity
        command.destNation = destNation
        command.destGeneral = destGeneral
    }

    fun hydrateCommandForConstraintCheck(
        command: BaseCommand,
        general: General,
        env: CommandEnv,
        arg: Map<String, Any>?,
    ) {
        applyDestinationContext(command, env.worldId, arg)
        command.constraintEnv = buildConstraintEnv(general, env)
        // Inject action modifiers for onCalcDomestic/onCalcStat usage in commands
        val nation = command.nation
        command.modifiers = modifierService.getModifiers(general, nation)

        // Load dest city generals for sabotage defence calculations and injury effects
        val destCity = command.destCity
        if (destCity != null) {
            val ports = worldPortFactory.create(env.worldId)
            command.destCityGenerals = ports.generalsByCity(destCity.id).map { it.toEntity() }
                .filter { it.id != general.id }
        }
    }

    private fun buildConstraintEnv(general: General, env: CommandEnv): Map<String, Any> {
        val worldId = env.worldId
        val mapName = (env.gameStor["mapName"] as? String) ?: "che"
        val ports = worldPortFactory.create(worldId)

        val allCities = ports.allCities().map { it.toEntity() }
        val cityNationById = allCities.associate { it.id to it.nationId }
        val citySupplyStateById = allCities.associate { it.id to it.supplyState.toInt() }

        // DB city.id ↔ mapCityId bidirectional mapping
        // mapAdjacency keys are mapCityId (from map JSON: 1,2,3...)
        // DB city.id is auto-generated (1129, 1146...) — they don't match
        val dbToMapId = allCities.associate { it.id to it.mapCityId.toLong() }
        val mapToDbId = allCities.associate { it.mapCityId.toLong() to it.id }
        val cityNationByMapId = allCities.associate { it.mapCityId.toLong() to it.nationId }

        val allGenerals = ports.allGenerals().map { it.toEntity() }
        val totalNpcCount = allGenerals.count { it.npcState.toInt() > 0 }
        val totalGeneralCount = allGenerals.size - totalNpcCount

        val mapAdjacency = try {
            mapService.getCities(mapName).associate { cityConst ->
                cityConst.id.toLong() to cityConst.connections.map { it.toLong() }
            }
        } catch (_: Exception) {
            emptyMap()
        }

        val troopMemberExistsByTroopId = ports.allGenerals().map { it.toEntity() }
            .asSequence()
            .filter { it.troopId > 0L }
            .groupBy { it.troopId }
            .mapValues { (_, members) -> members.any { m -> m.id != m.troopId } }

        val atWarNationIds = if (general.nationId == 0L) {
            emptySet()
        } else {
            ports.activeDiplomacies().map { it.toEntity() }
                .asSequence()
                .filter { it.stateCode == "선전포고" || it.stateCode == "전쟁" }
                .mapNotNull {
                    when (general.nationId) {
                        it.srcNationId -> it.destNationId
                        it.destNationId -> it.srcNationId
                        else -> null
                    }
                }
                .toSet()
        }

        val openingPartYears = (env.gameStor["openingPartYears"] as? Number)?.toInt()
            ?: (env.gameStor["openingPartYear"] as? Number)?.toInt()
            ?: 3
        val joinActionLimit = (env.gameStor["joinActionLimit"] as? Number)?.toInt() ?: 12

        val wanderingEmperor = allGenerals.firstOrNull {
            it.npcState.toInt() == 10 && (it.meta["emperorStatus"] as? String) == "wandering"
        }

        val result = mutableMapOf<String, Any>(
            "worldId" to worldId,
            "mapName" to mapName,
            "openingPartYears" to openingPartYears,
            "mapAdjacency" to mapAdjacency,
            "cityNationById" to cityNationById,
            "cityNationByMapId" to cityNationByMapId,
            "dbToMapId" to dbToMapId,
            "mapToDbId" to mapToDbId,
            "citySupplyStateById" to citySupplyStateById,
            "totalGeneralCount" to totalGeneralCount,
            "totalNpcCount" to totalNpcCount,
            "troopMemberExistsByTroopId" to troopMemberExistsByTroopId,
            "atWarNationIds" to atWarNationIds,
            "joinActionLimit" to joinActionLimit,
        )
        if (wanderingEmperor != null) {
            result["wanderingEmperorCityId"] = wanderingEmperor.cityId
            result["wanderingEmperorGeneralId"] = wanderingEmperor.id
        }
        return result
    }

    private fun ensureNationContextForFounding(
        message: String?,
        general: General,
        city: City?,
        nation: Nation?,
    ): Nation? {
        if (message.isNullOrBlank()) return nation

        val json = runCatching { mapper.readValue<Map<String, Any>>(message) }.getOrNull() ?: return nation
        val nationChanges = readStringAnyMap(json["nationChanges"])
        val nationFoundation = readStringAnyMap(json["nationFoundation"])
        val cityChanges = readStringAnyMap(json["cityChanges"])
        val findRandomCity = readStringAnyMap(json["findRandomCity"])
        val claimCity = readBooleanValue(cityChanges["claimCity"]) == true
        val moveAllNationGenerals = readBooleanValue(json["moveAllNationGenerals"]) == true

        var effectiveNation = nation
        if (effectiveNation == null) {
            val createWandering = readBooleanValue(nationChanges["createWanderingNation"]) == true
            val foundNation = readBooleanValue(nationChanges["foundNation"]) == true || nationFoundation.isNotEmpty()

            if (createWandering || foundNation) {
                val nationName = (nationChanges["nationName"] as? String)
                    ?: (nationFoundation["name"] as? String)
                    ?: general.name
                val nationTypeRaw = (nationChanges["nationType"] as? String)
                    ?: (nationFoundation["type"] as? String)
                val capitalCityId = readIntValue(nationChanges["capital"])?.toLong()
                    ?: readIntValue(nationFoundation["capitalCityId"])?.toLong()
                    ?: general.cityId
                val colorType = readIntValue(nationChanges["colorType"])
                    ?: readIntValue(nationFoundation["colorType"])
                val level = readIntValue(nationChanges["level"])
                    ?: if (createWandering) 0 else 1
                val secretLimit = readIntValue(nationChanges["secretLimit"]) ?: 3

                val createdNation = nationRepository.save(
                    Nation(
                        worldId = general.worldId,
                        name = nationName,
                        color = resolveNationColor(colorType),
                        capitalCityId = capitalCityId,
                        chiefGeneralId = general.id,
                        secretLimit = secretLimit.toShort(),
                        level = level.toShort(),
                        typeCode = resolveNationTypeCode(nationTypeRaw, createWandering),
                        gennum = 1,
                    )
                )

                val aux = readStringAnyMap(nationChanges["aux"]).toMutableMap()
                readIntValue(nationChanges["can_국기변경"])?.let { aux["can_국기변경"] = it }
                readIntValue(nationChanges["can_무작위수도이전"])?.let { aux["can_무작위수도이전"] = it }
                readIntValue(nationFoundation["can_국기변경"])?.let { aux["can_국기변경"] = it }
                if (aux.isNotEmpty()) {
                    createdNation.meta.putAll(aux)
                }

                effectiveNation = createdNation
                general.nationId = createdNation.id
                if (general.officerLevel < 20) {
                    general.officerLevel = 20
                }
            }
        }

        if (effectiveNation != null && nationFoundation.isNotEmpty()) {
            (nationFoundation["name"] as? String)?.takeIf { it.isNotBlank() }?.let { effectiveNation.name = it }
            (nationFoundation["type"] as? String)?.let { effectiveNation.typeCode = resolveNationTypeCode(it, false) }
            readIntValue(nationFoundation["colorType"])?.let { effectiveNation.color = resolveNationColor(it) }
            readIntValue(nationFoundation["capitalCityId"])?.toLong()?.let { effectiveNation.capitalCityId = it }
            readIntValue(nationFoundation["can_국기변경"])?.let { effectiveNation.meta["can_국기변경"] = it }
        }

        if (effectiveNation != null) {
            val randomFoundingCity = resolveRandomFoundingCity(findRandomCity, general.worldId)
            if (randomFoundingCity != null) {
                effectiveNation.capitalCityId = randomFoundingCity.id
                randomFoundingCity.nationId = effectiveNation.id
                if (city?.id == randomFoundingCity.id) {
                    city.nationId = effectiveNation.id
                } else {
                    cityRepository.save(randomFoundingCity)
                }

                if (moveAllNationGenerals) {
                    val nationGenerals = generalRepository.findByWorldIdAndNationId(general.worldId, effectiveNation.id)
                    nationGenerals.forEach { it.cityId = randomFoundingCity.id }
                    general.cityId = randomFoundingCity.id
                    if (nationGenerals.isNotEmpty()) {
                        generalRepository.saveAll(nationGenerals)
                    }
                }
            }
        }

        if (claimCity && city != null) {
            val nationIdForClaim = effectiveNation?.id ?: general.nationId
            if (nationIdForClaim > 0L) {
                city.nationId = nationIdForClaim
            }
        }

        return effectiveNation
    }

    private fun readBooleanValue(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun readIntValue(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun resolveNationTypeCode(raw: String?, createWandering: Boolean): String {
        if (createWandering) return "che_중립"
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return "che_군벌"
        return if (normalized.startsWith("che_")) normalized else "che_$normalized"
    }

    private fun resolveNationColor(colorType: Int?): String {
        if (colorType == null) return NATION_COLORS.first()
        return NATION_COLORS.getOrElse(colorType) { NATION_COLORS.first() }
    }

    private fun resolveRandomFoundingCity(findRandomCity: Map<String, Any>, worldId: Long): City? {
        if (findRandomCity.isEmpty()) return null
        val query = (findRandomCity["query"] as? String)?.trim().orEmpty()
        if (query.isNotBlank() && query != "neutral_constructable") {
            return null
        }

        val levelMin = readIntValue(findRandomCity["levelMin"]) ?: 5
        val levelMax = readIntValue(findRandomCity["levelMax"]) ?: 6
        val candidates = cityRepository.findByWorldId(worldId).filter { city ->
            city.nationId == 0L && city.level.toInt() in levelMin..levelMax
        }
        if (candidates.isEmpty()) return null
        return candidates.random()
    }

    private fun extractLong(arg: Map<String, Any>, vararg keys: String): Long? {
        for (key in keys) {
            val raw = arg[key] ?: continue
            val parsed = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseIntMap(raw: Any?): MutableMap<String, Int> {
        if (raw !is Map<*, *>) return mutableMapOf()
        val result = mutableMapOf<String, Int>()
        raw.forEach { (k, v) ->
            if (k is String) {
                val num = (v as? Number)?.toInt()
                if (num != null) {
                    result[k] = num
                }
            }
        }
        return result
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Any>()
        raw.forEach { (k, v) ->
            if (k is String && v != null) {
                result[k] = v
            }
        }
        return result
    }

    private fun mapToMetaValueMap(values: Map<String, Int>): MutableMap<String, Any> {
        val result = mutableMapOf<String, Any>()
        values.forEach { (key, value) -> result[key] = value }
        return result
    }

    companion object {
        private const val GENERAL_NEXT_EXECUTE_KEY = "next_execute"

        private val NATION_COLORS = listOf(
            "#FF0000", "#800000", "#A0522D", "#FF6347", "#FFA500",
            "#FFDAB9", "#FFD700", "#FFFF00", "#7CFC00", "#00FF00",
            "#808000", "#008000", "#2E8B57", "#008080", "#20B2AA",
            "#6495ED", "#7FFFD4", "#AFEEEE", "#87CEEB", "#00FFFF",
            "#00BFFF", "#0000FF", "#000080", "#483D8B", "#7B68EE",
            "#BA55D3", "#800080", "#FF00FF", "#FFC0CB", "#F5F5DC",
            "#E0FFFF", "#FFFFFF", "#A9A9A9",
        )
    }
}
