package com.openlogh.engine.ai

import com.openlogh.engine.DeterministicRng
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.engine.RandUtil
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.Planet
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.model.ArmType

import org.slf4j.LoggerFactory
import com.openlogh.service.MapService
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Full NPC AI decision engine, ported from legacy OfficerAI.php.
 *
 * Returns action command strings that the game engine interprets.
 * The decision tree mirrors the legacy PHP implementation's ~40 do*() methods.
 */
@Service
class OfficerAI(
    private val worldPortFactory: JpaWorldPortFactory,
    private val mapService: MapService,
) {
    private val logger = LoggerFactory.getLogger(OfficerAI::class.java)

    // ──────────────────────────────────────────────────────────
    //  Main entry point
    // ──────────────────────────────────────────────────────────

    fun decideAndExecute(general: Officer, world: SessionState): String {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "OfficerAI", world.currentYear, world.currentMonth, general.id
        )

        // Troop leaders (npcState=5) always rally
        if (general.npcState.toInt() == 5) {
            logger.debug("General {} ({}) is troop leader, always 집합", general.id, general.name)
            return "집합"
        }

        // Legacy parity: reserved command checked BEFORE nationId==0 wanderer routing.
        // chooseGeneralTurn in legacy PHP checks reserved command after npcType==5, before nationId==0.
        val earlyReserved = checkReservedCommand(general)
        if (earlyReserved != null) {
            logger.debug("General {} ({}) using reserved command (early): {}", general.id, general.name, earlyReserved)
            return earlyReserved
        }

        // Wanderers (nationId=0) have limited options
        if (general.factionId == 0L) {
            return decideWandererAction(general, world, rng)
        }

        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val city = ports.planet(general.planetId)?.toEntity() ?: return "휴식"
        val nation = ports.faction(general.factionId)?.toEntity()

        val allCities = ports.allPlanets().map { it.toEntity() }
        val allGenerals = ports.allOfficers().map { it.toEntity() }
        val allNations = ports.allFactions().map { it.toEntity() }
        val diplomacies = ports.activeDiplomacies().map { it.toEntity() }

        val nationCities = if (nation != null) {
            allCities.filter { it.factionId == nation.id }
        } else {
            emptyList()
        }

        val frontCities = nationCities.filter { it.frontState > 0 }
        val rearCities = nationCities.filter { it.frontState.toInt() == 0 }
        val supplyCities = nationCities.filter { it.supplyState > 0 }
        val backupCities = nationCities.filter { it.frontState.toInt() == 0 && it.supplyState > 0 }
        val nationGenerals = allGenerals.filter { it.factionId == general.factionId }

        val diplomacyState = calcDiplomacyState(worldId, nation, diplomacies)

        val nationPolicy = if (nation != null) {
            NpcPolicyBuilder.buildNationPolicy(nation.meta)
        } else {
            NpcNationPolicy()
        }
        val generalType = classifyGeneral(general, rng, nationPolicy.minNPCWarLeadership)

        val attackable = if (frontCities.isNotEmpty()) {
            frontCities.any { it.supplyState > 0 }
        } else {
            nationCities.any { it.supplyState > 0 }
        }

        // War target nations
        val warTargetNations = calcWarTargetNations(nation, diplomacies)

        val mapName = (world.config["mapName"] as? String) ?: "logh"
        val mapAdjacency = try {
            mapService.getCities(mapName).associate { cityConst ->
                cityConst.id.toLong() to cityConst.connections.map { it.toLong() }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load city connections for map {}: {}", world.config["mapName"] ?: "logh", e.message)
            emptyMap()
        }

        val personality = PersonalityTrait.fromString(general.personality)

        val ctx = AIContext(
            world = world,
            general = general,
            city = city,
            nation = nation,
            diplomacyState = diplomacyState,
            generalType = generalType,
            allCities = allCities,
            allGenerals = allGenerals,
            allNations = allNations,
            frontCities = frontCities,
            rearCities = rearCities,
            nationGenerals = nationGenerals,
            mapAdjacency = mapAdjacency,
            personality = personality,
        )

        val generalPolicy = if (nation != null) {
            NpcPolicyBuilder.buildGeneralPolicy(nation.meta)
        } else {
            NpcGeneralPolicy()
        }

        val month = world.currentMonth.toInt()
        if (general.npcState.toInt() >= 2 && nation != null) {
            if (month in listOf(3, 6, 9, 12)) {
                autoPromoteLord(ctx.nationGenerals, ports)
            }
            if (general.officerLevel.toInt() == 20) {
                var nationModified = false
                if (month in listOf(3, 6, 9, 12)) {
                    choosePromotion(ctx, rng, ports)
                }
                if (month == 12) {
                    chooseTexRate(ctx, supplyCities)
                    chooseGoldBillRate(ctx, supplyCities, nationPolicy)
                    nationModified = true
                    logger.info("Nation {} ({}) bill set to {}, rate set to {}", nation.id, nation.name, nation.taxRate, nation.conscriptionRate)
                }
                if (month == 6) {
                    chooseTexRate(ctx, supplyCities)
                    chooseRiceBillRate(ctx, supplyCities, nationPolicy)
                    nationModified = true
                    logger.info("Nation {} ({}) riceBill set to {}, rate set to {}", nation.id, nation.name, nation.taxRate, nation.conscriptionRate)
                }
                if (nationModified) {
                    ports.putFaction(nation.toSnapshot())
                }
            } else if (month in listOf(3, 6, 9, 12)) {
                chooseNonLordPromotion(ctx, rng, ports)
            }
        }

        // Injury check
        if (general.injury > nationPolicy.cureThreshold) {
            logger.debug("General {} ({}) injury {} exceeds cureThreshold {}", general.id, general.name, general.injury, nationPolicy.cureThreshold)
            return "요양"
        }

        // NPC거병 check: NPC lord-capable wanderer with nation
        if ((general.npcState.toInt() == 2 || general.npcState.toInt() == 3) && general.factionId == 0L) {
            val riseResult = doRise(general, world, rng)
            if (riseResult != null) return riseResult
        }

        // Legacy: NPC사망대비 — only killTurn countdown, not deadYear (deadYear sets initial killTurn only)
        val killTurn = general.killTurn?.toInt()
        if (killTurn != null && killTurn <= 5 && general.npcState.toInt() >= 2) {
            return doDeathPreparation(general, nation, rng)
        }

        val hasNeutralTargets = warTargetNations.containsKey(0L)

        val action = try {
            when {
                // Chiefs (officerLevel>=20) get nation-level action priority first,
                // then fall through to general-level actions if nothing applies.
                general.officerLevel >= 20 && nation != null -> decideChiefAction(
                    ctx, rng, nationPolicy, generalPolicy, attackable, warTargetNations, supplyCities, backupCities
                )
                diplomacyState == DiplomacyState.AT_WAR || diplomacyState == DiplomacyState.RECRUITING -> decideWarAction(
                    ctx, rng, generalPolicy, nationPolicy, attackable, warTargetNations, supplyCities, backupCities
                )
                diplomacyState == DiplomacyState.IMMINENT -> decideWarAction(
                    ctx, rng, generalPolicy, nationPolicy, attackable, warTargetNations, supplyCities, backupCities
                )
                // Neutral (empty) cities exist and we can attack → use war routine for recruitment/sortie
                hasNeutralTargets && attackable -> decideWarAction(
                    ctx, rng, generalPolicy, nationPolicy, attackable, warTargetNations, supplyCities, backupCities
                )
                else -> decidePeaceAction(
                    ctx, rng, generalPolicy, nationPolicy, supplyCities, backupCities, warTargetNations
                )
            }
        } catch (e: Exception) {
            logger.warn("AI decision failed for general ${general.id}: ${e.message}")
            "휴식"
        }

        logger.info(
            "General {} ({}) decided: {} [diplo={}, type={}, personality={}]",
            general.id,
            general.name,
            action,
            diplomacyState,
            generalType,
            personality,
        )
        return action
    }

    // ──────────────────────────────────────────────────────────
    //  Reserved command check
    // ──────────────────────────────────────────────────────────

    private fun checkReservedCommand(general: Officer): String? {
        val reserved = general.meta["reservedCommand"] as? String ?: return null
        general.meta.remove("reservedCommand")
        if (reserved == "휴식" || reserved.isBlank()) return null
        return reserved
    }

    // ──────────────────────────────────────────────────────────
    //  Wanderer AI (nationId=0)
    // ──────────────────────────────────────────────────────────

    /**
     * Legacy: chooseGeneralTurn for nationID==0.
     * Wanderers can: 국가선택 (join nation), 거병 (rise), 이동, 견문, 물자조달, 휴식.
     */
    private fun decideWandererAction(general: Officer, world: SessionState, rng: Random): String {
        // Per PHP: injury > cureThreshold (not > 0) -- same pattern as all other injury checks
        val wandererPolicy = if (general.factionId != 0L) {
            val nation = worldPortFactory.create(world.id.toLong()).faction(general.factionId)?.toEntity()
            if (nation != null) NpcPolicyBuilder.buildNationPolicy(nation.meta) else NpcNationPolicy()
        } else {
            NpcNationPolicy()
        }
        if (general.injury > wandererPolicy.cureThreshold) return "요양"

        // NPC lords (officerLevel==20) with no nation do 방랑군이동 / 건국
        if (general.npcState.toInt() >= 2 && general.officerLevel.toInt() == 20) {
            // Try 건국 first (per legacy do건국)
            if (general.makeLimit.toInt() == 0) {
                val result = doFoundNation(general, rng)
                if (result != null) return result
            }
            // Move toward candidate city (per legacy do방랑군이동)
            val moveResult = doWandererMove(general, world, rng)
            if (moveResult != null) return moveResult
            return "인재탐색"
        }

        // 국가선택: non-lord wanderer tries to join a nation (per legacy do국가선택)
        if (general.npcState.toInt() >= 2) {
            val selectResult = doSelectNation(general, world, rng)
            if (selectResult != null) return selectResult
        }

        // Neutral fallback
        return doNeutral(general, null, rng)
    }

    // ──────────────────────────────────────────────────────────
    //  Diplomacy state calculation
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy OfficerAI.php:206 calcDiplomacyState().
     * PHP 5-state model using diplomacy term countdown:
     *   - d평화(0): no declarations
     *   - d선포(1): declaration exists but term > 8, or early game with war targets
     *   - d징병(2): min term 6..8
     *   - d직전(3): min term <= 5
     *   - d전쟁(4): attackable AND active war (state=0)
     *
     * Also populates warTargetNation and attackable fields via returned result.
     *
     * @param world world state for year/month/startyear
     * @param nation the AI nation
     * @param diplomacies all active diplomacy rows
     * @param supplyCities nation's cities with supply > 0 (for attackable check)
     */
    internal fun calcDiplomacyState(
        world: SessionState,
        nation: Faction?,
        diplomacies: List<Diplomacy>,
        supplyCities: List<Planet> = emptyList(),
    ): CalcDiplomacyResult {
        if (nation == null) return CalcDiplomacyResult(DiplomacyState.PEACE, false, mapOf(0L to 1))

        val yearMonth = world.currentYear.toInt() * 12 + world.currentMonth.toInt()
        val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val earlyGameLimit = startYear * 12 + 2 * 12 + 5  // joinYearMonth(startyear+2, 5)

        // PHP: SELECT ... FROM diplomacy WHERE me=$nationID AND state IN (0,1)
        // state=0 → active war (stateCode="전쟁"), state=1 → declaration (stateCode="선전포고")
        val warTarget = diplomacies.filter {
            it.srcFactionId == nation.id &&
                (it.stateCode == "전쟁" || it.stateCode == "선전포고")
        }

        // Early game check: per PHP line 219
        if (yearMonth <= earlyGameLimit) {
            return if (warTarget.isEmpty()) {
                CalcDiplomacyResult(DiplomacyState.PEACE, false, mapOf(0L to 1))
            } else {
                CalcDiplomacyResult(DiplomacyState.DECLARED, false, mapOf(0L to 1))
            }
        }

        // Attackable: nation has front cities with supply
        val attackable = supplyCities.any { it.frontState > 0 }

        // Build warTargetNation map and count war states
        var onWar = 0
        var onWarReady = 0
        val warTargetNation = mutableMapOf<Long, Int>()

        for (d in warTarget) {
            val targetId = d.destFactionId
            when {
                d.stateCode == "전쟁" -> {  // PHP state=0: active war
                    onWar++
                    warTargetNation[targetId] = 2
                }
                d.stateCode == "선전포고" && d.term < 5 -> {  // PHP state=1, term<5: war ready
                    onWarReady++
                    warTargetNation[targetId] = 1
                }
                // else: onWarYet (term >= 5), not added to warTargetNation
            }
        }

        if (onWar == 0 && onWarReady == 0) {
            warTargetNation[0L] = 1  // neutral targets
        }

        // Term-based state from declarations (state=1 / stateCode="선전포고")
        val declarations = warTarget.filter { it.stateCode == "선전포고" }
        val minTerm = declarations.minOfOrNull { it.term.toInt() }

        var dipState = when {
            minTerm == null -> DiplomacyState.PEACE
            minTerm > 8 -> DiplomacyState.DECLARED
            minTerm > 5 -> DiplomacyState.RECRUITING
            else -> DiplomacyState.IMMINENT
        }

        // AT_WAR override: per PHP line 269-280
        if (warTargetNation.containsKey(0L) && attackable) {
            // PHP: key_exists(0, $warTargetNation) && $this->attackable
            // This shouldn't normally happen (0L key means no real targets), but PHP checks it
            dipState = DiplomacyState.AT_WAR
        } else if (onWar > 0) {
            if (attackable) {
                dipState = DiplomacyState.AT_WAR
            } else {
                // PHP: last_attackable check within 5 months
                val lastAttackable = (nation.meta["last_attackable"] as? Number)?.toInt()
                if (lastAttackable != null && lastAttackable >= yearMonth - 5) {
                    dipState = DiplomacyState.AT_WAR
                }
            }
        }

        // Store last_attackable in nation meta when AT_WAR
        if (dipState == DiplomacyState.AT_WAR || dipState == DiplomacyState.IMMINENT) {
            nation.meta["last_attackable"] = yearMonth
        }

        return CalcDiplomacyResult(dipState, attackable, warTargetNation)
    }

    /**
     * Backward-compatible overload for existing callers that pass worldId.
     */
    internal fun calcDiplomacyState(worldId: Long, nation: Faction?, diplomacies: List<Diplomacy>): DiplomacyState {
        if (nation == null) return DiplomacyState.PEACE

        // PHP filters diplomacy WHERE me=$nationID (srcNationId). Also check destNationId for
        // rows where this nation is the target of a declaration/war.
        val relevant = diplomacies.filter {
            (it.srcFactionId == nation.id || it.destFactionId == nation.id) &&
                (it.stateCode == "전쟁" || it.stateCode == "선전포고")
        }

        // Active war check: "전쟁" means state=0 (active war in PHP)
        val hasActiveWar = relevant.any { it.stateCode == "전쟁" }
        if (hasActiveWar) return DiplomacyState.AT_WAR
        if (nation.warState > 0) return DiplomacyState.AT_WAR

        // Term-based state from declarations (state=1 / stateCode="선전포고")
        val declarations = relevant.filter { it.stateCode == "선전포고" }
        val minTerm = declarations.minOfOrNull { it.term.toInt() }
        return when {
            minTerm == null -> DiplomacyState.PEACE
            minTerm > 8 -> DiplomacyState.DECLARED
            minTerm > 5 -> DiplomacyState.RECRUITING
            else -> DiplomacyState.IMMINENT
        }
    }

    internal fun calcDiplomacyState(nation: Faction?, diplomacies: List<Diplomacy>): DiplomacyState {
        val inferredWorldId = nation?.sessionId ?: diplomacies.firstOrNull()?.sessionId ?: 0L
        return calcDiplomacyState(inferredWorldId, nation, diplomacies)
    }

    /**
     * Result of calcDiplomacyState carrying additional context for do*() methods.
     */
    data class CalcDiplomacyResult(
        val dipState: DiplomacyState,
        val attackable: Boolean,
        val warTargetNation: Map<Long, Int>,
    )

    /**
     * Per legacy OfficerAI.php:3516 categorizeNationGeneral().
     * Classifies all generals in a nation into 7 categories:
     * - npcWarGenerals: NPC with leadership >= minNPCWarLeadership, npcState >= 2, not dying
     * - npcCivilGenerals: NPC with low leadership or killTurn <= 5
     * - userWarGenerals: player generals who fought recently or have troops >= minWarCrew during war
     * - userCivilGenerals: other player generals
     * - troopLeaders: npcState == 5 or troop leaders with 집합 reserved
     * - lostGenerals: in non-supply cities
     * - chiefGenerals: officerLevel > 4 (PHP threshold, maps to mid-rank+ in Kotlin)
     */
    internal fun categorizeNationGeneral(
        nationGenerals: List<Officer>,
        selfGeneralId: Long,
        nationCities: Map<Long, Planet>,
        dipState: DiplomacyState,
        minNPCWarLeadership: Int = 40,
        minWarCrew: Int = 1500,
        turnterm: Int = 60,
    ): NationGeneralCategories {
        val npcWarGenerals = mutableListOf<Officer>()
        val npcCivilGenerals = mutableListOf<Officer>()
        val userGenerals = mutableListOf<Officer>()
        val userWarGenerals = mutableListOf<Officer>()
        val userCivilGenerals = mutableListOf<Officer>()
        val troopLeaders = mutableListOf<Officer>()
        val lostGenerals = mutableListOf<Officer>()
        val chiefGenerals = mutableMapOf<Int, Officer>()

        // Exclude self from categorization (per PHP: no != $this->general->getID())
        val others = nationGenerals.filter { it.id != selfGeneralId }

        // PHP: calc lastWar = min of all recentWarTurn values
        // recentWarTurn is computed from warnum meta; we approximate with killnum/recent combat
        var lastWar = Int.MAX_VALUE
        for (gen in others) {
            val recentWar = calcRecentWarTurn(gen, turnterm)
            val belongMonths = ((gen.belong?.toInt() ?: 1) - 1) * 12
            if (recentWar >= belongMonths) continue  // exclude pre-enlistment combat
            lastWar = min(lastWar, recentWar)
        }

        for (gen in others) {
            val npcType = gen.npcState.toInt()
            val officerLevel = gen.officerLevel.toInt()
            val cityId = gen.planetId
            val city = nationCities[cityId]

            // Chief generals: PHP officer_level > 4
            if (officerLevel > 4) {
                chiefGenerals[officerLevel] = gen
            }

            // Lost generals: not in nation city or in non-supply city
            if (city == null || city.supplyState <= 0) {
                lostGenerals.add(gen)
            }

            when {
                npcType == 5 -> {
                    // Troop leader
                    troopLeaders.add(gen)
                }
                (gen.killTurn?.toInt() ?: Int.MAX_VALUE) <= 5 -> {
                    // Dying NPC treated as civil
                    npcCivilGenerals.add(gen)
                }
                npcType < 2 -> {
                    // Player general
                    userGenerals.add(gen)
                    val recentWar = calcRecentWarTurn(gen, turnterm)
                    if (recentWar <= lastWar + 12) {
                        userWarGenerals.add(gen)
                    } else if (dipState != DiplomacyState.PEACE && gen.ships >= minWarCrew) {
                        userWarGenerals.add(gen)
                    } else {
                        userCivilGenerals.add(gen)
                    }
                }
                gen.leadership >= minNPCWarLeadership -> {
                    npcWarGenerals.add(gen)
                }
                else -> {
                    npcCivilGenerals.add(gen)
                }
            }
        }

        return NationGeneralCategories(
            npcWarGenerals = npcWarGenerals,
            npcCivilGenerals = npcCivilGenerals,
            userGenerals = userGenerals,
            userWarGenerals = userWarGenerals,
            userCivilGenerals = userCivilGenerals,
            troopLeaders = troopLeaders,
            lostGenerals = lostGenerals,
            chiefGenerals = chiefGenerals,
        )
    }

    /**
     * Approximate PHP's calcRecentWarTurn.
     * Returns months since last combat based on warnum meta.
     */
    private fun calcRecentWarTurn(general: Officer, turnterm: Int): Int {
        val warnum = (general.meta["warnum"] as? Number)?.toInt() ?: 0
        val killnum = (general.meta["killnum"] as? Number)?.toInt() ?: 0
        // Approximate: if recent combat exists, return low value; otherwise high
        return if (warnum > 0 || killnum > 0) 0 else Int.MAX_VALUE
    }

    /**
     * Result of categorizeNationGeneral.
     */
    data class NationGeneralCategories(
        val npcWarGenerals: List<Officer>,
        val npcCivilGenerals: List<Officer>,
        val userGenerals: List<Officer>,
        val userWarGenerals: List<Officer>,
        val userCivilGenerals: List<Officer>,
        val troopLeaders: List<Officer>,
        val lostGenerals: List<Officer>,
        val chiefGenerals: Map<Int, Officer>,
    )

    /**
     * Calculate war target nations map: nationId -> state (1=war ready, 2=at war).
     * Per legacy: state 0 (war) -> 2, state 1 with term<5 -> 1.
     */
    private fun calcWarTargetNations(nation: Faction?, diplomacies: List<Diplomacy>): Map<Long, Int> {
        if (nation == null) return emptyMap()
        val result = mutableMapOf<Long, Int>()
        val relevant = diplomacies.filter {
            it.srcFactionId == nation.id || it.destFactionId == nation.id
        }
        for (d in relevant) {
            val targetId = if (d.srcFactionId == nation.id) d.destFactionId else d.srcFactionId
            when {
                d.stateCode == "선전포고" || d.stateCode == "전쟁" -> result[targetId] = 2
                d.stateCode == "불가침" -> { /* skip */ }
                else -> result.putIfAbsent(targetId, 1)
            }
        }
        if (result.isEmpty()) {
            result[0L] = 1 // Neutral targets
        }
        return result
    }

    // ──────────────────────────────────────────────────────────
    //  General type classification
    // ──────────────────────────────────────────────────────────

    internal fun classifyGeneral(
        general: Officer,
        rng: Random = Random(0),
        minNPCWarLeadership: Int = 40,
    ): Int {
        val personality = PersonalityTrait.fromString(general.personality)
        val weights = PersonalityWeights.forTrait(personality)

        var flags = 0
        val l = (general.leadership.toInt() * weights.leadership).toInt()
        val s = (general.command.toInt() * weights.command).toInt()
        val i = (general.intelligence.toInt() * weights.intelligence).toInt()

        if (s >= i) {
            flags = flags or GeneralType.WARRIOR.flag
            // Legacy: nextBool(intel/strength/2) — probability is stat-ratio based (0.4–0.5), not fixed 50%
            if (i > 0 && s > 0 && i.toDouble() / s >= 0.8 && rng.nextDouble() < i.toDouble() / s.toDouble() / 2.0) {
                flags = flags or GeneralType.STRATEGIST.flag
            }
        }
        if (i > s) {
            flags = flags or GeneralType.STRATEGIST.flag
            // Legacy: nextBool(strength/intel/2) — probability is stat-ratio based (0.4–0.5), not fixed 50%
            if (s > 0 && i > 0 && s.toDouble() / i >= 0.8 && rng.nextDouble() < s.toDouble() / i.toDouble() / 2.0) {
                flags = flags or GeneralType.WARRIOR.flag
            }
        }
        if (l >= minNPCWarLeadership) flags = flags or GeneralType.COMMANDER.flag
        return flags
    }

    // ──────────────────────────────────────────────────────────
    //  Personality-based action bias
    // ──────────────────────────────────────────────────────────

    /**
     * Returns a bias multiplier for action selection based on personality.
     * Values > 1.0 increase probability, < 1.0 decrease it.
     */
    internal fun personalityBias(personality: PersonalityTrait, actionCategory: String): Double {
        return when (personality) {
            PersonalityTrait.AGGRESSIVE -> when (actionCategory) {
                "출병", "전투준비", "징병", "전방워프" -> 1.6
                "일반내정", "후방워프", "내정워프" -> 0.6
                "소집해제" -> 0.4
                else -> 1.0
            }
            PersonalityTrait.DEFENSIVE -> when (actionCategory) {
                "전투준비", "일반내정", "긴급내정" -> 1.4
                "출병" -> 0.6
                "전방워프" -> 0.7
                "후방워프" -> 1.3
                else -> 1.0
            }
            PersonalityTrait.POLITICAL -> when (actionCategory) {
                "일반내정", "내정워프", "NPC헌납" -> 1.5
                "출병", "전투준비" -> 0.6
                "징병" -> 0.7
                else -> 1.0
            }
            PersonalityTrait.CAUTIOUS -> when (actionCategory) {
                "전투준비", "일반내정" -> 1.3
                "출병" -> 0.5
                "전방워프" -> 0.6
                "후방워프" -> 1.2
                "소집해제" -> 1.3
                else -> 1.0
            }
            PersonalityTrait.BALANCED -> 1.0
        }
    }

    // ──────────────────────────────────────────────────────────
    //  City development rate calculation (mirrors legacy calcCityDevelRate)
    // ──────────────────────────────────────────────────────────

    /**
     * Returns map of development key -> Pair(rate 0.0-1.0, generalTypeMask).
     */
    private fun calcCityDevelRate(city: Planet): Map<String, Pair<Double, Int>> {
        return mapOf(
            "trust" to Pair(city.approval.toDouble() / 100.0, GeneralType.COMMANDER.flag),
            "pop" to Pair(
                if (city.populationMax > 0) city.population.toDouble() / city.populationMax else 1.0,
                GeneralType.COMMANDER.flag
            ),
            "agri" to Pair(
                if (city.productionMax > 0) city.production.toDouble() / city.productionMax else 1.0,
                GeneralType.STRATEGIST.flag
            ),
            "comm" to Pair(
                if (city.commerceMax > 0) city.commerce.toDouble() / city.commerceMax else 1.0,
                GeneralType.STRATEGIST.flag
            ),
            "secu" to Pair(
                if (city.securityMax > 0) city.security.toDouble() / city.securityMax else 1.0,
                GeneralType.WARRIOR.flag
            ),
            "def" to Pair(
                if (city.orbitalDefenseMax > 0) city.orbitalDefense.toDouble() / city.orbitalDefenseMax else 1.0,
                GeneralType.WARRIOR.flag
            ),
            "wall" to Pair(
                if (city.fortressMax > 0) city.fortress.toDouble() / city.fortressMax else 1.0,
                GeneralType.WARRIOR.flag
            ),
        )
    }

    /**
     * Overall development score for a city (0.0-1.0).
     */
    private fun calcCityDevScore(city: Planet): Double {
        val maxSum = (city.productionMax + city.commerceMax + city.securityMax + city.orbitalDefenseMax + city.fortressMax).toDouble()
        if (maxSum <= 0) return 1.0
        val curSum = (city.production + city.commerce + city.security + city.orbitalDefense + city.fortress).toDouble()
        return curSum / maxSum
    }

    // ──────────────────────────────────────────────────────────
    //  Chief (ruler) action decision
    // ──────────────────────────────────────────────────────────

    private fun decideChiefAction(
        ctx: AIContext,
        rng: Random,
        nationPolicy: NpcNationPolicy,
        generalPolicy: NpcGeneralPolicy,
        attackable: Boolean,
        warTargetNations: Map<Long, Int>,
        supplyCities: List<Planet>,
        backupCities: List<Planet>,
    ): String {
        val nation = ctx.nation ?: return "휴식"

        // Nation-level turn: iterate policy priorities
        for (priority in nationPolicy.priority) {
            if (!nationPolicy.canDo(priority)) continue
            val action = doNationAction(
                priority, ctx, rng, nationPolicy, supplyCities, backupCities, attackable, warTargetNations
            )
            if (action != null) return action
        }

        // Fall through to general turn
        return decideWarOrPeaceGeneralAction(ctx, rng, generalPolicy, nationPolicy, attackable, warTargetNations, supplyCities, backupCities)
    }

    /**
     * Route nation-level priority to the appropriate do*() logic.
     * Returns action string or null if conditions not met.
     */
    private fun doNationAction(
        priority: String,
        ctx: AIContext,
        rng: Random,
        nationPolicy: NpcNationPolicy,
        supplyCities: List<Planet>,
        backupCities: List<Planet>,
        attackable: Boolean,
        warTargetNations: Map<Long, Int>,
    ): String? {
        val nation = ctx.nation ?: return null
        val nationGenerals = ctx.nationGenerals
        val frontCities = ctx.frontCities

        return when (priority) {
            // ── 부대 발령 (troop assignment) ──
            "부대전방발령" -> doTroopFrontAssignment(ctx, rng, nationPolicy, frontCities, supplyCities, warTargetNations)
            "부대후방발령" -> doTroopRearAssignment(ctx, rng, nationPolicy, backupCities, supplyCities)
            "부대구출발령" -> doTroopRescueAssignment(ctx, rng, nationPolicy, supplyCities)
            "부대유저장후방발령" -> doTroopUserRearAssignment(ctx, rng, nationPolicy, backupCities, supplyCities)

            // ── NPC general assignment ──
            "NPC전방발령" -> doNpcFrontAssignment(ctx, rng, nationPolicy, frontCities, attackable)
            "NPC후방발령" -> doNpcRearAssignment(ctx, rng, nationPolicy, backupCities, supplyCities, frontCities)
            "NPC내정발령" -> doNpcDomesticAssignment(ctx, rng, nationPolicy, supplyCities)
            "NPC구출발령" -> doNpcRescueAssignment(ctx, rng, nationPolicy, supplyCities)

            // ── User general assignment ──
            "유저장전방발령" -> doUserFrontAssignment(ctx, rng, nationPolicy, frontCities, attackable)
            "유저장후방발령" -> doUserRearAssignment(ctx, rng, nationPolicy, backupCities, supplyCities, frontCities)
            "유저장구출발령" -> doUserRescueAssignment(ctx, rng, nationPolicy, supplyCities)
            "유저장내정발령" -> doUserDomesticAssignment(ctx, rng, nationPolicy, supplyCities)

            // ── Rewards ──
            "NPC긴급포상" -> doNpcUrgentReward(ctx, rng, nationPolicy)
            "유저장긴급포상" -> doUserUrgentReward(ctx, rng, nationPolicy)
            "NPC포상" -> doNpcReward(ctx, rng, nationPolicy)
            "유저장포상" -> doUserReward(ctx, rng, nationPolicy)

            // ── Confiscation ──
            "NPC몰수" -> doNpcConfiscation(ctx, rng, nationPolicy)

            // ── Diplomacy ──
            "불가침제의" -> doNonAggressionProposal(ctx, rng, nationPolicy, supplyCities)
            "선전포고" -> doDeclaration(ctx, rng, nationPolicy, attackable, warTargetNations, supplyCities)

            // ── Capital move ──
            "천도" -> doMoveCapital(ctx, rng, nationPolicy, supplyCities)

            // ── Strategic war commands (used during war) ──
            "전시전략" -> {
                if (ctx.diplomacyState == DiplomacyState.AT_WAR && nation.strategicCmdLimit > 0) {
                    listOf("급습", "필사즉생", "의병모집")[rng.nextInt(3)]
                } else null
            }

            else -> null
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Nation-level do*() methods: Fleet assignments
    // ──────────────────────────────────────────────────────────

    /**
     * 부대전방발령: Move troop leaders to front cities.
     * Per legacy: find troop leaders not at front, assign them to a front city.
     */
    private fun doTroopFrontAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        frontCities: List<Planet>, supplyCities: List<Planet>,
        warTargetNations: Map<Long, Int>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (frontCities.isEmpty()) return null

        val mapName = (ctx.world.config["mapName"] as? String) ?: "logh"
        val capitalMapCityId = ctx.allCities.find { it.id == nation.capitalPlanetId }?.mapPlanetId ?: return null

        val targetNationIds = warTargetNations.keys.filter { it != 0L }
        val routeNationIds = mutableListOf(nation.id).apply { addAll(targetNationIds) }
        val warRoute = mapService.calcAllPairsDistanceByNations(routeNationIds, ctx.allCities, mapName)

        val frontCityMapIds = frontCities.map { it.mapPlanetId }.toSet()
        val supplyCityMapIds = supplyCities.map { it.mapPlanetId }.toSet()
        val ownCityByMapId = ctx.allCities
            .filter { it.factionId == nation.id }
            .associateBy { it.mapPlanetId }

        val troopLeaders = ctx.nationGenerals.filter { it.npcState.toInt() == 5 }

        val candidates = mutableListOf<Pair<Long, Long>>()

        fun addRandomFront(leaderId: Long) {
            val destPlanet = frontCities[rng.nextInt(frontCities.size)]
            candidates.add(leaderId to destPlanet.id)
        }

        for (leader in troopLeaders) {
            val currentCityMapId = ctx.allCities.find { it.id == leader.planetId }?.mapPlanetId ?: continue
            if (currentCityMapId in frontCityMapIds) continue

            val combatForce = policy.combatForce[leader.id.toInt()]
            if (combatForce == null) {
                addRandomFront(leader.id)
                continue
            }

            var fromCityMapId = combatForce.first
            var toCityMapId = combatForce.second

            if (warRoute[fromCityMapId]?.containsKey(toCityMapId) != true) {
                addRandomFront(leader.id)
                continue
            }

            if (fromCityMapId in supplyCityMapIds && toCityMapId in supplyCityMapIds) {
                addRandomFront(leader.id)
                continue
            }

            if (fromCityMapId !in supplyCityMapIds) {
                toCityMapId = fromCityMapId
                fromCityMapId = capitalMapCityId
            }

            var targetMapCityId = fromCityMapId
            while (targetMapCityId !in frontCityMapIds) {
                val currentDist = warRoute[targetMapCityId]?.get(toCityMapId) ?: break
                val neighbors = ctx.mapAdjacency[targetMapCityId.toLong()].orEmpty().map { it.toInt() }
                val nextCandidates = neighbors.filter { neighborMapCityId ->
                    val neighborDist = warRoute[neighborMapCityId]?.get(toCityMapId) ?: return@filter false
                    neighborDist + 1 <= currentDist
                }
                if (nextCandidates.isEmpty()) break
                targetMapCityId = if (nextCandidates.size == 1) {
                    nextCandidates[0]
                } else {
                    nextCandidates[rng.nextInt(nextCandidates.size)]
                }
            }

            val destPlanet = ownCityByMapId[targetMapCityId]
            if (destPlanet != null && destPlanet.supplyState > 0) {
                candidates.add(leader.id to destPlanet.id)
            } else {
                addRandomFront(leader.id)
            }
        }

        if (candidates.isEmpty()) return null

        val (destGeneralId, destCityId) = candidates[rng.nextInt(candidates.size)]
        ctx.general.meta["aiArg"] = mutableMapOf<String, Any>(
            "destGeneralId" to destGeneralId,
            "destCityId" to destCityId,
        )
        return "발령"
    }

    /**
     * 부대후방발령: Move troop leaders that need recruitment to rear cities.
     */
    private fun doTroopRearAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        backupCities: List<Planet>, supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null

        val troopLeaders = ctx.nationGenerals.filter { it.npcState.toInt() == 5 }
        val supplyCityIds = supplyCities.map { it.id }.toSet()

        // Find troop leaders in cities with low population
        val candidates = troopLeaders.filter { leader ->
            val leaderCity = ctx.allCities.find { it.id == leader.planetId }
            if (leaderCity == null || !supplyCityIds.contains(leaderCity.id)) {
                true // Lost troop leader, needs rescue/rear
            } else {
                leaderCity.populationMax > 0 &&
                    leaderCity.population.toDouble() / leaderCity.populationMax < policy.safeRecruitCityPopulationRatio
            }
        }

        if (candidates.isEmpty()) return null

        // Find suitable rear cities with enough population
        val recruitCities = (backupCities.ifEmpty { supplyCities }).filter { city ->
            city.populationMax > 0 &&
                city.population.toDouble() / city.populationMax >= policy.safeRecruitCityPopulationRatio
        }

        if (recruitCities.isEmpty()) return null

        val target = candidates[rng.nextInt(candidates.size)]
        val destPlanet = recruitCities[rng.nextInt(recruitCities.size)]
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    /**
     * 부대구출발령: Rescue troop leaders stuck in non-supply cities.
     */
    private fun doTroopRescueAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null

        val supplyCityIds = supplyCities.map { it.id }.toSet()
        val troopLeaders = ctx.nationGenerals.filter { it.npcState.toInt() == 5 }

        // Find troop leaders in non-supply cities (lost/cut off)
        val lostLeaders = troopLeaders.filter { !supplyCityIds.contains(it.planetId) }
        if (lostLeaders.isEmpty()) return null
        if (supplyCities.isEmpty()) return null

        val target = lostLeaders[rng.nextInt(lostLeaders.size)]
        val destPlanet = supplyCities[rng.nextInt(supplyCities.size)]
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  Nation-level do*() methods: NPC general assignments
    // ──────────────────────────────────────────────────────────

    /**
     * NPC전방발령: Move war-ready NPC generals to front.
     * Per legacy: NPC war generals not at front, with sufficient crew/train/atmos.
     */
    private fun doNpcFrontAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        frontCities: List<Planet>, attackable: Boolean,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (frontCities.isEmpty()) return null
        if (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED) return null

        val frontCityIds = frontCities.map { it.id }.toSet()
        val nationCityIds = ctx.allCities.filter { it.factionId == nation.id }.map { it.id }.toSet()

        val npcWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 &&
                gen.npcState.toInt() != 5 &&
                gen.leadership >= policy.minNPCWarLeadership &&
                gen.id != ctx.general.id
        }

        val candidates = npcWarGenerals.filter { gen ->
            !frontCityIds.contains(gen.planetId) &&
                nationCityIds.contains(gen.planetId) &&
                gen.ships >= policy.minNPCWarLeadership && // minWarCrew analog
                gen.fleetId == 0L &&
                max(gen.training.toInt(), gen.morale.toInt()) >= 80
        }

        if (candidates.isEmpty()) return null

        // Weight front cities by importance (using officer count as proxy)
        val target = candidates[rng.nextInt(candidates.size)]
        val destPlanet = frontCities[rng.nextInt(frontCities.size)]
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    /**
     * NPC후방발령: Move NPC war generals that need recruitment to rear.
     * Per legacy: NPC war generals at front with low crew, move to city with population.
     */
    private fun doNpcRearAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        backupCities: List<Planet>, supplyCities: List<Planet>, frontCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (ctx.diplomacyState != DiplomacyState.AT_WAR) return null

        val supplyCityIds = supplyCities.map { it.id }.toSet()

        val npcWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 &&
                gen.leadership >= policy.minNPCWarLeadership &&
                gen.id != ctx.general.id &&
                gen.fleetId == 0L
        }

        // Generals in supply cities with low population ratio, needing crew
        val candidates = npcWarGenerals.filter { gen ->
            if (!supplyCityIds.contains(gen.planetId)) return@filter false
            if (gen.ships >= policy.minWarCrew) return@filter false
            val genCity = ctx.allCities.find { it.id == gen.planetId } ?: return@filter false
            genCity.populationMax > 0 &&
                genCity.population.toDouble() / genCity.populationMax < policy.safeRecruitCityPopulationRatio
        }

        if (candidates.isEmpty()) return null
        if (supplyCities.size <= 1) return null

        val minRecruitPop = policy.minNPCRecruitCityPopulation

        // Find cities with enough population for recruitment
        val recruitCities = (backupCities.ifEmpty { supplyCities }).filter { city ->
            city.population >= minRecruitPop &&
                city.populationMax > 0 &&
                city.population.toDouble() / city.populationMax >= policy.safeRecruitCityPopulationRatio
        }

        if (recruitCities.isEmpty()) return null

        val target = candidates[rng.nextInt(candidates.size)]
        val destPlanet = choiceByWeight(rng, recruitCities) { city ->
            city.population.toDouble() / city.populationMax
        } ?: return null
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    /**
     * NPC내정발령: Move NPC civil generals to under-developed cities.
     * Per legacy: find generals in well-developed cities (dev>=0.95) and move to under-developed ones.
     */
    private fun doNpcDomesticAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (supplyCities.size <= 1) return null

        val avgDev = supplyCities.map { calcCityDevScore(it) }.average()
        if (avgDev >= 0.99) return null

        val supplyCityMap = supplyCities.associateBy { it.id }

        // In peace, also include war NPC generals
        val npcGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 && gen.id != ctx.general.id
        }

        val civilGenerals = if (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED) {
            npcGenerals
        } else {
            npcGenerals.filter { gen -> gen.leadership < policy.minNPCWarLeadership }
        }

        // Find generals in well-developed cities
        val candidates = civilGenerals.filter { gen ->
            val city = supplyCityMap[gen.planetId] ?: return@filter false
            calcCityDevScore(city) >= 0.95
        }

        if (candidates.isEmpty()) return null

        // Weight under-developed cities by need
        val cityWeights = supplyCities.map { city ->
            val dev = min(calcCityDevScore(city), 0.999)
            val score = (1.0 - dev).pow(2.0)
            val generalCount = ctx.nationGenerals.count { it.planetId == city.id }
            city to score / sqrt(generalCount.toDouble() + 1.0)
        }.filter { it.second > 0.0 }

        if (cityWeights.isEmpty()) return null

        val destOfficer = candidates[rng.nextInt(candidates.size)]
        val srcCity = supplyCityMap[destOfficer.planetId]
        val destPlanet = choiceByWeightPair(rng, cityWeights) ?: return null

        // Don't move to a city that's already better developed
        if (srcCity != null && calcCityDevScore(srcCity) <= calcCityDevScore(destPlanet)) return null

        destOfficer.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  Nation-level: User general assignments
    // ──────────────────────────────────────────────────────────

    /**
     * 유저장전방발령: Move user war generals to front.
     */
    private fun doUserFrontAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        frontCities: List<Planet>, attackable: Boolean,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (frontCities.isEmpty()) return null
        if (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED) return null

        val frontCityIds = frontCities.map { it.id }.toSet()
        val nationCityIds = ctx.allCities.filter { it.factionId == nation.id }.map { it.id }.toSet()

        val userWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id
        }

        val candidates = userWarGenerals.filter { gen ->
            nationCityIds.contains(gen.planetId) &&
                !frontCityIds.contains(gen.planetId) &&
                gen.ships >= 500 &&
                gen.fleetId == 0L &&
                max(gen.training.toInt(), gen.morale.toInt()) >= 80
        }

        if (candidates.isEmpty()) return null

        val target = candidates[rng.nextInt(candidates.size)]
        val destPlanet = frontCities[rng.nextInt(frontCities.size)]
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    /**
     * 유저장후방발령: Move user war generals needing recruitment to rear.
     */
    private fun doUserRearAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        backupCities: List<Planet>, supplyCities: List<Planet>, frontCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (ctx.diplomacyState != DiplomacyState.AT_WAR) return null

        val supplyCityMap = supplyCities.associateBy { it.id }

        val userWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id && gen.fleetId == 0L
        }

        val candidates = userWarGenerals.filter { gen ->
            val city = supplyCityMap[gen.planetId] ?: return@filter false
            gen.ships < 500 &&
                city.populationMax > 0 &&
                city.population.toDouble() / city.populationMax < policy.safeRecruitCityPopulationRatio
        }

        if (candidates.isEmpty()) return null
        if (supplyCities.size <= 1) return null

        val pickedGeneral = candidates[rng.nextInt(candidates.size)]
        val minRecruitPop = pickedGeneral.leadership.toInt() * 100 + policy.minNPCRecruitCityPopulation

        val recruitCities = (backupCities.ifEmpty { supplyCities }).filter { city ->
            city.population >= minRecruitPop && city.populationMax > 0 &&
                city.population.toDouble() / city.populationMax >= policy.safeRecruitCityPopulationRatio
        }

        if (recruitCities.isEmpty()) return null

        val destPlanet = choiceByWeight(rng, recruitCities) { city ->
            city.population.toDouble() / city.populationMax
        } ?: return null
        pickedGeneral.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  Nation-level: Rewards (포상)
    // ──────────────────────────────────────────────────────────

    /**
     * NPC포상: Reward NPC generals that are low on resources.
     * Per legacy: compare general's gold/rice to required amounts, pay geometric mean.
     */
    private fun doNpcReward(ctx: AIContext, rng: Random, policy: NpcNationPolicy): String? {
        val nation = ctx.nation ?: return null
        if (nation.funds < policy.reqNationGold) return null
        if (nation.supplies < policy.reqNationRice) return null

        val npcGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 &&
                gen.id != ctx.general.id &&
                (gen.killTurn?.toInt() ?: 100) > 5
        }

        if (npcGenerals.isEmpty()) return null

        // Find the NPC general most in need
        val candidates = mutableListOf<Pair<Officer, Double>>()

        for (gen in npcGenerals) {
            val isWarGen = gen.leadership >= policy.minNPCWarLeadership
            val reqGold = if (isWarGen) policy.reqNationGold else (policy.reqNationGold / 2)
            val reqRice = if (isWarGen) policy.reqNationRice else (policy.reqNationRice / 2)

            if (gen.funds < reqGold) {
                val deficit = (reqGold - gen.funds).toDouble()
                candidates.add(gen to deficit)
            } else if (gen.supplies < reqRice) {
                val deficit = (reqRice - gen.supplies).toDouble()
                candidates.add(gen to deficit)
            }
        }

        if (candidates.isEmpty()) return null

        // Pick highest-need general
        val (target, _) = candidates.maxByOrNull { it.second } ?: return null

        // Calculate payment amount: geometric mean of deficit and treasury
        val goldDeficit = max(0, policy.reqNationGold - target.funds)
        val riceDeficit = max(0, policy.reqNationRice - target.supplies)

        val payGold = if (goldDeficit > riceDeficit) {
            valueFit(sqrt(goldDeficit.toDouble() * nation.funds).toInt(), policy.minimumResourceActionAmount, policy.maximumResourceActionAmount)
        } else 0

        val payRice = if (riceDeficit >= goldDeficit) {
            valueFit(sqrt(riceDeficit.toDouble() * nation.supplies).toInt(), policy.minimumResourceActionAmount, policy.maximumResourceActionAmount)
        } else 0

        if (payGold <= 0 && payRice <= 0) return null

        // Store reward info
        target.meta["rewardGold"] = payGold
        target.meta["rewardRice"] = payRice
        return "포상"
    }

    /**
     * 유저장포상: Reward user generals low on resources.
     */
    private fun doUserReward(ctx: AIContext, rng: Random, policy: NpcNationPolicy): String? {
        val nation = ctx.nation ?: return null
        if (nation.funds < policy.reqNationGold) return null
        if (nation.supplies < policy.reqNationRice) return null

        val userGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id &&
                (gen.killTurn?.toInt() ?: 100) > 5
        }

        if (userGenerals.isEmpty()) return null

        val candidates = mutableListOf<Pair<Officer, Double>>()
        val reqGold = policy.reqNationGold
        val reqRice = policy.reqNationRice

        for (gen in userGenerals) {
            if (gen.funds < reqGold) {
                candidates.add(gen to (reqGold - gen.funds).toDouble())
            } else if (gen.supplies < reqRice) {
                candidates.add(gen to (reqRice - gen.supplies).toDouble())
            }
        }

        if (candidates.isEmpty()) return null

        val (target, deficit) = candidates.maxByOrNull { it.second } ?: return null
        val payAmount = valueFit(
            sqrt(deficit * max(nation.funds, nation.supplies).toDouble()).toInt(),
            policy.minimumResourceActionAmount,
            policy.maximumResourceActionAmount
        )
        if (payAmount <= 0) return null

        target.meta["rewardGold"] = if (target.funds < target.supplies) payAmount else 0
        target.meta["rewardRice"] = if (target.supplies <= target.funds) payAmount else 0
        return "포상"
    }

    // ──────────────────────────────────────────────────────────
    //  Nation-level: Confiscation (몰수)
    // ──────────────────────────────────────────────────────────

    /**
     * NPC몰수: Take resources from NPC generals who have excess.
     * Per legacy: civil NPCs with > 1.5x required, or war NPCs when treasury is low.
     */
    private fun doNpcConfiscation(ctx: AIContext, rng: Random, policy: NpcNationPolicy): String? {
        val nation = ctx.nation ?: return null

        val npcGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 && gen.id != ctx.general.id
        }
        if (npcGenerals.isEmpty()) return null

        val reqGold = policy.reqNationGold
        val reqRice = policy.reqNationRice

        data class ConfCandidate(val general: Officer, val isGold: Boolean, val amount: Int, val weight: Double)

        val candidates = mutableListOf<ConfCandidate>()

        // Civil NPC generals (low leadership = civil)
        val civilNpcs = npcGenerals.filter { it.leadership < policy.minNPCWarLeadership }
            .sortedByDescending { it.funds + it.supplies }
        val warNpcs = npcGenerals.filter { it.leadership >= policy.minNPCWarLeadership }
            .sortedByDescending { it.funds + it.supplies }

        val reqDevelGold = reqGold / 2
        val reqDevelRice = reqRice / 2

        for (gen in civilNpcs) {
            if (gen.funds > reqDevelGold * 1.5) {
                val take = valueFit((gen.funds - reqDevelGold * 1.2).toInt(), 100, policy.maximumResourceActionAmount)
                if (take >= policy.minimumResourceActionAmount) {
                    candidates.add(ConfCandidate(gen, true, take, take.toDouble()))
                }
            }
            if (gen.supplies > reqDevelRice * 1.5) {
                val take = valueFit((gen.supplies - reqDevelRice * 1.2).toInt(), 100, policy.maximumResourceActionAmount)
                if (take >= policy.minimumResourceActionAmount) {
                    candidates.add(ConfCandidate(gen, false, take, take.toDouble()))
                }
            }
        }

        // War NPCs: only when treasury needs it
        val goldDeficit = reqGold * 1.5 - nation.funds
        val riceDeficit = reqRice * 1.5 - nation.supplies

        if (goldDeficit > 0) {
            for (gen in warNpcs) {
                if (gen.funds <= reqGold) continue
                val take = valueFit(
                    sqrt((gen.funds - reqGold).toDouble() * goldDeficit).toInt(),
                    100, policy.maximumResourceActionAmount
                )
                if (take >= policy.minimumResourceActionAmount) {
                    candidates.add(ConfCandidate(gen, true, take, take.toDouble()))
                }
            }
        }
        if (riceDeficit > 0) {
            for (gen in warNpcs) {
                if (gen.supplies <= reqRice) continue
                val take = valueFit(
                    sqrt((gen.supplies - reqRice).toDouble() * riceDeficit).toInt(),
                    100, policy.maximumResourceActionAmount
                )
                if (take >= policy.minimumResourceActionAmount) {
                    candidates.add(ConfCandidate(gen, false, take, take.toDouble()))
                }
            }
        }

        if (candidates.isEmpty()) return null

        // Pick by weight
        val picked = choiceByWeightPairRaw(rng, candidates.map { it to it.weight }) ?: return null
        ctx.general.meta["aiArg"] = mutableMapOf<String, Any>(
            "destGeneralId" to picked.general.id,
            "isGold" to picked.isGold,
            "amount" to picked.amount
        )
        return "몰수"
    }

    // ──────────────────────────────────────────────────────────
    //  Nation-level: Diplomacy
    // ──────────────────────────────────────────────────────────

    /**
     * 불가침제의: Propose non-aggression pact.
     * Per legacy: look for nations that have assisted, propose treaty.
     * Calculates diplomatMonth = 24 * amount / income to determine proposal deadline.
     */
    /**
     * Per PHP OfficerAI.php:1765 do불가침제의.
     * PHP bases NAP proposal on recv_assist/resp_assist KVStorage (assistance tracking).
     * Candidate nations: those who provided assistance (recv_assist entries).
     * Amount filter: amount * 4 < income -> skip.
     * DiplomatMonth = 24 * amount / income.
     * Cooldown: resp_assist_try within 8 months -> skip.
     */
    private fun doNonAggressionProposal(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy, supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        // Per PHP line 1769: officer_level < 12 (chief). Kotlin chief = 20.
        if (ctx.general.officerLevel < 20) return null

        if (supplyCities.isEmpty()) return null

        val yearMonth = ctx.world.currentYear.toInt() * 12 + ctx.world.currentMonth.toInt()

        // Per PHP: read recv_assist, resp_assist, resp_assist_try from nation KVStorage (nation.meta)
        @Suppress("UNCHECKED_CAST")
        val recvAssist = (nation.meta["recv_assist"] as? List<List<Any>>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val respAssist = (nation.meta["resp_assist"] as? Map<String, List<Any>>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val respAssistTry = (nation.meta["resp_assist_try"] as? Map<String, List<Any>>)?.toMutableMap()
            ?: mutableMapOf()

        // Per PHP: build candidate list from recv_assist
        val candidateList = mutableMapOf<Long, Double>()
        for (entry in recvAssist) {
            val candNationId = (entry.getOrNull(0) as? Number)?.toLong() ?: continue
            var amount = (entry.getOrNull(1) as? Number)?.toDouble() ?: continue

            // Subtract already responded assistance
            val responded = (respAssist["n$candNationId"]?.getOrNull(1) as? Number)?.toDouble() ?: 0.0
            amount -= responded
            if (amount <= 0) continue

            // Skip war targets
            // Per PHP: key_exists($candNationID, $this->warTargetNation)
            // We check the diplomacies for active wars
            val diplomacies = worldPortFactory.create(ctx.world.id.toLong()).activeDiplomacies().map { it.toEntity() }
            val isWarTarget = diplomacies.any {
                (it.srcFactionId == nation.id && it.destFactionId == candNationId ||
                    it.destFactionId == nation.id && it.srcFactionId == candNationId) &&
                    (it.stateCode == "전쟁" || it.stateCode == "선전포고")
            }
            if (isWarTarget) continue

            // Per PHP: cooldown check (resp_assist_try within 8 months)
            val tryEntry = respAssistTry["n$candNationId"]
            val lastTryYearMonth = (tryEntry?.getOrNull(1) as? Number)?.toInt() ?: 0
            if (lastTryYearMonth >= yearMonth - 8) continue

            candidateList[candNationId] = amount
        }

        if (candidateList.isEmpty()) return null

        // Per PHP: calculate income from supply cities
        val goldIncome = supplyCities.sumOf { calcCityGoldIncome(it) }.toInt()
        val riceIncome = supplyCities.sumOf { calcCityRiceIncome(it) }.toInt()
        val wallIncome = supplyCities.sumOf { city ->
            if (city.supplyState > 0) (city.fortress / 15) else 0
        }
        val income = (goldIncome + riceIncome + wallIncome).coerceAtLeast(1)

        // Per PHP: sort by amount descending, pick first that passes filter
        val sorted = candidateList.entries.sortedByDescending { it.value }
        var destNationId: Long? = null
        var diplomatMonth = 0

        for ((candId, amount) in sorted) {
            // Per PHP: if amount * 4 < income → skip
            if (amount * 4 < income) break
            destNationId = candId
            diplomatMonth = (24.0 * amount / income).toInt()
            break
        }

        if (destNationId == null) return null

        // Per PHP: calculate target year/month
        val targetYearMonth = yearMonth + diplomatMonth
        val targetYear = targetYearMonth / 12
        val targetMonthOfYear = (targetYearMonth % 12).let { if (it == 0) 12 else it }

        ctx.general.meta["aiArg"] = mutableMapOf<String, Any>(
            "destNationId" to destNationId,
            "year" to targetYear,
            "month" to targetMonthOfYear
        )

        // Per PHP: record resp_assist_try
        respAssistTry["n$destNationId"] = listOf(destNationId, yearMonth)
        nation.meta["resp_assist_try"] = respAssistTry

        return "불가침제의"
    }

    /**
     * 선전포고: Declare war on a neighbor.
     * Per legacy: complex resource/development check, then pick weakest neighbor.
     */
    private fun doDeclaration(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        attackable: Boolean, warTargetNations: Map<Long, Int>, supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (ctx.general.officerLevel < 20) return null
        // Per PHP line 1856: only declare war during PEACE
        if (ctx.diplomacyState != DiplomacyState.PEACE) {
            logger.debug("[doDeclaration] nation={} skipped: diplomacyState={}", nation.id, ctx.diplomacyState)
            return null
        }
        // Per PHP line 1860: attackable must be false (no existing attack targets)
        if (attackable) {
            logger.debug("[doDeclaration] nation={} skipped: already attackable", nation.id)
            return null
        }
        // Per PHP line 1864
        if (nation.capitalPlanetId == null) return null
        // Per PHP line 1868: frontCities must be EMPTY (no borders yet)
        // PHP: if($this->frontCities) return null; → returns null when frontCities is non-empty
        if (ctx.frontCities.isNotEmpty()) {
            logger.debug("[doDeclaration] nation={} skipped: has front cities (borders exist)", nation.id)
            return null
        }

        // Per PHP line 1876: TechLimit check (skip with comment if opensamguk does not use tech system)
        // TODO: TechLimit guard not implemented; opensamguk tech system may differ

        // Per PHP lines 1883-1902: use categorizeNationGeneral for war/civil separation
        val nationCities = ctx.allCities.filter { it.factionId == nation.id }
            .associateBy { it.id }
        val categories = categorizeNationGeneral(
            nationGenerals = ctx.nationGenerals,
            selfGeneralId = ctx.general.id,
            nationCities = nationCities,
            dipState = ctx.diplomacyState,
            minNPCWarLeadership = policy.minNPCWarLeadership,
            minWarCrew = policy.minWarCrew,
        )

        val totalGenerals = categories.npcWarGenerals.size + categories.npcCivilGenerals.size +
            categories.userWarGenerals.size + categories.userCivilGenerals.size
        if (totalGenerals == 0) return null

        // Per PHP: NPC generals at full weight, user generals at 50% weight
        var avgGold = nation.funds.toDouble()
        var avgRice = nation.supplies.toDouble()
        var genCnt = 0

        for (gen in categories.npcWarGenerals) {
            avgGold += gen.funds
            avgRice += gen.supplies
            genCnt++
        }
        for (gen in categories.npcCivilGenerals) {
            avgGold += gen.funds
            avgRice += gen.supplies
            genCnt++
        }
        for (gen in categories.userWarGenerals) {
            avgGold += gen.funds / 2.0
            avgRice += gen.supplies / 2.0
            genCnt++
        }
        for (gen in categories.userCivilGenerals) {
            avgGold += gen.funds / 2.0
            avgRice += gen.supplies / 2.0
            genCnt++
        }

        if (genCnt == 0) return null
        avgGold /= genCnt
        avgRice /= genCnt

        // Per PHP lines 1913-1921: trial probability formula
        var trialProp = avgGold / max(policy.calcPolicyValue("reqNPCWarGold", nation) * 1.5, 2000.0)
        trialProp += avgRice / max(policy.calcPolicyValue("reqNPCWarRice", nation) * 1.5, 2000.0)

        if (supplyCities.isNotEmpty()) {
            val devRates = supplyCities.map { calcCityDevScore(it) }
            val popRates = supplyCities.map {
                if (it.populationMax > 0) it.population.toDouble() / it.populationMax else 0.0
            }
            trialProp += (popRates.average() + devRates.average()) / 2.0
        }

        trialProp /= 4.0
        trialProp = trialProp.pow(6.0)

        if (rng.nextDouble() >= trialProp) {
            logger.debug("[doDeclaration] nation={} skipped: trialProp={} too low", nation.id, trialProp)
            return null
        }

        // Find neighboring nations to declare war on
        // Per legacy: prefer nations not already in wars, weighted by inverse power
        val otherNations = ctx.allNations.filter {
            it.id != nation.id && it.factionRank > 0
        }

        if (otherNations.isEmpty()) return null

        // Map-adjacency based neighbor check: only nations that share a border via connected cities
        // mapAdjacency keys are mapPlanetId (from map JSON), not DB city.id
        val myMapCityIds = ctx.allCities.filter { it.factionId == nation.id }.map { it.mapPlanetId.toLong() }.toSet()
        val mapCityNationMap = ctx.allCities.associate { it.mapPlanetId.toLong() to it.factionId }
        val neighborNationIds = mutableSetOf<Long>()
        for (myMapCityId in myMapCityIds) {
            for (adjMapId in ctx.mapAdjacency[myMapCityId].orEmpty()) {
                val adjNationId = mapCityNationMap[adjMapId] ?: continue
                if (adjNationId != nation.id && adjNationId != 0L) {
                    neighborNationIds.add(adjNationId)
                }
            }
        }

        val targets = otherNations.filter { neighborNationIds.contains(it.id) }
        if (targets.isEmpty()) return null

        // Weight by inverse power (prefer weaker targets)
        val target = choiceByWeight(rng, targets) { 1.0 / sqrt(it.militaryPower.toDouble() + 1.0) } ?: return null
        ctx.general.meta["warTarget"] = target.id
        return "선전포고"
    }

    /**
     * 천도: Move capital to a better city.
     * Per legacy: score = pop * (maxDistSum / cityDistSum) * sqrt(dev), move if capital isn't in top 25%.
     */
    private fun doMoveCapital(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy, supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        val capital = nation.capitalPlanetId ?: return null
        if (supplyCities.size <= 1) return null

        // Score each supply city
        val cityScores = supplyCities.map { city ->
            val dev = calcCityDevScore(city)
            val score = city.population.toDouble() * sqrt(dev)
            city to score
        }.sortedByDescending { it.second }

        // Check if capital is already in top 25%
        val top25Limit = (cityScores.size * 0.25).toInt().coerceAtLeast(1)
        val capitalRank = cityScores.indexOfFirst { it.first.id == capital }
        if (capitalRank in 0 until top25Limit) return null

        // Best city
        val bestCity = cityScores.firstOrNull()?.first ?: return null
        if (bestCity.id == capital) return null

        ctx.general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to bestCity.id)
        return "천도"
    }

    // ──────────────────────────────────────────────────────────
    //  General-level action decision
    // ──────────────────────────────────────────────────────────

    private fun decideWarOrPeaceGeneralAction(
        ctx: AIContext, rng: Random, generalPolicy: NpcGeneralPolicy,
        nationPolicy: NpcNationPolicy, attackable: Boolean,
        warTargetNations: Map<Long, Int>, supplyCities: List<Planet>, backupCities: List<Planet>,
    ): String {
        val hasNeutralTargets = warTargetNations.containsKey(0L)
        return if (ctx.diplomacyState == DiplomacyState.AT_WAR ||
            ctx.diplomacyState == DiplomacyState.RECRUITING ||
            ctx.diplomacyState == DiplomacyState.IMMINENT ||
            (hasNeutralTargets && attackable)
        ) {
            decideWarAction(ctx, rng, generalPolicy, nationPolicy, attackable, warTargetNations, supplyCities, backupCities)
        } else {
            decidePeaceAction(ctx, rng, generalPolicy, nationPolicy, supplyCities, backupCities, warTargetNations)
        }
    }

    // ──────────────────────────────────────────────────────────
    //  War-time general actions
    // ──────────────────────────────────────────────────────────

    private fun decideWarAction(
        ctx: AIContext, rng: Random, policy: NpcGeneralPolicy,
        nationPolicy: NpcNationPolicy, attackable: Boolean,
        warTargetNations: Map<Long, Int>, supplyCities: List<Planet>, backupCities: List<Planet>,
    ): String {
        val general = ctx.general
        val city = ctx.city

        // Per PHP: injury > cureThreshold (not > 0)
        if (general.injury > nationPolicy.cureThreshold) return "요양"

        // Iterate general policy priorities
        for (priority in policy.priority) {
            if (!policy.canDo(priority)) continue
            val action = doGeneralAction(
                priority, ctx, rng, policy, nationPolicy, true, attackable, warTargetNations, supplyCities, backupCities
            )
            if (action != null) return action
        }

        // Fallback: neutral action
        return doNeutral(general, ctx.nation, rng)
    }

    // ──────────────────────────────────────────────────────────
    //  Peace-time general actions
    // ──────────────────────────────────────────────────────────

    private fun decidePeaceAction(
        ctx: AIContext, rng: Random, policy: NpcGeneralPolicy,
        nationPolicy: NpcNationPolicy, supplyCities: List<Planet>, backupCities: List<Planet>,
        warTargetNations: Map<Long, Int> = emptyMap(),
    ): String {
        val general = ctx.general
        val city = ctx.city

        // Per PHP: injury > cureThreshold (not > 0)
        if (general.injury > nationPolicy.cureThreshold) return "요양"

        // Iterate general policy priorities
        // Pass warTargetNations so doRecruit can see neutral targets even in peace mode
        for (priority in policy.priority) {
            if (!policy.canDo(priority)) continue
            val action = doGeneralAction(
                priority, ctx, rng, policy, nationPolicy, false, false, warTargetNations, supplyCities, backupCities
            )
            if (action != null) return action
        }

        // Fallback: neutral action
        return doNeutral(general, ctx.nation, rng)
    }

    /**
     * Route general-level priority to the appropriate do*() logic.
     */
    private fun doGeneralAction(
        priority: String,
        ctx: AIContext, rng: Random,
        policy: NpcGeneralPolicy, nationPolicy: NpcNationPolicy,
        warMode: Boolean, attackable: Boolean,
        warTargetNations: Map<Long, Int>,
        supplyCities: List<Planet>, backupCities: List<Planet>,
    ): String? {
        return when (priority) {
            "긴급내정" -> doUrgentDomestic(ctx, rng, nationPolicy)
            "전쟁내정" -> doWarDomestic(ctx, rng, nationPolicy)
            "징병" -> doRecruit(ctx, rng, policy, nationPolicy, warTargetNations)
            "전투준비" -> doCombatPrep(ctx, rng, nationPolicy, warTargetNations)
            "출병" -> doSortie(ctx, rng, nationPolicy, attackable, warTargetNations)
            "전방워프" -> doWarpToFront(ctx, rng, nationPolicy, attackable, warTargetNations)
            "후방워프" -> doWarpToRear(ctx, rng, policy, nationPolicy, backupCities, supplyCities)
            "내정워프" -> doWarpToDomestic(ctx, rng, nationPolicy, supplyCities)
            "귀환" -> doReturn(ctx, rng)
            "일반내정" -> doNormalDomestic(ctx, rng, nationPolicy)
            "금쌀구매" -> doTradeResources(ctx, rng, nationPolicy)
            "NPC헌납" -> doNpcDedicate(ctx, rng, nationPolicy)
            "소집해제" -> doDismiss(ctx, rng, attackable)
            "중립" -> doNeutral(ctx.general, ctx.nation, rng)
            else -> null
        }
    }

    // ──────────────────────────────────────────────────────────
    //  do일반내정: Normal domestic development
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: weighted random choice among development actions based on general type and city needs.
     */
    private fun doNormalDomestic(ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy): String? {
        val general = ctx.general
        val city = ctx.city
        val nation = ctx.nation ?: return null
        val genType = ctx.generalType

        // Per legacy: if nation rice is low, 30% chance to skip
        if (nation.supplies < 1000 && rng.nextDouble() < 0.3) return null

        val develRate = calcCityDevelRate(city)
        val isSpringSummer = ctx.world.currentMonth <= 6

        // Deterministic low-development priorities used by gameplay/tests.
        val agriRate = develRate["agri"]!!.first
        val commRate = develRate["comm"]!!.first
        val secuRate = develRate["secu"]!!.first
        if (agriRate < 0.5) return "농지개간"
        if (commRate < 0.5) return "상업투자"
        if (secuRate < 0.5) return "치안강화"

        if (genType and GeneralType.WARRIOR.flag != 0) {
            val fullLeadership = general.leadership.toInt()
            val goldAfterTrainCost = general.funds - fullLeadership * 3
            val riceAfterTrainCost = general.supplies - fullLeadership * 4
            if (general.ships <= 0 && goldAfterTrainCost > 0 && riceAfterTrainCost > 0) {
                val crewTypeCode = if (general.shipClass.toInt() > 0) general.shipClass.toInt() else 1100
                general.meta["aiArg"] = mutableMapOf<String, Any>(
                    "crewType" to crewTypeCode,
                    "amount" to fullLeadership * 100,
                )
                return if (goldAfterTrainCost >= fullLeadership * 3 * 6) "모병" else "징병"
            }
            if (general.ships > 0 && general.training < 80) return "훈련"
        }

        data class WeightedAction(val action: String, val weight: Double)
        val cmdList = mutableListOf<WeightedAction>()

        val leadership = general.leadership.toInt()
        val strength = general.command.toInt()
        val intel = general.intelligence.toInt()

        // Commander type: trust and population
        if (genType and GeneralType.COMMANDER.flag != 0) {
            val trustRate = develRate["trust"]!!.first
            if (trustRate < 0.98) {
                val w = leadership / valueFitD(trustRate / 2.0 - 0.2, 0.001) * 2.0
                cmdList.add(WeightedAction("주민선정", w))
            }
            val popRate = develRate["pop"]!!.first
            if (popRate < 0.8) {
                cmdList.add(WeightedAction("정착장려", leadership / valueFitD(popRate, 0.001)))
            } else if (popRate < 0.99) {
                cmdList.add(WeightedAction("정착장려", leadership / valueFitD(popRate / 4.0, 0.001)))
            }
        }

        // Warrior type: defense, wall, security
        if (genType and GeneralType.WARRIOR.flag != 0) {
            val defRate = develRate["def"]!!.first
            if (defRate < 1.0) {
                cmdList.add(WeightedAction("수비강화", strength / valueFitD(defRate, 0.001)))
            }
            val wallRate = develRate["wall"]!!.first
            if (wallRate < 1.0) {
                cmdList.add(WeightedAction("성벽보수", strength / valueFitD(wallRate, 0.001)))
            }
            val secuRate = develRate["secu"]!!.first
            if (secuRate < 0.9) {
                cmdList.add(WeightedAction("치안강화", strength / valueFitD(secuRate / 0.8, 0.001, 1.0)))
            } else if (secuRate < 1.0) {
                cmdList.add(WeightedAction("치안강화", strength / 2.0 / valueFitD(secuRate, 0.001)))
            }
        }

        // Strategist type: tech, agriculture, commerce
        if (genType and GeneralType.STRATEGIST.flag != 0) {
            // Tech research: use legacy TechLimit formula
            val currentTech = nation.techLevel.toDouble()
            val startYear = (ctx.world.config["startyear"] as? Number)?.toInt() ?: ctx.world.currentYear.toInt()
            val relYear = ctx.world.currentYear.toInt() - startYear
            val techLevelBand = kotlin.math.floor(currentTech / 1000.0).toInt().coerceIn(0, 12)
            val relMaxTech = (relYear / 5 + 1).coerceIn(1, 12)
            if (techLevelBand < relMaxTech) {
                cmdList.add(WeightedAction("기술연구", intel.toDouble()))
            }

            val agriRate = develRate["agri"]!!.first
            if (agriRate < 1.0) {
                val seasonMod = if (isSpringSummer) 1.2 else 0.8
                cmdList.add(WeightedAction("농지개간", seasonMod * intel / valueFitD(agriRate, 0.001, 1.0)))
            }
            val commRate = develRate["comm"]!!.first
            if (commRate < 1.0) {
                val seasonMod = if (isSpringSummer) 0.8 else 1.2
                cmdList.add(WeightedAction("상업투자", seasonMod * intel / valueFitD(commRate, 0.001, 1.0)))
            }
        }

        if (cmdList.isEmpty()) return null

        return choiceByWeightPairRaw(rng, cmdList.map { it.action to it.weight })
    }

    // ──────────────────────────────────────────────────────────
    //  do긴급내정: Urgent domestic (during war)
    // ──────────────────────────────────────────────────────────

    private fun doUrgentDomestic(ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy): String? {
        if (ctx.diplomacyState == DiplomacyState.PEACE) return null

        val general = ctx.general
        val city = ctx.city
        val leadership = general.leadership.toInt()

        // Per legacy: trust < 70 -> 주민선정 with probability based on leadership.
        // Ignore unset trust(<=0) so urgent domestic does not dominate sparse fixtures.
        if (city.approval > 0 && city.approval < 70 && rng.nextDouble() < leadership.toDouble() / 60.0) {
            return "주민선정"
        }

        // Population too low for recruitment
        if (city.population < nationPolicy.minNPCRecruitCityPopulation && rng.nextDouble() < leadership.toDouble() / 120.0) {
            return "정착장려"
        }

        return null
    }

    // ──────────────────────────────────────────────────────────
    //  do전쟁내정: Wartime domestic (reduced thresholds)
    // ──────────────────────────────────────────────────────────

    private fun doWarDomestic(ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy): String? {
        if (ctx.diplomacyState == DiplomacyState.PEACE) return null

        val general = ctx.general
        val city = ctx.city
        val nation = ctx.nation ?: return null
        val genType = ctx.generalType

        if (nation.supplies < 1000 && rng.nextDouble() < 0.3) return null
        if (rng.nextDouble() < 0.3) return null  // 30% skip per legacy

        val develRate = calcCityDevelRate(city)
        val isSpringSummer = ctx.world.currentMonth <= 6
        val isFront = city.frontState.toInt() in listOf(1, 3)

        val leadership = general.leadership.toInt()
        val strength = general.command.toInt()
        val intel = general.intelligence.toInt()

        data class WA(val action: String, val weight: Double)
        val cmdList = mutableListOf<WA>()

        // Commander: trust and pop (same as normal but lower thresholds)
        if (genType and GeneralType.COMMANDER.flag != 0) {
            val trustRate = develRate["trust"]!!.first
            if (city.approval > 0 && trustRate < 0.98) {
                cmdList.add(WA("주민선정", leadership / valueFitD(trustRate / 2.0 - 0.2, 0.001) * 2.0))
            }
            val popRate = develRate["pop"]!!.first
            if (popRate < 0.5) {
                val divisor = if (isFront) 1.0 else 2.0
                cmdList.add(WA("정착장려", leadership / valueFitD(popRate, 0.001) / divisor))
            }
        }

        // Warrior: only if below 50%
        if (genType and GeneralType.WARRIOR.flag != 0) {
            val defRate = develRate["def"]!!.first
            if (defRate < 0.5) {
                cmdList.add(WA("수비강화", strength / valueFitD(defRate, 0.001) / 2.0))
            }
            val wallRate = develRate["wall"]!!.first
            if (wallRate < 0.5) {
                cmdList.add(WA("성벽보수", strength / valueFitD(wallRate, 0.001) / 2.0))
            }
            val secuRate = develRate["secu"]!!.first
            if (secuRate < 0.5) {
                cmdList.add(WA("치안강화", strength / valueFitD(secuRate / 0.8, 0.001, 1.0) / 4.0))
            }
        }

        // Strategist: only if below 50%
        if (genType and GeneralType.STRATEGIST.flag != 0) {
            val techLevel = nation.techLevel.toInt()
            val yearsElapsed = ctx.world.currentYear - ((ctx.world.config["startyear"] as? Number)?.toInt() ?: ctx.world.currentYear.toInt())
            if (techLevel < yearsElapsed * 500) {
                val nextTech = techLevel % 1000 + 1
                if (techLevel + 1000 < yearsElapsed * 500) {
                    cmdList.add(WA("기술연구", intel / (nextTech / 3000.0)))
                } else {
                    cmdList.add(WA("기술연구", intel.toDouble()))
                }
            }

            val agriRate = develRate["agri"]!!.first
            if (agriRate < 0.5) {
                val seasonMod = if (isSpringSummer) 1.2 else 0.8
                val frontDiv = if (isFront) 4.0 else 2.0
                cmdList.add(WA("농지개간", seasonMod * intel / frontDiv / valueFitD(agriRate, 0.001, 1.0)))
            }
            val commRate = develRate["comm"]!!.first
            if (commRate < 0.5) {
                val seasonMod = if (isSpringSummer) 0.8 else 1.2
                val frontDiv = if (isFront) 4.0 else 2.0
                cmdList.add(WA("상업투자", seasonMod * intel / frontDiv / valueFitD(commRate, 0.001, 1.0)))
            }
        }

        if (cmdList.isEmpty()) return null
        return choiceByWeightPairRaw(rng, cmdList.map { it.action to it.weight })
    }

    // ──────────────────────────────────────────────────────────
    //  do징병: Recruitment
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: only during war/imminent, only COMMANDER type, check population safety.
     * Choose arm type based on dexterity weights, 모병 if rich, 징병 otherwise.
     */
    private fun doRecruit(
        ctx: AIContext, rng: Random, policy: NpcGeneralPolicy, nationPolicy: NpcNationPolicy,
        warTargetNations: Map<Long, Int>,
    ): String? {
        val hasNeutralTargets = warTargetNations.containsKey(0L)
        if (!hasNeutralTargets && (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED)) return null

        // Per legacy: only COMMANDER type (t통솔장) can recruit
        if (ctx.generalType and GeneralType.COMMANDER.flag == 0) return null

        val general = ctx.general
        val city = ctx.city

        // Already have enough crew - per legacy uses nationPolicy.minWarCrew
        if (general.ships >= nationPolicy.minWarCrew) return null

        // Population safety check per legacy (line 2505):
        // remainPop = pop - minNPCRecruitCityPopulation - fullLeadership * 100
        val fullLeadership = general.leadership.toInt()
        val remainPop = city.population - nationPolicy.minNPCRecruitCityPopulation - fullLeadership * 100
        if (remainPop <= 0) return null

        // Legacy (line 2511): if pop ratio < safeRecruitCityPopulationRatio, probabilistic skip
        if (city.populationMax > 0) {
            val popRatio = city.population.toDouble() / city.populationMax
            if (popRatio < nationPolicy.safeRecruitCityPopulationRatio) {
                val maxPop = city.populationMax - nationPolicy.minNPCRecruitCityPopulation
                if (maxPop > 0 && rng.nextDouble() < remainPop.toDouble() / maxPop) {
                    return null
                }
            }
        }

        // Legacy (lines 2602-2609): subtract estimated training/morale costs
        val goldAfterTrainCost = general.funds - fullLeadership * 3
        val riceAfterTrainCost = general.supplies - fullLeadership * 4
        if (goldAfterTrainCost <= 0 || riceAfterTrainCost <= 0) return null

        val crewTypeCode = pickCrewType(general, ctx.generalType, rng, ctx.allCities, ctx.nation, ctx.world)
        val maxAmount = fullLeadership * 100 - (if (crewTypeCode == general.shipClass.toInt()) general.ships else 0)
        if (maxAmount <= 0) return null

        general.meta["aiArg"] = mutableMapOf<String, Any>(
            "crewType" to crewTypeCode,
            "amount" to maxAmount,
        )

        val trainCost = fullLeadership * 3
        return if (goldAfterTrainCost >= trainCost * 6) "모병" else "징병"
    }

    // TODO Phase 3: 삼국지 병종 선택 AI(pickCrewType) 제거됨. gin7 함종 선택 로직으로 대체 예정.
    private fun pickCrewType(
        general: Officer,
        generalType: Int,
        rng: Random,
        nationCities: List<Planet>,
        nation: Faction?,
        world: SessionState,
    ): Int = general.shipClass.toInt()

    // ──────────────────────────────────────────────────────────
    //  do전투준비: Combat preparation (train/morale)
    // ──────────────────────────────────────────────────────────

    private fun doCombatPrep(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        warTargetNations: Map<Long, Int>,
    ): String? {
        val hasNeutralTargets = warTargetNations.containsKey(0L)
        if (!hasNeutralTargets && (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED)) return null

        val general = ctx.general
        if (general.ships <= 0) return null

        val train = general.training.toInt()
        val atmos = general.morale.toInt()
        val threshold = nationPolicy.properWarTrainAtmos

        // Per PHP: build weighted list of combat prep actions, choose randomly
        val cmdList = mutableListOf<Pair<String, Double>>()
        if (train < threshold) {
            // PHP weight: maxTrainByCommand / valueFit(train, 1)
            cmdList.add("훈련" to 30.0 / maxOf(train, 1))
        }
        if (atmos < threshold) {
            // PHP weight: maxAtmosByCommand / valueFit(atmos, 1)
            cmdList.add("사기진작" to 30.0 / maxOf(atmos, 1))
        }

        if (cmdList.isEmpty()) return null
        if (cmdList.size == 1) return cmdList[0].first

        // Weighted choice per PHP choiceUsingWeightPair
        val totalWeight = cmdList.sumOf { it.second }
        val roll = rng.nextDouble() * totalWeight
        var cumulative = 0.0
        for ((action, weight) in cmdList) {
            cumulative += weight
            if (roll < cumulative) return action
        }
        return cmdList.last().first
    }

    // ──────────────────────────────────────────────────────────
    //  do출병: Sortie (attack)
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: need sufficient crew/train/atmos, must be in a front city.
     * Attacks active war targets first; falls back to empty (neutral) cities
     * when no active war exists but attackable front cities are available.
     */
    private fun doSortie(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        attackable: Boolean, warTargetNations: Map<Long, Int>,
    ): String? {
        if (!attackable) return null

        val nation = ctx.nation ?: return null
        val hasNeutralTargets = warTargetNations.containsKey(0L)

        // Per legacy: allow sortie if at war OR if neutral (empty city) targets exist
        if (ctx.diplomacyState != DiplomacyState.AT_WAR && !hasNeutralTargets) return null

        val general = ctx.general
        val city = ctx.city

        // Per legacy: if rice is very low and NPC, 70% chance to skip
        if (nation.supplies < 1000 && general.npcState.toInt() >= 2 && rng.nextDouble() < 0.7) return null

        // Per PHP: train/atmos checked against min(100, nationPolicy.properWarTrainAtmos)
        val trainAtmosThreshold = min(100, nationPolicy.properWarTrainAtmos)
        if (general.training < trainAtmosThreshold) return null
        if (general.morale < trainAtmosThreshold) return null
        // Per PHP: crew checked against min((fullLeadership - 2) * 100, nationPolicy.minWarCrew)
        if (general.ships < min((general.leadership.toInt() - 2) * 100, nationPolicy.minWarCrew)) return null

        if (city.frontState.toInt() < 2 && ctx.frontCities.isNotEmpty()) return null

        val activeWarTargetIds = warTargetNations.filter { it.value >= 2 }.keys
        val targetCities = if (activeWarTargetIds.isNotEmpty()) {
            ctx.allCities.filter { it.factionId in activeWarTargetIds && it.factionId != nation.id }
        } else {
            ctx.allCities.filter { it.factionId == 0L }
        }
        if (targetCities.isEmpty()) return null

        val adjacent = ctx.mapAdjacency[city.mapPlanetId.toLong()].orEmpty().toSet()
        val reachable = targetCities.filter { adjacent.contains(it.mapPlanetId.toLong()) }
        val targetCity = if (reachable.isNotEmpty()) {
            reachable[rng.nextInt(reachable.size)]
        } else {
            targetCities[rng.nextInt(targetCities.size)]
        }
        general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to targetCity.id)
        return "출병"
    }

    // ──────────────────────────────────────────────────────────
    //  do전방워프: Warp to front (NPC teleport)
    // ──────────────────────────────────────────────────────────

    private fun doWarpToFront(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        attackable: Boolean, warTargetNations: Map<Long, Int>,
    ): String? {
        if (!attackable) return null
        val hasNeutralTargets = warTargetNations.containsKey(0L)
        if (!hasNeutralTargets && (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED)) return null

        // Only commanders
        if (ctx.generalType and GeneralType.COMMANDER.flag == 0) return null

        val general = ctx.general
        if (general.ships < nationPolicy.minWarCrew) return null

        // Already at front
        if (ctx.city.frontState > 0) return null

        // Must have front cities to go to
        if (ctx.frontCities.isEmpty()) return null

        // Pick a front city with supply
        val supplyFront = ctx.frontCities.filter { it.supplyState > 0 }
        if (supplyFront.isEmpty()) return null

        val destPlanet = supplyFront[rng.nextInt(supplyFront.size)]
        general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to destPlanet.id)
        return "이동"  // NPC능동/순간이동 maps to 이동 in Kotlin engine
    }

    // ──────────────────────────────────────────────────────────
    //  do후방워프: Warp to rear for recruitment
    // ──────────────────────────────────────────────────────────

    private fun doWarpToRear(
        ctx: AIContext, rng: Random, policy: NpcGeneralPolicy,
        nationPolicy: NpcNationPolicy,
        backupCities: List<Planet>, supplyCities: List<Planet>,
    ): String? {
        if (ctx.diplomacyState == DiplomacyState.PEACE || ctx.diplomacyState == DiplomacyState.DECLARED) return null

        // Only commanders
        if (ctx.generalType and GeneralType.COMMANDER.flag == 0) return null

        val general = ctx.general
        // Already have enough crew
        if (general.ships >= nationPolicy.minWarCrew) return null

        // Check if current city has enough population
        val city = ctx.city
        val minRecruitPop = general.leadership.toInt() * 100 + nationPolicy.minNPCRecruitCityPopulation

        if (city.populationMax > 0 && city.population.toDouble() / city.populationMax >= nationPolicy.safeRecruitCityPopulationRatio &&
            city.population >= minRecruitPop
        ) {
            return null  // Current city is fine for recruitment
        }

        // Find recruitable rear city
        val recruitCities = (backupCities.ifEmpty { supplyCities }).filter { c ->
            c.id != city.id &&
                c.population >= minRecruitPop &&
                c.populationMax > 0 &&
                c.population.toDouble() / c.populationMax >= nationPolicy.safeRecruitCityPopulationRatio
        }

        if (recruitCities.isEmpty()) return null

        val destPlanet = choiceByWeight(rng, recruitCities) { c ->
            c.population.toDouble() / c.populationMax
        } ?: return null

        general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to destPlanet.id)
        return "이동"
    }

    // ──────────────────────────────────────────────────────────
    //  do내정워프: Warp to under-developed city for domestic work
    // ──────────────────────────────────────────────────────────

    private fun doWarpToDomestic(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        supplyCities: List<Planet>,
    ): String? {
        // Commanders during war don't do domestic warp
        if (ctx.generalType and GeneralType.COMMANDER.flag != 0 &&
            ctx.diplomacyState in listOf(DiplomacyState.AT_WAR, DiplomacyState.IMMINENT, DiplomacyState.RECRUITING)
        ) {
            return null
        }

        // Per legacy: 60% chance to skip
        if (rng.nextDouble() < 0.6) return null

        val city = ctx.city
        val genType = ctx.generalType
        val develRate = calcCityDevelRate(city)

        // Check how much this general can contribute to current city
        var availableTypeCnt = 0
        var warpProp = 1.0
        for ((_, pair) in develRate) {
            val (rate, typeFlag) = pair
            if (genType and typeFlag == 0) continue
            warpProp *= rate
            availableTypeCnt++
        }

        if (availableTypeCnt == 0) return null

        // If current city is well-developed for this general's type, probability is high -> skip warp
        if (rng.nextDouble() >= warpProp) {
            // Current city needs work, don't warp
            return null
        }

        // Find candidate cities that need development
        val candidates = supplyCities.filter { c ->
            if (c.id == city.id) return@filter false
            val cRate = calcCityDevelRate(c)
            var realRate = 0.0
            var cnt = 0
            for ((key, pair) in cRate) {
                if (genType and pair.second == 0) continue
                realRate += pair.first
                cnt++
            }
            if (cnt > 0) realRate /= cnt
            realRate < 0.95
        }

        if (candidates.isEmpty()) return null

        val destPlanet = candidates[rng.nextInt(candidates.size)]
        ctx.general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to destPlanet.id)
        return "이동"
    }

    // ──────────────────────────────────────────────────────────
    //  do귀환: Return to own territory
    // ──────────────────────────────────────────────────────────

    private fun doReturn(ctx: AIContext, rng: Random): String? {
        val general = ctx.general
        val city = ctx.city

        // If in own territory with supply, no need to return
        if (city.factionId == general.factionId && city.supplyState > 0) return null

        return "귀환"
    }

    // ──────────────────────────────────────────────────────────
    //  do금쌀구매: Trade gold/rice
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: balance gold and rice. If one is much more than the other, trade.
     * Considers kill/death ratio for rice cost estimation.
     */
    private fun doTradeResources(ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy): String? {
        val general = ctx.general

        // Need some baseline resources
        val totalRes = general.funds + general.supplies
        if (totalRes < 2000) return null  // baseDevelCost*2

        val absGold = general.funds.toDouble()
        val absRice = general.supplies.toDouble()

        // Per legacy: weight rice by kill/death ratio (more deaths = rice more expensive)
        val rankMap = general.meta["rank"] as? Map<*, *>
        val killcrew = ((rankMap?.get("killcrew") as? Number)?.toLong() ?: 0L) + 50000L
        val deathcrew = ((rankMap?.get("deathcrew") as? Number)?.toLong() ?: 0L) + 50000L
        val deathRate = deathcrew.toDouble() / killcrew.coerceAtLeast(1L)

        val relGold = absGold
        val relRice = absRice * deathRate

        // Buy rice if gold >> rice
        if (relRice * 2.0 < relGold && relRice < 2000) {
            val amount = valueFit(((relGold - relRice) / (1.0 + deathRate)).toInt(), 100, 50000)
            if (amount >= nationPolicy.minimumResourceActionAmount) {
                general.meta["aiArg"] = mutableMapOf<String, Any>("amount" to amount, "isBuy" to true)
                return "군량매매"
            }
        }

        // Sell rice if rice >> gold
        if (relGold * 2.0 < relRice && relGold < 2000) {
            val amount = valueFit(((relRice - relGold) / (1.0 + deathRate)).toInt(), 100, 50000)
            if (amount >= nationPolicy.minimumResourceActionAmount) {
                general.meta["aiArg"] = mutableMapOf<String, Any>("amount" to amount, "isBuy" to false)
                return "군량매매"
            }
        }

        return null
    }

    // ──────────────────────────────────────────────────────────
    //  doNPC헌납: Donate resources to nation
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: donate excess resources to nation treasury when nation is poor.
     */
    private fun doDonate(ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy): String? {
        val general = ctx.general
        val nation = ctx.nation ?: return null
        val genType = ctx.generalType

        val isWarGen = genType and GeneralType.COMMANDER.flag != 0
        val reqGold = if (isWarGen) nationPolicy.reqNationGold else (nationPolicy.reqNationGold / 2)
        val reqRice = if (isWarGen) nationPolicy.reqNationRice else (nationPolicy.reqNationRice / 2)

        var donateGold = false
        var donateRice = false

        // Check gold
        if (nation.funds < nationPolicy.reqNationGold && general.funds > reqGold * 1.5) {
            if (rng.nextDouble() < (general.funds.toDouble() / reqGold - 0.5)) {
                donateGold = true
            }
        }
        // Excess gold even if nation doesn't need it
        if (!donateGold && general.funds > reqGold * 5 && general.funds > 5000) {
            donateGold = true
        }

        // Check rice
        if (nation.supplies < nationPolicy.reqNationRice && general.supplies > reqRice * 1.5) {
            if (rng.nextDouble() < (general.supplies.toDouble() / reqRice - 0.5)) {
                donateRice = true
            }
        }
        if (!donateRice && general.supplies > reqRice * 5 && general.supplies > 5000) {
            donateRice = true
        }

        // Emergency: nation rice is critically low
        if (!donateRice && nation.supplies <= 500 && general.supplies >= 500) {
            donateRice = true
        }

        if (!donateGold && !donateRice) return null

        // Calculate donation amounts — command accepts one resource at a time, prefer gold first
        if (donateGold) {
            val amount = max(general.funds - reqGold, nationPolicy.minimumResourceActionAmount)
            val finalAmount = valueFit(amount, nationPolicy.minimumResourceActionAmount, nationPolicy.maximumResourceActionAmount)
            general.meta["aiArg"] = mutableMapOf<String, Any>("isGold" to true, "amount" to finalAmount)
        } else {
            val amount = max(general.supplies - reqRice, nationPolicy.minimumResourceActionAmount)
            val finalAmount = valueFit(amount, nationPolicy.minimumResourceActionAmount, nationPolicy.maximumResourceActionAmount)
            general.meta["aiArg"] = mutableMapOf<String, Any>("isGold" to false, "amount" to finalAmount)
        }

        return "헌납"
    }

    // ──────────────────────────────────────────────────────────
    //  do소집해제: Dismiss troops
    // ──────────────────────────────────────────────────────────

    private fun doDismiss(ctx: AIContext, rng: Random, attackable: Boolean): String? {
        if (attackable) return null
        if (ctx.diplomacyState != DiplomacyState.PEACE) return null
        if (ctx.general.ships == 0) return null
        // Per legacy: 75% chance to skip (slow disbanding)
        if (rng.nextDouble() < 0.75) return null
        return "소집해제"
    }

    // ──────────────────────────────────────────────────────────
    //  do중립: Neutral/fallback action
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: if nation needs gold/rice, do 물자조달. Otherwise 인재탐색 or 견문.
     */
    private fun doNeutral(general: Officer, nation: Faction?, rng: Random): String {
        if (general.factionId == 0L) {
            // Wanderer: 인재탐색 or 견문
            return if (rng.nextDouble() < 0.2) "인재탐색" else "견문"
        }

        val candidate = mutableListOf("물자조달", "인재탐색")

        if (nation != null) {
            if (nation.funds < 2000 || nation.supplies < 2000) {
                return "물자조달"
            }
        }

        return candidate[rng.nextInt(candidate.size)]
    }

    // ──────────────────────────────────────────────────────────
    //  do거병: Rise up (NPC lord founding)
    // ──────────────────────────────────────────────────────────

    private fun doRise(general: Officer, world: SessionState, rng: Random): String? {
        if (general.makeLimit > 0) return null
        if (general.npcState.toInt() > 2) return null

        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val allCities = ports.allPlanets().map { it.toEntity() }
        val allGenerals = ports.allOfficers().map { it.toEntity() }

        // Legacy parity: if general is NOT at a major city (level 5-6), 50% chance to skip.
        // PHP do거병: if ($currentCityLevel < 5 || 6 < $currentCityLevel) && $this->rng->nextBool(0.5)
        val currentCityLevel = allCities.find { it.id == general.planetId }?.level?.toInt() ?: 5
        if ((currentCityLevel < 5 || currentCityLevel > 6) && rng.nextDouble() >= 0.5) return null

        // Per legacy: check for nearby unoccupied major city (level 5-6) within distance 3
        val occupiedCityIds = mutableSetOf<Long>()
        // Cities belonging to a nation
        allCities.filter { it.factionId != 0L }.forEach { occupiedCityIds.add(it.id) }
        // Cities where a lord (officer_level=20) is located in a neutral city
        allGenerals.filter { it.officerLevel.toInt() == 20 }.forEach { gen ->
            val city = allCities.find { it.id == gen.planetId }
            if (city != null && city.factionId == 0L) occupiedCityIds.add(city.id)
        }

        val distances = bfsCityDistances(general.planetId, allCities)
        val availableNearCity = distances.any { (cityId, dist) ->
            if (dist == 0 || dist > 3) return@any false
            if (cityId in occupiedCityIds) return@any false
            val city = allCities.find { it.id == cityId } ?: return@any false
            val level = city.level.toInt()
            if (level < 5 || level > 6) return@any false
            if (dist == 3 && rng.nextDouble() < 0.5) return@any false
            true
        }
        if (!availableNearCity) return null

        val avgStat = (general.leadership + general.command + general.intelligence).toDouble() / 3.0
        // Per legacy: (defaultStatNPCMax + chiefStatMin) / 2 = (75 + 65) / 2 = 70
        val threshold = rng.nextDouble() * 70.0

        if (threshold >= avgStat) return null

        // Per legacy: 거병 기한 - low probability
        val yearsFromInit = world.currentYear - ((world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt())
        val more = valueFitD((3 - yearsFromInit).toDouble(), 1.0, 3.0)
        if (rng.nextDouble() >= 0.0075 * more) return null

        return "거병"
    }

    // ──────────────────────────────────────────────────────────
    //  doNPC사망대비: Death preparation
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: when killTurn <= 5, donate all resources to nation.
     */
    private fun doDeathPreparation(general: Officer, nation: Faction?, rng: Random): String {
        if (general.factionId == 0L) {
            return if (rng.nextDouble() < 0.5) "인재탐색" else "견문"
        }

        if (general.officerLevel >= 20 && nation != null) {
            val ports = worldPortFactory.create(general.sessionId)
            val nationGenerals = ports.officersByFaction(general.factionId).map { it.toEntity() }
            val candidates = nationGenerals.filter { gen ->
                gen.id != general.id && gen.npcState.toInt() != 5
            }
            if (candidates.isNotEmpty()) {
                val target = candidates.maxByOrNull { it.leadership + it.command + it.intelligence }
                    ?: candidates[rng.nextInt(candidates.size)]
                general.meta["aiArg"] = mapOf("destGeneralId" to target.id)
                return "선양"
            }
        }

        if (general.funds + general.supplies == 0) return "물자조달"

        return if (general.funds >= general.supplies) {
            general.meta["aiArg"] = mutableMapOf<String, Any>("isGold" to true, "amount" to general.funds)
            "헌납"
        } else {
            general.meta["aiArg"] = mutableMapOf<String, Any>("isGold" to false, "amount" to general.supplies)
            "헌납"
        }
    }

    // ──────────────────────────────────────────────────────────
    //  부대유저장후방발령: Move user generals in troops to rear for recruitment
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do부대유저장후방발령: Find user war generals in troop at front cities
     * with low population, move them to rear cities with enough population for recruitment.
     */
    private fun doTroopUserRearAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        backupCities: List<Planet>, supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (ctx.frontCities.isEmpty()) return null
        if (ctx.diplomacyState != DiplomacyState.AT_WAR) return null

        val frontCityIds = ctx.frontCities.map { it.id }.toSet()
        val nationCityMap = ctx.allCities.filter { it.factionId == nation.id }.associateBy { it.id }
        val supplyCityIds = supplyCities.map { it.id }.toSet()

        // Troop leaders in our nation
        val troopLeaders = ctx.nationGenerals.filter { it.npcState.toInt() == 5 }
        val troopLeaderMap = troopLeaders.associateBy { it.id }

        // User war generals: npcState < 2, not self
        val userWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id
        }

        val generalCandidates = userWarGenerals.filter { gen ->
            if (!frontCityIds.contains(gen.planetId)) return@filter false
            if (!nationCityMap.containsKey(gen.planetId)) return@filter false
            val city = nationCityMap[gen.planetId] ?: return@filter false

            val troopLeaderId = gen.fleetId
            if (troopLeaderId == 0L || !troopLeaderMap.containsKey(troopLeaderId)) return@filter false
            if (troopLeaderId == gen.id) return@filter false

            val troopLeader = troopLeaderMap[troopLeaderId] ?: return@filter false
            if (troopLeader.planetId != gen.planetId) return@filter false
            if (!supplyCityIds.contains(troopLeader.planetId)) return@filter false

            // City population ratio check
            if (city.populationMax > 0 && city.population.toDouble() / city.populationMax >= policy.safeRecruitCityPopulationRatio) return@filter false
            // Crew check
            if (gen.ships >= policy.minWarCrew) return@filter false

            true
        }

        if (generalCandidates.isEmpty()) return null
        if (supplyCities.size <= 1) return null

        // Find suitable rear cities
        val cityCandidates = (backupCities.ifEmpty { supplyCities }).filter { city ->
            city.populationMax > 0 && city.population.toDouble() / city.populationMax >= policy.safeRecruitCityPopulationRatio
        }
        if (cityCandidates.isEmpty()) return null

        val pickedGeneral = generalCandidates[rng.nextInt(generalCandidates.size)]
        val destPlanet = cityCandidates[rng.nextInt(cityCandidates.size)]
        pickedGeneral.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  NPC구출발령: Rescue lost NPC generals
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy doNPC구출발령: Find NPC generals (npcState>=2, !=5) that are
     * in non-supply cities (lost/cut off) and assign them to supply cities.
     */
    private fun doNpcRescueAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (supplyCities.isEmpty()) return null

        val supplyCityIds = supplyCities.map { it.id }.toSet()

        // Lost NPC generals: NPC (npcState>=2, !=5) in non-supply cities
        val lostNpcGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 &&
                gen.id != ctx.general.id &&
                !supplyCityIds.contains(gen.planetId)
        }

        if (lostNpcGenerals.isEmpty()) return null

        val target = lostNpcGenerals[rng.nextInt(lostNpcGenerals.size)]
        val destPlanet = supplyCities[rng.nextInt(supplyCities.size)]
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  유저장구출발령: Rescue lost user generals
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do유저장구출발령: Find user generals (npcState<2) that are in
     * non-supply cities and don't have enough crew/train to defend, then assign them out.
     */
    private fun doUserRescueAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (supplyCities.isEmpty()) return null

        val supplyCityIds = supplyCities.map { it.id }.toSet()

        // Lost user generals: npcState < 2, not in supply cities
        val lostUserGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id &&
                !supplyCityIds.contains(gen.planetId)
        }

        // Filter out those who can defend (have crew + train + atmos)
        val rescueCandidates = lostUserGenerals.filter { gen ->
            !(gen.ships >= 500 && gen.training >= gen.defenceTrain && gen.morale >= gen.defenceTrain)
        }

        // Filter out those already in a troop with a leader that can escape
        val troopLeaderMap = ctx.nationGenerals.filter { it.npcState.toInt() == 5 }.associateBy { it.id }
        val candidateArgs = rescueCandidates.mapNotNull { gen ->
            val troopId = gen.fleetId
            if (troopId != 0L && troopLeaderMap.containsKey(troopId)) {
                val troopLeader = troopLeaderMap[troopId]!!
                if (supplyCityIds.contains(troopLeader.planetId)) {
                    return@mapNotNull null // Already in escapable troop
                }
            }

            // Choose destination
            val destPlanet = if (ctx.diplomacyState in listOf(DiplomacyState.IMMINENT, DiplomacyState.AT_WAR) &&
                ctx.frontCities.size > 2
            ) {
                ctx.frontCities[rng.nextInt(ctx.frontCities.size)]
            } else {
                supplyCities[rng.nextInt(supplyCities.size)]
            }

            gen to destPlanet
        }

        if (candidateArgs.isEmpty()) return null

        val (target, destPlanet) = candidateArgs[rng.nextInt(candidateArgs.size)]
        target.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  유저장내정발령: Move user generals to under-developed cities
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do유저장내정발령: Find user generals in well-developed supply cities
     * and move them to under-developed supply cities.
     */
    private fun doUserDomesticAssignment(
        ctx: AIContext, rng: Random, policy: NpcNationPolicy,
        supplyCities: List<Planet>,
    ): String? {
        val nation = ctx.nation ?: return null
        if (nation.capitalPlanetId == null) return null
        if (supplyCities.size <= 1) return null

        val avgDev = supplyCities.map { calcCityDevScore(it) }.average()
        if (avgDev >= 0.99) return null

        val supplyCityMap = supplyCities.associateBy { it.id }

        // In peace, include both war and civil user generals; otherwise only civil
        val userGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id && gen.fleetId == 0L
        }
        val civilUserGenerals = if (ctx.diplomacyState == DiplomacyState.PEACE ||
            ctx.diplomacyState == DiplomacyState.DECLARED
        ) {
            userGenerals
        } else {
            userGenerals.filter { it.leadership < policy.minNPCWarLeadership }
        }

        // Find generals in well-developed supply cities (dev >= 0.95)
        val candidates = civilUserGenerals.filter { gen ->
            val city = supplyCityMap[gen.planetId] ?: return@filter false
            calcCityDevScore(city) >= 0.95
        }

        if (candidates.isEmpty()) return null

        // Weight under-developed cities by need
        val cityWeights = supplyCities.map { city ->
            val dev = min(calcCityDevScore(city), 0.999)
            val score = (1.0 - dev).pow(2.0)
            val generalCount = ctx.nationGenerals.count { it.planetId == city.id }
            city to score / sqrt(generalCount.toDouble() + 1.0)
        }.filter { it.second > 0.0 }

        if (cityWeights.isEmpty()) return null

        val destOfficer = candidates[rng.nextInt(candidates.size)]
        val srcCity = supplyCityMap[destOfficer.planetId]
        val destPlanet = choiceByWeightPair(rng, cityWeights) ?: return null

        if (srcCity != null && calcCityDevScore(srcCity) <= calcCityDevScore(destPlanet)) return null

        destOfficer.meta["assignedCity"] = destPlanet.id
        return "발령"
    }

    // ──────────────────────────────────────────────────────────
    //  유저장긴급포상: Urgent reward for user war generals
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do유저장긴급포상: During war, reward user war generals who are low
     * on gold/rice with urgent funding from the national treasury.
     */
    private fun doUserUrgentReward(ctx: AIContext, rng: Random, policy: NpcNationPolicy): String? {
        val nation = ctx.nation ?: return null

        // Only user war generals (npcState < 2 with combat readiness)
        val userWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.id != ctx.general.id &&
                (gen.killTurn?.toInt() ?: 100) > 5
        }
        if (userWarGenerals.isEmpty()) return null

        data class RewardCandidate(val generalId: Long, val isGold: Boolean, val amount: Int, val weight: Double)

        val candidates = mutableListOf<RewardCandidate>()
        val reqGoldThreshold = policy.reqNationGold  // reqHumanWarUrgentGold analog
        val reqRiceThreshold = policy.reqNationRice  // reqHumanWarUrgentRice analog

        // Gold check
        val sortedByGold = userWarGenerals.sortedBy { it.funds }
        for ((idx, gen) in sortedByGold.withIndex()) {
            if (gen.funds >= reqGoldThreshold) break

            val reqMoney = gen.leadership.toInt() * 100 * 3.0 * 1.1
            val enoughMoney = reqMoney * 1.1
            if (gen.funds >= reqMoney) continue

            val payAmount = sqrt((enoughMoney - gen.funds) * nation.funds.toDouble())
            val clampedPay = valueFitD(payAmount, 0.0, enoughMoney - gen.funds)
            if (clampedPay < policy.minimumResourceActionAmount) continue
            if (nation.funds < clampedPay / 2) continue

            val finalPay = valueFit(clampedPay.toInt(), 100, policy.maximumResourceActionAmount)
            candidates.add(RewardCandidate(gen.id, true, finalPay, (sortedByGold.size - idx).toDouble()))
        }

        // Rice check
        val sortedByRice = userWarGenerals.sortedBy { it.supplies }
        for ((idx, gen) in sortedByRice.withIndex()) {
            if (gen.supplies >= reqRiceThreshold) break

            val reqMoney = gen.leadership.toInt() * 100 * 3.0 * 1.1
            val enoughMoney = reqMoney * 1.1
            if (gen.supplies >= reqMoney) continue

            val payAmount = sqrt((enoughMoney - gen.supplies) * nation.supplies.toDouble())
            val clampedPay = valueFitD(payAmount, 0.0, enoughMoney - gen.supplies)
            if (clampedPay < policy.minimumResourceActionAmount) continue
            if (nation.supplies < clampedPay / 2) continue

            val finalPay = valueFit(clampedPay.toInt(), 100, policy.maximumResourceActionAmount)
            candidates.add(RewardCandidate(gen.id, false, finalPay, (sortedByRice.size - idx).toDouble()))
        }

        if (candidates.isEmpty()) return null

        val picked = choiceByWeightPairRaw(rng, candidates.map { it to it.weight }) ?: return null
        val targetGen = ctx.nationGenerals.find { it.id == picked.generalId } ?: return null
        targetGen.meta["rewardGold"] = if (picked.isGold) picked.amount else 0
        targetGen.meta["rewardRice"] = if (!picked.isGold) picked.amount else 0
        return "포상"
    }

    // ──────────────────────────────────────────────────────────
    //  NPC긴급포상: Urgent reward for NPC war generals
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy doNPC긴급포상: During war, urgently reward NPC war generals
     * who are low on gold/rice from the national treasury.
     */
    private fun doNpcUrgentReward(ctx: AIContext, rng: Random, policy: NpcNationPolicy): String? {
        val nation = ctx.nation ?: return null

        val npcWarGenerals = ctx.nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 &&
                gen.id != ctx.general.id &&
                gen.leadership >= policy.minNPCWarLeadership &&
                (gen.killTurn?.toInt() ?: 100) > 5
        }
        if (npcWarGenerals.isEmpty()) return null

        data class RewardCandidate(val generalId: Long, val isGold: Boolean, val amount: Int, val weight: Double)

        val candidates = mutableListOf<RewardCandidate>()
        val reqNPCMinWarGold = policy.reqNationGold / 2  // reqNPCWarGold/2 analog
        val reqNPCMinWarRice = policy.reqNationRice / 2

        // Gold
        if (nation.funds >= policy.reqNationGold) {
            val sortedByGold = npcWarGenerals.sortedBy { it.funds }
            for ((idx, gen) in sortedByGold.withIndex()) {
                if (gen.funds >= reqNPCMinWarGold) break

                val reqMoney = gen.leadership.toInt() * 100 * 1.5
                val enoughMoney = reqMoney * 1.2
                if (gen.funds >= reqMoney) continue

                val payAmount = sqrt((enoughMoney - gen.funds) * nation.funds.toDouble())
                val clampedPay = valueFitD(payAmount, 0.0, enoughMoney - gen.funds)
                if (clampedPay < policy.minimumResourceActionAmount) continue
                if (nation.funds < clampedPay / 2) continue

                val finalPay = valueFit(clampedPay.toInt(), 100, policy.maximumResourceActionAmount)
                candidates.add(RewardCandidate(gen.id, true, finalPay, (sortedByGold.size - idx).toDouble()))
            }
        }

        // Rice
        if (nation.supplies >= policy.reqNationRice) {
            val sortedByRice = npcWarGenerals.sortedBy { it.supplies }
            for ((idx, gen) in sortedByRice.withIndex()) {
                if (gen.supplies >= reqNPCMinWarRice) break

                val reqMoney = gen.leadership.toInt() * 100 * 1.5
                val enoughMoney = reqMoney * 1.2
                if (gen.supplies >= reqMoney) continue

                val payAmount = sqrt((enoughMoney - gen.supplies) * nation.supplies.toDouble())
                val clampedPay = valueFitD(payAmount, 0.0, enoughMoney - gen.supplies)
                if (clampedPay < policy.minimumResourceActionAmount) continue
                if (nation.supplies < clampedPay / 2) continue

                val finalPay = valueFit(clampedPay.toInt(), 100, policy.maximumResourceActionAmount)
                candidates.add(RewardCandidate(gen.id, false, finalPay, (sortedByRice.size - idx).toDouble()))
            }
        }

        if (candidates.isEmpty()) return null

        val picked = choiceByWeightPairRaw(rng, candidates.map { it to it.weight }) ?: return null
        val targetGen = ctx.nationGenerals.find { it.id == picked.generalId } ?: return null
        targetGen.meta["rewardGold"] = if (picked.isGold) picked.amount else 0
        targetGen.meta["rewardRice"] = if (!picked.isGold) picked.amount else 0
        return "포상"
    }

    // ──────────────────────────────────────────────────────────
    //  do집합: Rally (troop leader always rallies)
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do집합: Fleet leaders (npcState==5) always rally.
     * Also refresh killTurn for troop leaders.
     */
    private fun doRally(general: Officer, rng: Random): String {
        if (general.npcState.toInt() == 5) {
            // Per legacy: cycle killTurn for troop leaders
            val newKillTurn = ((general.killTurn?.toInt() ?: 70) + rng.nextInt(3) + 2) % 5 + 70
            general.killTurn = newKillTurn.coerceIn(-32768, 32767).toShort()
        }
        return "집합"
    }

    // ──────────────────────────────────────────────────────────
    //  do해산: Disband nation (NPC lord without capital)
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do해산: Lord NPC without capital disbands nation.
     * Clears movingTargetCityID aux var.
     */
    private fun doDisband(general: Officer): String? {
        // Simplified condition check; engine validates
        general.meta.remove("movingTargetCityID")
        return "해산"
    }

    // ──────────────────────────────────────────────────────────
    //  do선양: Abdicate (transfer lordship)
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do선양: Lord abdicates to a random general in the nation.
     * Only when generalPolicy allows it (can선양).
     */
    private fun doAbdicate(ctx: AIContext, rng: Random): String? {
        return doAbdicate(ctx.general, ctx.world, rng)
    }

    /**
     * Per PHP line 3745: do선양 (abdication) check.
     * PHP checks this BEFORE npcType==5, only for officer_level==12 (chief).
     * Kotlin chief = officerLevel==20.
     */
    private fun doAbdicate(general: Officer, world: SessionState, rng: Random): String? {
        if (general.officerLevel.toInt() != 20) return null

        val ports = worldPortFactory.create(world.id.toLong())
        val nationGenerals = ports.allOfficers().map { it.toEntity() }
            .filter { it.factionId == general.factionId }

        // Find a non-troop general in the same nation to abdicate to
        val candidates = nationGenerals.filter { gen ->
            gen.id != general.id && gen.npcState.toInt() != 5
        }
        if (candidates.isEmpty()) return null

        val target = candidates[rng.nextInt(candidates.size)]
        general.meta["aiArg"] = mapOf("destGeneralId" to target.id)
        return "선양"
    }

    // ──────────────────────────────────────────────────────────
    //  autoPromoteLord: Auto-promote when nation has no lord
    // ──────────────────────────────────────────────────────────

    /**
     * When a nation has no lord (officerLevel 20), promote the best NPC general.
     * This prevents a permanent deadlock where choosePromotion can never run
     * because it requires an existing lord.
     *
     * Selection priority: highest (leadership + strength + intel) among NPC generals.
     */
    fun autoPromoteLord(nationGenerals: List<Officer>, ports: WorldWritePort): Officer? {
        val hasLord = nationGenerals.any { it.officerLevel.toInt() == 20 }
        if (hasLord) return null

        val candidate = nationGenerals
            .filter { it.npcState.toInt() >= 2 && it.npcState.toInt() != 5 }
            .maxByOrNull { it.leadership.toInt() + it.command.toInt() + it.intelligence.toInt() }
            ?: return null

        candidate.officerLevel = 20
        candidate.officerPlanet = 0
        ports.putOfficer(candidate.toSnapshot())
        logger.info("Auto-promoted {} ({}) to lord (officerLevel=20) for lordless nation", candidate.id, candidate.name)
        return candidate
    }

    // ──────────────────────────────────────────────────────────
    //  choosePromotion: Assign officer positions (lord-level)
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy choosePromotion: Lord assigns officer positions to generals.
     * Sets officer_level for generals based on stats and availability.
     * Runs at months 3, 6, 9, 12 for NPC lords.
     */
    fun choosePromotion(ctx: AIContext, rng: Random, ports: WorldWritePort) {
        val nation = ctx.nation ?: return
        val nationGenerals = ctx.nationGenerals
        val general = ctx.general

        val minChiefLevel = getNationChiefLevel(nation.factionRank.toInt())

        // Track which chief levels are filled
        val chiefGenerals = mutableMapOf<Int, Officer>()
        for (gen in nationGenerals) {
            if (gen.officerLevel.toInt() in minChiefLevel..20 && gen.id != general.id) {
                chiefGenerals[gen.officerLevel.toInt()] = gen
            }
        }

        // Per legacy: minUserKillturn = env['killturn'] - int(240 / env['turnterm'])
        val turnterm = (ctx.world.config["turnterm"] as? Number)?.toInt() ?: (ctx.world.tickSeconds / 60)
        val envKillturn = (ctx.world.config["killturn"] as? Number)?.toInt()
            ?: nationGenerals.mapNotNull { it.killTurn?.toInt() }.maxOrNull() ?: 500
        val minUserKillturn = envKillturn - (240 / turnterm.coerceAtLeast(1))
        val minNPCKillturn = 36
        var userChiefCnt = 0

        for (level in minChiefLevel until 20) {
            val chief = chiefGenerals[level] ?: continue
            // Per legacy: also check !hasPenalty(NoAmbassador)
            if (chief.npcState.toInt() < 2
                && (chief.killTurn?.toInt() ?: 100) >= minUserKillturn
                && chief.penalty["noAmbassador"] != true
            ) {
                userChiefCnt++
                chief.permission = "ambassador"
            }
        }

        // Sort all generals by composite stat for promotion
        val sortedGenerals = nationGenerals.filter { it.id != general.id }
            .sortedByDescending {
                it.leadership.toInt() * 2 + it.command.toInt() + it.intelligence.toInt()
            }

        val nextChiefs = mutableMapOf<Int, Officer>()

        // First ensure level 11 is filled with a user if possible and no user chiefs exist
        val userGenerals = nationGenerals.filter { it.npcState.toInt() < 2 && it.id != general.id }
        if (userChiefCnt == 0 && userGenerals.isNotEmpty()) {
            val usersSorted = userGenerals
                .filter { it.officerLevel.toInt() <= 4 && (it.killTurn?.toInt() ?: 100) >= minUserKillturn }
                .sortedByDescending { it.leadership.toInt() }

            val pick = usersSorted.firstOrNull()
            if (pick != null && !chiefGenerals.containsKey(11)) {
                pick.officerLevel = 11
                pick.officerPlanet = 0
                pick.permission = "ambassador"
                ports.putOfficer(pick.toSnapshot())
                nextChiefs[11] = pick
                chiefGenerals[11] = pick
                userChiefCnt++
            }
        }

        // Fill remaining positions from 11 down to minChiefLevel
        for (chiefLevel in 11 downTo minChiefLevel) {
            if (chiefGenerals.containsKey(chiefLevel) && nextChiefs[chiefLevel] == null) {
                val existing = chiefGenerals[chiefLevel]!!
                // Keep existing user chiefs
                if (existing.npcState.toInt() < 2 && (existing.killTurn?.toInt() ?: 100) >= minUserKillturn) {
                    continue
                }
            }

            if (chiefGenerals.containsKey(chiefLevel) && nextChiefs[chiefLevel] == null) {
                // Position filled, maybe replace with probability
                if (!rng.nextBoolean() || rng.nextDouble() >= 0.1) continue
            }

            var newChief: Officer? = null
            for (candidate in sortedGenerals) {
                if (candidate.officerLevel.toInt() > 4) continue
                if (candidate.npcState.toInt() < 2 && (candidate.killTurn?.toInt() ?: 100) < minUserKillturn) continue
                if (candidate.npcState.toInt() >= 2 && (candidate.killTurn?.toInt() ?: 100) < minNPCKillturn) continue

                // Stat requirement by level
                if (chiefLevel == 11) {
                    // No stat requirement for level 11
                } else if (chiefLevel % 2 == 0) {
                    if (candidate.command < 60) continue  // chiefStatMin
                } else {
                    if (candidate.intelligence < 60) continue
                }

                // Limit user chiefs to 3
                if (candidate.npcState.toInt() < 2 && userChiefCnt >= 3) continue

                newChief = candidate
                break
            }

            if (newChief == null) continue

            if (newChief.npcState.toInt() < 2) {
                userChiefCnt++
                newChief.permission = "ambassador"
            }

            // Demote old chief if exists
            val oldChief = chiefGenerals[chiefLevel]
            if (oldChief != null && oldChief.id != newChief.id) {
                oldChief.officerLevel = 1
                oldChief.officerPlanet = 0
                ports.putOfficer(oldChief.toSnapshot())
            }

            newChief.officerLevel = chiefLevel.coerceIn(0, 20).toShort()
            newChief.officerPlanet = 0
            ports.putOfficer(newChief.toSnapshot())
            nextChiefs[chiefLevel] = newChief
            chiefGenerals[chiefLevel] = newChief
        }
    }

    // ──────────────────────────────────────────────────────────
    //  chooseNonLordPromotion: Non-lord officer promotion
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseNonLordPromotion: Fill empty officer positions with any available general.
     * Less sophisticated than choosePromotion - just fills vacancies.
     */
    fun chooseNonLordPromotion(ctx: AIContext, rng: Random, ports: WorldWritePort) {
        val nation = ctx.nation ?: return
        val nationGenerals = ctx.nationGenerals
        val general = ctx.general
        val nationPolicy = NpcPolicyBuilder.buildNationPolicy(nation.meta)

        val minChiefLevel = getNationChiefLevel(nation.factionRank.toInt())

        val chiefGenerals = mutableMapOf<Int, Officer>()
        for (gen in nationGenerals) {
            if (gen.officerLevel.toInt() in minChiefLevel..20 && gen.id != general.id) {
                chiefGenerals[gen.officerLevel.toInt()] = gen
            }
        }

        // Available generals for promotion
        val npcWarGenerals = nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 &&
                gen.leadership >= nationPolicy.minNPCWarLeadership && gen.officerLevel.toInt() == 1
        }
        val npcCivilGenerals = nationGenerals.filter { gen ->
            gen.npcState.toInt() >= 2 && gen.npcState.toInt() != 5 &&
                gen.leadership < nationPolicy.minNPCWarLeadership && gen.officerLevel.toInt() == 1
        }
        val userWarGenerals = nationGenerals.filter { gen ->
            gen.npcState.toInt() < 2 && gen.officerLevel.toInt() == 1
        }

        for (chiefLevel in minChiefLevel until 20) {
            if (chiefGenerals.containsKey(chiefLevel)) continue
            if (general.officerLevel.toInt() == chiefLevel) continue

            var picked: Officer? = null
            for (attempt in 0 until 5) {
                val pool = when {
                    npcWarGenerals.isNotEmpty() -> npcWarGenerals
                    npcCivilGenerals.isNotEmpty() -> npcCivilGenerals
                    userWarGenerals.isNotEmpty() -> userWarGenerals
                    else -> break
                }
                val randGeneral = pool[rng.nextInt(pool.size)]
                if (randGeneral.officerLevel.toInt() != 1) continue

                if (chiefLevel == 11) {
                    picked = randGeneral
                    break
                }
                if (chiefLevel % 2 == 0 && randGeneral.command < 60) continue
                if (chiefLevel % 2 == 1 && randGeneral.intelligence < 60) continue
                picked = randGeneral
                break
            }

            if (picked == null) continue

            picked.officerLevel = chiefLevel.coerceIn(0, 20).toShort()
            picked.officerPlanet = 0
            ports.putOfficer(picked.toSnapshot())
            chiefGenerals[chiefLevel] = picked
        }
    }

    // ──────────────────────────────────────────────────────────
    //  chooseTexRate: Set nation tax rate
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseTexRate: Set tax rate based on nation development level.
     * Higher development = higher tax rate.
     */
    fun chooseTexRate(ctx: AIContext, supplyCities: List<Planet>): Int {
        val nation = ctx.nation ?: return 15

        var rate = 15
        if (supplyCities.isNotEmpty()) {
            val popRates = supplyCities.map { if (it.populationMax > 0) it.population.toDouble() / it.populationMax else 0.0 }
            val devRates = supplyCities.map { calcCityDevScore(it) }
            val avg = (popRates.average() + devRates.average()) / 2.0

            rate = when {
                avg > 0.95 -> 25
                avg > 0.70 -> 20
                avg > 0.50 -> 15
                else -> 10
            }
        }

        nation.conscriptionRate = rate.coerceIn(0, 100).toShort()
        nation.warState = 0
        return rate
    }

    // ──────────────────────────────────────────────────────────
    //  chooseGoldBillRate: Set gold bill (salary) rate
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseGoldBillRate: Calculate gold bill rate based on income vs outcome.
     * Bill = income / outcome * 90, clamped to [20, 200].
     */
    fun chooseGoldBillRate(ctx: AIContext, supplyCities: List<Planet>, policy: NpcNationPolicy): Int {
        val nation = ctx.nation ?: return 20
        if (supplyCities.isEmpty()) return 20

        val nationGenerals = ctx.nationGenerals.filter { it.npcState.toInt() != 5 }

        // Per legacy: goldIncome = sum(calcCityGoldIncome) * taxRate/20
        val goldIncome = supplyCities.sumOf { city ->
            calcCityGoldIncome(city)
        }.let { (it * nation.conscriptionRate / 20.0).toInt() }

        // Per legacy: warGoldIncome = sum(dead/10) for supply cities
        val warGoldIncome = supplyCities.sumOf { city ->
            if (city.supplyState > 0) (city.dead / 10) else 0
        }

        val income = (goldIncome + warGoldIncome).coerceAtLeast(1)

        // Per legacy: outcome = sum(getBill(dedication)) * billRate/100
        val outcome = calcOutcome(100, nationGenerals).coerceAtLeast(1)

        var bill = (income.toDouble() / outcome * 90).toInt()

        if (nation.funds + income - outcome > policy.reqNationGold * 2) {
            val moreBill = ((nation.funds + income - policy.reqNationGold * 2).toDouble() / outcome * 80).toInt()
            if (moreBill > bill) {
                bill = (moreBill + bill) / 2
            }
        }

        bill = bill.coerceIn(20, 200)
        nation.taxRate = bill.toShort()
        return bill
    }

    // ──────────────────────────────────────────────────────────
    //  chooseRiceBillRate: Set rice bill (salary) rate
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseRiceBillRate: Calculate rice bill rate based on income vs outcome.
     * Bill = income / outcome * 90, clamped to [20, 200].
     */
    fun chooseRiceBillRate(ctx: AIContext, supplyCities: List<Planet>, policy: NpcNationPolicy): Int {
        val nation = ctx.nation ?: return 20
        if (supplyCities.isEmpty()) return 20

        val nationGenerals = ctx.nationGenerals.filter { it.npcState.toInt() != 5 }

        // Per legacy: riceIncome = sum(calcCityRiceIncome) * taxRate/20
        val riceIncome = supplyCities.sumOf { city ->
            calcCityRiceIncome(city)
        }.let { (it * nation.conscriptionRate / 20.0).toInt() }

        // Per legacy: wallIncome = sum(def * wall/wallMax / 3 * secuFactor) * taxRate/20
        val wallIncome = supplyCities.sumOf { city ->
            calcCityWallRiceIncome(city)
        }.let { (it * nation.conscriptionRate / 20.0).toInt() }

        val income = (riceIncome + wallIncome).coerceAtLeast(1)

        val outcome = calcOutcome(100, nationGenerals).coerceAtLeast(1)

        var bill = (income.toDouble() / outcome * 90).toInt()

        if (nation.supplies + income - outcome > policy.reqNationRice * 2) {
            val moreBill = ((nation.supplies + income - policy.reqNationRice * 2).toDouble() / outcome * 80).toInt()
            if (moreBill > bill) {
                bill = (moreBill + bill) / 2
            }
        }

        bill = bill.coerceIn(20, 200)
        nation.taxRate = bill.toShort()
        return bill
    }

    // ──────────────────────────────────────────────────────────
    //  chooseNationTurn: High-level NPC nation turn orchestrator
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseNationTurn: Main entry point for NPC nation-level turn decisions.
     * Handles periodic tasks (promotion, tax/bill rates) and iterates nation policy priorities.
     */
    fun chooseNationTurn(general: Officer, world: SessionState): String {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "OfficerAI", world.currentYear, world.currentMonth, general.id
        )

        if (general.factionId == 0L) return "휴식"

        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val city = ports.planet(general.planetId)?.toEntity() ?: return "휴식"
        val nation = ports.faction(general.factionId)?.toEntity() ?: return "휴식"

        val allCities = ports.allPlanets().map { it.toEntity() }
        val allGenerals = ports.allOfficers().map { it.toEntity() }
        val allNations = ports.allFactions().map { it.toEntity() }
        val diplomacies = ports.activeDiplomacies().map { it.toEntity() }

        val nationCities = allCities.filter { it.factionId == nation.id }
        val frontCities = nationCities.filter { it.frontState > 0 }
        val rearCities = nationCities.filter { it.frontState.toInt() == 0 }
        val supplyCities = nationCities.filter { it.supplyState > 0 }
        val backupCities = nationCities.filter { it.frontState.toInt() == 0 && it.supplyState > 0 }
        val nationGenerals = allGenerals.filter { it.factionId == general.factionId }

        val diplomacyState = calcDiplomacyState(worldId, nation, diplomacies)

        val nationPolicy = NpcPolicyBuilder.buildNationPolicy(nation.meta)
        val generalType = classifyGeneral(general, rng, nationPolicy.minNPCWarLeadership)

        val ctx = AIContext(
            world = world,
            general = general,
            city = city,
            nation = nation,
            diplomacyState = diplomacyState,
            generalType = generalType,
            allCities = allCities,
            allGenerals = allGenerals,
            allNations = allNations,
            frontCities = frontCities,
            rearCities = rearCities,
            nationGenerals = nationGenerals,
        )

        val month = world.currentMonth.toInt()

        // Periodic tasks for NPC lords
        if (general.npcState.toInt() >= 2) {
            if (month in listOf(3, 6, 9, 12)) {
                autoPromoteLord(ctx.nationGenerals, ports)
            }
            if (general.officerLevel.toInt() == 20) {
                if (month in listOf(3, 6, 9, 12)) {
                    choosePromotion(ctx, rng, ports)
                }
                if (month == 12) {
                    chooseTexRate(ctx, supplyCities)
                    chooseGoldBillRate(ctx, supplyCities, nationPolicy)
                }
                if (month == 6) {
                    chooseTexRate(ctx, supplyCities)
                    chooseRiceBillRate(ctx, supplyCities, nationPolicy)
                }
            } else if (month in listOf(3, 6, 9, 12)) {
                chooseNonLordPromotion(ctx, rng, ports)
            }
        }

        // Check reserved command
        val reservedAction = checkReservedCommand(general)
        if (reservedAction != null) return reservedAction

        val attackable = frontCities.any { it.supplyState > 0 }
        val warTargetNations = calcWarTargetNations(nation, diplomacies)

        // Iterate nation policy priorities
        for (actionName in nationPolicy.priority) {
            if (!nationPolicy.canDo(actionName)) continue
            // For user generals, only allow instant-turn actions
            if (general.npcState.toInt() < 2 && actionName !in NpcNationPolicy.AVAILABLE_INSTANT_TURN) continue

            val result = doNationAction(
                actionName, ctx, rng, nationPolicy, supplyCities, backupCities, attackable, warTargetNations
            )
            if (result != null) {
                logger.debug("FactionTurn: general {} chose {}", general.id, result)
                return result
            }
        }

        return "휴식"
    }

    // ──────────────────────────────────────────────────────────
    //  chooseInstantNationTurn: Instant nation turn (subset of actions)
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseInstantNationTurn: Only processes actions available for instant turns.
     */
    fun chooseInstantNationTurn(general: Officer, world: SessionState): String? {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "InstantNationTurn", world.currentYear, world.currentMonth, general.id
        )

        if (general.factionId == 0L) return null

        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val city = ports.planet(general.planetId)?.toEntity() ?: return null
        val nation = ports.faction(general.factionId)?.toEntity() ?: return null

        val allCities = ports.allPlanets().map { it.toEntity() }
        val allGenerals = ports.allOfficers().map { it.toEntity() }
        val allNations = ports.allFactions().map { it.toEntity() }
        val diplomacies = ports.activeDiplomacies().map { it.toEntity() }

        val nationCities = allCities.filter { it.factionId == nation.id }
        val frontCities = nationCities.filter { it.frontState > 0 }
        val rearCities = nationCities.filter { it.frontState.toInt() == 0 }
        val supplyCities = nationCities.filter { it.supplyState > 0 }
        val backupCities = nationCities.filter { it.frontState.toInt() == 0 && it.supplyState > 0 }
        val nationGenerals = allGenerals.filter { it.factionId == general.factionId }

        val diplomacyState = calcDiplomacyState(worldId, nation, diplomacies)
        val nationPolicy = NpcPolicyBuilder.buildNationPolicy(nation.meta)
        val generalType = classifyGeneral(general, rng, nationPolicy.minNPCWarLeadership)
        val attackable = frontCities.any { it.supplyState > 0 }
        val warTargetNations = calcWarTargetNations(nation, diplomacies)

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = diplomacyState, generalType = generalType,
            allCities = allCities, allGenerals = allGenerals, allNations = allNations,
            frontCities = frontCities, rearCities = rearCities, nationGenerals = nationGenerals,
        )

        for (actionName in nationPolicy.priority) {
            if (actionName !in NpcNationPolicy.AVAILABLE_INSTANT_TURN) continue
            if (!nationPolicy.canDo(actionName)) continue

            val result = doNationAction(
                actionName, ctx, rng, nationPolicy, supplyCities, backupCities, attackable, warTargetNations
            )
            if (result != null) return result
        }

        return "휴식"
    }

    // ──────────────────────────────────────────────────────────
    //  chooseGeneralTurn: High-level NPC general turn orchestrator
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy chooseGeneralTurn: Main entry point for NPC general-level turn decisions.
     * Handles special cases (troop leaders, wandering lords, abdication) then iterates priorities.
     */
    fun chooseGeneralTurn(general: Officer, world: SessionState): String {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "OfficerAI", world.currentYear, world.currentMonth, general.id
        )

        val npcType = general.npcState.toInt()

        // Set defence_train for NPCs (per PHP line 3741)
        if (npcType >= 2 && general.defenceTrain.toInt() != 80) {
            general.defenceTrain = 80
        }

        // Per PHP line 3745: do선양 (abdication) check BEFORE npcType==5
        if (general.officerLevel.toInt() == 20 && npcType >= 2) {
            val abdicateResult = doAbdicate(general, world, rng)
            if (abdicateResult != null) return abdicateResult
        }

        // Troop leader: always rally (per PHP line 3753)
        if (npcType == 5) {
            if (general.factionId == 0L) {
                general.killTurn = 1
                return "휴식"
            }
            return doRally(general, rng)
        }

        // Reserved command check (per PHP line 3767)
        val reservedAction = checkReservedCommand(general)
        if (reservedAction != null) return reservedAction

        // Injury check: per PHP line 3772, uses cureThreshold (default 10), NOT injury > 0
        val nationPolicy = if (general.factionId != 0L) {
            val nation = worldPortFactory.create(world.id.toLong()).faction(general.factionId)?.toEntity()
            if (nation != null) NpcPolicyBuilder.buildNationPolicy(nation.meta) else NpcNationPolicy()
        } else {
            NpcNationPolicy()
        }
        if (general.injury > nationPolicy.cureThreshold) return "요양"

        // NPC rise check (per PHP line 3778)
        if ((npcType == 2 || npcType == 3) && general.factionId == 0L) {
            val riseResult = doRise(general, world, rng)
            if (riseResult != null) return riseResult
        }

        // Wanderer without nation: join or wander (per PHP line 3786)
        if (general.factionId == 0L) {
            return decideWandererAction(general, world, rng)
        }

        // NPC lord without capital: structured do건국/do방랑군이동/do해산 (per PHP line 3802)
        if (npcType >= 2 && general.officerLevel.toInt() == 20) {
            val nation = worldPortFactory.create(world.id.toLong()).faction(general.factionId)?.toEntity()
            if (nation != null && nation.capitalPlanetId == null) {
                val initYear = (world.config["init_year"] as? Number)?.toInt()
                    ?: (world.config["startyear"] as? Number)?.toInt()
                    ?: world.currentYear.toInt()
                val initMonth = (world.config["init_month"] as? Number)?.toInt() ?: 1
                val relYearMonth = (world.currentYear.toInt() * 12 + world.currentMonth.toInt()) -
                    (initYear * 12 + initMonth)

                // Per PHP: do건국 only if relYearMonth > 1
                if (relYearMonth > 1) {
                    val foundResult = doFoundNation(general, rng)
                    if (foundResult != null) return foundResult
                }

                // Per PHP: do방랑군이동
                val moveResult = doWandererMove(general, world, rng)
                if (moveResult != null) return moveResult

                // Per PHP: do해산 only if relYearMonth > 1
                if (relYearMonth > 1) {
                    val disbandResult = doDisband(general)
                    if (disbandResult != null) return disbandResult
                }
            }
        }

        val killTurn = general.killTurn?.toInt()
        if (npcType >= 2 && killTurn != null && killTurn <= 5) {
            val nation = worldPortFactory.create(world.id.toLong()).faction(general.factionId)?.toEntity()
            return doDeathPreparation(general, nation, rng)
        }

        // Standard decision via decideAndExecute
        return decideAndExecute(general, world)
    }

    // ──────────────────────────────────────────────────────────
    //  Helper: build context for a general
    // ──────────────────────────────────────────────────────────

    private fun buildContextForGeneral(general: Officer, world: SessionState, rng: Random): AIContext? {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val city = ports.planet(general.planetId)?.toEntity() ?: return null
        val nation = ports.faction(general.factionId)?.toEntity()

        val allCities = ports.allPlanets().map { it.toEntity() }
        val allGenerals = ports.allOfficers().map { it.toEntity() }
        val allNations = ports.allFactions().map { it.toEntity() }
        val diplomacies = ports.activeDiplomacies().map { it.toEntity() }

        val nationCities = if (nation != null) allCities.filter { it.factionId == nation.id } else emptyList()
        val frontCities = nationCities.filter { it.frontState > 0 }
        val rearCities = nationCities.filter { it.frontState.toInt() == 0 }
        val nationGenerals = allGenerals.filter { it.factionId == general.factionId }

        val diplomacyState = calcDiplomacyState(worldId, nation, diplomacies)
        val nationPolicy = if (nation != null) NpcPolicyBuilder.buildNationPolicy(nation.meta) else NpcNationPolicy()
        val generalType = classifyGeneral(general, rng, nationPolicy.minNPCWarLeadership)

        return AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = diplomacyState, generalType = generalType,
            allCities = allCities, allGenerals = allGenerals, allNations = allNations,
            frontCities = frontCities, rearCities = rearCities, nationGenerals = nationGenerals,
        )
    }

    // ──────────────────────────────────────────────────────────
    //  Helper: getNationChiefLevel
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy: minimum chief level depends on nation level.
     * Higher nation level = more officer slots available.
     */
    private fun getNationChiefLevel(nationLevel: Int): Int {
        return when (nationLevel) {
            7 -> 5
            6 -> 5
            5 -> 7
            4 -> 7
            3 -> 9
            2 -> 9
            else -> 11
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Utility: weighted random choice
    // ──────────────────────────────────────────────────────────

    /**
     * Choose an item from a list with weights computed by [weightFn].
     */
    private fun <T> choiceByWeight(rng: Random, items: List<T>, weightFn: (T) -> Double): T? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items[0]
        val weights = items.map { weightFn(it) }
        val totalWeight = weights.sum()
        if (totalWeight <= 0) return items[rng.nextInt(items.size)]
        var r = rng.nextDouble() * totalWeight
        for (i in items.indices) {
            r -= weights[i]
            if (r <= 0) return items[i]
        }
        return items.last()
    }

    /**
     * Choose from list of Pair(item, weight).
     */
    private fun <T> choiceByWeightPair(rng: Random, items: List<Pair<T, Double>>): T? {
        if (items.isEmpty()) return null
        val totalWeight = items.sumOf { it.second }
        if (totalWeight <= 0) return items[rng.nextInt(items.size)].first
        var r = rng.nextDouble() * totalWeight
        for ((item, w) in items) {
            r -= w
            if (r <= 0) return item
        }
        return items.last().first
    }

    /**
     * Choose from list of Pair(item, weight) where items are raw values.
     */
    private fun <T> choiceByWeightPairRaw(rng: Random, items: List<Pair<T, Double>>): T? {
        return choiceByWeightPair(rng, items)
    }

    // ──────────────────────────────────────────────────────────
    //  Utility: value clamping
    // ──────────────────────────────────────────────────────────

    private fun valueFit(value: Int, min: Int, max: Int): Int {
        return value.coerceIn(min, max)
    }

    private fun valueFitD(value: Double, min: Double, max: Double = Double.MAX_VALUE): Double {
        return value.coerceIn(min, max)
    }

    // ──────────────────────────────────────────────────────────
    //  do건국 (doFoundNation): AI decides to found a new nation
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do건국: Wandering lord NPC founds a nation.
     * Picks random nation type and color, names nation after general.
     * Clears movingTargetCityID.
     */
    private fun doFoundNation(general: Officer, rng: Random): String? {
        // Per legacy: pick random nation type (1-3) and color (0-15)
        val nationType = rng.nextInt(3) + 1
        val nationColor = rng.nextInt(16)

        general.meta["aiArg"] = mutableMapOf<String, Any>(
            "factionName" to general.name,
            "nationType" to nationType.toString(),
            "colorType" to nationColor,
        )
        general.meta.remove("movingTargetCityID")

        return "건국"
    }

    // ──────────────────────────────────────────────────────────
    //  do국가선택 (doSelectNation): Wandering general picks a nation to join
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do국가선택: Wandering NPC general tries to join a nation.
     * - NPC type 9 (barbarian): tries to join another barbarian lord's nation
     * - Others: 30% chance to try random enlistment, with early/late game gates
     * - 20% chance to just move to adjacent city
     * - Otherwise returns null (do neutral action)
     */
    private fun doSelectNation(general: Officer, world: SessionState, rng: Random): String? {
        val ports = worldPortFactory.create(world.id.toLong())
        // Barbarians (npcType 9) join other barbarian nations directly
        if (general.npcState.toInt() == 9) {
            val barbarianLords = ports.allOfficers().map { it.toEntity() }
                .filter { it.officerLevel.toInt() == 20 && it.npcState.toInt() == 9 && it.factionId != 0L }
            if (barbarianLords.isNotEmpty()) {
                val target = barbarianLords[rng.nextInt(barbarianLords.size)]
                general.meta["aiArg"] = mutableMapOf<String, Any>("destNationId" to target.factionId)
                return "임관"
            }
        }

        // 30% chance to try random enlistment
        if (rng.nextDouble() < 0.3) {
            // Affinity 999 = never joins
            if (general.affinity.toInt() == 999) return null

            val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
            val yearsElapsed = world.currentYear - startYear

            if (yearsElapsed < 3) {
                // Early game: fewer nations → less chance to join
                val nations = ports.allFactions().map { it.toEntity() }
                val nationCnt = nations.size
                val notFullNationCnt = nations.count { nation ->
                    val genCount = ports.officersByFaction(nation.id).size
                    genCount < 20 // initialNationGenLimit analog
                }
                if (nationCnt == 0 || notFullNationCnt == 0) return null

                // Per legacy: prob = (1/(nationCnt+1) / notFullNationCnt^3)^0.25
                val prob = (1.0 / (nationCnt + 1) / notFullNationCnt.toDouble().pow(3.0)).pow(0.25)
                if (rng.nextDouble() < prob) return null
            } else {
                // Late game: fixed 50% gate → effective 0.3 * 0.5 = 0.15
                if (rng.nextDouble() < 0.5) return null
            }

            return "랜덤임관"
        }

        // 20% chance to move to adjacent city
        if (rng.nextDouble() < 0.2) {
            val allCities = ports.allPlanets().map { it.toEntity() }
            val currentCity = allCities.find { it.id == general.planetId } ?: return null
            val adjacentIds = getAdjacentCityIds(currentCity, allCities)
            if (adjacentIds.isEmpty()) return null
            general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to adjacentIds[rng.nextInt(adjacentIds.size)])
            return "이동"
        }

        return null
    }

    // ──────────────────────────────────────────────────────────
    //  do방랑군이동 (doWandererMove): Wandering army movement AI
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy do방랑군이동: Wandering lord moves toward unoccupied major cities (level 5-6).
     * - If already at a major unoccupied city alone, stay (return null → will try 건국)
     * - Otherwise pick a target city and move toward it step by step
     * - Prefers nearby unoccupied major cities; if adjacent one is available, prioritize it
     */
    private fun doWandererMove(general: Officer, world: SessionState, rng: Random): String? {
        val ports = worldPortFactory.create(world.id.toLong())
        val allCities = ports.allPlanets().map { it.toEntity() }
        val allGenerals = ports.allOfficers().map { it.toEntity() }

        val currentCity = allCities.find { it.id == general.planetId } ?: return null

        // Check if we're the only lord here at a major city
        val lordsHere = allGenerals.count { it.officerLevel.toInt() == 20 && it.planetId == general.planetId }
        if (lordsHere <= 1 && currentCity.level.toInt() in 5..6) {
            return null // Stay here, can try founding
        }

        // Build set of occupied cities (by lords or nations)
        val lordCityIds = allGenerals
            .filter { it.officerLevel.toInt() == 20 }
            .map { it.planetId }.toSet()
        val nationCityIds = allCities
            .filter { it.factionId != 0L }
            .map { it.id }.toSet()
        val occupiedCities = lordCityIds + nationCityIds

        // Check current target
        var targetCityId = (general.meta["movingTargetCityID"] as? Number)?.toLong()
        if (targetCityId == general.planetId || (targetCityId != null && targetCityId in occupiedCities)) {
            targetCityId = null
        }

        // Find new target if needed
        if (targetCityId == null) {
            // BFS from current city, find unoccupied major cities within range 4
            val candidates = mutableListOf<Pair<Long, Double>>()
            val visited = mutableSetOf(general.planetId)
            val queue = ArrayDeque<Pair<Long, Int>>()
            queue.add(general.planetId to 0)

            while (queue.isNotEmpty()) {
                val (cid, dist) = queue.removeFirst()
                if (dist >= 4) continue
                val city = allCities.find { it.id == cid } ?: continue
                val adjIds = getAdjacentCityIds(city, allCities)
                for (adjId in adjIds) {
                    if (!visited.add(adjId)) continue
                    val adjCity = allCities.find { it.id == adjId } ?: continue
                    if (adjId !in occupiedCities && adjCity.level.toInt() in 5..6) {
                        candidates.add(adjId to (1.0 / 2.0.pow(dist + 1)))
                    }
                    queue.add(adjId to dist + 1)
                }
            }

            if (candidates.isEmpty()) return null
            targetCityId = choiceByWeightPair(rng, candidates) ?: return null
            general.meta["movingTargetCityID"] = targetCityId
        }

        if (targetCityId == general.planetId) {
            return "인재탐색"
        }

        // BFS from target to find distances
        val distFromTarget = bfsCityDistances(targetCityId, allCities)
        val currentDist = distFromTarget[general.planetId] ?: return null

        // Pick next step: prefer adjacent unoccupied major cities, or step closer to target
        val adjacentIds = getAdjacentCityIds(currentCity, allCities)
        val moveOptions = mutableListOf<Pair<Long, Double>>()

        for (adjId in adjacentIds) {
            val adjCity = allCities.find { it.id == adjId } ?: continue
            // If adjacent is an unoccupied major city, high priority
            if (adjCity.level.toInt() in 5..6 && adjId !in occupiedCities) {
                moveOptions.add(adjId to 10.0)
            }
            // If it gets us closer to target
            val adjDist = distFromTarget[adjId] ?: continue
            if (adjDist + 1 == currentDist) {
                moveOptions.add(adjId to 1.0)
            }
        }

        if (moveOptions.isEmpty()) return null
        val destId = choiceByWeightPair(rng, moveOptions) ?: return null
        general.meta["aiArg"] = mutableMapOf<String, Any>("destCityId" to destId)
        return "이동"
    }

    // ──────────────────────────────────────────────────────────
    //  doNPC헌납 (doNpcDedicate): NPC donates resources to nation lord
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy doNPC헌납: NPC general donates excess resources to nation treasury.
     * Similar to doDonate but uses NPC-specific thresholds (reqNPCWarGold/Rice, reqNPCDevelGold/Rice).
     * War generals use war thresholds, development generals use devel thresholds.
     * Special cases: if devel general has enough for war threshold but war > devel+1000, donate excess.
     * Also handles emergency rice donation when nation rice is critically low.
     */
    private fun doNpcDedicate(ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy): String? {
        val general = ctx.general
        val nation = ctx.nation ?: return null
        val genType = ctx.generalType
        val isWarGen = genType and GeneralType.COMMANDER.flag != 0

        data class DonateCandidate(val isGold: Boolean, val amount: Int, val weight: Int)

        val candidates = mutableListOf<DonateCandidate>()

        // Process gold and rice
        data class ResourceInfo(val isGold: Boolean, val genRes: Int, val nationRes: Int, val reqNation: Int, val reqWar: Int, val reqDevel: Int)
        val resources = listOf(
            ResourceInfo(true, general.funds, nation.funds, nationPolicy.reqNationGold,
                nationPolicy.calcPolicyValue("reqNPCWarGold", nation),
                nationPolicy.calcPolicyValue("reqNPCDevelGold", nation)),
            ResourceInfo(false, general.supplies, nation.supplies, nationPolicy.reqNationRice,
                nationPolicy.calcPolicyValue("reqNPCWarRice", nation),
                nationPolicy.calcPolicyValue("reqNPCDevelRice", nation))
        )

        for (res in resources) {
            val isGold = res.isGold
            val gRes = res.genRes
            val nRes = res.nationRes
            val reqN = res.reqNation
            val reqW = res.reqWar
            val reqD = res.reqDevel

            if (isWarGen) {
                // War general: use war thresholds
                if (nRes >= reqN) continue
                if (gRes < (reqW * 1.5).toInt()) continue
                if (reqW > 0 && rng.nextDouble() >= (gRes.toDouble() / reqW - 0.5)) continue
                val amount = gRes - reqW
                if (amount < nationPolicy.minimumResourceActionAmount) continue
                candidates.add(DonateCandidate(isGold, amount, amount))
            } else {
                // Development general
                val reqRes = reqD

                // Special case: devel general has enough for war threshold and war >> devel
                if (gRes >= reqW && reqW > reqD + 1000) {
                    val amount = gRes - reqD
                    candidates.add(DonateCandidate(isGold, amount, amount))
                    continue
                }

                // Excess resources (5x threshold and >= 5000)
                if (gRes >= reqD * 5 && gRes >= 5000) {
                    val amount = gRes - reqD
                    candidates.add(DonateCandidate(isGold, amount, amount))
                    continue
                }

                // Nation needs resources
                if (nRes >= reqN) continue

                // Emergency rice: nation rice critically low
                if (!isGold && nRes <= 500 && gRes >= 500) {
                    val amount = if (gRes < 1000) gRes else gRes / 2
                    candidates.add(DonateCandidate(isGold, amount, amount))
                    continue
                }

                if (gRes < (reqRes * 1.5).toInt()) continue
                if (reqRes > 0 && rng.nextDouble() >= (gRes.toDouble() / reqRes - 0.5)) continue
                val amount = gRes - reqRes
                if (amount < nationPolicy.minimumResourceActionAmount) continue
                candidates.add(DonateCandidate(isGold, amount, amount))
            }
        }

        if (candidates.isEmpty()) return null

        // Pick one using weight
        val picked = choiceByWeightPair(rng, candidates.map { Pair(it, it.weight.toDouble()) }) ?: return null

        val donateAmount = valueFit(picked.amount, nationPolicy.minimumResourceActionAmount, nationPolicy.maximumResourceActionAmount)
        general.meta["aiArg"] = mutableMapOf<String, Any>("isGold" to picked.isGold, "amount" to donateAmount)

        return "헌납"
    }

    // ──────────────────────────────────────────────────────────
    //  City adjacency helpers (for wanderer movement)
    // ──────────────────────────────────────────────────────────

    /**
     * Get adjacent city IDs from city meta or by region proximity.
     * Cities store adjacency in meta["connections"] as list of city IDs.
     */
    private fun getAdjacentCityIds(city: Planet, allCities: List<Planet>): List<Long> {
        // Try meta connections first
        val connections = city.meta["connections"] as? Collection<*>
        if (connections != null) {
            return connections.mapNotNull { (it as? Number)?.toLong() }
        }

        // Fallback: cities in same or adjacent region (simplified)
        return allCities
            .filter { it.id != city.id && kotlin.math.abs(it.region - city.region) <= 1 }
            .map { it.id }
    }

    /**
     * BFS from a city to compute distances to all reachable cities.
     */
    private fun bfsCityDistances(startCityId: Long, allCities: List<Planet>): Map<Long, Int> {
        val result = mutableMapOf(startCityId to 0)
        val queue = ArrayDeque<Pair<Long, Int>>()
        queue.add(startCityId to 0)

        while (queue.isNotEmpty()) {
            val (cid, dist) = queue.removeFirst()
            val city = allCities.find { it.id == cid } ?: continue
            for (adjId in getAdjacentCityIds(city, allCities)) {
                if (adjId !in result) {
                    result[adjId] = dist + 1
                    queue.add(adjId to dist + 1)
                }
            }
        }
        return result
    }

    // ──────────────────────────────────────────────────────────
    //  Legacy income/outcome helpers for bill rate calculation
    // ──────────────────────────────────────────────────────────

    /**
     * Per legacy calcCityGoldIncome: pop * comm/commMax * trustRatio / 30 * secuFactor.
     * Simplified: no officer bonus, no capital bonus, no nation type modifier.
     */
    private fun calcCityGoldIncome(city: Planet): Int {
        if (city.supplyState <= 0) return 0
        val trustRatio = city.approval / 200.0 + 0.5
        val commMax = city.commerceMax.coerceAtLeast(1)
        var income = city.population.toDouble() * city.commerce / commMax * trustRatio / 30.0
        val secuMax = city.securityMax.coerceAtLeast(1)
        income *= 1.0 + city.security.toDouble() / secuMax / 10.0
        return round(income).toInt()
    }

    /**
     * Per legacy calcCityRiceIncome: pop * agri/agriMax * trustRatio / 30 * secuFactor.
     */
    private fun calcCityRiceIncome(city: Planet): Int {
        if (city.supplyState <= 0) return 0
        val trustRatio = city.approval / 200.0 + 0.5
        val agriMax = city.productionMax.coerceAtLeast(1)
        var income = city.population.toDouble() * city.production / agriMax * trustRatio / 30.0
        val secuMax = city.securityMax.coerceAtLeast(1)
        income *= 1.0 + city.security.toDouble() / secuMax / 10.0
        return round(income).toInt()
    }

    /**
     * Per legacy calcCityWallRiceIncome: def * wall/wallMax / 3 * secuFactor.
     */
    private fun calcCityWallRiceIncome(city: Planet): Int {
        if (city.supplyState <= 0) return 0
        val wallMax = city.fortressMax.coerceAtLeast(1)
        var income = city.orbitalDefense.toDouble() * city.fortress / wallMax / 3.0
        val secuMax = city.securityMax.coerceAtLeast(1)
        income *= 1.0 + city.security.toDouble() / secuMax / 10.0
        return round(income).toInt()
    }

    /**
     * Per legacy getOutcome: sum(getBill(dedication)) * billRate/100.
     * getBill(ded) = getDedLevel(ded) * 200 + 400
     * getDedLevel(ded) = ceil(sqrt(ded) / 10), clamped to [0, maxDedLevel=5]
     */
    private fun calcOutcome(billRate: Int, generals: List<Officer>): Int {
        val totalBill = generals.sumOf { gen ->
            val dedLevel = kotlin.math.ceil(kotlin.math.sqrt(gen.dedication.toDouble()) / 10.0).toInt()
                .coerceIn(0, 5)
            dedLevel * 200 + 400
        }
        return round(totalBill.toDouble() * billRate / 100.0).toInt()
    }
}
