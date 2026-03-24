package com.openlogh.engine.espionage

import kotlin.random.Random

/**
 * 첩보 서비스 (諜報サービス).
 *
 * gin7 매뉴얼 9.7 — 15종 첩보 커맨드 실행 엔진.
 * 상위 6종 커맨드에 대한 실행 로직을 제공한다.
 */

// ===== 입력 데이터 클래스 =====

/**
 * 첩보 커맨드 실행에 필요한 장교 데이터 (전달용 DTO).
 */
data class EspionageOfficerData(
    val officerId: Long,
    val name: String,
    val intelligence: Int,    // 정보 능력치
    val rank: Int,            // 계급 (0~10)
    val intelOps: Int,        // 정보공작 능력치
    val mobility: Int,        // 기동 능력치
    val attack: Int,          // 공격 능력치
    val defense: Int,         // 방어 능력치
    val planetId: Long,       // 현재 행성 ID
    val fleetId: Long,        // 현재 함대 ID
    val factionId: Long,      // 소속 진영 ID
    val injury: Int = 0,      // 부상도 (0~100)
    /** 체포 허가 목록: officerId 집합 (Officer.meta["arrestPermits"]) */
    val arrestPermits: Set<Long> = emptySet(),
    /** 체포 집행 권한 부여된 officerId 집합 (Officer.meta["executeAuthority"]) */
    val executeAuthority: Set<Long> = emptySet(),
)

// ===== 결과 데이터 클래스 =====

/** 일제 수색 결과 */
data class MassSearchResult(
    val success: Boolean,
    /** 발견된 잠입자 officerId 목록 */
    val discovered: List<Long>,
    val log: String,
)

/** 체포 허가 결과 */
data class ArrestPermitResult(
    val success: Boolean,
    val targetId: Long,
    val log: String,
)

/** 집행 명령 결과 */
data class ExecuteOrderResult(
    val success: Boolean,
    /** 체포 집행 권한이 부여된 officerId */
    val grantedTo: Long,
    val log: String,
)

/** 체포 명령 결과 */
data class ArrestOrderResult(
    val success: Boolean,
    val targetId: Long,
    /** true이면 체포 성공, false이면 도주/실패 */
    val arrested: Boolean,
    val log: String,
)

/** 감시 결과 (매 턴 체크) */
data class SurveillanceResult(
    val active: Boolean,
    /** true이면 발각됨 */
    val detected: Boolean,
    /** 이번 턴 수집된 행동 정보 (발각 전까지 누적) */
    val intel: String?,
    val log: String,
)

/** 습격 결과 */
data class RaidResult(
    val success: Boolean,
    /** 습격 성공 여부 */
    val hit: Boolean,
    /** 대상에게 가한 부상도 (0~100) */
    val injuryDealt: Int,
    /** 역습으로 습격자가 받은 부상도 (0~100) */
    val counterInjury: Int,
    val log: String,
)

// ===== 서비스 =====

/**
 * 첩보 실행 서비스.
 *
 * 순수 함수형 — DB 의존 없음. 호출자가 결과를 보고 엔티티를 수정한다.
 */
object EspionageService {

    // ----- 1. 일제 수색 (Mass Search) -----

    /**
     * 일제 수색: 관할 행성 내 잠입 적 첩보원 탐색.
     *
     * gin7 9.7: 정보(intelligence) 능력치 + 행성 치안도로 발견 확률 결정.
     *
     * @param searcher 수색을 수행하는 장교
     * @param planetSecurity 관할 행성 치안도 (0~100)
     * @param suspects 같은 행성에 있는 적 장교 목록 (잠입 상태 포함 여부는 호출자가 필터링)
     * @param rng 난수 생성기
     */
    fun executeMassSearch(
        searcher: EspionageOfficerData,
        planetSecurity: Int,
        suspects: List<EspionageOfficerData>,
        rng: Random,
    ): MassSearchResult {
        val discovered = mutableListOf<Long>()
        for (suspect in suspects) {
            val found = EspionageEngine.attemptMassSearch(
                searcherIntelligence = searcher.intelligence,
                planetSecurity = planetSecurity,
                infiltratorIntelOps = suspect.intelOps,
                rng = rng,
            )
            if (found) discovered.add(suspect.officerId)
        }
        val log = if (discovered.isNotEmpty()) {
            // 일제 수색: ${discovered.size}명의 잠입자 발견
            "${searcher.name}의 일제 수색으로 ${discovered.size}명의 적 첩보원이 발각되었습니다."
        } else {
            "${searcher.name}의 일제 수색: 잠입자를 발견하지 못했습니다."
        }
        return MassSearchResult(
            success = true,
            discovered = discovered,
            log = log,
        )
    }

    // ----- 2. 체포 허가 (Arrest Warrant) -----

    /**
     * 체포 허가: 대상 장교를 체포 허가 목록에 등록.
     *
     * gin7 9.7: PCP 800 소비. 체포 허가 → 집행 명령 → 체포 명령의 3단계 프로세스 시작점.
     * 호출자는 issuer.pcp >= 800을 확인 후 호출해야 한다.
     *
     * @param issuer 허가를 발령하는 장교 (상급자)
     * @param targetId 체포 허가 대상 officerId
     */
    fun executeArrestPermit(
        issuer: EspionageOfficerData,
        targetId: Long,
        targetName: String,
    ): ArrestPermitResult {
        // 결과: issuer.meta["arrestPermits"]에 targetId 추가는 호출자 처리
        val log = "${issuer.name}이(가) ${targetName}에 대한 체포 허가를 발령했습니다. (집행 명령 대기)"
        return ArrestPermitResult(
            success = true,
            targetId = targetId,
            log = log,
        )
    }

    // ----- 3. 집행 명령 (Execute Order) -----

    /**
     * 집행 명령: 특정 장교에게 체포 허가된 대상에 대한 집행 권한 부여.
     *
     * gin7 9.7: PCP 800 소비. 체포 허가 목록에 있는 대상에게만 발령 가능.
     * 호출자는 targetId가 issuer.arrestPermits에 있는지 확인 후 호출해야 한다.
     *
     * @param issuer 명령 발령자 (체포 허가 발령자와 동일해야 함)
     * @param executor 실제 체포를 집행할 장교
     * @param targetId 집행 대상 officerId
     */
    fun executeExecuteOrder(
        issuer: EspionageOfficerData,
        executor: EspionageOfficerData,
        targetId: Long,
        targetName: String,
    ): ExecuteOrderResult {
        // 결과: executor.meta["executeAuthority"]에 targetId 추가는 호출자 처리
        val log = "${issuer.name}이(가) ${executor.name}에게 ${targetName} 체포 집행 권한을 부여했습니다."
        return ExecuteOrderResult(
            success = true,
            grantedTo = executor.officerId,
            log = log,
        )
    }

    // ----- 4. 체포 명령 (Arrest Command) -----

    /**
     * 체포 명령: 같은 스팟(행성/함대)에 있는 대상을 현장 체포 시도.
     *
     * gin7 9.7: PCP 160 소비.
     * 성공률 = (intelligence + rank) vs 대상의 (intelligence + rank).
     * 체포 집행 권한이 있어야 함.
     * 호출자는 sameSpot 여부와 executeAuthority 보유 여부를 확인 후 호출해야 한다.
     *
     * @param arrestor 체포를 시도하는 장교
     * @param target 체포 대상 장교
     * @param sameSpot 같은 위치(행성 또는 함대)에 있는지
     * @param rng 난수 생성기
     */
    fun executeArrestOrder(
        arrestor: EspionageOfficerData,
        target: EspionageOfficerData,
        sameSpot: Boolean,
        rng: Random,
    ): ArrestOrderResult {
        if (!sameSpot) {
            return ArrestOrderResult(
                success = false,
                targetId = target.officerId,
                arrested = false,
                log = "${arrestor.name}이(가) ${target.name} 체포 시도: 대상이 같은 위치에 없습니다.",
            )
        }

        // gin7: 체포 성공률 = (arrestor.intelligence + arrestor.rank) vs (target.intelligence + target.rank)
        val arrestorScore = arrestor.intelligence + arrestor.rank * 5
        val targetScore = target.intelligence + target.rank * 5
        val chance = 0.5 + (arrestorScore - targetScore) * 0.004
        val arrested = rng.nextDouble() < chance.coerceIn(0.10, 0.90)

        val log = if (arrested) {
            "${arrestor.name}이(가) ${target.name}을(를) 체포했습니다!"
        } else {
            "${arrestor.name}의 체포 시도: ${target.name}이(가) 도주했습니다."
        }
        return ArrestOrderResult(
            success = true,
            targetId = target.officerId,
            arrested = arrested,
            log = log,
        )
    }

    // ----- 5. 감시 (Surveillance) -----

    /**
     * 감시 시작: 대상 장교 감시 등록.
     *
     * gin7 9.7: PCP 160 소비. 발각될 때까지 매 턴 대상 행동 정보를 수집.
     * 반환값은 감시 시작 이벤트. 매 턴 체크는 checkSurveillanceTurn()을 사용.
     *
     * @param watcher 감시를 수행하는 장교
     * @param target 감시 대상 장교
     */
    fun startSurveillance(
        watcher: EspionageOfficerData,
        target: EspionageOfficerData,
    ): SurveillanceTarget {
        return SurveillanceTarget(
            targetOfficerId = target.officerId,
            watcherOfficerId = watcher.officerId,
            startTurn = 0, // 호출자가 현재 턴으로 설정
        )
    }

    /**
     * 감시 중 매 턴 발각 판정 + 정보 수집.
     *
     * gin7: 시간 경과에 따라 발각 확률 증가.
     *
     * @param surveillance 현재 감시 상태
     * @param watcher 감시자 장교
     * @param target 감시 대상 장교
     * @param currentTurn 현재 턴 번호
     * @param targetActionDescription 이번 턴 대상의 행동 설명 (수집된 정보)
     * @param rng 난수 생성기
     */
    fun checkSurveillanceTurn(
        surveillance: SurveillanceTarget,
        watcher: EspionageOfficerData,
        target: EspionageOfficerData,
        currentTurn: Int,
        targetActionDescription: String,
        rng: Random,
    ): SurveillanceResult {
        val turnsSinceStart = currentTurn - surveillance.startTurn

        // 발각 판정
        val detected = EspionageEngine.checkSurveillanceDetection(
            watcherIntelOps = watcher.intelOps,
            targetIntelligence = target.intelligence,
            turnsSinceStart = turnsSinceStart,
            rng = rng,
        )

        return if (detected) {
            SurveillanceResult(
                active = false,
                detected = true,
                intel = null,
                log = "${watcher.name}의 ${target.name} 감시가 발각되었습니다. (${turnsSinceStart}턴 지속)",
            )
        } else {
            SurveillanceResult(
                active = true,
                detected = false,
                intel = targetActionDescription,
                log = "${watcher.name}: ${target.name} 감시 중 — $targetActionDescription",
            )
        }
    }

    // ----- 6. 습격 (Raid) -----

    /**
     * 습격: 같은 스팟의 적 장교를 직접 공격.
     *
     * gin7 9.7: PCP 160 소비.
     * 피해 = 습격자 command vs 대상 defense.
     * 역습: 대상도 습격자에게 반격 가능.
     *
     * @param raider 습격을 수행하는 장교
     * @param target 습격 대상 장교
     * @param sameSpot 같은 위치에 있는지
     * @param rng 난수 생성기
     */
    fun executeRaid(
        raider: EspionageOfficerData,
        target: EspionageOfficerData,
        sameSpot: Boolean,
        rng: Random,
    ): RaidResult {
        if (!sameSpot) {
            return RaidResult(
                success = false,
                hit = false,
                injuryDealt = 0,
                counterInjury = 0,
                log = "${raider.name}의 습격 실패: 대상이 같은 위치에 없습니다.",
            )
        }

        // gin7: attack(공격) vs target.defense(방어) 기반 습격
        val (hit, injury) = EspionageEngine.attemptRaid(
            raiderAttack = raider.attack,
            raiderIntelOps = raider.intelOps,
            targetDefense = target.defense,
            rng = rng,
        )

        // 역습: 대상이 반격 (defense vs raider.defense)
        val counterChance = 0.3 + (target.defense - raider.defense) * 0.004
        val counterHit = hit && rng.nextDouble() < counterChance.coerceIn(0.05, 0.50)
        val counterInjury = if (counterHit) (10 + rng.nextInt(30)) else 0

        val log = when {
            !hit -> "${raider.name}의 ${target.name} 습격 실패."
            counterHit -> "${raider.name}이(가) ${target.name}에게 부상(${injury})을 입혔으나 역습으로 자신도 부상(${counterInjury})."
            else -> "${raider.name}이(가) ${target.name}을(를) 습격하여 부상(${injury})을 입혔습니다."
        }

        return RaidResult(
            success = true,
            hit = hit,
            injuryDealt = injury,
            counterInjury = counterInjury,
            log = log,
        )
    }
}
