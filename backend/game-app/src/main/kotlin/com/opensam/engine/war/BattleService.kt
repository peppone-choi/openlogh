package com.opensam.engine.war

import com.opensam.engine.DeterministicRng
import com.opensam.engine.DiplomacyService
import com.opensam.engine.EmperorConstants
import com.opensam.engine.EventService
import com.opensam.engine.modifier.ActionModifier
import com.opensam.engine.modifier.ModifierService
import com.opensam.engine.modifier.StatContext
import com.opensam.entity.City
import com.opensam.model.CrewType
import com.opensam.entity.General
import com.opensam.entity.Message
import com.opensam.entity.Record
import com.opensam.entity.WorldState
import com.opensam.entity.OldNation
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.NationTurnRepository
import com.opensam.repository.OldNationRepository
import com.opensam.repository.RecordRepository
import com.opensam.repository.TroopRepository
import com.opensam.service.GameConstService
import com.opensam.service.HistoryService
import com.opensam.service.InheritanceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.roundToInt
import kotlin.random.Random

@Service
class BattleService(
    private val cityRepository: CityRepository,
    private val generalRepository: GeneralRepository,
    private val nationRepository: NationRepository,
    private val messageRepository: MessageRepository,
    private val recordRepository: RecordRepository,
    private val oldNationRepository: OldNationRepository,
    private val troopRepository: TroopRepository,
    private val nationTurnRepository: NationTurnRepository,
    private val eventService: EventService,
    private val diplomacyService: DiplomacyService,
    private val modifierService: ModifierService,
    private val gameConstService: GameConstService,
    private val historyService: HistoryService,
    private val inheritanceService: InheritanceService,
) {
    private val logger = LoggerFactory.getLogger(BattleService::class.java)
    private val battleEngine = BattleEngine()

    companion object {
        /** Legacy GameConst values */
        const val BASE_GOLD = 0
        const val BASE_RICE = 2000
        const val JOIN_RUINED_NPC_PROP = 0.1
        const val NPC_JOIN_MAX_DELAY = 12  // turns

        /** NPC states eligible for auto-join (npcState 2-8 except 5) */
        val NPC_AUTO_JOIN_STATES = setOf<Short>(2, 3, 4, 6, 7, 8)
    }

    @Transactional
    fun executeBattle(
        attacker: General,
        targetCity: City,
        world: WorldState,
    ): BattleResult {
        if (attacker.npcState == EmperorConstants.NPC_STATE_EMPEROR) {
            return BattleResult(attackerWon = false)
        }

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "default"
        val rng = DeterministicRng.create(
            hiddenSeed, "ConquerCity",
            world.currentYear, world.currentMonth,
            attacker.nationId, attacker.id, targetCity.id
        )

        val attackerNation = nationRepository.findById(attacker.nationId).orElse(null)
        val attackerUnit = WarUnitGeneral(
            attacker,
            nationTech = attackerNation?.tech ?: 0f,
            isAttacker = true,
            cityLevel = targetCity.level.toInt(),
            capitalCityId = attackerNation?.capitalCityId ?: 0,
        )
        val attackerModifiers = modifierService.getModifiers(attacker, attackerNation)

        // Get defenders in the city
        val defenderEntries = generalRepository.findByCityId(targetCity.id)
            .filter {
                it.nationId == targetCity.nationId &&
                    it.crew > 0 &&
                    it.npcState != EmperorConstants.NPC_STATE_EMPEROR
            }
            .map { gen ->
                val defNation = nationRepository.findById(gen.nationId).orElse(null)
                val unit = WarUnitGeneral(
                    gen,
                    nationTech = defNation?.tech ?: 0f,
                    isAttacker = false,
                    cityLevel = targetCity.level.toInt(),
                    capitalCityId = defNation?.capitalCityId ?: 0,
                )
                val modifiers = modifierService.getModifiers(gen, defNation)
                Triple(unit, gen, modifiers)
            }

        val primaryDefender = defenderEntries.firstOrNull()
        applyWarModifiers(
            unit = attackerUnit,
            modifiers = attackerModifiers,
            opponentCrewType = primaryDefender?.first?.crewType?.toString().orEmpty(),
            opposeModifiers = primaryDefender?.third ?: emptyList(),
            isAttacker = true,
        )

        for ((unit, _, modifiers) in defenderEntries) {
            applyWarModifiers(
                unit = unit,
                modifiers = modifiers,
                opponentCrewType = attackerUnit.crewType.toString(),
                opposeModifiers = attackerModifiers,
                isAttacker = false,
            )
        }

        val defenders = defenderEntries.map { it.first }

        val result = battleEngine.resolveBattle(attackerUnit, defenders, targetCity, rng)

        // Handle city occupation
        if (result.cityOccupied) {
            occupyCity(targetCity, attacker, world, rng)
        }

        // Legacy: dead split 0.4 to attacker's city, 0.6 to defender's city
        val totalDead = result.attackerDamageDealt + result.defenderDamageDealt
        if (totalDead > 0) {
            val attackerCity = cityRepository.findById(attacker.cityId).orElse(null)
            if (attackerCity != null) {
                attackerCity.dead += (totalDead * 0.4).toInt()
                cityRepository.save(attackerCity)
            }
            targetCity.dead += (totalDead * 0.6).toInt()
        }

        cityRepository.save(targetCity)
        generalRepository.save(attacker)
        defenders.forEach { it.general.let { gen -> generalRepository.save(gen) } }

        // ── Battle log persistence (legacy parity: pushBattleResultTemplate) ──
        persistBattleLogs(
            world = world,
            attacker = attacker,
            attackerUnit = attackerUnit,
            defenders = defenderEntries.map { it.first },
            targetCity = targetCity,
            result = result,
        )

        inheritanceService.accruePoints(attacker, "combat", 1)
        defenders.forEach { inheritanceService.accruePoints(it.general, "combat", 1) }

        return result
    }

    private fun persistBattleLogs(
        world: WorldState,
        attacker: General,
        attackerUnit: WarUnitGeneral,
        defenders: List<WarUnitGeneral>,
        targetCity: City,
        result: BattleResult,
    ) {
        val worldId = world.id.toLong()
        val year = world.currentYear.toInt()
        val month = world.currentMonth.toInt()
        val attackerCrewType = CrewType.fromCode(attacker.crewType.toInt())

        val messages = mutableListOf<Message>()

        fun buildSummary(
            me: General, meUnit: WarUnitGeneral, meCrewType: CrewType?,
            opp: String, oppCrewType: CrewType?, oppRemain: Int, oppKilled: Int,
            warTypeStr: String,
        ): String {
            val meName = me.name
            val meTypeName = meCrewType?.displayName ?: "병종${me.crewType}"
            val meRemain = meUnit.hp
            val meKilled = -(meUnit.maxHp - meUnit.hp)
            val oppTypeName = oppCrewType?.displayName ?: "?"
            return "【${meTypeName}】${meName} ${meRemain}(${meKilled}) ${warTypeStr} 【${oppTypeName}】${opp} ${oppRemain}(${-oppKilled})"
        }

        val primaryDefender = defenders.firstOrNull()
        val defenderName = primaryDefender?.general?.name ?: targetCity.name
        val defenderCrewType = primaryDefender?.let { CrewType.fromCode(it.crewType) }
        val defenderRemain = primaryDefender?.hp ?: 0
        val defenderKilled = primaryDefender?.let { it.maxHp - it.hp } ?: 0

        val attackSummary = buildSummary(
            me = attacker, meUnit = attackerUnit, meCrewType = attackerCrewType,
            opp = defenderName, oppCrewType = defenderCrewType,
            oppRemain = defenderRemain, oppKilled = defenderKilled,
            warTypeStr = "→",
        )

        // battle_result for attacker (legacy: pushGeneralBattleResultLog)
        messages += Message(
            worldId = worldId,
            mailboxCode = "battle_result",
            messageType = "log",
            srcId = attacker.id,
            destId = attacker.id,
            payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$attackSummary", "year" to year, "month" to month),
        )

        // battle_detail for attacker — all phase logs (legacy: pushGeneralBattleDetailLog)
        val detailText = result.attackerLogs.joinToString("\n")
        if (detailText.isNotBlank()) {
            messages += Message(
                worldId = worldId,
                mailboxCode = "battle_detail",
                messageType = "log",
                srcId = attacker.id,
                destId = attacker.id,
                payload = mutableMapOf("message" to detailText, "year" to year, "month" to month),
            )
        }

        // general_action for attacker (legacy: pushBattleResultTemplate also pushes to generalActionLog)
        messages += Message(
            worldId = worldId,
            mailboxCode = "general_action",
            messageType = "log",
            srcId = attacker.id,
            destId = attacker.id,
            payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$attackSummary", "year" to year, "month" to month),
        )

        // Defender battle logs (for each general defender)
        for (defUnit in defenders) {
            val defGen = defUnit.general
            val defCrewType = CrewType.fromCode(defGen.crewType.toInt())

            val defenseSummary = buildSummary(
                me = defGen, meUnit = defUnit, meCrewType = defCrewType,
                opp = attacker.name, oppCrewType = attackerCrewType,
                oppRemain = attackerUnit.hp, oppKilled = attackerUnit.maxHp - attackerUnit.hp,
                warTypeStr = "←",
            )

            messages += Message(
                worldId = worldId,
                mailboxCode = "battle_result",
                messageType = "log",
                srcId = defGen.id,
                destId = defGen.id,
                payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$defenseSummary", "year" to year, "month" to month),
            )

            messages += Message(
                worldId = worldId,
                mailboxCode = "battle_detail",
                messageType = "log",
                srcId = defGen.id,
                destId = defGen.id,
                payload = mutableMapOf("message" to detailText, "year" to year, "month" to month),
            )

            messages += Message(
                worldId = worldId,
                mailboxCode = "general_action",
                messageType = "log",
                srcId = defGen.id,
                destId = defGen.id,
                payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$defenseSummary", "year" to year, "month" to month),
            )
        }

        // world_record: global action log (legacy: pushGlobalActionLog for battle)
        val globalSummary = "${attacker.name}이(가) ${targetCity.name}에서 전투 (${if (result.attackerWon) "승리" else "패배"})"
        messages += Message(
            worldId = worldId,
            mailboxCode = "world_record",
            messageType = "log",
            srcId = attacker.id,
            payload = mutableMapOf("message" to "<C>●</>$month 월:$globalSummary", "year" to year, "month" to month),
        )

        if (messages.isNotEmpty()) {
            messageRepository.saveAll(messages)

            // Dual-write to records table so FrontInfoService and history page can read battle logs
            val records = messages.map { msg ->
                Record(
                    worldId = msg.worldId,
                    recordType = msg.mailboxCode,
                    srcId = msg.srcId,
                    destId = msg.destId,
                    year = (msg.payload["year"] as? Int) ?: year,
                    month = (msg.payload["month"] as? Int) ?: month,
                    payload = msg.payload,
                )
            }
            recordRepository.saveAll(records)
        }
    }

    private fun occupyCity(city: City, attacker: General, world: WorldState, rng: Random) {
        val oldNationId = city.nationId
        val conquerNationId = resolveConquerNationId(city, attacker.nationId)
        city.nationId = conquerNationId
        city.trust = 0F

        // Reset city post-occupation (legacy: supply=1, term=0, conflict={}, officer_set=0)
        city.supplyState = 1
        city.term = 0
        city.conflict = mutableMapOf()
        city.officerSet = 0

        // Reduce agri/comm/secu by 30% (legacy: multiply by 0.7)
        city.agri = (city.agri * 0.7).toInt()
        city.comm = (city.comm * 0.7).toInt()
        city.secu = (city.secu * 0.7).toInt()

        // Legacy: city level > 3 → set def/wall to defaultCityWall; else def_max/2, wall_max/2
        val defaultCityWall = gameConstService.getInt("defaultCityWall")
        if (city.level > 3) {
            city.def = defaultCityWall
            city.wall = defaultCityWall
        } else {
            city.def = (city.defMax / 2.0).roundToInt()
            city.wall = (city.wallMax / 2.0).roundToInt()
        }

        // Dispatch OCCUPY_CITY event
        eventService.dispatchEvents(world, "OCCUPY_CITY")

        // Log conquest
        logConquest(city, attacker, world)

        // Demote city officers of the old nation in this city
        demoteCityOfficers(city.id, oldNationId)

        // Check if old nation lost capital or is destroyed
        val oldNation = nationRepository.findById(oldNationId).orElse(null)
        if (oldNation != null) {
            val remainingCities = cityRepository.findByNationId(oldNationId)
                .filter { it.id != city.id }

            if (remainingCities.isEmpty()) {
                // Nation destroyed
                destroyNation(oldNationId, attacker, world, rng)
            } else if (oldNation.capitalCityId == city.id) {
                // Capital lost - relocate
                relocateCapital(oldNation, remainingCities, world)
            }
        }

        // Update conflict tracking
        val conflictMap = city.conflict.toMutableMap()
        val attackerKey = attacker.nationId.toString()
        val currentScore = (conflictMap[attackerKey] as? Number)?.toInt() ?: 0
        conflictMap[attackerKey] = currentScore + 1
        city.conflict = conflictMap
    }

    private fun resolveConquerNationId(city: City, attackerNationId: Long): Long {
        val conflictMap = city.conflict
        if (conflictMap.isEmpty()) {
            return attackerNationId
        }

        val cityWinner = listOf(city.id.toString(), city.mapCityId.toString())
            .firstNotNullOfOrNull { key ->
                val value = conflictMap[key] ?: return@firstNotNullOfOrNull null
                when (value) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> null
                }
            }
        if (cityWinner != null && cityWinner > 0) {
            return cityWinner
        }

        val legacyWinner = conflictMap.keys.firstNotNullOfOrNull { it.toLongOrNull() }
        if (legacyWinner != null && legacyWinner > 0) {
            return legacyWinner
        }

        val valueWinner = conflictMap.values.firstNotNullOfOrNull { value ->
            when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }
        if (valueWinner != null && valueWinner > 0) {
            return valueWinner
        }

        return attackerNationId
    }

    // Legacy parity: func.php deleteNation() — hard-delete nation after archiving
    private fun destroyNation(
        destroyedNationId: Long,
        attacker: General,
        world: WorldState,
        rng: Random,
    ) {
        val destroyedNation = nationRepository.findById(destroyedNationId).orElse(null) ?: return
        logger.info("Nation {} ({}) destroyed by nation {}", destroyedNation.name, destroyedNationId, attacker.nationId)

        val generals = generalRepository.findByNationId(destroyedNationId)
        var totalGoldLoss = 0
        var totalRiceLoss = 0

        // Apply losses to all defender generals (legacy: 20-50% gold/rice, -10% exp, -50% dedication)
        for (gen in generals) {
            val lossRatio = 0.2 + rng.nextDouble() * 0.3  // 20-50%
            val goldLoss = (gen.gold * lossRatio).toInt()
            val riceLoss = (gen.rice * lossRatio).toInt()
            val expLoss = (gen.experience * 0.1).toInt()
            val dedLoss = (gen.dedication * 0.5).toInt()

            gen.gold -= goldLoss
            gen.rice -= riceLoss
            gen.experience -= expLoss
            gen.dedication -= dedLoss

            totalGoldLoss += goldLoss
            totalRiceLoss += riceLoss

            // Release from nation (legacy: nation=0, officer_level=0, officer_city=0, belong=0, troop=0)
            gen.nationId = 0
            gen.officerLevel = 0
            gen.officerCity = 0
            gen.belong = 0
            gen.troopId = 0

            // NPC auto-join to attacker nation (legacy: npcState 2-8 except 5, gated by joinRuinedNPCProp)
            if (gen.npcState in NPC_AUTO_JOIN_STATES && rng.nextDouble() < JOIN_RUINED_NPC_PROP) {
                val delay = rng.nextInt(0, NPC_JOIN_MAX_DELAY + 1)
                gen.meta["autoJoinNationId"] = attacker.nationId
                gen.meta["autoJoinDelay"] = delay
            }

            generalRepository.save(gen)
        }

        // Distribute conquest rewards to attacker nation
        // Legacy: half of nation gold/rice above base + half of general losses
        val nationGoldAboveBase = maxOf(0, destroyedNation.gold - BASE_GOLD)
        val nationRiceAboveBase = maxOf(0, destroyedNation.rice - BASE_RICE)
        val goldReward = nationGoldAboveBase / 2 + totalGoldLoss / 2
        val riceReward = nationRiceAboveBase / 2 + totalRiceLoss / 2

        val attackerNation = nationRepository.findById(attacker.nationId).orElse(null)
        if (attackerNation != null && (goldReward > 0 || riceReward > 0)) {
            attackerNation.gold += goldReward
            attackerNation.rice += riceReward
            nationRepository.save(attackerNation)

            // Log reward to all chiefs (officer_level >= 5)
            logConquestReward(world, attacker.nationId, destroyedNation.name, goldReward, riceReward)
        }

        logNationDestroyed(world, attackerNation?.name ?: attacker.name, attacker.nationId, destroyedNation.name)

        // Legacy: set all cities to neutral (nation=0, front=0)
        val cities = cityRepository.findByNationId(destroyedNationId)
        for (city in cities) {
            city.nationId = 0
            city.frontState = 0
            cityRepository.save(city)
        }

        // Legacy: delete all troops of the nation
        val troops = troopRepository.findByNationId(destroyedNationId)
        if (troops.isNotEmpty()) {
            troopRepository.deleteAll(troops)
        }

        // Kill all diplomatic relations
        diplomacyService.killAllRelationsForNation(world.id.toLong(), destroyedNationId)

        // Legacy: delete nation turns
        nationTurnRepository.deleteByNationId(destroyedNationId)

        // Legacy: archive nation data to old_nation before deletion
        oldNationRepository.save(
            OldNation(
                serverId = world.id.toString(),
                nation = destroyedNationId,
                data = mutableMapOf(
                    "name" to destroyedNation.name,
                    "color" to destroyedNation.color,
                    "level" to destroyedNation.level,
                    "gold" to destroyedNation.gold,
                    "rice" to destroyedNation.rice,
                    "tech" to destroyedNation.tech,
                    "gennum" to destroyedNation.gennum,
                    "generals" to generals.map { it.id },
                ),
                date = java.time.OffsetDateTime.now(),
            )
        )

        // Legacy: hard-delete nation from DB (not soft delete)
        nationRepository.delete(destroyedNation)

        eventService.dispatchEvents(world, "DESTROY_NATION")
    }

    /**
     * Relocate capital when the capital city is captured but nation survives.
     * Legacy: pick closest city with highest pop, halve nation gold/rice, 20% morale loss.
     */
    private fun relocateCapital(
        nation: com.opensam.entity.Nation,
        remainingCities: List<City>,
        world: WorldState,
    ) {
        val newCapital = remainingCities.maxByOrNull { it.pop } ?: return

        logger.info("Nation {} relocates capital to {}", nation.name, newCapital.name)

        nation.capitalCityId = newCapital.id
        nation.gold /= 2
        nation.rice /= 2
        nationRepository.save(nation)

        // 20% morale loss to all generals
        val nationals = generalRepository.findByNationId(nation.id)
        for (gen in nationals) {
            gen.atmos = (gen.atmos * 0.8).toInt().toShort()
            if (gen.officerLevel >= 5) {
                gen.cityId = newCapital.id
            }
            generalRepository.save(gen)
        }

        // Log emergency relocation
        messageRepository.save(
            Message(
                worldId = world.id.toLong(),
                mailboxCode = "national",
                messageType = "capital_relocated",
                destId = nation.id,
                payload = mutableMapOf(
                    "nationName" to nation.name,
                    "newCapital" to newCapital.name,
                    "year" to world.currentYear.toInt(),
                    "month" to world.currentMonth.toInt(),
                ),
            )
        )
    }

    /**
     * Demote city officers (태수/군사/종사) to regular generals when city is captured.
     * Legacy: officer_level = 1, officer_city = 0 for generals who had officer_city == capturedCityId.
     */
    private fun demoteCityOfficers(cityId: Long, oldNationId: Long) {
        val generals = generalRepository.findByCityId(cityId)
            .filter { it.nationId == oldNationId && it.officerCity == cityId.toInt() }
        for (gen in generals) {
            gen.officerLevel = 1
            gen.officerCity = 0
            generalRepository.save(gen)
        }
    }

    private fun logConquest(city: City, attacker: General, world: WorldState) {
        val nation = nationRepository.findById(attacker.nationId).orElse(null)
        val message = if (nation != null) {
            "${nation.name}의 ${attacker.name}이(가) ${city.name}을(를) 점령하였습니다"
        } else {
            "${attacker.name}이(가) ${city.name}을(를) 점령하였습니다"
        }
        historyService.logWorldHistory(
            worldId = world.id.toLong(),
            message = message,
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
        )
    }

    private fun logConquestReward(
        world: WorldState,
        attackerNationId: Long,
        destroyedNationName: String,
        goldReward: Int,
        riceReward: Int,
    ) {
        messageRepository.save(
            Message(
                worldId = world.id.toLong(),
                mailboxCode = "national",
                messageType = "conquest_reward",
                destId = attackerNationId,
                payload = mutableMapOf(
                    "destroyedNation" to destroyedNationName,
                    "goldReward" to goldReward,
                    "riceReward" to riceReward,
                    "year" to world.currentYear.toInt(),
                    "month" to world.currentMonth.toInt(),
                ),
            )
        )
    }

    private fun logNationDestroyed(
        world: WorldState,
        attackerNationName: String,
        attackerNationId: Long,
        destroyedNationName: String,
    ) {
        val year = world.currentYear.toInt()
        val month = world.currentMonth.toInt()
        historyService.logWorldHistory(
            world.id.toLong(),
            "【멸망】 ${destroyedNationName} 세력이 ${attackerNationName}에게 정복되어 멸망했습니다.",
            year,
            month,
        )
        if (attackerNationId > 0L) {
            historyService.logNationHistory(
                world.id.toLong(),
                attackerNationId,
                "${destroyedNationName} 정복",
                year,
                month,
            )
        }
    }

    /**
     * 전투 전 모디파이어 적용: 국가 타입, 성격, 특기, 아이템 보너스를 WarUnit에 반영.
     * ModifierService에서 수집한 StatContext를 WarUnit 필드에 매핑.
     */
    private fun applyWarModifiers(
        unit: WarUnitGeneral,
        modifiers: List<ActionModifier>,
        opponentCrewType: String = "",
        opposeModifiers: List<ActionModifier> = emptyList(),
        isAttacker: Boolean = false,
    ) {
        if (modifiers.isEmpty() && opposeModifiers.isEmpty()) return

        val hpRatio = if (unit.maxHp <= 0) 1.0 else unit.hp.toDouble() / unit.maxHp.toDouble()
        val baseCtx = StatContext(
            crewType = unit.crewType.toString(),
            opponentCrewType = opponentCrewType,
            hpRatio = hpRatio,
            leadership = unit.leadership.toDouble(),
            strength = unit.strength.toDouble(),
            intel = unit.intel.toDouble(),
            criticalChance = unit.criticalChance,
            dodgeChance = unit.dodgeChance,
            magicChance = unit.magicChance,
            isAttacker = isAttacker,
        )
        var modified = modifierService.applyStatModifiers(modifiers, baseCtx)
        if (opposeModifiers.isNotEmpty()) {
            val opposeCtx = modified.copy(
                crewType = opponentCrewType,
                opponentCrewType = unit.crewType.toString(),
            )
            modified = modifierService.applyOpposeStatModifiers(opposeModifiers, opposeCtx)
        }

        // 스탯 반영 (0-100 범위 클램핑)
        unit.leadership = modified.leadership.toInt().coerceIn(0, 100)
        unit.strength = modified.strength.toInt().coerceIn(0, 100)
        unit.intel = modified.intel.toInt().coerceIn(0, 100)
        unit.criticalChance = modified.criticalChance
        unit.dodgeChance = modified.dodgeChance
        unit.magicChance = modified.magicChance
        unit.magicDamageMultiplier = modified.magicSuccessDamage

        // 훈련/사기 보너스
        if (modified.bonusTrain != 0.0) {
            unit.train = (unit.train + modified.bonusTrain.toInt()).coerceIn(0, 100)
        }
        if (modified.bonusAtmos != 0.0) {
            unit.atmos = (unit.atmos + modified.bonusAtmos.toInt()).coerceIn(0, 100)
        }

        if (modified.warPower != 1.0) {
            unit.attackMultiplier *= modified.warPower
        }

        // 전투력 배율 (warPower multiplier)
        val warPowerMult = modifierService.getTotalWarPowerMultiplier(modifiers)
        if (warPowerMult != 1.0) {
            unit.attackMultiplier *= warPowerMult
        }
    }
}
