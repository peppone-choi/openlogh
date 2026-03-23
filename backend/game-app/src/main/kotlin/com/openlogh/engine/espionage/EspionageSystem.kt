package com.openlogh.engine.espionage

import kotlin.random.Random

/**
 * 첩보 코맨드군 (諜報コマンド).
 *
 * gin7 매뉴얼 기반 11종 첩보 커맨드.
 * 정보공작(intelOps) 능력치를 소비하여 실행.
 */

// ===== 첩보 명령 타입 =====

enum class EspionageOrderType(
    val code: String,
    val displayName: String,
    val jpName: String,
    val intelOpsCost: Int,
    val mcpCost: Int,
    val description: String,
) {
    /** 일제 수색: 관할 구역 내 적 첩보원 탐색 */
    MASS_SEARCH("mass_search", "일제 수색", "一斉捜索", 500, 200,
        "관할 구역 내 적 첩보원을 탐색. 정보 능력치가 높을수록 발견 확률 증가"),

    /** 체포 허가: 체포 대상 지정 (4단계 프로세스 시작) */
    ARREST_PERMIT("arrest_permit", "체포 허가", "逮捕許可", 200, 100,
        "특정 인물의 체포를 허가. 집행 명령 → 체포 명령 순서 필요"),

    /** 집행 명령: 체포 허가 후 실제 집행 지시 */
    EXECUTE_ORDER("execute_order", "집행 명령", "執行命令", 100, 50,
        "체포 허가된 인물에 대한 실제 집행 지시"),

    /** 체포 명령: 현장 체포 실행 */
    ARREST_ORDER("arrest_order", "체포 명령", "逮捕命令", 300, 100,
        "대상 인물을 현장에서 체포 시도. 같은 스팟에 있어야 함"),

    /** 사열: 쿠데타/반란 징후 탐지 */
    INSPECTION("inspection", "사열", "査閲", 400, 150,
        "부대 사열로 쿠데타/반란 징후를 탐지. 반의 상태 장교 발견"),

    /** 습격: 적 인물 직접 습격 (암살/납치) */
    RAID("raid", "습격", "襲撃", 1000, 300,
        "적 인물을 직접 습격. 부상/전사 가능. 같은 스팟 필요"),

    /** 감시: 특정 인물 지속 감시 (발각까지) */
    SURVEILLANCE("surveillance", "감시", "監視", 300, 100,
        "특정 인물의 행동을 지속 감시. 발각되기 전까지 정보 수집"),

    /** 잠입 공작: 적 행성 침입 시도 */
    INFILTRATE("infiltrate", "잠입 공작", "侵入工作", 800, 200,
        "적 행성에 첩보원을 침입시킴. 정보 능력치 vs 적 치안"),

    /** 탈출 공작: 침입 스팟에서 탈출 */
    EXFILTRATE("exfiltrate", "탈출 공작", "脱出工作", 200, 100,
        "침입한 행성에서 안전하게 탈출 시도"),

    /** 정보 공작: 시설 정보 획득/송신 */
    INTEL_GATHERING("intel_gathering", "정보 공작", "情報工作", 500, 150,
        "적 시설 정보를 수집하여 아군에 송신. 군사/경제/기술 정보"),

    /** 파괴 공작: 적 시설/함선 파괴 */
    SABOTAGE("sabotage", "파괴 공작", "破壊工作", 1500, 400,
        "적 시설 또는 정박 중인 함선에 시한폭탄 설치. 잠입 상태 필요"),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): EspionageOrderType? = byCode[code]
    }
}

// ===== 첩보 상태 =====

/** 첩보원(장교)의 잠입 상태 */
enum class InfiltrationState(val code: String, val displayName: String) {
    NONE("none", "일반"),
    INFILTRATING("infiltrating", "잠입 중"),
    DETECTED("detected", "발각됨"),
    CAPTURED("captured", "포로"),
    ESCAPED("escaped", "탈출 완료"),
}

/** 감시 대상 정보 */
data class SurveillanceTarget(
    val targetOfficerId: Long,
    val watcherOfficerId: Long,
    val startTurn: Int,
    var detected: Boolean = false,
)

// ===== 첩보 엔진 =====

object EspionageEngine {

    /**
     * 잠입 공작 성공 판정.
     * @param intelOps 잠입자의 정보공작 능력치
     * @param targetSecurity 대상 행성 치안도
     * @param targetIntelligence 방어측 정보 능력치 (최고)
     */
    fun attemptInfiltration(
        intelOps: Int,
        targetSecurity: Int,
        targetIntelligence: Int,
        rng: Random,
    ): Boolean {
        val successBase = 0.3 + (intelOps - targetSecurity) * 0.001 - targetIntelligence * 0.002
        val chance = successBase.coerceIn(0.05, 0.85)
        return rng.nextDouble() < chance
    }

    /**
     * 파괴 공작 피해 산정.
     * @param intelOps 공작원의 정보공작 능력치
     * @return 파괴 피해율 (0.0 ~ 1.0)
     */
    fun calculateSabotageDamage(intelOps: Int, rng: Random): Double {
        val base = 0.1 + intelOps * 0.0001
        val variance = 0.8 + rng.nextDouble() * 0.4
        return (base * variance).coerceIn(0.05, 0.5)
    }

    /**
     * 일제 수색 발견 판정.
     * @param searcherIntelligence 수색자 정보 능력치
     * @param infiltratorIntelOps 잠입자 정보공작 능력치
     */
    fun attemptMassSearch(
        searcherIntelligence: Int,
        planetSecurity: Int,
        infiltratorIntelOps: Int,
        rng: Random,
    ): Boolean {
        val chance = 0.2 + (searcherIntelligence + planetSecurity * 0.5 - infiltratorIntelOps) * 0.002
        return rng.nextDouble() < chance.coerceIn(0.05, 0.80)
    }

    /**
     * 체포 시도 성공 판정.
     */
    fun attemptArrest(
        arrestorIntelligence: Int,
        targetMobility: Int,
        sameSpot: Boolean,
        rng: Random,
    ): Boolean {
        if (!sameSpot) return false
        val chance = 0.5 + (arrestorIntelligence - targetMobility) * 0.005
        return rng.nextDouble() < chance.coerceIn(0.10, 0.90)
    }

    /**
     * 습격 결과 판정.
     * @return Pair(습격 성공 여부, 대상 부상도 0~100)
     */
    fun attemptRaid(
        raiderAttack: Int,
        raiderIntelOps: Int,
        targetDefense: Int,
        rng: Random,
    ): Pair<Boolean, Int> {
        val successChance = 0.3 + (raiderAttack + raiderIntelOps * 0.5 - targetDefense) * 0.003
        val success = rng.nextDouble() < successChance.coerceIn(0.05, 0.70)
        val injury = if (success) {
            (20 + rng.nextInt(60)).coerceAtMost(100)
        } else 0
        return Pair(success, injury)
    }

    /**
     * 사열 (쿠데타 징후 탐지).
     * @param rebellionIntent 대상의 반의 수준 (Officer.meta["rebellionIntent"])
     * @param inspectorIntelligence 사열자 정보 능력치
     */
    fun attemptInspection(
        rebellionIntent: Int,
        inspectorIntelligence: Int,
        rng: Random,
    ): Boolean {
        if (rebellionIntent <= 0) return false
        val chance = 0.2 + rebellionIntent * 0.05 + inspectorIntelligence * 0.003
        return rng.nextDouble() < chance.coerceIn(0.05, 0.95)
    }

    /**
     * 탈출 공작 성공 판정.
     */
    fun attemptExfiltration(
        escapeeIntelOps: Int,
        escapeMobility: Int,
        planetSecurity: Int,
        rng: Random,
    ): Boolean {
        val chance = 0.4 + (escapeeIntelOps + escapeMobility - planetSecurity) * 0.002
        return rng.nextDouble() < chance.coerceIn(0.10, 0.90)
    }

    /**
     * 정보 공작 수집량 산정.
     * @return 수집 정보 카테고리와 품질 (0~100)
     */
    fun gatherIntel(
        intelOps: Int,
        intelligenceStat: Int,
        rng: Random,
    ): Map<String, Int> {
        val quality = (intelOps * 0.01 + intelligenceStat * 0.5 + rng.nextInt(20)).toInt().coerceIn(0, 100)
        val categories = listOf("military", "economy", "tech", "diplomacy")
        val gathered = mutableMapOf<String, Int>()
        for (cat in categories) {
            if (rng.nextDouble() < 0.4 + intelligenceStat * 0.005) {
                gathered[cat] = quality + rng.nextInt(-10, 10)
            }
        }
        return gathered
    }

    /**
     * 감시 중 발각 판정 (매 턴).
     */
    fun checkSurveillanceDetection(
        watcherIntelOps: Int,
        targetIntelligence: Int,
        turnsSinceStart: Int,
        rng: Random,
    ): Boolean {
        // 시간이 지날수록 발각 확률 증가
        val chance = 0.05 + turnsSinceStart * 0.02 + (targetIntelligence - watcherIntelOps * 0.5) * 0.002
        return rng.nextDouble() < chance.coerceIn(0.02, 0.50)
    }

    /**
     * 선동 공작 (전략 레벨).
     * @return 지지율 감소량
     */
    fun calculatePropagandaEffect(
        intelOps: Int,
        politicsStat: Int,
        targetApproval: Float,
        rng: Random,
    ): Float {
        val base = (intelOps + politicsStat) * 0.01f
        val variance = 0.5f + rng.nextFloat()
        return (base * variance).coerceIn(1f, 15f)
    }
}
