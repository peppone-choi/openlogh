# PRD: gin7 함선 모델 역공학 파이프라인 완성

> Feature: ship-reverse-engineering
> Created: 2026-04-01
> Phase: PM Analysis

## Executive Summary

| 관점 | 내용 |
|------|------|
| Problem | gin7 전함 MDX 66개 중 s84 stride 파트가 84% 면 커버리지에 멈춰 있고, heuristic 파트에 gap strip이 미적용되어 완전한 3D 메시 추출이 불가능 |
| Solution | s84 누락 16% 면의 바이너리 위치를 규명하고, gap strip을 전 파트에 확대 적용하여 95%+ 밀폐 메시 달성 |
| Function UX Effect | 66개 전함 모두 Three.js/R3F에서 렌더링 가능한 수준의 OBJ 출력 |
| Core Value | 원작 gin7의 전함 에셋을 웹 게임에 재활용하여 개발 비용 절감 |

## 1. 배경 및 동기

Open LOGH는 gin7(은하영웅전설 VII, 2004 BOTHTEC)을 웹 기반으로 재구현하는 프로젝트다.
전투 시스템에서 전함 3D 모델이 필수적이며, 원작 에셋을 역공학하여 추출하는 것이 가장 효율적인 경로다.

### 현재 달성

- ARC1 언패커 완성 (`arc_extract.py`)
- 66/66 전함 MDX → OBJ 변환 성공 (461,803v / 495,138f)
- 258/258 텍스처(DDS+TGA) 추출 완료
- s72 파트: gap strip 적용으로 97%+ 커버리지

### 잔여 문제

1. **s84 prim=1 커버리지 부족**: strip 디코딩으로 84%만 커버, 16% 면 위치 불명
2. **heuristic 파트 gap strip 미적용**: Phase 3에서 발견된 VB에 gap strip 로직 미연결
3. **어항 테스트 미수행**: 밀폐(watertight) 메시 검증 부재

## 2. 타겟 사용자

| 사용자 | 니즈 |
|--------|------|
| Open LOGH 개발팀 | Three.js에서 렌더링 가능한 전함 3D 에셋 |
| 프론트엔드 개발자 | 밀폐된 메시로 조명/그림자 정상 작동 보장 |
| 게임 디자이너 | 66개 전함 전체를 전투 UI에서 구분 가능한 수준 |

## 3. 성공 기준

| ID | 기준 | 측정 방법 |
|----|------|-----------|
| SC-1 | 66개 전함 모두 95%+ face 커버리지 | `2V-4` 공식 대비 실제 면 수 비율 |
| SC-2 | s84 prim=1 커버리지 84% → 95%+ | Brunhild s84 기준 검증 |
| SC-3 | heuristic 파트 gap strip 적용 | gap strip 전/후 면 수 비교 |
| SC-4 | 어항 테스트 정의 및 실행 | edge manifold 검사 통과율 |
| SC-5 | 기존 66/66 변환 성공률 유지 | regression 없음 |

## 4. 스코프

### In Scope

- `mdx_parse_mds.py` 수정: s84 누락 면 탐지 및 추출
- gap strip 로직 heuristic 파트 확대 적용
- 어항 테스트(edge manifold) 검증 스크립트
- Brunhild, battleship, berlin 등 주요 함선 검증

### Out of Scope

- 텍스처 매핑 (UV 좌표는 이미 추출 중)
- glTF/GLB 변환 (OBJ 완성 후 별도 단계)
- 캐릭터/행성 모델 역공학
- 실시간 렌더링 최적화

## 5. 핵심 기술 분석

### gin7 바이너리 레이아웃 (Brunhild 기준)

```
[descriptors (3 x 36B)]
[s24 VB (98v x 24B)] [s24 IB (156 idx)]
[gap/metadata]
[s72 VB (2943v x 72B)] [s72 list_IB (9297 idx)] [s72 strip_IB (gap)]
[s84 VB (3104v x 84B)] [s84 strip_IB (7191 idx)] [s84 PV (2483 values)]
```

### s84 문제의 핵심

- s84 디스크립터의 `prim=1` (strip 타입)
- VB 뒤에 strip IB가 오고, 그 뒤에 PV(Position Vector)가 따라옴
- PV는 `vc`개의 u16 값으로, IB와 구조가 동일하여 오인 위험
- 현재 코드는 strip IB만 디코딩하여 84% 달성
- **나머지 16%가 어디에 있는지가 미지의 영역**

### 가설

1. s84 strip IB 내에 restart 마커 패턴이 있어 추가 면 추출 가능
2. s84 VB 앞쪽에 별도의 list IB가 숨어 있을 가능성
3. 디스크립터 `ic` 값이 실제 strip 길이보다 작아 뒷부분이 잘리는 중
4. s84의 16%는 원본에서도 존재하지 않는 내부 면(backface cull 대상)

## 6. 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| s84 16%가 바이너리에 존재하지 않을 수 있음 | SC-2 미달성 | 84%를 수용 가능 기준으로 재설정 |
| gap strip 적용 시 오연결(spaghetti) 발생 | 메시 품질 저하 | clean_faces 필터링 강화 |
| PV 오인으로 인한 regression | 기존 97% 결과 파괴 | PV 감지 로직 보강 |

## 7. 우선순위

1. **P0**: s84 누락 면 바이너리 조사 (가설 검증)
2. **P1**: heuristic 파트 gap strip 적용
3. **P1**: 어항 테스트 스크립트 작성
4. **P2**: 66개 전함 일괄 재검증
