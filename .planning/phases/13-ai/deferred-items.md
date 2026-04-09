# Phase 13 Deferred Items

Items discovered during Phase 13 execution that are out of scope for this phase's tasks.

## Pre-existing Test Failures (Out of Scope)

### NpcPolicyTest: `default priority lists match expected order`

- **Test file:** `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/NpcPolicyTest.kt:40`
- **Failure:** `assertEquals("전시전략", nationPolicy.priority.last())` — test expects the last element of `NpcNationPolicy.DEFAULT_NATION_PRIORITY` to be `"전시전략"`.
- **Actual:** `DEFAULT_NATION_PRIORITY` last element is `"NPC몰수"` (see `NpcPolicy.kt:179`). The string `"전시전략"` does not appear anywhere in `NpcPolicy.kt` or elsewhere in `src/main` except for one unrelated mention in `OfficerAI.kt`.
- **Origin:** Not caused by Phase 13. `NpcPolicy.kt` was last touched by commit `613125d4 fix(phase-01): remove remaining 삼국지 CrewType references in NpcPolicy`. That phase-01 cleanup removed legacy 삼국지 priority entries without updating this test.
- **Scope:** This is a Phase 1 leftover, not related to the strategic AI implementation of Phase 13-ai. The Plan 13-02 success criteria focus on `NationAITest` (which is fully green: 13/13) and `com.openlogh.engine.ai.strategic.*` tests (21/21 green from Plan 13-01).
- **Fix (when addressed):** Either update the test assertion to match the current `DEFAULT_NATION_PRIORITY.last() == "NPC몰수"`, or restore `"전시전략"` to the list if the priority slot was intentional. Decision requires product input on whether `"전시전략"` should be a distinct nation priority.
