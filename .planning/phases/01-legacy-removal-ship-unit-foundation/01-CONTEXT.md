# Phase 1: 레거시 제거 + 함종 유닛 기반 - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

삼국지 게임 로직(93개 커맨드, 병종 상성, 수치비교 전투, 농업/상업 경제, 무기/서적/말 아이템, NPC 데이터)을 완전히 제거하고, gin7 기반 ShipUnit 엔티티(11함종 × I~VIII 서브타입), 기함 유닛, 육전대 유닛, 승조원 수련도 시스템을 수립한다. 기존 엔티티(Officer, Fleet, Planet 등)와의 연결을 확보하고, 삼국지 전용 필드는 Flyway V45+ 마이그레이션으로 제거한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Key considerations:
- 삼국지 커맨드는 gin7 stub으로 대체 (빈 구현체, Phase 2에서 실제 구현)
- ShipUnit 엔티티는 ship_stats_empire.json / ship_stats_alliance.json 데이터 기반
- officerLevel >= 5 권한 우회 7+ 위치 모두 stub 대체 필요
- BattleEngine.kt (삼국지) 삭제, TacticalBattleEngine.kt 유지 및 확장 준비
- DB 마이그레이션은 V45__ 이후 번호 사용

</decisions>

<code_context>
## Existing Code Insights

### Key Files to Modify/Remove
- `CommandRegistry.kt` — 93개 삼국지 커맨드 등록 → gin7 81종 stub 대체
- `BattleService.kt` / `BattleEngine.kt` — 삼국지 수치비교 전투 → 제거
- `EconomyService.kt` — 삼국지 농업/상업 → 제거 (Phase 4에서 재구현)
- `general_pool.json` — 삼국지 NPC 데이터 → 제거

### Key Files to Create
- `ShipUnit.kt` — 11함종 × 서브타입 엔티티
- `FlagshipUnit.kt` — 기함 유닛 엔티티
- `GroundUnit.kt` — 육전대 유닛 엔티티
- `ShipStatsLoader.kt` — ship_stats JSON 로더
- V45+ Flyway 마이그레이션 SQL

### Existing Assets to Preserve
- `ShipClassType.kt`, `ShipSubtype.kt` — 이미 존재하는 enum
- `CrewProficiency.kt` — 4단계 수련도 enum
- `TacticalBattleEngine.kt` — 기존 전술전 기초 (확장 대상)
- `commands.json` — gin7 81종 커맨드 정의 (Phase 2 데이터 소스)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. Refer to ROADMAP phase description and success criteria.

</specifics>

<deferred>
## Deferred Ideas

None — infrastructure phase.

</deferred>
