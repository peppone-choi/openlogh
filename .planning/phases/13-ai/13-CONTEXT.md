# Phase 13: 전략 AI - Context

**Gathered:** 2026-04-09
**Status:** Ready for planning

<domain>
## Phase Boundary

AI 진영이 전쟁 상태에서 자동으로 작전계획(OperationPlan)을 수립하고, 전력 평가에 따라 적절한 작전 유형(점령/방어/소탕)을 선택한다. FactionAI의 기존 atWar 분기를 삼국지 잔재에서 OperationPlan 기반 전략 AI로 완전 교체한다.

**범위:**
- 성계별 복합 전력 평가 시스템 (함선 수 + 사령관 능력 + 요새 방어력)
- 안개 제한 적용 (첩보원 존재 여부에 따른 정보 정확도)
- 작전 대상 성계 선정 (전선 + 전략적 가치 복합 평가)
- FactionAI.decideNationAction() atWar 분기 완전 교체
- CommandExecutor를 통한 OperationPlanCommand 실행
- 전력 기반 최적 함대 배정
- 성격(PersonalityTrait) 기반 작전 유형 경향

**범위 제외:**
- 프론트엔드 UI (Phase 14)
- 새 작전 유형 추가 (CONQUEST/DEFENSE/SWEEP만)
- 전술 AI 변경 (Phase 11 완료)
- OperationPlan 엔티티/서비스 변경 (Phase 12 완료)

</domain>

<decisions>
## Implementation Decisions

### 전력 평가 모델 (SAI-02)
- **D-01:** 성계별 복합 스코어링 — 함선 수(ships 합산), 사령관 능력(command/leadership), 궤도방어/요새 방어력을 다차원 점수화. 구체적 가중치는 Claude 재량.
- **D-02:** 안개 제한 적용 — 해당 성계에 첩보원(intelligence 높은 장교)이 실제 체류 중이어야 적 전력을 정확하게 평가. 첩보원이 없으면 노이즈가 추가된 추정치 사용.
- **D-03:** 적 정보 접근은 전술전 안개(Phase 14 FE-05)와 별개 — 전략 AI 안개는 성계 단위, 전술전 안개는 유닛 단위.

### 작전 대상 선정 (SAI-01)
- **D-04:** 복합 기준(전선 + 전략적 가치) — CONQUEST: 적 전선 성계 중 전력 약하고 전략적 가치(자원량/연결성) 높은 곳. DEFENSE: 아군 전선 성계 중 위협받는 곳. SWEEP: 아군 영역 내 적 함대 존재 성계.
- **D-05:** 동시 작전 수 무제한 — Phase 12 D-02와 일관. AI도 필요한 만큼 작전 수립.

### FactionAI 통합 방식
- **D-06:** CommandExecutor를 통해 OperationPlanCommand 실행 — 기존 커맨드 파이프라인(CP 소모, 권한 검증, 로그 발행) 일관 유지.
- **D-07:** AI 발령자는 진영 원수/의장(sovereign) — commander 그룹 권한을 자동 보유하므로 별도 권한 우회 불필요.
- **D-08:** atWar 분기의 삼국지 잔재(급습/의병모집/필사즉생, strategicCmdLimit) 완전 제거 → 작전계획 수립 로직으로 교체.

### 부대 배정 전략
- **D-09:** 전력 기반 최적 배정 — 목표 성계의 적 전력보다 충분한 전력이 되도록 최소 함대 조합 계산. 나머지는 방어 예비.
- **D-10:** 성격(PersonalityTrait) 기반 작전 유형 경향 — AGGRESSIVE 진영 원수는 CONQUEST 우선, DEFENSIVE는 DEFENSE 우선, CAUTIOUS는 보수적 투입 등. Phase 11의 전술 AI 성격 시스템과 일관.

### Claude's Discretion
- 복합 스코어링의 구체적 가중치 및 공식
- 첩보원 판정 기준 (intelligence 임계값)
- 노이즈 추정치의 오차 범위
- 전략적 가치 평가 세부 요소 (자원량/항로 수/인구 등)
- 작전 수립 주기 (매 tick? 특정 인터벌?)
- 최소 함대 조합 알고리즘 (greedy? knapsack?)
- 기존 FactionAI의 비전쟁 로직(발령/증축/포상/불가침) 유지 범위

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 기존 AI 시스템 (확장 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt` — decideNationAction() atWar 분기 교체 대상. pickWarTarget(), categorizeAssignmentNeeds() 등 기존 패턴 참고
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAIScheduler.kt` — 슬롯 기반 라운드 로빈 (1 tick = 1 faction). 전략 AI도 동일 스케줄러 사용
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAIPort.kt` — 인터페이스 (테스트 대체용)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/PersonalityTrait.kt` — 5종 성격 + 스탯 가중치
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/UtilityScorer.kt` — 유틸리티 스코어링 패턴 (전략 AI도 동일 패턴 권장)

### Phase 12 산출물 (호출 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` — 작전계획 커맨드 (arg: objective, targetStarSystemId, participantFleetIds, scale)
- `backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt` — assignOperation() @Transactional, D-04 원자성
- `backend/game-app/src/main/kotlin/com/openlogh/entity/OperationPlan.kt` — 엔티티 구조
- `backend/game-app/src/main/kotlin/com/openlogh/repository/OperationPlanRepository.kt` — 리포지토리
- `backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt` — 작전 생명주기
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt` — CONQUEST/DEFENSE/SWEEP enum

### 커맨드 실행 파이프라인
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` — AI가 커맨드 실행하는 진입점
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt` — 커맨드에 주입되는 서비스 번들

### 엔티티/데이터
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt` — ships, planetId 등 함대 정보
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt` — frontState, orbitalDefense, fortress 등 성계 정보
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` — intelligence 스탯 (첩보원 판정용)
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt` — sovereign, warState 등 진영 정보

### 프로젝트 가이드
- `CLAUDE.md` — 도메인 매핑, 함종, 계급, 조직 구조
- `.planning/REQUIREMENTS.md` — SAI-01, SAI-02 요구사항

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **FactionAI.decideNationAction()**: atWar 분기를 교체하되, 비전쟁 로직(발령/증축/포상/불가침/천도)은 유지
- **FactionAI.pickWarTarget()**: 인접 진영 탐색 패턴 — 성계 단위로 확장 가능
- **FactionAI.categorizeAssignmentNeeds()**: 전선/후방 분류 패턴 — 전력 평가에 재활용
- **UtilityScorer 패턴**: stat × weight 스코어링 — StrategicPowerScorer로 동일 패턴 적용
- **OperationPlanService.assignOperation()**: 이미 완비된 작전 생성 API (D-04 원자성 보장)
- **NpcPolicyBuilder**: 진영 정책 빌드 패턴

### Established Patterns
- FactionAI는 @Service (Spring DI) — DB 접근 가능
- worldPortFactory.create(worldId)로 포트 생성 → 엔티티 조회
- decideNationAction()은 커맨드 이름 String 반환 → CommandExecutor에서 실행
- FactionAIScheduler: 1 tick = 1 faction 라운드 로빈
- nation.meta에 AI 결정 데이터 저장 (aiWarTarget, aiAssignmentTarget 등)

### Integration Points
- FactionAI.decideNationAction() atWar 분기 → 전략 AI 작전 수립 로직으로 교체
- CommandExecutor를 통한 OperationPlanCommand 실행 (sovereign 장교 기반)
- OperationPlanRepository: 기존 PENDING/ACTIVE 작전 조회 (중복 배정 방지)
- FactionAIScheduler: 기존 스케줄러 그대로 사용 (변경 없음)

</code_context>

<specifics>
## Specific Ideas

- 첩보원 기반 안개 제한: 성계에 intelligence 높은 아군 장교가 체류해야 정확한 적 전력 정보. 없으면 랜덤 노이즈 추가 → AI가 잘못된 판단할 수 있는 게임적 재미
- 성격 기반 작전 경향: 라인하르트형(AGGRESSIVE) AI는 공격 작전 우선, 양웬리형(DEFENSIVE) AI는 방어 우선 — 원작 분위기 재현
- 전력 기반 최적 배정: 적보다 약간 우세한 전력만 투입하고 나머지는 방어 예비로 유지 — 과도한 전력 집중 방지
- Phase 12 specifics 인용: "FactionAI에서 호출 가능한 형태로 의식적 노출" → OperationPlanService.assignOperation()이 정확히 이 역할

</specifics>

<deferred>
## Deferred Ideas

- **연쇄 작전(CASCADE)**: Phase 12에서도 deferred — 작전 완료 시 다음 작전 자동 발령
- **작전 우선순위**: 다중 작전 시 자원/CP 우선순위
- **전략 AI 학습**: ML 기반 전술 판단 (Out of Scope — REQUIREMENTS.md)
- **외교 AI 고도화**: 동맹/불가침 전략적 제안 (현재는 단순 확률 기반)

None — discussion stayed within phase scope

</deferred>

---

*Phase: 13-ai*
*Context gathered: 2026-04-09*
