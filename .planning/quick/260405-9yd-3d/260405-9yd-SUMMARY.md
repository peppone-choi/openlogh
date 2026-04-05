---
phase: quick
plan: 260405-9yd
subsystem: frontend-3d-map
tags: [3d-map, ux, player-location]
dependency_graph:
  requires: []
  provides: [3d-my-city-pulse-ring]
  affects: [CityModel]
tech_stack:
  added: []
  patterns: [pure-function-extraction, sin-pulse-animation]
key_files:
  created:
    - frontend/src/components/game/map-3d/city/CityModel.test.ts
  modified:
    - frontend/src/components/game/map-3d/city/CityModel.tsx
decisions:
  - Extract pulse logic as pure functions (shouldShowMyLocationRing, calcPulseOpacity) for testability
  - Use ringGeometry with baseR*1.15~1.3 for ring size slightly larger than city base circle
  - Sin-based opacity animation (0.3~0.9) matching 2D animate-pulse feel
metrics:
  duration: 125s
  completed: "2026-04-05"
  tasks_completed: 1
  tasks_total: 1
  tests_added: 4
  files_changed: 2
---

# Quick Task 260405-9yd: 3D Map Player Location Pulse Ring Summary

3D CityModel에 isMyCity 기반 빨간 펄스 링(ringGeometry, sin opacity 0.3~0.9) 추가

## What Was Done

### Task 1: CityModel에 내 도시 펄스 링 이펙트 추가 (TDD)

**RED:** CityModel.test.ts 생성 -- shouldShowMyLocationRing, calcPulseOpacity 순수 함수 4개 테스트 작성

**GREEN:** CityModel.tsx에 구현:
- `shouldShowMyLocationRing(isMyCity)`: 조건 분기 순수 함수
- `calcPulseOpacity(time)`: sin 기반 0.3~0.9 opacity 계산
- `MyLocationRing` 내부 컴포넌트: ringGeometry(baseR*1.15 ~ baseR*1.3), meshBasicMaterial(#ef4444, transparent, depthWrite=false, DoubleSide)
- useFrame으로 매 프레임 opacity 업데이트
- CityModel JSX에서 `city.isMyCity && <MyLocationRing>` 조건부 렌더링

**Commit:** `b309ceb`

## Verification

- 4 new tests PASSED (shouldShowMyLocationRing true/false, calcPulseOpacity range/variation)
- 10 existing CastleLoader tests PASSED (no regression)
- Total: 14/14 tests passed

## Deviations from Plan

None -- plan executed exactly as written.

## Known Stubs

None.

## Self-Check: PASSED
