# Design: gin7 함선 모델 역공학 파이프라인 완성

> Feature: ship-reverse-engineering
> Created: 2026-04-01
> Phase: Design
> Plan: `docs/01-plan/features/ship-reverse-engineering.plan.md`

## Context Anchor

| 항목 | 내용 |
|------|------|
| WHY | 전투 UI에 전함 3D 모델 필수, 밀폐 메시여야 조명/그림자 정상 작동 |
| WHO | Open LOGH 개발팀 |
| RISK | s84 16%가 바이너리에 없을 수 있음; PV 오인 regression |
| SUCCESS | 66개 95%+, s84 해결, gap strip 확대, 어항 테스트, regression 없음 |
| SCOPE | mdx_parse_mds.py 수정 + mdx_watertight_test.py 신규 |

## 1. Overview

gin7 MDX 파서(`mdx_parse_mds.py`)의 s84 stride 커버리지 문제를 해결하고,
heuristic 파트에 gap strip을 확대 적용하며, 어항 테스트로 메시 품질을 검증한다.

## 2. 아키텍처 옵션

### Option A — Minimal: s84 ic 확장만

**접근**: 디스크립터 `ic` 값을 넘어서 strip IB를 계속 읽되, PV 진입 시점을 감지하여 중단.

**변경 범위**:
- `extract_descriptor_group()` (L364-457): s84 strip IB 읽기 범위 확장
- Phase 4 gap strip (L800): s84 skip 제거, PV 경계 감지 추가

**장점**: 최소 변경, 기존 구조 유지
**단점**: PV 경계 감지 heuristic이 불안정할 수 있음
**리스크**: PV 오인 → spaghetti

```
변경 파일: 1개
변경 라인: ~40줄
복잡도: 낮
```

### Option B — Clean: s84 전용 디코더 분리

**접근**: s84 전용 `decode_s84_mesh()` 함수를 만들어 list+strip 이중 구조, PV 감지, gap strip을 통합 처리.

**변경 범위**:
- 새 함수 `decode_s84_mesh(data, desc, vb_off)` 추가
- `extract_descriptor_group()`에서 s84일 때 전용 함수 호출
- Phase 4 gap strip에서 s84 제외 유지 (전용 함수에서 처리)

**장점**: s84 로직 완전 분리, 테스트/디버깅 용이
**단점**: 코드 중복 (strip decode, clean_faces 호출 등)
**리스크**: 중복 코드 유지보수 부담

```
변경 파일: 1개
변경 라인: ~120줄
복잡도: 중
```

### Option C — Pragmatic Balance (권장)

**접근**: 기존 `extract_descriptor_group()` 내에서 s84 처리를 확장하되,
PV 경계 감지를 별도 헬퍼(`find_pv_boundary`)로 분리. gap strip도 s84에 조건부 적용.

**변경 범위**:
- 새 헬퍼 `find_pv_boundary(data, ib_end, vc, stride)` 추가
- `extract_descriptor_group()` L408-413: s84 strip 뒤 gap strip 시도
- Phase 4 (L800): s84 skip 조건을 PV 경계 기반으로 변경
- 어항 테스트: `mdx_watertight_test.py` 신규

**장점**: 적절한 분리, 기존 흐름 유지, PV 감지 재사용 가능
**단점**: Option A보다는 복잡
**리스크**: 중간 수준 — PV 감지 헬퍼의 정확도에 의존

```
변경 파일: 2개 (mdx_parse_mds.py 수정 + mdx_watertight_test.py 신규)
변경 라인: ~80줄 수정 + ~60줄 신규
복잡도: 중
```

## 3. 옵션 비교

| 기준 | A: Minimal | B: Clean | C: Pragmatic (권장) |
|------|-----------|----------|-------------------|
| 변경 규모 | ~40줄 | ~120줄 | ~140줄 |
| 복잡도 | 낮 | 중 | 중 |
| s84 해결 확실성 | 중 | 높 | 높 |
| 유지보수성 | 중 | 높 | 높 |
| Regression 리스크 | 높 | 낮 | 낮 |
| PV 오인 방지 | 약 | 강 | 강 |

**권장: Option C** — PV 경계 감지를 별도 함수로 분리하면서 기존 흐름을 크게 바꾸지 않는 균형 잡힌 접근.

## 4. 상세 설계 (Option C 기준)

### 4.1 `find_pv_boundary(data, strip_ib_start, vc, stride)` 신규

```python
def find_pv_boundary(data, strip_ib_start, vc, stride):
    """s84 strip IB 뒤에서 PV 시작 지점을 찾는다.
    
    PV 특성:
    - vc개의 u16 값
    - 값 분포가 IB와 다름 (순차적이거나 0 근처)
    - strip IB는 OOB restart 마커(>= vc)를 포함하지만 PV는 안 함
    
    Returns: PV 시작 offset (strip IB 사용 가능 범위의 끝)
    """
```

**PV 감지 전략**:
1. strip IB 영역에서 sliding window(64 idx)로 OOB 마커 빈도 측정
2. OOB 마커가 갑자기 사라지는 지점 = PV 시작
3. 추가 검증: PV 영역의 값이 순차적 패턴인지 확인

### 4.2 `extract_descriptor_group()` s84 확장

현재 (L408-413):
```python
if stride == 84:
    faces = decode_strip_faces(data, ib_off, ic, vc)
else:
    faces = decode_list_faces(data, ib_off, ic, vc)
```

변경:
```python
if stride == 84:
    faces = decode_strip_faces(data, ib_off, ic, vc)
    # s84: ic 이후에도 strip IB가 계속될 수 있음
    # PV 경계까지 추가 strip 디코딩
    pv_start = find_pv_boundary(data, ib_off + ic * 2, vc, stride)
    if pv_start and pv_start > ib_off + ic * 2:
        extra = decode_gap_strip(data, ib_off + ic * 2, pv_start, vc)
        faces.extend(extra)
else:
    faces = decode_list_faces(data, ib_off, ic, vc)
```

### 4.3 Phase 4 gap strip — s84 조건 변경

현재 (L800-801):
```python
if part['stride'] == 84:
    continue  # s84 = strip; gap after it is PV, not extra IB
```

변경:
```python
if part['stride'] == 84:
    continue  # s84 gap strip은 4.2에서 PV 경계 기반으로 처리됨
```

> Phase 4에서 s84를 skip하는 것은 유지하되, 이유를 명확히 한다.
> s84의 추가 strip은 4.2에서 PV 경계 감지와 함께 처리되므로 Phase 4에서 중복 처리 불필요.

### 4.4 heuristic 파트 gap strip 확인

현재 코드 분석 결과, Phase 4 (L792-818)는 `all_parts` 전체를 순회하며 heuristic 파트도 포함한다.
heuristic 파트는 `ib_end`를 설정하고 있으므로 (L719) gap strip이 이미 적용될 수 있다.

**확인 필요**: heuristic s72 파트에서 실제로 gap strip이 동작하는지 로그로 검증.
동작하지 않는 경우: `all_vb_starts`에 heuristic VB가 포함되지 않아 `next_vb` 계산이 잘못될 수 있음.

### 4.5 `mdx_watertight_test.py` 신규

```python
"""어항 테스트 — OBJ 메시의 edge manifold 검사.

Usage:
  python mdx_watertight_test.py <obj_dir>

Metrics:
  - total edges
  - manifold edges (shared by exactly 2 faces)
  - boundary edges (shared by 1 face)
  - non-manifold edges (shared by 3+ faces)
  - manifold ratio = manifold / total
"""
```

**판정 기준**:
- manifold ratio >= 95%: PASS
- 90-95%: WARN
- < 90%: FAIL

## 5. 데이터 흐름

```
MDX binary
  ↓
Phase 1: find_all_descriptors() → group_descriptors()
  ↓
extract_descriptor_group()
  ├─ s24/s36/s72: list IB decode
  ├─ s84: strip IB decode + [NEW] PV 경계까지 추가 strip
  └─ dedup (list vs strip)
  ↓
Phase 2: inline descriptors (s24/s36)
  ↓
Phase 3: W=1.0 heuristic (uncovered VBs)
  ↓
Phase 4: gap strip (s72/s36/s24 list 파트)
  ↓
write_obj() → OBJ file
  ↓
[NEW] mdx_watertight_test.py → manifold ratio
```

## 6. 파일 변경 목록

| 파일 | 변경 | 내용 |
|------|------|------|
| `backend/scripts/mdx_parse_mds.py` | 수정 | find_pv_boundary 추가, s84 확장, 로깅 강화 |
| `backend/scripts/mdx_watertight_test.py` | 신규 | OBJ edge manifold 검증 스크립트 |

## 7. 테스트 계획

| ID | 테스트 | 입력 | 기대 결과 |
|----|--------|------|-----------|
| T-1 | Brunhild s84 커버리지 | brunhild_h.mdx | s84 faces >= 95% of 2V-4 |
| T-2 | battleship s84 | battleship_h.mdx | s84 faces >= 95% of 2V-4 |
| T-3 | 66개 일괄 regression | warshipmodel_mdx/ 전체 | 66/66 성공, 총 faces >= baseline |
| T-4 | 어항 테스트 Brunhild | brunhild high.obj | manifold ratio >= 90% |
| T-5 | PV 오인 방지 | s84 파트 | spaghetti face 0개 |
| T-6 | heuristic gap strip | gap strip 전/후 비교 | 면 수 증가 또는 동일 |

## 8. 구현 순서

### Module 1: 바이너리 포렌식 (세션 1)

1. Brunhild s84 영역 hex dump 분석
2. ic vs 실제 strip 길이 비교
3. PV 경계 패턴 확인
4. 결론 도출: 추가 strip IB 존재 여부

### Module 2: 파서 개선 (세션 2)

1. `find_pv_boundary()` 구현
2. `extract_descriptor_group()` s84 확장
3. heuristic gap strip 동작 확인/수정
4. 단일 파일 테스트 (Brunhild)

### Module 3: 검증 (세션 3)

1. `mdx_watertight_test.py` 작성
2. 66개 전함 일괄 재실행
3. baseline 대비 비교
4. 어항 테스트 실행

## 9. Session Guide

| 세션 | 모듈 | 스코프 | 예상 작업 |
|------|------|--------|-----------|
| 1 | module-1 | 바이너리 포렌식 | hex 분석, 가설 검증 |
| 2 | module-2 | 파서 개선 | 코드 수정, 단일 테스트 |
| 3 | module-3 | 검증 | 어항 테스트, 일괄 실행 |
