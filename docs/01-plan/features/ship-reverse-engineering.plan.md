# Plan: gin7 함선 모델 역공학 파이프라인 완성

> Feature: ship-reverse-engineering
> Created: 2026-04-01
> Phase: Plan
> PRD: `docs/00-pm/ship-reverse-engineering.prd.md`

## Executive Summary

| 관점 | 내용 |
|------|------|
| Problem | s84 stride 파트 84% 커버리지, heuristic gap strip 미적용, 어항 테스트 부재 |
| Solution | 바이너리 포렌식으로 s84 누락 면 위치 규명 → 파서 개선 → 전체 검증 |
| Function UX Effect | 66개 전함 95%+ 밀폐 메시 OBJ 출력 |
| Core Value | 원작 에셋 재활용으로 3D 모델링 비용 제거 |

## Context Anchor

| 항목 | 내용 |
|------|------|
| WHY | 전투 UI에 전함 3D 모델이 필수이며, 밀폐 메시여야 조명/그림자 정상 작동 |
| WHO | Open LOGH 개발팀 (프론트엔드 + 게임 디자인) |
| RISK | s84 16%가 바이너리에 없을 수 있음; PV 오인 regression |
| SUCCESS | SC-1~5: 66개 95%+, s84 해결, gap strip 확대, 어항 테스트, regression 없음 |
| SCOPE | mdx_parse_mds.py 수정 + 검증 스크립트 추가 |

## 1. 요구사항

### R-1: s84 prim=1 누락 면 해결

**현상**: Brunhild s84 — strip IB로 84% 커버, 16% 면 위치 불명
**현재 코드**: `extract_descriptor_group()` L410-411에서 s84는 `decode_strip_faces()` 호출
Phase 4 gap strip에서 `stride == 84: continue` (L800-801)로 s84 skip

**조사 항목**:
1. s84 디스크립터의 `ic` 값 vs 실제 strip IB 길이 비교
2. strip IB 뒤 ~ PV 시작 사이에 추가 데이터 존재 여부
3. s84에도 list_IB + strip_IB 이중 구조가 있는지 확인
4. `ic`보다 더 많은 인덱스를 strip으로 읽으면 커버리지가 올라가는지

### R-2: heuristic 파트 gap strip 적용

**현상**: Phase 3(W=1.0 heuristic)에서 발견된 VB에 gap strip 미적용
**현재 코드**: Phase 4 (L792-818)에서 `all_parts` 전체를 순회하지만, heuristic 파트의 `ib_end`가 실제 list IB 끝인지 확인 필요
**해결**: heuristic 파트 중 stride != 84인 것에 gap strip 적용

### R-3: 어항 테스트 (Watertight Mesh Validation)

**정의**: 모든 edge가 정확히 2개의 face에 공유되면 manifold (밀폐)
**구현**: OBJ 출력 후 edge-manifold 검사 스크립트
**기준**: non-manifold edge 비율 < 5%이면 PASS

### R-4: Regression 방지

**기준**: 기존 66/66 변환 성공 유지, s72 커버리지 97%+ 유지
**방법**: 변경 전 baseline 수치 기록 → 변경 후 비교

## 2. 구현 전략

### Phase A: 바이너리 포렌식 (s84 조사)

1. Brunhild s84 영역을 hex dump로 정밀 분석
2. 디스크립터 `ic` vs 실제 valid index 범위 비교
3. strip IB 뒤 ~ PV 앞 사이 데이터 구조 해석
4. 가설 검증: ic 확장, list+strip 이중 구조, OOB restart 패턴

### Phase B: 파서 개선

1. s84 조사 결과에 따라 `extract_descriptor_group()` 수정
2. Phase 4 gap strip에서 s84 처리 로직 추가 (PV 오인 방지 포함)
3. heuristic 파트 gap strip 적용 확인

### Phase C: 검증

1. 어항 테스트 스크립트 작성
2. 66개 전함 일괄 재실행
3. baseline 대비 regression 확인
4. 주요 함선 (Brunhild, battleship, berlin) 상세 비교

## 3. 성공 기준

| ID | 기준 | 측정 | 목표 |
|----|------|------|------|
| SC-1 | 66개 전함 face 커버리지 | `실제 faces / (2V-4)` | >= 95% 평균 |
| SC-2 | s84 커버리지 | Brunhild s84 기준 | 84% → 95%+ |
| SC-3 | heuristic gap strip | 적용 전/후 면 수 | 증가 확인 |
| SC-4 | 어항 테스트 | non-manifold edge 비율 | < 5% |
| SC-5 | regression | 변환 성공률 | 66/66 유지 |

## 4. 리스크 및 대응

| 리스크 | 확률 | 영향 | 대응 |
|--------|------|------|------|
| s84 16%가 바이너리에 없음 | 중 | SC-2 미달성 | 84%를 수용 기준으로 하향 조정 |
| PV 오인 regression | 낮 | s84 spaghetti | `_pick_best_ic` 로직 강화 |
| gap strip 오연결 | 낮 | 메시 품질 저하 | clean_faces threshold 조정 |
| strip restart 마커 패턴 변형 | 중 | 일부 함선 실패 | 함선별 적응형 마커 감지 |

## 5. 일정 (세션 단위)

| 세션 | 내용 | 산출물 |
|------|------|--------|
| 1 | 바이너리 포렌식 — s84 hex 분석 | 분석 결과 문서 |
| 2 | 파서 개선 — s84 + gap strip | 수정된 mdx_parse_mds.py |
| 3 | 검증 — 어항 테스트 + 66개 일괄 | 검증 결과 보고서 |
