package com.openlogh.service

import com.openlogh.dto.BuildPoolGeneralRequest
import com.openlogh.dto.CreateGeneralRequest
import com.openlogh.engine.DeterministicRng
import com.openlogh.engine.modifier.TraitSpecRegistry
import com.openlogh.entity.Officer
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import kotlin.random.Random

@Service
class OfficerService(
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val gameConstService: GameConstService,
) {
    companion object {
        private const val JOIN_STAT_TOTAL = 350
        private const val STAT_MIN = 10
        private const val STAT_MAX = 100

        private val LEGACY_PERSONALITY_CODES = listOf(
            "che_안전", "che_유지", "che_재간", "che_출세", "che_할거",
            "che_정복", "che_패권", "che_의협", "che_대의", "che_왕좌",
        )

        private val LEGACY_FIRST_NAMES = listOf(
            "가", "간", "감", "강", "고", "공", "공손", "곽", "관", "괴", "교", "금", "노", "뇌", "능", "도", "동", "두",
            "등", "마", "맹", "문", "미", "반", "방", "부", "비", "사", "사마", "서", "설", "성", "소", "손", "송", "순",
            "신", "심", "악", "안", "양", "엄", "여", "염", "오", "왕", "요", "우", "원", "위", "유", "육", "윤", "이",
            "장", "저", "전", "정", "제갈", "조", "종", "주", "진", "채", "태사", "하", "하후", "학", "한", "향", "허",
            "호", "화", "황", "공손", "손", "왕", "유", "장", "조",
        )
        private val LEGACY_LAST_NAMES = listOf(
            "가", "간", "강", "거", "건", "검", "견", "경", "공", "광", "권", "규", "녕", "단", "대", "도", "등", "람",
            "량", "례", "로", "료", "모", "민", "박", "범", "보", "비", "사", "상", "색", "서", "소", "속", "송", "수",
            "순", "습", "승", "양", "연", "영", "온", "옹", "완", "우", "웅", "월", "위", "유", "윤", "융", "이", "익",
            "임", "정", "제", "조", "주", "준", "지", "찬", "책", "충", "탁", "택", "통", "패", "평", "포", "합", "해",
            "혁", "현", "화", "환", "회", "횡", "후", "훈", "휴", "흠", "흥",
        )
    }

    private val availableSpecialCodes: Set<String> = TraitSpecRegistry.war.map { it.key }.toSet()

    fun listBySession(sessionId: Long): List<Officer> {
        return officerRepository.findBySessionId(worldId)
    }

    fun getById(id: Long): Officer? {
        return officerRepository.findById(id).orElse(null)
    }

    @Transactional
    fun getMyOfficer(worldId: Long, loginId: String): Officer? {
        val userId = getCurrentUserId(loginId) ?: return null
        val general = officerRepository.findBySessionIdAndUserId(worldId, userId)
            .firstOrNull { it.npcState < 5 } ?: return null
        if (general.nationId > 0L && general.officerLevel < 1) {
            general.officerLevel = 1
            general.officerCity = 0
            general.permission = "normal"
            if (general.makeLimit > 0) {
                general.makeLimit = 0
            }
            return officerRepository.save(general)
        }
        return general
    }

    fun listByNation(nationId: Long): List<Officer> {
        return officerRepository.findByFactionId(nationId)
    }

    fun listByPlanet(cityId: Long): List<Officer> {
        return officerRepository.findByPlanetId(cityId)
    }

    @Transactional
    fun createOfficer(worldId: Long, loginId: String, request: CreateGeneralRequest): Officer? {
        val user = findUserByLoginId(loginId)
        val userId = user.id ?: throw IllegalArgumentException("계정 정보를 확인할 수 없습니다. 다시 로그인해주세요.")
        val world = sessionStateRepository.findById(worldId.toShort()).orElseThrow {
            IllegalArgumentException("월드를 찾을 수 없습니다.")
        }

        ensureCreateAllowed(world, worldId, userId)
        validateFiveStats(request.leadership, request.strength, request.intel, request.politics, request.charm)

        val prePurchasedSpecial = user.meta["inheritSpecificSpecialWar"] as? String
        val prePurchasedCity = (user.meta["inheritCity"] as? Number)?.toLong()
        val inheritSpecial = request.inheritSpecial?.takeIf { it.isNotBlank() } ?: prePurchasedSpecial
        val inheritCity = request.inheritCity ?: prePurchasedCity

        if (inheritSpecial != null && inheritSpecial !in availableSpecialCodes) {
            throw IllegalArgumentException("전투 특기가 잘못 지정되었습니다.")
        }

        val inheritBonusStat = normalizeInheritBonusStat(request.inheritBonusStat)
        val nameBlocked = isCustomNameBlocked(world)
        val nationId = request.nationId ?: 0L
        val nation = if (nationId > 0L) {
            factionRepository.findById(nationId).orElseThrow { IllegalArgumentException("국가를 찾을 수 없습니다.") }
                .also {
                    if (it.worldId != worldId) {
                        throw IllegalArgumentException("다른 월드의 국가입니다.")
                    }
                }
        } else {
            null
        }

        val finalCityId = inheritCity ?: request.cityId ?: pickRandomStartPlanet(worldId, world, nationId)
        val city = planetRepository.findById(finalCityId).orElseThrow {
            IllegalArgumentException("도시를 찾을 수 없습니다.")
        }.also {
            if (it.worldId != worldId) {
                throw IllegalArgumentException("다른 월드의 도시입니다.")
            }
            if (nation != null && it.nationId != 0L && it.nationId != nation.id) {
                throw IllegalArgumentException("선택한 도시는 해당 국가 소속이 아닙니다.")
            }
        }

        val allGenerals = officerRepository.findBySessionId(worldId)
        val rng = createJoinRng(world, userId)
        val name = if (nameBlocked) {
            "__pending__${userId}_${System.currentTimeMillis()}"
        } else {
            normalizeName(request.name)
        }

        if (!nameBlocked && officerRepository.findByNameAndSessionId(name, worldId) != null) {
            throw IllegalArgumentException("이미 있는 장수입니다. 다른 이름으로 등록해 주세요!")
        }

        val pointsToSpend = calculateJoinInheritCost(
            request = request,
            inheritSpecial = inheritSpecial,
            inheritCity = inheritCity,
            inheritBonusStat = inheritBonusStat,
            prePurchasedSpecial = prePurchasedSpecial,
            prePurchasedCity = prePurchasedCity,
        )
        applyJoinInheritCost(user, pointsToSpend)

        val bonusStat = inheritBonusStat ?: randomBornBonus(rng, request.leadership.toInt(), request.strength.toInt(), request.intel.toInt(), request.politics.toInt(), request.charm.toInt())
        val relYear = (world.currentYear.toInt() - getStartYear(world)).coerceAtLeast(0)
        val age = (20 + bonusStat.sum() * 2 - rng.nextInt(0, 2)).toShort()
        val specAge = calcSpecAge(age.toInt(), relYear)
        val spec2Age = if (inheritSpecial != null) age else calcWarSpecAge(age.toInt(), relYear)
        val experience = nation?.let {
            val initialNationGenLimit = (world.config["initialNationGenLimit"] as? Number)?.toInt()
                ?: gameConstService.getInt("initialNationGenLimit")
            if (it.gennum < initialNationGenLimit) 700 else 100
        } ?: calcStartingExperience(allGenerals, relYear)
        val useOwnIcon = request.useOwnIcon ?: request.pic ?: false
        val (picture, imageServer) = resolvePicture(user, useOwnIcon)

        val saved = officerRepository.save(
            Officer(
                worldId = worldId,
                userId = userId,
                name = name,
                cityId = finalCityId,
                nationId = nationId,
                affinity = rng.nextInt(1, 151).toShort(),
                picture = picture,
                imageServer = imageServer,
                leadership = (request.leadership + bonusStat[0]).toShort(),
                strength = (request.strength + bonusStat[1]).toShort(),
                intel = (request.intel + bonusStat[2]).toShort(),
                politics = (request.politics + bonusStat[3]).toShort(),
                charm = (request.charm + bonusStat[4]).toShort(),
                experience = experience,
                officerLevel = if (nationId > 0L) 1 else 0,
                officerCity = 0,
                permission = "normal",
                gold = gameConstService.getInt("defaultGold"),
                rice = gameConstService.getInt("defaultRice"),
                crewType = request.crewType,
                ownerName = user.displayName,
                turnTime = createInitialTurnTime(world, rng, request.inheritTurntimeZone),
                killTurn = resolveKillTurn(world).toShort(),
                age = age,
                startAge = age,
                betray = if (relYear >= 4) 2 else 0,
                personalCode = normalizePersonalityCode(request.personality, rng),
                specialCode = "None",
                specAge = specAge,
                special2Code = inheritSpecial ?: "None",
                spec2Age = spec2Age,
            ),
        )

        if (nameBlocked) {
            saved.name = generateObfuscatedName(saved.id, world)
            officerRepository.save(saved)
        }

        officerTurnRepository.saveAll(
            (0 until gameConstService.getInt("maxTurn")).map { turnIdx ->
                OfficerTurn(
                    worldId = worldId,
                    generalId = saved.id,
                    turnIdx = turnIdx.toShort(),
                    actionCode = "휴식",
                    brief = "휴식",
                )
            },
        )

        if (nation != null) {
            nation.gennum += 1
            factionRepository.save(nation)
        }

        if (inheritSpecial != null && inheritSpecial == prePurchasedSpecial) {
            user.meta.remove("inheritSpecificSpecialWar")
        }
        if (inheritCity != null && inheritCity == prePurchasedCity) {
            user.meta.remove("inheritCity")
        }
        if (pointsToSpend > 0 || inheritSpecial == prePurchasedSpecial || inheritCity == prePurchasedCity) {
            appUserRepository.save(user)
        }

        return saved
    }

    fun listAvailableNpcs(worldId: Long): List<Officer> {
        return officerRepository.findBySessionId(worldId)
            .filter { it.npcState.toInt() == 1 && it.userId == null }
    }

    @Transactional
    fun possessNpc(worldId: Long, loginId: String, generalId: Long): Officer? {
        val userId = getCurrentUserId(loginId) ?: return null
        if (hasActiveOfficer(worldId, userId)) return null
        val general = officerRepository.findById(generalId).orElse(null) ?: return null
        if (general.worldId != worldId || general.npcState.toInt() == 0 || general.userId != null) return null
        general.userId = userId
        general.npcState = 0
        return officerRepository.save(general)
    }

    fun listPool(worldId: Long): List<Officer> {
        return officerRepository.findBySessionId(worldId)
            .filter { it.npcState.toInt() == 5 && it.userId == null }
    }

    @Transactional
    fun selectFromPool(worldId: Long, loginId: String, generalId: Long): Officer? {
        val userId = getCurrentUserId(loginId) ?: return null
        if (hasActiveOfficer(worldId, userId)) return null
        val general = officerRepository.findById(generalId).orElse(null) ?: return null
        if (general.worldId != worldId || general.npcState.toInt() != 5 || general.userId != null) return null
        
        if (general.officerLevel.toInt() == 20) {
            throw IllegalStateException("황제는 플레이할 수 없습니다.")
        }
        
        general.userId = userId
        general.npcState = 0
        return officerRepository.save(general)
    }

    @Transactional
    fun buildPoolOfficer(worldId: Long, loginId: String, request: BuildPoolGeneralRequest): Officer? {
        val user = findUserByLoginId(loginId)
        val userId = user.id ?: throw IllegalArgumentException("계정 정보를 확인할 수 없습니다. 다시 로그인해주세요.")
        validateFiveStats(request.leadership, request.strength, request.intel, request.politics, request.charm)
        if (hasActiveOfficer(worldId, userId)) return null

        val rng = createJoinRng(
            world = sessionStateRepository.findById(worldId.toShort()).orElseGet { SessionState(id = worldId.toShort()) },
            userId = userId,
        )
        val (picture, imageServer) = resolvePicture(user, true)
        val general = Officer(
            worldId = worldId,
            userId = userId,
            name = normalizeName(request.name),
            leadership = request.leadership,
            strength = request.strength,
            intel = request.intel,
            politics = request.politics,
            charm = request.charm,
            npcState = 5,
            turnTime = OffsetDateTime.now(),
            picture = picture,
            imageServer = imageServer,
            ownerName = user.displayName,
            personalCode = normalizePersonalityCode(request.personality ?: request.ego, rng),
        )
        return officerRepository.save(general)
    }

    @Transactional
    fun updatePoolOfficer(
        worldId: Long,
        loginId: String,
        generalId: Long,
        leadership: Short,
        strength: Short,
        intel: Short,
        politics: Short,
        charm: Short,
    ): Officer? {
        validateFiveStats(leadership, strength, intel, politics, charm)
        val userId = getCurrentUserId(loginId) ?: return null
        val general = officerRepository.findById(generalId).orElse(null) ?: return null
        if (general.worldId != worldId || general.userId != userId || general.npcState.toInt() != 5) return null
        general.leadership = leadership
        general.strength = strength
        general.intel = intel
        general.politics = politics
        general.charm = charm
        return officerRepository.save(general)
    }

    fun getMyActiveOfficer(loginId: String): Officer? {
        val userId = getCurrentUserId(loginId) ?: return null
        return officerRepository.findByUserId(userId).firstOrNull { it.npcState.toInt() == 0 }
    }

    fun getCurrentUserId(loginId: String): Long? {
        return appUserRepository.findByLoginId(loginId)?.id
            ?: appUserRepository.findByLoginIdIgnoreCase(loginId.trim())?.id
    }

    private fun findUserByLoginId(loginId: String): com.openlogh.entity.AppUser {
        val normalized = loginId.trim()
        return appUserRepository.findByLoginId(normalized)
            ?: appUserRepository.findByLoginIdIgnoreCase(normalized)
            ?: throw IllegalArgumentException("계정 정보를 찾을 수 없습니다. 다시 로그인해주세요.")
    }

    private fun ensureCreateAllowed(world: SessionState, worldId: Long, userId: Long) {
        val blockBits = readInt(world.meta["blockGeneralCreate"])
            ?: readInt(world.config["blockGeneralCreate"])
            ?: 0
        if ((blockBits and 1) != 0) {
            throw IllegalArgumentException("장수 직접 생성이 불가능한 모드입니다.")
        }

        if (hasActiveOfficer(worldId, userId)) {
            throw IllegalArgumentException("이미 등록하셨습니다!")
        }

        val maxGeneral = readInt(world.config["maxGeneral"])
            ?: readInt(world.config["generalCntLimit"])
            ?: readInt(world.meta["generalCntLimit"])
            ?: gameConstService.getInt("defaultMaxGeneral")
        val currentPlayers = officerRepository.findBySessionId(worldId)
            .count { it.userId != null && it.npcState.toInt() != 5 }
        if (currentPlayers >= maxGeneral) {
            throw IllegalArgumentException("더이상 등록할 수 없습니다!")
        }
    }

    private fun isCustomNameBlocked(world: SessionState): Boolean {
        return readBoolean(world.meta["blockCustomGeneralName"])
            ?: readBoolean(world.config["blockCustomGeneralName"])
            ?: false
    }

    private fun hasActiveOfficer(worldId: Long, userId: Long): Boolean {
        return officerRepository.findBySessionIdAndUserId(worldId, userId).any { it.npcState.toInt() < 5 }
    }

    private fun validateFiveStats(
        leadership: Short,
        strength: Short,
        intel: Short,
        politics: Short,
        charm: Short,
    ) {
        val values = listOf(leadership, strength, intel, politics, charm).map { it.toInt() }
        if (values.any { it < STAT_MIN || it > STAT_MAX }) {
            throw IllegalArgumentException("능력치는 ${STAT_MIN}~${STAT_MAX} 사이여야 합니다.")
        }
        if (values.sum() != JOIN_STAT_TOTAL) {
            throw IllegalArgumentException("능력치 합계가 ${JOIN_STAT_TOTAL}이어야 합니다.")
        }
    }

    private fun normalizeInheritBonusStat(raw: List<Int>?): List<Int>? {
        if (raw == null) return null
        if (raw.size != 5) {
            throw IllegalArgumentException("보너스 능력치는 5개 항목이어야 합니다.")
        }
        if (raw.any { it < 0 }) {
            throw IllegalArgumentException("보너스 능력치는 음수일 수 없습니다.")
        }
        val sum = raw.sum()
        return when {
            sum == 0 -> null
            sum in 3..5 -> raw
            else -> throw IllegalArgumentException("보너스 능력치는 합 0 또는 3~5 사이여야 합니다.")
        }
    }

    private fun normalizeName(rawName: String?): String {
        val trimmed = rawName?.trim().orEmpty()
            .replace(Regex("[\\p{Cntrl}]"), "")
            .replace(Regex("[<>]"), "")
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("이름을 입력해주세요.")
        }
        if (trimmed.length > 18) {
            throw IllegalArgumentException("이름이 유효하지 않습니다. 다시 가입해주세요!")
        }
        return trimmed
    }

    private fun normalizePersonalityCode(raw: String?, rng: Random): String {
        if (raw.isNullOrBlank() || raw == "Random") {
            return LEGACY_PERSONALITY_CODES.random(rng)
        }
        if (raw in LEGACY_PERSONALITY_CODES) {
            return raw
        }
        throw IllegalArgumentException("성격이 잘못 지정되었습니다.")
    }

    private fun randomBornBonus(rng: Random, leadership: Int, strength: Int, intel: Int, politics: Int, charm: Int): List<Int> {
        val totalBonus = rng.nextInt(3, 6)
        var addLeadership = 0
        var addStrength = 0
        var addIntel = 0
        var addPolitics = 0
        var addCharm = 0
        val weights = listOf(leadership, strength, intel, politics, charm)
        repeat(totalBonus) {
            when (choiceUsingWeight(rng, weights)) {
                0 -> addLeadership += 1
                1 -> addStrength += 1
                2 -> addIntel += 1
                3 -> addPolitics += 1
                else -> addCharm += 1
            }
        }
        return listOf(addLeadership, addStrength, addIntel, addPolitics, addCharm)
    }

    private fun choiceUsingWeight(rng: Random, weights: List<Int>): Int {
        val total = weights.sum().coerceAtLeast(1)
        var pick = rng.nextInt(total)
        weights.forEachIndexed { idx, weight ->
            pick -= weight
            if (pick < 0) return idx
        }
        return weights.lastIndex
    }

    private fun calcSpecAge(age: Int, relYear: Int): Short {
        val retirementYear = gameConstService.getInt("retirementYear")
        val wait = maxOf(((retirementYear - age) / 12.0 - relYear / 2.0).roundToInt(), 3)
        return (wait + age).toShort()
    }

    private fun calcWarSpecAge(age: Int, relYear: Int): Short {
        val retirementYear = gameConstService.getInt("retirementYear")
        val wait = maxOf(((retirementYear - age) / 6.0 - relYear / 2.0).roundToInt(), 3)
        return (wait + age).toShort()
    }

    private fun calcStartingExperience(allGenerals: List<Officer>, relYear: Int): Int {
        if (relYear < 3) return 0
        val experienced = allGenerals
            .filter { it.nationId != 0L && it.npcState.toInt() < 4 }
            .map { it.experience }
            .sorted()
        if (experienced.isEmpty()) return 0
        val index = (experienced.size * 0.2).roundToInt().coerceAtLeast(1) - 1
        return (experienced[index] * 0.8).roundToInt()
    }

    private fun resolvePicture(user: com.openlogh.entity.AppUser, useOwnIcon: Boolean): Pair<String, Short> {
        val picture = user.meta["picture"] as? String
        val imageServer = (user.meta["imageServer"] as? Number)?.toShort() ?: 0
        if (useOwnIcon && user.grade >= 1 && !picture.isNullOrBlank()) {
            return picture to imageServer
        }
        return "default.jpg" to 0
    }

    private fun createJoinRng(world: SessionState, userId: Long): Random {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        return DeterministicRng.create(hiddenSeed, "MakeGeneral", userId, OffsetDateTime.now().toEpochSecond())
    }

    private fun getStartYear(world: SessionState): Int {
        return (world.config["startYear"] as? Number)?.toInt() ?: gameConstService.getInt("defaultStartYear")
    }

    // Parity: getRandTurn($rng, $env['turnterm'], new DateTimeImmutable($env['turntime']))
    private fun createInitialTurnTime(world: SessionState, rng: Random, inheritTurntimeZone: Int?): OffsetDateTime {
        val tickSeconds = world.tickSeconds.toLong().coerceAtLeast(1L)
        val baseDateTime = world.updatedAt

        val randSeconds = if (inheritTurntimeZone != null) {
            inheritTurntimeZone.toLong().coerceIn(0, tickSeconds - 1)
        } else {
            rng.nextLong(tickSeconds)
        }

        var candidate = baseDateTime.plusSeconds(randSeconds)

        // Parity: Join.php:374-376
        val now = OffsetDateTime.now()
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusSeconds(tickSeconds)
        }

        return candidate
    }

    private fun calculateJoinInheritCost(
        request: CreateGeneralRequest,
        inheritSpecial: String?,
        inheritCity: Long?,
        inheritBonusStat: List<Int>?,
        prePurchasedSpecial: String?,
        prePurchasedCity: Long?,
    ): Int {
        var required = 0
        if (inheritSpecial != null && inheritSpecial != prePurchasedSpecial) {
            required += gameConstService.getInt("inheritBornSpecialPoint")
        }
        if (inheritCity != null && inheritCity != prePurchasedCity) {
            required += gameConstService.getInt("inheritBornCityPoint")
        }
        if (inheritBonusStat != null) {
            required += gameConstService.getInt("inheritBornStatPoint")
        }
        if (request.inheritTurntimeZone != null) {
            required += gameConstService.getInt("inheritBornTurntimePoint")
        }
        return required
    }

    private fun applyJoinInheritCost(user: com.openlogh.entity.AppUser, pointsToSpend: Int) {
        if (pointsToSpend <= 0) return
        val currentPoints = (user.meta["inheritPoints"] as? Number)?.toInt() ?: 0
        if (currentPoints < pointsToSpend) {
            throw IllegalArgumentException("유산 포인트가 부족합니다. 다시 가입해주세요!")
        }
        user.meta["inheritPoints"] = currentPoints - pointsToSpend
    }

    private fun pickRandomStartPlanet(worldId: Long, world: SessionState, nationId: Long): Long {
        val allCities = planetRepository.findBySessionId(worldId)
        
        val cities = if (nationId > 0L) {
            allCities.filter { it.nationId == nationId }
                .ifEmpty {
                    allCities.filter { it.level.toInt() in 5..6 && it.nationId == 0L }
                }
        } else {
            allCities.filter { it.level.toInt() in 5..6 && it.nationId == 0L }
                .ifEmpty {
                    allCities.filter { it.level.toInt() in 5..6 }
                }
        }
        
        if (cities.isEmpty()) {
            throw IllegalArgumentException("시작 가능한 도시가 없습니다.")
        }
        val rng = DeterministicRng.create(
            (world.config["hiddenSeed"] as? String) ?: world.id.toString(),
            "StartCity",
            worldId,
            OffsetDateTime.now().toEpochSecond(),
        )
        return cities.random(rng).id
    }

    private fun generateObfuscatedName(id: Long, world: SessionState): String {
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        val pool = LEGACY_FIRST_NAMES.flatMap { first ->
            LEGACY_LAST_NAMES.map { last -> "$first$last" }
        }.shuffled(DeterministicRng.create(hiddenSeed, "obfuscatedNamePool"))
        val safePool = if (pool.isEmpty()) listOf("장수$id") else pool
        val zeroBased = (id - 1).coerceAtLeast(0)
        val idx = (zeroBased % safePool.size).toInt()
        val dupIdx = zeroBased / safePool.size
        return if (dupIdx == 0L) safePool[idx] else "${safePool[idx]}$dupIdx"
    }

    private fun readInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    /**
     * Legacy parity: killturn = 4800 / turnterm.
     * npcmode==1 (빙의 모드)이면 1/3.
     */
    private fun resolveKillTurn(world: SessionState): Int {
        return (world.config["killturn"] as? Number)?.toInt()
            ?: (world.config["killTurn"] as? Number)?.toInt()
            ?: calcDefaultKillTurn(world)
    }

    private fun calcDefaultKillTurn(world: SessionState): Int {
        val turnterm = (world.tickSeconds / 60).coerceAtLeast(1)
        val base = 4800 / turnterm
        val npcmode = (world.config["npcmode"] as? Number)?.toInt() ?: 0
        return if (npcmode == 1) base / 3 else base
    }

    private fun readBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when {
            value.equals("true", ignoreCase = true) -> true
            value == "1" -> true
            value.equals("false", ignoreCase = true) -> false
            value == "0" -> false
            else -> null
        }
        else -> null
    }
}
