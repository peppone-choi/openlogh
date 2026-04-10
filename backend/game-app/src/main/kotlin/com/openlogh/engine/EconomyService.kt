package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.Message
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.round
import kotlin.math.sqrt

@Service
class EconomyService @Autowired constructor(
    private val worldPortFactory: JpaWorldPortFactory,
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    @Suppress("unused") private val mapService: MapService,
    @Suppress("unused") private val historyService: HistoryService,
    @Suppress("unused") private val inheritanceService: InheritanceService,
    private val gin7EconomyService: Gin7EconomyService? = null,
) {
    constructor(
        planetRepository: PlanetRepository,
        factionRepository: FactionRepository,
        officerRepository: OfficerRepository,
        messageRepository: MessageRepository,
        mapService: MapService,
        historyService: HistoryService,
        inheritanceService: InheritanceService,
    ) : this(
        JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        ),
        officerRepository,
        messageRepository,
        mapService,
        historyService,
        inheritanceService,
        null,
    )

    /**
     * 8-arg constructor for production wiring — passes Gin7EconomyService through for
     * delegation of `updateCitySupplyState` (Plan 23-06).
     */
    constructor(
        planetRepository: PlanetRepository,
        factionRepository: FactionRepository,
        officerRepository: OfficerRepository,
        messageRepository: MessageRepository,
        mapService: MapService,
        historyService: HistoryService,
        inheritanceService: InheritanceService,
        gin7EconomyService: Gin7EconomyService?,
    ) : this(
        JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        ),
        officerRepository,
        messageRepository,
        mapService,
        historyService,
        inheritanceService,
        gin7EconomyService,
    )

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val NATION_LEVEL_THRESHOLDS = intArrayOf(0, 1, 2, 4, 6, 9, 12, 16, 20, 25)

        private val NATION_LEVEL_NAME = arrayOf(
            "방랑군", "도위", "주자사", "주목", "중랑장", "대장군", "대사마", "공", "왕", "황제"
        )

        fun getNationLevelName(level: Int): String =
            NATION_LEVEL_NAME.getOrElse(level) { "???" }
    }

    private data class DisasterOrBoomEntry(
        val stateCode: Short,
        val title: String,
        val body: String,
    )

    /**
     * TODO Phase 4: gin7 Gin7EconomyService로 대체 예정.
     * 삼국지 농업/상업 기반 경제 로직 제거됨.
     * 현재 stub — 아무 처리도 하지 않음.
     */
    @Transactional
    fun processMonthly(world: SessionState) {
        // gin7EconomyService.processMonthly()로 교체됨 — Gin7EconomyService.kt 참조
    }

    /**
     * Legacy parity: hwe/func_gamerule.php:189 preUpdateMonthly()
     *
     * Upstream a7a19cc3 fix: this method must NOT process income or war income.
     * Income/salary is handled exclusively via scheduled events:
     *   - ProcessIncomeAction: month 1 (gold/funds) and month 7 (rice/supplies)
     *   - ProcessWarIncomeAction: every month via pre_month event
     *
     * Previously the upstream version drained gold 12x/year by calling processIncome
     * every month with default "all" resourceType. This LOGH port keeps the method as
     * an explicit no-op for call-site compatibility (InMemoryTurnProcessor,
     * EconomyPreUpdateStep) so any future Phase 4 economy wiring inherits the
     * legacy-correct schedule by default.
     *
     * TODO Phase 4: when Gin7EconomyService gains salary outlay, ensure that work
     * still flows through the event scheduler — never call it from here.
     */
    @Transactional
    fun preUpdateMonthly(world: SessionState) {
        // No-op: see KDoc. Income/salary is event-driven only (legacy month 1/7 schedule).
    }

    /**
     * Legacy parity: hwe/func_gamerule.php:260 postUpdateMonthly()
     *
     * Upstream a7a19cc3 fix: this method must NOT call processSemiAnnual. Semi-annual
     * decay is handled exclusively via ProcessSemiAnnualAction (month 1 funds decay,
     * month 7 supplies decay). Previously the upstream version decayed both resources
     * 4x/year by calling processSemiAnnual at both months 1 and 7 AND decaying both
     * resources per call.
     *
     * This LOGH port keeps the method as an explicit no-op for call-site compatibility
     * (InMemoryTurnProcessor, EconomyPostUpdateStep). Other post-month responsibilities
     * (city supply state recompute, faction-rank refresh, disaster/boom, trade-rate
     * randomization) live in their own scheduled events / pipeline steps.
     *
     * TODO Phase 4: when Gin7EconomyService gains semi-annual decay, route it through
     * the event scheduler — never call it from here.
     */
    @Transactional
    fun postUpdateMonthly(world: SessionState) {
        // No-op: see KDoc. Semi-annual decay is event-driven only (legacy month 1/7 schedule).
    }

    /**
     * Public entry point for per-turn supply state recalculation (traffic update).
     * Called by TurnService each turn to keep supply routes current.
     *
     * Plan 23-06: the logic lives in `Gin7EconomyService.updatePlanetSupplyState` now.
     * This method delegates so existing call sites (UpdateCitySupplyAction,
     * InMemoryTurnProcessor.updateTraffic, EventServiceTest) keep working without
     * a rename cascade. When `gin7EconomyService` is absent (legacy 7-arg test
     * constructor), falls back to the in-place legacy `updateCitySupply` helper
     * that still exists below as dead-for-production code.
     */
    @Transactional
    fun updateCitySupplyState(world: SessionState) {
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.updatePlanetSupplyState(world)
            return
        }
        // Legacy fallback path — Gin7EconomyService not wired. Retained for the
        // pre-23-06 7-arg constructor so any tests relying on that path still
        // exercise the original BFS logic identically. Production wiring always
        // provides gin7EconomyService via the 8-arg constructor / Spring DI.
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }
        updateCitySupply(world, nations, cities, generals)
        saveCities(ports, cities)
        saveGenerals(ports, generals)
    }

    /**
     * Public entry point for event-driven income processing.
     *
     * Legacy parity: hwe/sammo/Event/Action/ProcessIncome.php (upstream a7a19cc3)
     *
     * Triggered by scenario events on a strict per-resource schedule:
     *   - ["ProcessIncome", "gold"] in month 1 → processes faction.funds only
     *   - ["ProcessIncome", "rice"] in month 7 → processes faction.supplies only
     *
     * The resource literal is the OpenSamguk wire format ("gold"/"rice"); internally
     * this maps to LOGH's faction.funds / faction.supplies. Kept on the wire so any
     * imported legacy event JSON works without translation.
     *
     * CRITICAL: This method MUST NOT be called every month. It is designed to run
     * ONCE per year per resource (gold in Jan, rice in Jul). Calling it monthly
     * results in the upstream 12x salary drain bug.
     *
     * Phase 23-10 wired: routes to `Gin7EconomyService.processIncome(world, resource)`
     * which implements the per-resource body + officer salary outlay (on the gold
     * branch only). When `gin7EconomyService` is absent (legacy 7-arg test
     * constructor), this is a no-op — production wiring always provides Gin7 via
     * the 8-arg constructor / Spring DI.
     *
     * @param world the active session
     * @param resource "gold" (→ funds) or "rice" (→ supplies)
     * @throws IllegalArgumentException if resource is anything other than "gold" or "rice"
     */
    @Transactional
    fun processIncomeEvent(world: SessionState, resource: String) {
        require(resource == "gold" || resource == "rice") {
            "Invalid resource for processIncomeEvent: $resource (expected 'gold' or 'rice')"
        }
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.processIncome(world, resource)
            return
        }
        log.debug("[World {}] processIncomeEvent({}): no gin7 wiring (legacy test ctor)", world.id, resource)
    }

    /**
     * Backward-compatible 1-arg overload — defaults resource to "gold" (month 1 schedule).
     *
     * Retained for EventServiceTest compatibility and any LOGH-internal call sites that
     * predate the upstream a7a19cc3 per-resource port. New call sites MUST use the
     * 2-arg overload to make the resource explicit.
     */
    @Deprecated(
        "Use processIncomeEvent(world, resource) — resource literal is now required by upstream a7a19cc3 contract",
        ReplaceWith("processIncomeEvent(world, \"gold\")"),
    )
    @Transactional
    fun processIncomeEvent(world: SessionState) {
        processIncomeEvent(world, "gold")
    }

    /**
     * Public entry point for event-driven semi-annual processing.
     *
     * Legacy parity: hwe/sammo/Event/Action/ProcessSemiAnnual.php::run($resource) (upstream a7a19cc3)
     *
     * Triggered by scenario events on a strict per-resource schedule:
     *   - ["ProcessSemiAnnual", "gold"] in month 1 → decays faction.funds maintenance only
     *   - ["ProcessSemiAnnual", "rice"] in month 7 → decays faction.supplies maintenance only
     *
     * Previously the upstream version decayed BOTH gold and rice per call AND was
     * triggered from both postUpdateMonthly AND the scenario event, resulting in 4x
     * decay. The fix splits the work per resource and runs each exactly once per year.
     *
     * Phase 23-10 wired: routes to `Gin7EconomyService.processSemiAnnual(world, resource)`
     * which applies the progressive-bracket decay to faction treasury and officer
     * personal stockpiles for a single resource. When `gin7EconomyService` is
     * absent (legacy 7-arg test constructor), this is a no-op.
     *
     * @param world the active session
     * @param resource "gold" (→ funds decay) or "rice" (→ supplies decay)
     * @throws IllegalArgumentException if resource is anything other than "gold" or "rice"
     */
    @Transactional
    fun processSemiAnnualEvent(world: SessionState, resource: String) {
        require(resource == "gold" || resource == "rice") {
            "Invalid resource for processSemiAnnualEvent: $resource (expected 'gold' or 'rice')"
        }
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.processSemiAnnual(world, resource)
            return
        }
        log.debug("[World {}] processSemiAnnualEvent({}): no gin7 wiring (legacy test ctor)", world.id, resource)
    }

    /**
     * Backward-compatible 1-arg overload — defaults resource to "gold" (month 1 schedule).
     */
    @Deprecated(
        "Use processSemiAnnualEvent(world, resource) — resource literal is now required by upstream a7a19cc3 contract",
        ReplaceWith("processSemiAnnualEvent(world, \"gold\")"),
    )
    @Transactional
    fun processSemiAnnualEvent(world: SessionState) {
        processSemiAnnualEvent(world, "gold")
    }

    /**
     * Public test entry point for war income processing.
     *
     * Upstream a7a19cc3 added this as a public method so tests can drive war-income
     * generation without going through the full event scheduler.
     *
     * Distinct from processIncomeEvent: war income is paid every month (not just
     * Jan/Jul) per legacy hwe/sammo/Event/Action/ProcessWarIncome.php. The actual
     * upstream body is **casualty salvage** keyed on `planet.dead > 0` — not a
     * `war_state > 0` gate as earlier KDoc stated. This Plan 23-03 correction is
     * documented in Phase 23 CONTEXT.md (EC-03 port drift).
     *
     * Phase 23-10 wired: routes to `Gin7EconomyService.processWarIncome(world)`.
     * When `gin7EconomyService` is absent (legacy 7-arg test constructor), this is
     * a no-op.
     */
    @Transactional
    fun processWarIncomeEvent(world: SessionState) {
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.processWarIncome(world)
            return
        }
        log.debug("[World {}] processWarIncomeEvent: no gin7 wiring (legacy test ctor)", world.id)
    }

    /**
     * Public entry point for event-driven faction rank update.
     *
     * Phase 23-10 wired: routes to `Gin7EconomyService.updateFactionRank(world)`.
     * The actual formula is `count(planet.level >= 4)` against
     * `FACTION_RANK_THRESHOLDS` (10-level table), **not** a `military_power +
     * population` composite — the earlier plan text was a Plan 23-05 drift that
     * Phase 23 CONTEXT.md documents. Method name retained as
     * `updateNationLevelEvent` to preserve call-site compatibility
     * (`UpdateNationLevelAction`, `EventServiceTest`).
     */
    @Transactional
    fun updateNationLevelEvent(world: SessionState) {
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.updateFactionRank(world)
            return
        }
        log.debug("[World {}] updateNationLevelEvent: no gin7 wiring (legacy test ctor)", world.id)
    }

    // ── Supply state calculation (map-connectivity based — gin7 compatible) ──

    private fun updateCitySupply(world: SessionState, nations: List<Faction>, cities: List<Planet>, generals: List<Officer>) {
        val mapCode = (world.config["mapCode"] as? String) ?: "logh"
        val citiesByNation = cities.groupBy { it.factionId }
        val cityById = cities.associateBy { it.id }
        val generalsByCity = generals.groupBy { it.planetId }

        // Build map city ID <-> DB city ID lookups
        val dbToMapId = cities.associate { it.id to it.mapPlanetId }
        val mapToDbId = cities.associate { it.mapPlanetId to it.id }

        // Neutral cities are always supplied
        for (city in cities) {
            if (city.factionId == 0L) {
                city.supplyState = 1
            }
        }

        for (nation in nations) {
            val nationCities = citiesByNation[nation.id] ?: continue
            val suppliedMapIds = mutableSetOf<Int>()

            val capitalId = nation.capitalPlanetId
            val capitalMapId = if (capitalId != null) dbToMapId[capitalId] else null
            if (capitalMapId != null && capitalMapId != 0) {
                val queue = ArrayDeque<Int>()
                queue.add(capitalMapId)
                suppliedMapIds.add(capitalMapId)

                while (queue.isNotEmpty()) {
                    val currentMapId = queue.removeFirst()
                    val adjacentMapIds = try {
                        mapService.getAdjacentCities(mapCode, currentMapId)
                    } catch (e: Exception) {
                        log.warn("Failed to get adjacent cities for mapPlanetId={}: {}", currentMapId, e.message)
                        emptyList()
                    }
                    for (adjMapId in adjacentMapIds) {
                        if (adjMapId !in suppliedMapIds) {
                            val adjDbId = mapToDbId[adjMapId]
                            val adjCity = if (adjDbId != null) cityById[adjDbId] else null
                            if (adjCity != null && adjCity.factionId == nation.id) {
                                suppliedMapIds.add(adjMapId)
                                queue.add(adjMapId)
                            }
                        }
                    }
                }
            }

            val supplied = suppliedMapIds.mapNotNull { mapToDbId[it] }.toMutableSet()
            if (capitalId != null) supplied.add(capitalId)

            for (city in nationCities) {
                if (city.id in supplied) {
                    city.supplyState = 1
                } else {
                    city.supplyState = 0
                    city.population = (city.population * 0.9).toInt()
                    city.approval = city.approval * 0.9F
                    city.production = (city.production * 0.9).toInt()
                    city.commerce = (city.commerce * 0.9).toInt()
                    city.security = (city.security * 0.9).toInt()
                    city.orbitalDefense = (city.orbitalDefense * 0.9).toInt()
                    city.fortress = (city.fortress * 0.9).toInt()

                    val cityGenerals = generalsByCity[city.id] ?: emptyList()
                    for (general in cityGenerals) {
                        general.ships = (general.ships * 0.95).toInt()
                        general.morale = (general.morale * 0.95).toInt().coerceIn(0, 150).toShort()
                        general.training = (general.training * 0.95).toInt().coerceIn(0, 110).toShort()
                    }

                    if (city.approval < 30 && city.id != nation.capitalPlanetId) {
                        log.info("City {} (id={}) lost to isolation (trust={})", city.name, city.id, city.approval)
                        for (general in cityGenerals) {
                            if (general.officerPlanet == city.id.toInt()) {
                                general.officerLevel = 1
                                general.officerPlanet = 0
                            }
                        }
                        city.factionId = 0
                        city.officerSet = 0
                        city.conflict = mutableMapOf()
                        city.term = 0
                        city.frontState = 0
                    }
                }
            }
        }
    }

    /**
     * 연초 통계: 국가 국력(power)·장수수(gennum) 갱신.
     * TurnService에서 매년 1월에 호출.
     *
     * Phase 23-10: delegates to `Gin7EconomyService.processYearlyStatistics` when
     * available. The legacy body below is retained as dead-for-production code
     * for the pre-23-07 7-arg constructor test path; production wiring always
     * routes through Gin7.
     */
    @Transactional
    fun processYearlyStatistics(world: SessionState) {
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.processYearlyStatistics(world)
            return
        }
        processYearlyStatisticsLegacy(world)
    }

    private fun processYearlyStatisticsLegacy(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val nations = ports.allFactions().map { it.toEntity() }
        val cities = ports.allPlanets().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }

        val citiesByNation = cities.groupBy { it.factionId }
        val generalsByNation = generals
            .filter { it.npcState.toInt() != 5 && it.npcState != SovereignConstants.NPC_STATE_EMPEROR }
            .groupBy { it.factionId }
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()

        for (nation in nations) {
            if (nation.factionRank.toInt() == 0) continue

            val nationGenerals = generalsByNation[nation.id] ?: emptyList()
            val nationCities = citiesByNation[nation.id] ?: emptyList()

            // 자원: (국가금+쌀 + 장수금+쌀합) / 100
            val generalGoldRice = nationGenerals.sumOf { (it.funds + it.supplies).toLong() }
            val resource = ((nation.funds + nation.supplies).toLong() + generalGoldRice) / 100.0

            // 기술
            val tech = nation.techLevel.toDouble()

            // 도시파워: sum(pop) * sum(pop+production+commerce+security+fortress+orbitalDefense)
            //           / sum(popMax+productionMax+commerceMax+securityMax+fortressMax+orbitalDefenseMax) / 100
            val suppliedCities = nationCities.filter { it.supplyState.toInt() == 1 }
            val cityPower = if (suppliedCities.isNotEmpty()) {
                val popSum = suppliedCities.sumOf { it.population.toLong() }
                val valueSum = suppliedCities.sumOf { (it.population + it.production + it.commerce + it.security + it.fortress + it.orbitalDefense).toLong() }
                val maxSum = suppliedCities.sumOf { (it.populationMax + it.productionMax + it.commerceMax + it.securityMax + it.fortressMax + it.orbitalDefenseMax).toLong() }
                if (maxSum > 0) (popSum * valueSum).toDouble() / maxSum / 100.0 else 0.0
            } else 0.0

            // 장수능력
            val statPower = nationGenerals.sumOf { g ->
                val lead = g.leadership.toDouble()
                val str = g.command.toDouble()
                val intel = g.intelligence.toDouble()
                val npcMul = if (g.npcState < 2) 1.2 else 1.0
                val leaderCore = if (lead >= 40) lead else 0.0
                npcMul * leaderCore * 2 + (sqrt(intel * str) * 2 + lead / 2) / 2
            }

            // 숙련: sum(dex1..dex5) / 1000
            val dexSum = nationGenerals.sumOf { (it.dex1 + it.dex2 + it.dex3 + it.dex4 + it.dex5).toLong() }
            val dexPower = dexSum / 1000.0

            // 경험공헌: sum(experience+dedication) / 100
            val expDed = nationGenerals.sumOf { (it.experience + it.dedication).toLong() } / 100.0

            val rawPower = ((resource + tech + cityPower + statPower + dexPower + expDed) / 10.0)
            val rng = DeterministicRng.create(
                hiddenSeed,
                "nationPower",
                world.currentYear,
                world.currentMonth,
                nation.id,
            )
            val power = round(rawPower * rng.nextDouble(0.95, 1.05)).toInt()

            // 최대 국력 기록
            val prevMaxPower = (nation.meta["maxPower"] as? Number)?.toInt() ?: 0
            if (power > prevMaxPower) {
                nation.meta["maxPower"] = power
            }

            nation.militaryPower = power
        }

        saveNations(ports, nations)
        log.info("[World {}] Yearly statistics updated for {} nations", world.id, nations.size)
    }

    // ── Phase A3: processDisasterOrBoom ──
    //
    // Phase 23-10: delegates to `Gin7EconomyService.processDisasterOrBoom` when
    // available. Legacy body retained as dead-for-production code (see KDoc
    // on processYearlyStatistics above for rationale).

    fun processDisasterOrBoom(world: SessionState) {
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.processDisasterOrBoom(world)
            return
        }
        processDisasterOrBoomLegacy(world)
    }

    private fun processDisasterOrBoomLegacy(world: SessionState) {
        val startYear = try {
            (world.config["startYear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        } catch (e: Exception) {
            log.warn("Failed to resolve startYear from config: {}", e.message)
            world.currentYear.toInt()
        }

        // Skip first 3 years
        if (startYear + 3 > world.currentYear.toInt()) return

        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val month = world.currentMonth.toInt()

        // Reset disaster state
        for (city in cities) {
            if (city.state <= 10) {
                city.state = 0
            }
        }

        // Boom probability by month (4,7 = 25%, others = 0)
        val boomRate = when (month) {
            4, 7 -> 0.25
            else -> 0.0
        }

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "disaster",
            world.currentYear, world.currentMonth
        )
        val isGood = boomRate > 0 && rng.nextDouble() < boomRate

        val targetCities = mutableListOf<Planet>()
        for (city in cities) {
            val secuRatio = if (city.securityMax > 0) city.security.toDouble() / city.securityMax else 0.0
            val raiseProp = if (isGood) {
                0.02 + secuRatio * 0.05  // 2~7%
            } else {
                0.06 - secuRatio * 0.05  // 1~6%
            }
            if (rng.nextDouble() < raiseProp) {
                targetCities.add(city)
            }
        }

        if (targetCities.isEmpty()) {
            saveCities(ports, cities)
            return
        }

        val disasterEntries = mapOf(
            1 to listOf(
                DisasterOrBoomEntry(4, "【재난】", "역병이 발생하여 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "에너지 부족으로 주민들이 고통받고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "반란군이 출현해 행성을 습격하고 있습니다."),
            ),
            4 to listOf(
                DisasterOrBoomEntry(7, "【재난】", "우주 방사선으로 인해 피해가 급증하고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(6, "【재난】", "소행성 충돌로 인해 피해가 속출하고 있습니다."),
            ),
            7 to listOf(
                DisasterOrBoomEntry(8, "【재난】", "자원 고갈로 인해 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(8, "【재난】", "흉작으로 굶어죽는 주민들이 늘어나고 있습니다."),
            ),
            10 to listOf(
                DisasterOrBoomEntry(3, "【재난】", "혹한 행성 환경으로 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "함대 봉쇄로 인해 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "반란군이 출현해 행성을 습격하고 있습니다."),
            ),
        )
        val boomEntries = mapOf(
            4 to DisasterOrBoomEntry(2, "【호황】", "호황으로 행성이 번창하고 있습니다."),
            7 to DisasterOrBoomEntry(1, "【풍작】", "풍년으로 행성이 번창하고 있습니다."),
        )

        if (isGood) {
            val entry = boomEntries[month] ?: boomEntries[4]!!
            for (city in targetCities) {
                val secuRatio = if (city.securityMax > 0) city.security.toDouble() / city.securityMax / 0.8 else 0.0
                val affectRatio = 1.01 + secuRatio.coerceIn(0.0, 1.0) * 0.04

                city.state = entry.stateCode
                city.population = (city.population * affectRatio).toInt().coerceAtMost(city.populationMax)
                city.approval = (city.approval * affectRatio.toFloat()).coerceAtMost(100F)
                city.production = (city.production * affectRatio).toInt().coerceAtMost(city.productionMax)
                city.commerce = (city.commerce * affectRatio).toInt().coerceAtMost(city.commerceMax)
                city.security = (city.security * affectRatio).toInt().coerceAtMost(city.securityMax)
                city.orbitalDefense = (city.orbitalDefense * affectRatio).toInt().coerceAtMost(city.orbitalDefenseMax)
                city.fortress = (city.fortress * affectRatio).toInt().coerceAtMost(city.fortressMax)
            }

            val cityNames = targetCities.joinToString(" ") { it.name }
            historyService.logWorldHistory(
                worldId = world.id.toLong(),
                message = "${entry.title} ${cityNames}에 ${entry.body}",
                year = world.currentYear.toInt(),
                month = month,
            )
        } else {
            val entries = disasterEntries[month] ?: disasterEntries[1]!!
            val entry = entries[rng.nextInt(entries.size)]
            for (city in targetCities) {
                val secuRatio = if (city.securityMax > 0) city.security.toDouble() / city.securityMax / 0.8 else 0.0
                val affectRatio = 0.8 + secuRatio.coerceIn(0.0, 1.0) * 0.15

                city.state = entry.stateCode
                city.population = (city.population * affectRatio).toInt()
                city.approval = city.approval * affectRatio.toFloat()
                city.production = (city.production * affectRatio).toInt()
                city.commerce = (city.commerce * affectRatio).toInt()
                city.security = (city.security * affectRatio).toInt()
                city.orbitalDefense = (city.orbitalDefense * affectRatio).toInt()
                city.fortress = (city.fortress * affectRatio).toInt()
            }

            val affectedCityIds = targetCities.map { it.id }
            val generals = officerRepository.findBySessionIdAndPlanetIdIn(world.id.toLong(), affectedCityIds)
            val injuryMessages = mutableListOf<Message>()
            for (general in generals) {
                if (rng.nextDouble() >= 0.3) continue
                val injuryAmount = rng.nextInt(1, 17)
                general.injury = (general.injury + injuryAmount).coerceIn(0, 80).toShort()
                general.ships = (general.ships * 0.98).toInt()
                general.morale = (general.morale * 0.98).toInt().coerceIn(0, 150).toShort()
                general.training = (general.training * 0.98).toInt().coerceIn(0, 110).toShort()
                injuryMessages += Message(
                    sessionId = world.id.toLong(),
                    mailboxCode = "general_action",
                    messageType = "log",
                    srcId = general.id,
                    destId = general.id,
                    payload = mutableMapOf(
                        "message" to "재난으로 인해 <R>부상</>을 당했습니다.",
                        "year" to world.currentYear.toInt(),
                        "month" to month,
                    ),
                )
            }
            officerRepository.saveAll(generals)
            if (injuryMessages.isNotEmpty()) {
                messageRepository.saveAll(injuryMessages)
            }

            val cityNames = targetCities.joinToString(" ") { it.name }
            historyService.logWorldHistory(
                worldId = world.id.toLong(),
                message = "${entry.title} ${cityNames}에 ${entry.body}",
                year = world.currentYear.toInt(),
                month = month,
            )
        }

        saveCities(ports, cities)
    }

    // ── Phase A3: randomizeCityTradeRate ──
    //
    // Phase 23-10: delegates to `Gin7EconomyService.randomizePlanetTradeRate`
    // when available. Legacy body retained as dead-for-production code.

    fun randomizeCityTradeRate(world: SessionState) {
        val gin7 = gin7EconomyService
        if (gin7 != null) {
            gin7.randomizePlanetTradeRate(world)
            return
        }
        randomizeCityTradeRateLegacy(world)
    }

    private fun randomizeCityTradeRateLegacy(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "tradeRate",
            world.currentYear, world.currentMonth
        )

        // Level-based probability
        val probByLevel = mapOf(
            4 to 0.2, 5 to 0.4, 6 to 0.6, 7 to 0.8, 8 to 1.0
        )

        for (city in cities) {
            val prob = probByLevel[city.level.toInt()] ?: 0.0
            if (prob > 0 && rng.nextDouble() < prob) {
                city.tradeRoute = rng.nextInt(95, 106)  // 95~105 inclusive
            } else {
                city.tradeRoute = 100  // 확률 미달: 기본값으로 리셋
            }
        }

        saveCities(ports, cities)
    }

    private fun saveCities(writePort: WorldWritePort, cities: List<Planet>) {
        cities.forEach { writePort.putPlanet(it.toSnapshot()) }
    }

    private fun saveNations(writePort: WorldWritePort, nations: List<Faction>) {
        nations.forEach { writePort.putFaction(it.toSnapshot()) }
    }

    private fun saveGenerals(writePort: WorldWritePort, generals: List<Officer>) {
        generals.forEach { writePort.putOfficer(it.toSnapshot()) }
    }
}
