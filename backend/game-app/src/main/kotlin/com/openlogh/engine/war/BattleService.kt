package com.openlogh.engine.war

import com.openlogh.engine.DeterministicRng
import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.SovereignConstants
import com.openlogh.engine.EventService
import com.openlogh.engine.modifier.ActionModifier
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.modifier.StatContext
import com.openlogh.entity.Planet
import com.openlogh.model.CrewType
import com.openlogh.entity.Officer
import com.openlogh.entity.Message
import com.openlogh.entity.Record
import com.openlogh.entity.SessionState
import com.openlogh.entity.OldNation
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import com.openlogh.repository.OldNationRepository
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.service.GameConstService
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

@Service
class BattleService(
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val messageRepository: MessageRepository,
    private val recordRepository: RecordRepository,
    private val oldNationRepository: OldNationRepository,
    private val fleetRepository: FleetRepository,
    private val factionTurnRepository: FactionTurnRepository,
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
        attacker: Officer,
        targetCity: Planet,
        world: SessionState,
    ): BattleResult {
        if (attacker.npcState == SovereignConstants.NPC_STATE_EMPEROR) {
            return BattleResult(attackerWon = false)
        }

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "default"
        val rng = DeterministicRng.create(
            hiddenSeed, "ConquerCity",
            world.currentYear, world.currentMonth,
            attacker.factionId, attacker.id, targetCity.id
        )

        val attackerNation = factionRepository.findById(attacker.factionId).orElse(null)
        val attackerUnit = WarUnitOfficer(
            attacker,
            nationTech = attackerNation?.tech ?: 0f,
            isAttacker = true,
            cityLevel = targetCity.level.toInt(),
            capitalCityId = attackerNation?.capitalPlanetId ?: 0,
        )
        val attackerModifiers = modifierService.getModifiers(attacker, attackerNation)

        // Get defenders in the city
        // C9: PHP also requires rice > crew/100, train >= defence_train, atmos >= defence_train
        val defenderEntries = officerRepository.findByPlanetId(targetCity.id)
            .filter {
                it.factionId == targetCity.factionId &&
                    it.ships > 0 &&
                    it.supplies > it.ships / 100 &&
                    it.training >= it.defenceTrain &&
                    it.morale >= it.defenceTrain &&
                    it.npcState != SovereignConstants.NPC_STATE_EMPEROR
            }
            .map { gen ->
                val defNation = factionRepository.findById(gen.factionId).orElse(null)
                val unit = WarUnitOfficer(
                    gen,
                    nationTech = defNation?.tech ?: 0f,
                    isAttacker = false,
                    cityLevel = targetCity.level.toInt(),
                    capitalCityId = defNation?.capitalPlanetId ?: 0,
                )
                val modifiers = modifierService.getModifiers(gen, defNation)
                Triple(unit, gen, modifiers)
            }

        val primaryDefender = defenderEntries.firstOrNull()
        applyWarModifiers(
            unit = attackerUnit,
            modifiers = attackerModifiers,
            opponentCrewType = primaryDefender?.first?.shipClass?.toString().orEmpty(),
            opposeModifiers = primaryDefender?.third ?: emptyList(),
            isAttacker = true,
        )

        for ((unit, _, modifiers) in defenderEntries) {
            applyWarModifiers(
                unit = unit,
                modifiers = modifiers,
                opponentCrewType = attackerUnit.shipClass.toString(),
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
            val attackerCity = planetRepository.findById(attacker.planetId).orElse(null)
            if (attackerCity != null) {
                attackerCity.dead += (totalDead * 0.4).toInt()
                planetRepository.save(attackerCity)
            }
            targetCity.dead += (totalDead * 0.6).toInt()
        }

        planetRepository.save(targetCity)
        officerRepository.save(attacker)
        defenders.forEach { it.general.let { gen -> officerRepository.save(gen) } }

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
        world: SessionState,
        attacker: Officer,
        attackerUnit: WarUnitOfficer,
        defenders: List<WarUnitOfficer>,
        targetCity: Planet,
        result: BattleResult,
    ) {
        val worldId = world.id.toLong()
        val year = world.currentYear.toInt()
        val month = world.currentMonth.toInt()
        val attackerCrewType = CrewType.fromCode(attacker.shipClass.toInt())

        val messages = mutableListOf<Message>()

        fun buildSummary(
            me: Officer, meUnit: WarUnitOfficer, meCrewType: CrewType?,
            opp: String, oppCrewType: CrewType?, oppRemain: Int, oppKilled: Int,
            warTypeStr: String,
        ): String {
            val meName = me.name
            val meTypeName = meCrewType?.displayName ?: "병종${me.shipClass}"
            val meRemain = meUnit.hp
            val meKilled = -(meUnit.maxHp - meUnit.hp)
            val oppTypeName = oppCrewType?.displayName ?: "?"
            return "【${meTypeName}】${meName} ${meRemain}(${meKilled}) ${warTypeStr} 【${oppTypeName}】${opp} ${oppRemain}(${-oppKilled})"
        }

        val primaryDefender = defenders.firstOrNull()
        val defenderName = primaryDefender?.general?.name ?: targetCity.name
        val defenderCrewType = primaryDefender?.let { CrewType.fromCode(it.shipClass) }
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
            sessionId = worldId,
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
                sessionId = worldId,
                mailboxCode = "battle_detail",
                messageType = "log",
                srcId = attacker.id,
                destId = attacker.id,
                payload = mutableMapOf("message" to detailText, "year" to year, "month" to month),
            )
        }

        // general_action for attacker (legacy: pushBattleResultTemplate also pushes to generalActionLog)
        messages += Message(
            sessionId = worldId,
            mailboxCode = "general_action",
            messageType = "log",
            srcId = attacker.id,
            destId = attacker.id,
            payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$attackSummary", "year" to year, "month" to month),
        )

        // Defender battle logs (for each general defender)
        for (defUnit in defenders) {
            val defGen = defUnit.general
            val defCrewType = CrewType.fromCode(defGen.shipClass.toInt())

            val defenseSummary = buildSummary(
                me = defGen, meUnit = defUnit, meCrewType = defCrewType,
                opp = attacker.name, oppCrewType = attackerCrewType,
                oppRemain = attackerUnit.hp, oppKilled = attackerUnit.maxHp - attackerUnit.hp,
                warTypeStr = "←",
            )

            messages += Message(
                sessionId = worldId,
                mailboxCode = "battle_result",
                messageType = "log",
                srcId = defGen.id,
                destId = defGen.id,
                payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$defenseSummary", "year" to year, "month" to month),
            )

            messages += Message(
                sessionId = worldId,
                mailboxCode = "battle_detail",
                messageType = "log",
                srcId = defGen.id,
                destId = defGen.id,
                payload = mutableMapOf("message" to detailText, "year" to year, "month" to month),
            )

            messages += Message(
                sessionId = worldId,
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
            sessionId = worldId,
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
                    worldId = msg.sessionId,
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

    private fun occupyCity(city: Planet, attacker: Officer, world: SessionState, rng: Random) {
        val oldNationId = city.factionId
        val conquerNationId = resolveConquerNationId(city, attacker.factionId)
        city.factionId = conquerNationId
        city.approval = 0F

        // Reset city post-occupation (legacy: supply=1, term=0, conflict={}, officer_set=0)
        city.supplyState = 1
        city.term = 0
        city.conflict = mutableMapOf()
        city.officerSet = 0

        // Reduce agri/comm/secu by 30% (legacy: multiply by 0.7)
        city.production = (city.production * 0.7).toInt()
        city.commerce = (city.commerce * 0.7).toInt()
        city.security = (city.security * 0.7).toInt()

        // Legacy: city level > 3 → set def/wall to defaultCityWall; else def_max/2, wall_max/2
        val defaultCityWall = gameConstService.getInt("defaultCityWall")
        if (city.level > 3) {
            city.orbitalDefense = defaultCityWall
            city.fortress = defaultCityWall
        } else {
            city.orbitalDefense = (city.orbitalDefenseMax / 2.0).roundToInt()
            city.fortress = (city.fortressMax / 2.0).roundToInt()
        }

        // Dispatch OCCUPY_CITY event
        eventService.dispatchEvents(world, "OCCUPY_CITY")

        // Log conquest
        logConquest(city, attacker, world)

        // Demote city officers of the old nation in this city
        demoteCityOfficers(city.id, oldNationId)

        // Check if old nation lost capital or is destroyed
        val oldNation = factionRepository.findById(oldNationId).orElse(null)
        if (oldNation != null) {
            val remainingCities = planetRepository.findByFactionId(oldNationId)
                .filter { it.id != city.id }

            if (remainingCities.isEmpty()) {
                // Nation destroyed
                destroyNation(oldNationId, attacker, world, rng)
            } else if (oldNation.capitalPlanetId == city.id) {
                // Capital lost - relocate
                relocateCapital(oldNation, remainingCities, world)
            }
        }

        // Update conflict tracking
        val conflictMap = city.conflict.toMutableMap()
        val attackerKey = attacker.factionId.toString()
        val currentScore = (conflictMap[attackerKey] as? Number)?.toInt() ?: 0
        conflictMap[attackerKey] = currentScore + 1
        city.conflict = conflictMap
    }

    private fun resolveConquerNationId(city: Planet, attackerNationId: Long): Long {
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
        attacker: Officer,
        world: SessionState,
        rng: Random,
    ) {
        val destroyedNation = factionRepository.findById(destroyedNationId).orElse(null) ?: return
        logger.info("Nation {} ({}) destroyed by nation {}", destroyedNation.name, destroyedNationId, attacker.factionId)

        val generals = officerRepository.findByFactionId(destroyedNationId)
        var totalGoldLoss = 0
        var totalRiceLoss = 0

        // Apply losses to all defender generals (legacy: 20-50% gold/rice, -10% exp, -50% dedication)
        for (gen in generals) {
            val lossRatio = 0.2 + rng.nextDouble() * 0.3  // 20-50%
            val goldLoss = (gen.funds * lossRatio).toInt()
            val riceLoss = (gen.supplies * lossRatio).toInt()
            val expLoss = (gen.experience * 0.1).toInt()
            val dedLoss = (gen.dedication * 0.5).toInt()

            gen.funds -= goldLoss
            gen.supplies -= riceLoss
            gen.experience -= expLoss
            gen.dedication -= dedLoss

            totalGoldLoss += goldLoss
            totalRiceLoss += riceLoss

            // Release from nation (legacy: nation=0, officer_level=0, officer_city=0, belong=0, troop=0)
            gen.factionId = 0
            gen.officerLevel = 0
            gen.officerCity = 0
            gen.belong = 0
            gen.fleetId = 0

            // NPC auto-join to attacker nation (legacy: npcState 2-8 except 5, gated by joinRuinedNPCProp)
            if (gen.npcState in NPC_AUTO_JOIN_STATES && rng.nextDouble() < JOIN_RUINED_NPC_PROP) {
                val delay = rng.nextInt(0, NPC_JOIN_MAX_DELAY + 1)
                gen.meta["autoJoinNationId"] = attacker.factionId
                gen.meta["autoJoinDelay"] = delay
            }

            officerRepository.save(gen)
        }

        // Distribute conquest rewards to attacker nation
        // Legacy: half of nation gold/rice above base + half of general losses
        val nationGoldAboveBase = maxOf(0, destroyedNation.funds - BASE_GOLD)
        val nationRiceAboveBase = maxOf(0, destroyedNation.supplies - BASE_RICE)
        val goldReward = nationGoldAboveBase / 2 + totalGoldLoss / 2
        val riceReward = nationRiceAboveBase / 2 + totalRiceLoss / 2

        val attackerNation = factionRepository.findById(attacker.factionId).orElse(null)
        if (attackerNation != null && (goldReward > 0 || riceReward > 0)) {
            attackerNation.funds += goldReward
            attackerNation.supplies += riceReward
            factionRepository.save(attackerNation)

            // Log reward to all chiefs (officer_level >= 5)
            logConquestReward(world, attacker.factionId, destroyedNation.name, goldReward, riceReward)
        }

        logNationDestroyed(world, attackerNation?.name ?: attacker.name, attacker.factionId, destroyedNation.name)

        // Legacy: set all cities to neutral (nation=0, front=0)
        val cities = planetRepository.findByFactionId(destroyedNationId)
        for (city in cities) {
            city.factionId = 0
            city.frontState = 0
            planetRepository.save(city)
        }

        // Legacy: delete all troops of the nation
        val troops = fleetRepository.findByFactionId(destroyedNationId)
        if (troops.isNotEmpty()) {
            fleetRepository.deleteAll(troops)
        }

        // Kill all diplomatic relations
        diplomacyService.killAllRelationsForNation(world.id.toLong(), destroyedNationId)

        // Legacy: delete nation turns
        factionTurnRepository.deleteByNationId(destroyedNationId)

        // Legacy: archive nation data to old_nation before deletion
        oldNationRepository.save(
            OldNation(
                serverId = world.id.toString(),
                nation = destroyedNationId,
                data = mutableMapOf(
                    "name" to destroyedNation.name,
                    "color" to destroyedNation.color,
                    "level" to destroyedNation.level,
                    "gold" to destroyedNation.funds,
                    "rice" to destroyedNation.supplies,
                    "tech" to destroyedNation.tech,
                    "gennum" to destroyedNation.officerCount,
                    "generals" to generals.map { it.id },
                ),
                date = java.time.OffsetDateTime.now(),
            )
        )

        // Legacy: hard-delete nation from DB (not soft delete)
        factionRepository.delete(destroyedNation)

        eventService.dispatchEvents(world, "DESTROY_NATION")
    }

    /**
     * Relocate capital when the capital city is captured but nation survives.
     * C10: Legacy picks closest city to old capital (by positionX/Y in meta), not highest pop.
     * Falls back to highest pop if coordinates are unavailable.
     * Also halves nation gold/rice, 20% morale loss.
     */
    private fun relocateCapital(
        nation: com.openlogh.entity.Faction,
        remainingCities: List<Planet>,
        world: SessionState,
    ) {
        // C10: PHP picks the CLOSEST city to the old capital, not highest population.
        val oldCapital = nation.capitalPlanetId?.let { planetRepository.findById(it).orElse(null) }
        val oldX = (oldCapital?.meta?.get("positionX") as? Number)?.toDouble()
        val oldY = (oldCapital?.meta?.get("positionY") as? Number)?.toDouble()
        val newCapital = if (oldX != null && oldY != null) {
            remainingCities.minWithOrNull(
                compareBy<Planet> {
                    val x = (it.meta["positionX"] as? Number)?.toDouble()
                    val y = (it.meta["positionY"] as? Number)?.toDouble()
                    if (x != null && y != null) hypot(x - oldX, y - oldY) else Double.MAX_VALUE
                }.thenByDescending { it.population }
            )
        } else {
            remainingCities.maxByOrNull { it.population }
        } ?: return

        logger.info("Nation {} relocates capital to {}", nation.name, newCapital.name)

        nation.capitalPlanetId = newCapital.id
        nation.funds /= 2
        nation.supplies /= 2
        factionRepository.save(nation)

        // 20% morale loss to all generals
        val nationals = officerRepository.findByFactionId(nation.id)
        for (gen in nationals) {
            gen.morale = (gen.morale * 0.8).toInt().coerceIn(0, 150).toShort()
            if (gen.officerLevel >= 5) {
                gen.planetId = newCapital.id
            }
            officerRepository.save(gen)
        }

        // Log emergency relocation
        messageRepository.save(
            Message(
                sessionId = world.id.toLong(),
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
        val generals = officerRepository.findByPlanetId(cityId)
            .filter { it.factionId == oldNationId && it.officerCity == cityId.toInt() }
        for (gen in generals) {
            gen.officerLevel = 1
            gen.officerCity = 0
            officerRepository.save(gen)
        }
    }

    private fun logConquest(city: Planet, attacker: Officer, world: SessionState) {
        val nation = factionRepository.findById(attacker.factionId).orElse(null)
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
        world: SessionState,
        attackerNationId: Long,
        destroyedNationName: String,
        goldReward: Int,
        riceReward: Int,
    ) {
        messageRepository.save(
            Message(
                sessionId = world.id.toLong(),
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
        world: SessionState,
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
        unit: WarUnitOfficer,
        modifiers: List<ActionModifier>,
        opponentCrewType: String = "",
        opposeModifiers: List<ActionModifier> = emptyList(),
        isAttacker: Boolean = false,
    ) {
        if (modifiers.isEmpty() && opposeModifiers.isEmpty()) return

        val hpRatio = if (unit.maxHp <= 0) 1.0 else unit.hp.toDouble() / unit.maxHp.toDouble()
        val rank = unit.general.meta["rank"] as? Map<*, *>
        val killnum = (rank?.get("killnum") as? Number)?.toDouble() ?: 0.0
        val baseCtx = StatContext(
            crewType = unit.shipClass.toString(),
            opponentCrewType = opponentCrewType,
            hpRatio = hpRatio,
            leadership = unit.leadership.toDouble(),
            strength = unit.command.toDouble(),
            intel = unit.intelligence.toDouble(),
            criticalChance = unit.criticalChance,
            dodgeChance = unit.dodgeChance,
            magicChance = unit.magicChance,
            killnum = killnum,
            isAttacker = isAttacker,
        )
        var modified = modifierService.applyStatModifiers(modifiers, baseCtx)
        if (opposeModifiers.isNotEmpty()) {
            val opposeCtx = modified.copy(
                crewType = opponentCrewType,
                opponentCrewType = unit.shipClass.toString(),
            )
            modified = modifierService.applyOpposeStatModifiers(opposeModifiers, opposeCtx)
        }

        // 스탯 반영 (0-100 범위 클램핑)
        unit.leadership = modified.leadership.toInt().coerceIn(0, 100)
        unit.command = modified.command.toInt().coerceIn(0, 100)
        unit.intelligence = modified.intelligence.toInt().coerceIn(0, 100)
        unit.criticalChance = modified.criticalChance
        unit.dodgeChance = modified.dodgeChance
        unit.magicChance = modified.magicChance
        unit.magicDamageMultiplier = modified.magicSuccessDamage

        // 훈련/사기 보너스
        if (modified.bonusTrain != 0.0) {
            unit.training = (unit.training + modified.bonusTrain.toInt()).coerceIn(0, 100)
        }
        if (modified.bonusAtmos != 0.0) {
            unit.morale = (unit.morale + modified.bonusAtmos.toInt()).coerceIn(0, 100)
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
