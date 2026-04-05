# Phase 8: NPC AI Parity - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

NPC 장수가 레거시 GeneralAI.php와 동일한 전략적, 전술적, 외교적 의사결정을 내리는지 검증하고, 불일치 발견 시 즉시 수정. GeneralAI.kt(~50 do*() 메서드)와 NationAI.kt(nation-level 의사결정)이 이미 구현되어 있으므로 신규 작성이 아닌 **검증 및 수정**이 핵심.

Requirements: AI-01, AI-02, AI-03, AI-04

</domain>

<decisions>
## Implementation Decisions

### 검증 범위 및 분할 (Verification Scope & Batching)
- **D-01:** 기능별 3분할 — Plan 1: 군사 AI(출병/징병/전투준비/워프/집합/소집해제), Plan 2: 내정/경제 AI(내정/금쌀구매/헌납/귀환), Plan 3: 외교/인사/방랑 AI(선전포고/불가침제의/발령/포상/몰수/방랑군이동/거병/국가선택). 각 Plan이 독립적으로 검증 가능.
- **D-02:** PHP 수동 추적 — Phase 5/6/7과 동일 패턴. PHP do*() 코드를 직접 읽고 고정 입력의 기대 결정값을 수동 계산 후 golden value로 잠금.

### 외교 AI 접근법 (Diplomacy AI Approach)
- **D-03:** PHP 기준 재작성 — REQUIREMENTS에 '외교 AI가 completely different'로 명시됨. PHP do선전포고()/do불가침제의()를 정밀 읽고 Kotlin 외교 로직을 PHP와 동일하게 수정. 발견 즉시 수정 패턴(Phase 5 D-04, Phase 6 D-03 계승).

### NationAI vs GeneralAI 경계 (Architecture Boundary)
- **D-04:** PHP 메서드 단위 1:1 대조 — PHP GeneralAI.php 하나에 통합된 nation-level do*() 메서드(chooseNationTurn, 발령/포상/몰수/선전포고 등 20+개)를 Kotlin GeneralAI.kt + NationAI.kt 양쪽에서 찾아 대조. 출력(action string)이 동일하면 OK. Kotlin 파일 구조 자체는 수정하지 않음.

### 테스트 전략 (Testing Strategy)
- **D-05:** 병행 접근 — do*() 메서드별 golden value 1~2개(고정 게임 상태 → 기대 action string) + 핵심 의사결정 분기점(전쟁/평화 분기, 자원 부족 분기 등)에 대한 조건별 단위 테스트 추가.
- **D-06:** 기존 테스트 파일 확장 — GeneralAITest.kt, NationAITest.kt, NpcAiParityTest.kt에 golden value assertion 추가. 새 파일 생성보다 기존 구조 보강.

### Claude's Discretion
- Plan 3분할 내 구체적 메서드 배치 경계
- PHP 코드 읽기 순서 및 카탈로그 정리 형식
- golden value fixture의 게임 상태(seed, 장수/도시/국가 조합) 설계
- 핵심 분기점 테스트의 구체적 조건 선정
- NationAI.kt 외교 로직 수정 시 리팩터링 범위

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/sammo/GeneralAI.php` — NPC AI 전체 구현 (4293줄): ~50 do*() 메서드, chooseGeneralTurn(), chooseNationTurn(), chooseInstantNationTurn(), 의사결정 트리
- `legacy-core/hwe/sammo/AutorunGeneralPolicy.php` — NPC 정책 클래스: 우선순위, 활성 액션, 자원 임계값

### Existing Kotlin AI Implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` — NPC 장수 AI (3743줄): decideAndExecute(), ~50 do*() 메서드
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NationAI.kt` — NPC 국가 AI (443줄): decideNationAction(), 발령/포상/증축/선전포고
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NpcPolicy.kt` — NPC 정책 (367줄): NpcGeneralPolicy, NpcNationPolicy, NpcPolicyBuilder
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/AIContext.kt` — AI 컨텍스트 데이터 클래스

### Existing Tests
- `backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt` — GeneralAI 단위 테스트
- `backend/game-app/src/test/kotlin/com/opensam/engine/ai/NationAITest.kt` — NationAI 단위 테스트
- `backend/game-app/src/test/kotlin/com/opensam/engine/ai/NpcPolicyTest.kt` — NpcPolicy 단위 테스트
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/NpcAiParityTest.kt` — NPC AI parity 테스트

### Prior Phase Context
- `.planning/phases/07-command-parity/07-CONTEXT.md` — golden value 패턴(D-02), 정확 문자열 매칭(D-05), PHP 수동 추적(D-02)
- `.planning/phases/06-economy-parity/06-CONTEXT.md` — 발견 즉시 수정(D-03), 병행 접근(D-01)
- `.planning/phases/05-modifier-pipeline/05-CONTEXT.md` — PHP 전수 읽기 방식(D-01), 발견 즉시 수정(D-04)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GeneralAI.kt`: 50+ do*() 메서드 이미 구현됨 — PHP 4293줄 대비 3743줄로 대부분 포팅 완료
- `NationAI.kt`: nation-level 의사결정(발령/포상/증축/선전포고/불가침/천도) 분리 구현
- `NpcPolicy.kt`: NpcGeneralPolicy(우선순위 13개 액션) + NpcNationPolicy(우선순위 21개 액션) + NpcPolicyBuilder
- `AIContext`: 의사결정에 필요한 전체 게임 상태를 담는 데이터 클래스
- `DeterministicRng`: GeneralAI에서 사용하는 결정론적 RNG (Phase 1에서 확립)
- 기존 테스트 3개 파일: GeneralAITest, NationAITest, NpcAiParityTest

### Established Patterns
- Golden value 테스트: 고정 입력 → PHP 수동 계산 기대값 하드코딩 비교 (Phase 4/5/6/7 확립)
- DeterministicRng.create(hiddenSeed, "GeneralAI", year, month, generalId): AI 결정의 재현성 보장
- decideAndExecute() 진입점: npcState=5 → 집합, reserved command check → wanderer routing → nation action
- NationAI.decideNationAction(): policy 기반 우선순위 매핑, meta에 aiWarTarget/aiAssignmentTarget 저장

### Integration Points
- `TurnService` → `GeneralAI.decideAndExecute()` 호출 (턴 처리 중 NPC 행동 결정)
- `GeneralAI` → `CommandExecutor` (결정된 action string을 커맨드로 실행)
- `NationAI.decideNationAction()` → nation.meta에 AI 결정 데이터 저장 → 커맨드가 읽어서 실행
- `NpcPolicyBuilder` → nation.meta에서 정책 로드 (legacy KVStorage 대응)

</code_context>

<specifics>
## Specific Ideas

- PHP GeneralAI.php의 chooseGeneralTurn()이 최상위 진입점: npcType=5(집합) → reserved command → nationId=0(방랑) → chief(국가 행동) → general(장수 행동) 순서로 분기
- PHP chooseNationTurn()이 nation-level 진입점: policy 우선순위대로 do*() 메서드 순회하며 첫 번째 성공 반환
- Kotlin NationAI.kt의 외교 로직(선전포고/불가침제의)이 PHP와 "completely different" — 재작성 필요
- PHP의 categorizeNationCities()와 categorizeNationGeneral()이 AI 결정의 핵심 전처리 — Kotlin 대응 로직 정확성 검증 필요
- PHP do출병()의 전쟁 목표 선정 로직(calcWarRoute 기반)이 복잡 — golden value로 정밀 검증 필요

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-npc-ai-parity*
*Context gathered: 2026-04-02*
