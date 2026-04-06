# Phase 4: 경제 시스템 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

gin7 행성 경제 시스템을 구현한다. 행성 자원(인구/생산/교역/치안/지지도/궤도방어/요새방어), 조병창 자동생산(행성별 품목, 지배권 이전까지 계속), 세율/납입률(90일 주기 징수), 행성/부대 창고 시스템, 페잔 차관(이자 10%/분기, 미상환 시 페잔 엔딩), 함대 출격 비용을 구현한다. TickEngine.runMonthlyPipeline()의 기존 TODO를 연결하여 경제 루프를 완성한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — infrastructure phase.

Key references:
- gin7 매뉴얼 Chapter 3: 병참 (pages 40-44)
- `docs/REWRITE_PROMPT.md` — 경제 시스템 상세 스펙
- `docs/reference/gin4ex_wiki.md` — 자금/경제 시스템
- TickEngine.runMonthlyPipeline() — TODO at line 126-136 (Phase 1 research 확인)
- ShipyardProductionService.kt — 기존 조병창 자동생산 서비스 (확장)
- EconomyService.kt — Phase 1에서 삼국지 로직 제거 후 stub 상태

핵심 경제 규칙:
- 세금: 90일(전략 30턴)마다, 1/1, 4/1, 7/1, 10/1
- 세율: 기본 30%, 낮으면 지지율↑, 높으면 지지율↓
- 행성당 경제력 상한: 50,000
- 차관: 페잔에서 일시불, 이자 10%/분기
- 함대 출격 비용: 출격 중 함대 수에 비례
- 운영력 높은 장교 = 비용 절감
- 조병창/체재기지/방위기지 유지비

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EconomyService.kt` — stub processMonthly() (Phase 1에서 삼국지 로직 제거)
- `ShipyardProductionService.kt` — 기존 조병창 자동생산 로직
- `TickEngine.kt` — runMonthlyPipeline() 연결 지점
- `Planet.kt` — 인구/생산/교역/치안/지지도 필드 이미 존재
- `FezzanLoan.kt` — 페잔 차관 엔티티 이미 존재
- `Faction.kt` — funds/supplies 필드 존재

### Integration Points
- TickEngine.runMonthlyPipeline() → Gin7EconomyService 연결
- 정치커맨드: 납입률변경/관세율변경/분배 (Phase 2에서 구현)
- 행성/부대 창고: PlanetWarehouse/UnitWarehouse

</code_context>

<specifics>
## Specific Ideas

No specific requirements — discuss phase skipped.

</specifics>

<deferred>
## Deferred Ideas

None.

</deferred>
