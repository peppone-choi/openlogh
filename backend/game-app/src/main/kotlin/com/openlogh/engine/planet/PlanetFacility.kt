package com.openlogh.engine.planet

/**
 * 행성 시설 (스팟) 시스템.
 *
 * gin7 매뉴얼: 행성 내 다양한 시설에서 캐릭터 상호작용.
 * 시설별 커맨드 접근 제한, 채팅 범위, NPC 배치 등.
 */

// ===== 시설 타입 =====

enum class FacilityType(
    val code: String,
    val displayName: String,
    val jpName: String,
    val description: String,
    /** 해당 시설에서 실행 가능한 커맨드 카테고리 */
    val availableCommands: Set<String>,
    /** 기본 수용 인원 */
    val capacity: Int = 50,
) {
    /** 거주구: 일반 생활 공간 */
    RESIDENTIAL("residential", "거주구", "居住区",
        "일반 거주 공간. 회견, 담화 등 개인 커맨드 가능",
        setOf("personal", "social"), 100),

    /** 조병공창: 함선 건조/수리 시설 */
    ARSENAL("arsenal", "조병공창", "造兵廠",
        "함선 건조 및 수리 시설. 건조/수리/보충 커맨드 가능",
        setOf("logistics", "production"), 30),

    /** 사관학교: 교육/훈련 시설 */
    ACADEMY("academy", "사관학교", "士官学校",
        "장교 교육 시설. 수강/강의/병기연습 커맨드 가능",
        setOf("education", "training"), 40),

    /** 사령부: 군사 지휘 시설 */
    HEADQUARTERS("headquarters", "사령부", "司令部",
        "군사 지휘 중심. 작전/발령/인사 커맨드 가능",
        setOf("command", "personnel", "operations"), 20),

    /** 총독부: 행정 통치 시설 */
    GOVERNMENT("government", "총독부", "総督府",
        "행정 통치 기관. 정치/외교/예산 커맨드 가능",
        setOf("politics", "diplomacy", "economy"), 20),

    /** 호텔: 숙박/휴식 시설 */
    HOTEL("hotel", "호텔", "ホテル",
        "숙박 시설. 회견/야회 등 사교 활동",
        setOf("personal", "social"), 30),

    /** 회의실: 공식 회의 장소 */
    CONFERENCE("conference", "회의실", "会議室",
        "공식 회의 장소. 회담/회견/제안 커맨드 가능",
        setOf("social", "politics", "command"), 15),

    /** 주점: 비공식 모임 장소 */
    TAVERN("tavern", "주점", "酒場",
        "비공식 모임 장소. 정보 수집, 모의 등",
        setOf("personal", "social", "espionage"), 40),

    /** 헌병대: 치안/체포 시설 */
    MILITARY_POLICE("military_police", "헌병대", "憲兵隊",
        "치안 유지 시설. 체포/수색/사열 커맨드 가능",
        setOf("espionage", "arrest"), 15),

    /** 우주항: 함선 발착 시설 */
    SPACEPORT("spaceport", "우주항", "宇宙港",
        "함선 발착 시설. 이동/출항/입항 커맨드",
        setOf("movement", "logistics"), 50),

    /** 창고: 물자 보관 */
    WAREHOUSE("warehouse", "창고", "倉庫",
        "물자 보관 시설. 반출입/할당 커맨드 가능",
        setOf("logistics"), 10),

    /** 연구소: 기술 연구 */
    RESEARCH_LAB("research_lab", "연구소", "研究所",
        "기술 연구 시설. 기술 개발 커맨드 가능",
        setOf("research"), 15),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): FacilityType? = byCode[code]
    }
}

// ===== 행성 시설 데이터 =====

/**
 * 행성에 존재하는 시설 인스턴스.
 * Planet.meta["facilities"] JSON에 저장.
 */
data class PlanetFacility(
    val type: String,
    val level: Int = 1,
    val capacity: Int = FacilityType.fromCode(type)?.capacity ?: 30,
    val operational: Boolean = true,
    /** 파괴 공작 등으로 피해 시 수리 필요 턴 */
    val repairTurnsLeft: Int = 0,
)

/**
 * 행성 유형별 기본 시설 구성.
 */
object DefaultFacilities {

    /** 수도 행성 */
    fun capital(): List<PlanetFacility> = listOf(
        PlanetFacility("residential", level = 3, capacity = 200),
        PlanetFacility("arsenal", level = 3, capacity = 50),
        PlanetFacility("academy", level = 2),
        PlanetFacility("headquarters", level = 3),
        PlanetFacility("government", level = 3),
        PlanetFacility("hotel", level = 2),
        PlanetFacility("conference", level = 2),
        PlanetFacility("tavern"),
        PlanetFacility("military_police", level = 2),
        PlanetFacility("spaceport", level = 3),
        PlanetFacility("warehouse", level = 2),
        PlanetFacility("research_lab", level = 2),
    )

    /** 일반 행성 */
    fun standard(): List<PlanetFacility> = listOf(
        PlanetFacility("residential"),
        PlanetFacility("headquarters"),
        PlanetFacility("government"),
        PlanetFacility("spaceport"),
        PlanetFacility("warehouse"),
        PlanetFacility("tavern"),
    )

    /** 군사 행성 */
    fun military(): List<PlanetFacility> = listOf(
        PlanetFacility("residential"),
        PlanetFacility("arsenal", level = 2),
        PlanetFacility("academy"),
        PlanetFacility("headquarters", level = 2),
        PlanetFacility("spaceport", level = 2),
        PlanetFacility("warehouse", level = 2),
        PlanetFacility("military_police"),
        PlanetFacility("tavern"),
    )

    /** 교역 행성 (페잔 등) */
    fun trade(): List<PlanetFacility> = listOf(
        PlanetFacility("residential", level = 2),
        PlanetFacility("hotel", level = 2),
        PlanetFacility("conference"),
        PlanetFacility("spaceport", level = 3),
        PlanetFacility("warehouse", level = 3),
        PlanetFacility("tavern", level = 2),
        PlanetFacility("research_lab"),
    )

    /** 요새 */
    fun fortress(): List<PlanetFacility> = listOf(
        PlanetFacility("residential"),
        PlanetFacility("arsenal", level = 2),
        PlanetFacility("headquarters", level = 3),
        PlanetFacility("spaceport", level = 2),
        PlanetFacility("warehouse", level = 2),
        PlanetFacility("military_police"),
    )
}

// ===== 총독 시스템 =====

/**
 * 행성 총독.
 * 총독 직무카드 보유 장교가 행성을 통치.
 * 통치 효과는 장교의 정치/운영 능력치에 비례.
 */
object GovernorSystem {

    /**
     * 총독의 월간 통치 효과 계산.
     * @param politics 총독의 정치 능력치
     * @param administration 총독의 운영 능력치
     * @return 행성 수치 변동량
     */
    fun calculateMonthlyEffect(
        politics: Int,
        administration: Int,
    ): GovernorEffect {
        return GovernorEffect(
            approvalGain = politics * 0.02f,
            securityGain = (administration * 0.15).toInt(),
            productionGain = (administration * 0.1).toInt(),
            commerceGain = (politics * 0.1).toInt(),
        )
    }

    data class GovernorEffect(
        val approvalGain: Float,
        val securityGain: Int,
        val productionGain: Int,
        val commerceGain: Int,
    )
}

// ===== 함선 건조 시스템 =====

/**
 * 조병공창 기반 함선 건조.
 * 조병공창 보유 행성에서만 건조 가능.
 */
object ShipBuildingSystem {

    /**
     * 건조 가능 여부.
     * @param facilities 행성 시설 목록
     */
    fun canBuildShips(facilities: List<PlanetFacility>): Boolean =
        facilities.any { it.type == "arsenal" && it.operational && it.repairTurnsLeft <= 0 }

    /**
     * 조병공창 레벨에 따른 턴당 최대 건조량.
     */
    fun maxBuildPerTurn(arsenalLevel: Int): Int = arsenalLevel * 2 // 레벨당 2유닛

    /**
     * 건조 비용.
     * @param shipClassCode 함종 코드
     * @param generation 서브타입 세대
     */
    fun buildCost(shipClassCode: String, generation: Int): BuildCost {
        val baseFunds = when (shipClassCode) {
            "battleship" -> 500
            "fast_battleship" -> 600
            "cruiser" -> 350
            "strike_cruiser" -> 400
            "destroyer" -> 200
            "carrier" -> 550
            "torpedo_carrier" -> 500
            "assault_ship" -> 300
            "transport" -> 150
            "engineering_ship" -> 250
            "hospital" -> 200
            else -> 300
        }
        val genMultiplier = 1.0 + (generation - 1) * 0.15
        val baseMaterials = (baseFunds * 0.6).toInt()
        return BuildCost(
            funds = (baseFunds * genMultiplier).toInt(),
            materials = (baseMaterials * genMultiplier).toInt(),
            turnsRequired = ((baseFunds / 100.0) * genMultiplier).toInt().coerceAtLeast(1),
        )
    }

    data class BuildCost(
        val funds: Int,
        val materials: Int,
        val turnsRequired: Int,
    )
}
