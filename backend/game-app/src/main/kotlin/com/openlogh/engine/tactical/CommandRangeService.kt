package com.openlogh.engine.tactical

/**
 * 커맨드 레인지 서비스 (コマンドレンジサービス).
 *
 * gin7 매뉴얼 10.12 — 기함 중심 지휘 반경 관리.
 *
 * 규칙 요약:
 * - 최대 반경 = 기함 아이템(flagshipCode)에 따른 고유 반경 + command 능력치 보정
 * - 유효 반경 = 최대 반경 × (command / 100)
 * - 새 명령 발령 시 반경 0으로 리셋, 이후 매 틱(턴) expandRate씩 확대
 * - 범위 밖 유닛: 기존 명령 계속 수행, 새 명령 수신 불가
 * - 혼란(사기 붕괴) 유닛: 범위 관계없이 명령 불가
 */

// ===== 기함별 최대 반경 테이블 =====

/**
 * 기함 코드 → 기본 최대 반경 매핑.
 *
 * gin7: 기함 아이템마다 고유한 커맨드 레인지 값을 가짐.
 * None (기함 없음)의 경우 기본값 적용.
 */
object FlagshipRangeTable {

    /** 기함 코드별 기본 최대 반경 (distance units) */
    private val table: Map<String, Double> = mapOf(
        // 제국군 기함
        "braunschweig"      to 500.0,  // 브라운슈바이크급
        "konigsberg"        to 450.0,  // 쾨니히스베르크급
        "rhineland"         to 480.0,  // 라인란트급
        "siegfried"         to 520.0,  // 지크프리트급 (최정예)
        // 동맹군 기함
        "hyperion"          to 500.0,  // 히페리온
        "triglav"           to 460.0,  // 트리글라프급
        "leonidas"          to 470.0,  // 레오니다스급
        // 공통/중립
        "iserlohn_fortress" to 800.0,  // 이제르론 요새 (최대)
        "gaeisburg_fortress" to 750.0,  // 가이에스부르크 요새
        // 기본값
        "None"              to 300.0,
    )

    /** 기함 코드로 최대 반경 조회 (미등록 코드는 기본값 반환) */
    fun getMaxRadius(flagshipCode: String): Double =
        table[flagshipCode] ?: table["None"]!!
}

// ===== 커맨드 레인지 상태 =====

/**
 * 함대별 커맨드 레인지 상태.
 *
 * gin7 10.12:
 * - currentRadius: 현재 유효 반경
 * - maxRadius: 기함 + 지휘 능력치로 결정되는 최대 반경
 * - expansionRate: 매 틱 확대량
 * - resetOnOrder: true이면 다음 명령 발령 시 currentRadius = 0
 */
data class FleetCommandRange(
    val fleetId: Long,
    val officerId: Long,
    /** 현재 유효 반경 (distance units) */
    var currentRadius: Double,
    /** 최대 반경 */
    val maxRadius: Double,
    /** 매 틱(턴) 확대량 */
    val expansionRate: Double,
)

// ===== 서비스 =====

object CommandRangeService {

    /** 기본 확대 속도 (턴당 distance units) */
    private const val BASE_EXPANSION_RATE = 30.0
    /** command 능력치 1당 추가 확대 속도 */
    private const val EXPANSION_PER_COMMAND = 0.5
    /** command 능력치로 최대 반경에 적용하는 가중치 분모 */
    private const val COMMAND_SCALE_DIVISOR = 100.0

    /**
     * 장교 스탯과 기함 코드로 FleetCommandRange 생성.
     *
     * gin7 10.12:
     *   최대 반경 = flagshipMaxRadius × (command / 100)
     *   확대 속도 = BASE + command × EXPANSION_PER_COMMAND
     *
     * 새 전투 시작 시 currentRadius = 0으로 초기화.
     */
    fun createForFleet(
        fleetId: Long,
        officerId: Long,
        commandStat: Int,
        flagshipCode: String,
    ): FleetCommandRange {
        val flagshipMax = FlagshipRangeTable.getMaxRadius(flagshipCode)
        // gin7: 유효 반경 = 최대 반경 × (command / 100)
        val effectiveMax = flagshipMax * (commandStat / COMMAND_SCALE_DIVISOR).coerceIn(0.1, 1.5)
        val expansionRate = BASE_EXPANSION_RATE + commandStat * EXPANSION_PER_COMMAND

        return FleetCommandRange(
            fleetId = fleetId,
            officerId = officerId,
            currentRadius = 0.0,
            maxRadius = effectiveMax,
            expansionRate = expansionRate,
        )
    }

    /**
     * 턴 시작 시 레인지 확대.
     *
     * gin7: 명령 발령 후 반경이 0이 되고, 매 턴 expansionRate씩 maxRadius까지 확대됨.
     */
    fun expandOnTurnStart(range: FleetCommandRange) {
        range.currentRadius = (range.currentRadius + range.expansionRate).coerceAtMost(range.maxRadius)
    }

    /**
     * 명령 발령 시 레인지 리셋.
     *
     * gin7: 새 명령을 내리면 지휘 집중으로 인해 반경이 0으로 초기화됨.
     */
    fun onCommandIssued(range: FleetCommandRange) {
        range.currentRadius = 0.0
    }

    /**
     * 유닛이 커맨드 레인지 내에 있는지 확인.
     *
     * @param flagshipPos 기함 위치
     * @param unitPos 대상 유닛 위치
     * @param range 현재 커맨드 레인지 상태
     */
    fun isInRange(flagshipPos: Position, unitPos: Position, range: FleetCommandRange): Boolean {
        return flagshipPos.distanceTo(unitPos) <= range.currentRadius
    }

    /**
     * 유닛에 신규 명령을 내릴 수 있는지 판정.
     *
     * gin7 10.12:
     * - 혼란(사기 붕괴) 유닛은 범위와 무관하게 명령 불가
     * - 범위 밖 유닛은 신규 명령 불가 (기존 명령은 계속 수행)
     * - 기함 자신은 항상 명령 가능
     *
     * @param flagshipPos 기함 위치
     * @param unitPos 대상 유닛 위치
     * @param range 현재 커맨드 레인지 상태
     * @param unitMorale 유닛의 현재 사기 (≤20 = 혼란)
     * @param isFlagship 기함 자신 여부
     */
    fun canIssueNewOrder(
        flagshipPos: Position,
        unitPos: Position,
        range: FleetCommandRange,
        unitMorale: Int,
        isFlagship: Boolean = false,
    ): Boolean {
        // 기함은 항상 명령 가능
        if (isFlagship) return true
        // 혼란 상태 유닛: 명령 불가 (gin7: 사기 혼란 유닛은 커맨드 레인지 영향 없음)
        if (unitMorale <= 20) return false
        return isInRange(flagshipPos, unitPos, range)
    }

    /**
     * 세션 내 모든 함대의 커맨드 레인지 확대 처리 (턴 시작 시 일괄 호출).
     */
    fun expandAllOnTurnStart(ranges: Collection<FleetCommandRange>) {
        ranges.forEach { expandOnTurnStart(it) }
    }
}
