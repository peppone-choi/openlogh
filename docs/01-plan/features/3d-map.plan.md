# Plan: 3D 게임 맵 (3d-map)

> **Feature**: 3d-map
> **Created**: 2026-04-03
> **PRD**: `docs/00-pm/3d-map.prd.md`
> **Phase**: Plan

---

## Executive Summary

| 관점 | 내용 |
|------|------|
| **Problem** | 2D HTML/CSS 맵(700×500px)은 공간감과 몰입감이 부족하여 전략적 지형 파악과 병력 추적이 직관적이지 않음 |
| **Solution** | R3F(@react-three/fiber) 기반 3D 맵 렌더링 — 하이폴리 리얼리스틱 지형 + 프로시저럴 성곽 + 카메라 조작 |
| **UX Effect** | 지형 고저차/텍스쳐로 전략적 공간 인지, 성곽 규모로 도시 중요도 직관 파악, 병력 이동 실시간 추적 |
| **Core Value** | samnet.kr 수준의 3D 시각 경험을 React 생태계 안에서 구현하여 게임 몰입감을 근본적으로 개선 |

---

## Context Anchor

| 항목 | 내용 |
|------|------|
| **WHY** | 2D 맵의 공간감 부재 → 전략적 판단력 저하 + 경쟁 게임 대비 시각적 열위 |
| **WHO** | PC 데스크톱 우선 (Chrome), 이후 모바일 확장 |
| **RISK** | 모바일 성능(30fps 확보), 기존 2D 인터랙션 깨짐, 번들 사이즈 증가 |
| **SUCCESS** | 2D 기능 100% 패러티 + PC 60fps + 모바일 30fps + 로딩 3초 이내 |
| **SCOPE** | P0 씬/지형/도시/카메라/클릭/툴팁 → P1 유닛/애니/모드전환/모바일 → P2 이펙트/계절 |

---

## 1. 배경 및 동기

### 1.1 현재 상태

- `MapCanvas` (26.5KB): 순수 HTML/CSS div 포지셔닝 기반 2D 렌더러
- `MapViewer` (8.5KB): 데이터 로딩 + RenderCity 변환 + 이벤트 핸들링
- 맵 데이터: `MapData { cities: CityConst[] }` — (x: 0-700, y: 0-500)
- 6개 레이어: 배경(계절) → 도로 PNG → 영역 오버레이 → 도시 → 유닛 마커 → 툴팁
- CDN 에셋: `cast_X.gif`, `bg_{season}.jpg`, `{code}_road.png`

### 1.2 문제점

1. **공간감 부재**: 모든 요소가 동일 평면 — 산/평야/숲 구분 불가
2. **도시 규모 직관성**: 16~32px 아이콘 크기 차이로 8단계 구분 어려움
3. **병력 추적**: 점선 + 마커로는 이동 경로/속도 체감 부족
4. **경쟁 열위**: samnet.kr은 Three.js 3D 맵으로 몰입감 확보

### 1.3 참고: samnet.kr 기술 분석

- Three.js v0.163.0, 12×12 타일맵, OrbitControls
- GLB 성곽 (4단계), GLB 병사 모델
- 프로시저럴 지형 (산봉우리, 나무 클러스터)
- 진군 애니메이션, 전투 파티클, 화염/연기
- 모바일: FPS 제한, AA OFF

---

## 2. 목표 및 범위

### 2.1 목표

samnet.kr 참고하여 메인 게임 맵을 Three.js 기반 하이폴리 리얼리스틱 3D로 전환.
기존 2D 맵은 100% 보존하고, 3D는 토글로 전환 가능.

### 2.2 범위 (Phase별)

#### Phase 1: 3D 기반 (P0) — 핵심

| ID | 항목 | 설명 |
|----|------|------|
| P0-1 | R3F 씬 초기화 | Canvas, 카메라, 라이팅, 렌더러 설정 |
| P0-2 | 3D 지형 렌더링 | 맵 데이터 기반 하이폴리 지형 메쉬 (고저차 + 텍스쳐) |
| P0-3 | 도시/성곽 프로시저럴 | 레벨별 성곽 자동 생성 (성벽/망루/천수각/기와지붕) |
| P0-4 | 국가 영역 | 국가 색상 지형 텍스쳐/오버레이 |
| P0-5 | OrbitControls | 회전/팬/줌 + 줌 제한 + 초기 뷰 프리셋 |
| P0-6 | 도시 클릭 | 레이캐스팅 기반 도시 선택 |
| P0-7 | 툴팁 연동 | HTML 오버레이 툴팁 (기존 CompactTooltip 재활용) |

#### Phase 2: 인터랙션 (P1)

| ID | 항목 | 설명 |
|----|------|------|
| P1-1 | 유닛 마커 3D | 병력 위치 3D 표시 (깃발/마커) |
| P1-2 | 이동 애니메이션 | 출발→도착 경로 애니메이션 (바운스, 깃발 흔들림) |
| P1-3 | 2D/3D 토글 | 모드 전환 버튼 + sessionStorage 기억 |
| P1-4 | 모바일 터치 | 핀치 줌, 드래그 팬, 탭 선택 |

#### Phase 3: 효과 (P2)

| ID | 항목 | 설명 |
|----|------|------|
| P2-1 | 계절 라이팅 | 봄/여름/가을/겨울별 조명 색온도/강도 변화 |
| P2-2 | 전투 이펙트 | 전투 지점 파티클 (스파크, vs 빌보드) |
| P2-3 | 이벤트 이펙트 | 약탈/역병/재난 시각 효과 |

### 2.3 범위 제외

- 실시간 전투 3D 씬 (별도 피처로 분리)
- 지형 에디터/맵 에디터
- VR/AR 지원
- 사운드 이펙트 (기존 시스템 유지)

---

## 3. 기술 스택

### 3.1 핵심 의존성

| 패키지 | 버전 | 용도 |
|--------|------|------|
| `three` | ^0.170 | 3D 엔진 코어 |
| `@react-three/fiber` | ^9 | React Three.js 바인딩 |
| `@react-three/drei` | ^10 | OrbitControls, Html, useTexture 등 유틸리티 |
| `@react-three/postprocessing` | ^3 | SSAO, Bloom (P2 이펙트용, 선택) |

### 3.2 아키텍처 결정

| 결정 | 선택 | 이유 |
|------|------|------|
| React 바인딩 | R3F (선언적) | 기존 React 컴포넌트 패턴 유지, Zustand 통합 |
| 지형 생성 | 프로시저럴 PlaneGeometry + displacement | 맵 데이터 좌표 기반 동적 생성 |
| 도시 모델 | Meshy.ai AI 생성 GLB | 하이폴리 리얼리스틱 성곽 모델, 레벨별 4종 생성 후 CDN 호스팅 |
| 지형 오브젝트 | Meshy.ai AI 생성 GLB | 산, 나무, 바위 등 지형 데코레이션 모델 |
| 텍스쳐 | PBR 텍스쳐 (Meshy.ai 내장) + CDN | AI 생성 모델에 PBR 텍스쳐 포함, 지형은 별도 텍스쳐 |
| 상태 관리 | 기존 Zustand gameStore | 2D/3D 동일 데이터 소스 |
| 툴팁 | drei `<Html>` 컴포넌트 | 기존 React 컴포넌트 3D 오버레이 |
| 스타일 | 하이폴리 리얼리스틱 | PBR 재질, 텍스쳐 매핑, 디테일 지오메트리 |

### 3.3 좌표계 매핑

```
2D 좌표계 (현재)          3D 좌표계 (Three.js)
─────────────────         ─────────────────
x: 0 → 700               x: -350 → 350
y: 0 → 500               z: -250 → 250  (y축은 높이)
                          y: 0 → height (지형 고도)

변환: x3d = x2d - 350
      z3d = y2d - 250
      y3d = terrainHeight(x3d, z3d)
```

---

## 4. 컴포넌트 설계

### 4.1 파일 구조

```
frontend/src/
├── components/game/
│   ├── map-viewer.tsx              (기존 — 2D/3D 분기 추가)
│   ├── map-canvas.tsx              (기존 — 2D 렌더러 유지)
│   ├── map-3d/
│   │   ├── map-3d-scene.tsx        (R3F Canvas + 씬 설정)
│   │   ├── terrain.tsx             (3D 지형 메쉬)
│   │   ├── terrain-materials.tsx   (PBR 재질 + 텍스쳐)
│   │   ├── city-model.tsx          (Meshy.ai GLB 성곽 로드/배치)
│   │   ├── city-label.tsx          (도시 이름 빌보드)
│   │   ├── nation-overlay.tsx      (국가 영역 색상)
│   │   ├── camera-controller.tsx   (OrbitControls 래퍼)
│   │   ├── city-interaction.tsx    (레이캐스팅 + 클릭)
│   │   ├── tooltip-overlay.tsx     (drei Html 툴팁)
│   │   ├── unit-markers-3d.tsx     (P1: 병력 마커)
│   │   ├── unit-animation.tsx      (P1: 이동 애니메이션)
│   │   ├── season-lighting.tsx     (P2: 계절 조명)
│   │   ├── battle-effects.tsx      (P2: 전투 파티클)
│   │   └── index.ts                (barrel export)
│   └── map-mode-toggle.tsx         (P1: 2D/3D 전환 버튼)
├── hooks/
│   └── useMap3d.ts                 (3D 맵 상태/설정 훅)
└── lib/
    ├── map-3d-utils.ts             (좌표 변환, 지형 높이 함수)
    └── castle-loader.ts             (Meshy.ai GLB 성곽 로더 + 캐시)
```

### 4.2 핵심 컴포넌트 상세

#### MapViewer 수정 (기존)
```
기존 MapViewer
├── mode === '2d' → <MapCanvas /> (기존 그대로)
└── mode === '3d' → <Map3dScene /> (새로 추가)
    └── 동일한 props (renderCities, onCityClick, ...)
```

#### Map3dScene (R3F Canvas)
```
<Canvas>
  <CameraController />
  <SeasonLighting season={season} />
  <Terrain mapCode={mapCode} cities={cities} />
  <NationOverlay cities={renderCities} />
  {renderCities.map(city => (
    <CityModel key={city.id} city={city} />
    <CityLabel city={city} />
  ))}
  <CityInteraction onCityClick={onCityClick} />
  <TooltipOverlay hoveredCity={hoveredCity} />
  {/* P1 */}
  <UnitMarkers3d units={units} />
  <UnitAnimation movements={movements} />
</Canvas>
```

#### Terrain (지형 생성)
- `PlaneGeometry(700, 500, segments, segments)` 기반
- 도시 위치 기반 지형 높이맵 생성 (region → 지형 타입 → 고도)
- PBR Material: normalMap + roughnessMap + 지형별 diffuseMap
- 지형 타입 매핑: region 데이터 → 산악/평야/숲/사막/수역

#### CityModel (Meshy.ai GLB 모델)
- Meshy.ai에서 AI 생성한 GLB 성곽 모델을 CDN에 호스팅 (레벨 체계에 맞춤):
  - **수/진** (Lv1-2): 수군 기지/진영 — "ancient chinese riverside naval camp with docks and wooden palisade"
  - **관/이** (Lv3-4): 관문/이민족 거점 — "ancient chinese fortified mountain pass with stone gate" / "barbarian tribal settlement with yurts and wooden walls"
  - **소/중** (Lv5-6): 성곽 도시 — "ancient chinese walled city with tiled roofs and watchtowers"
  - **대/특** (Lv7-8): 대규모 성곽 — "grand ancient chinese imperial fortress with multi-tiered pagoda, high walls and moat"
- 레벨 → 모델 매핑 (4종 기본) + scale 조절로 동일 Tier 내 차등 (예: Lv7 = 0.9x, Lv8 = 1.0x)
- 국가 색상: 깃발 메쉬의 material.color 동적 변경
- GLB에 PBR 텍스쳐 내장 (Meshy.ai 자동 생성)
- 추가 에셋: 산, 나무, 바위 등 지형 오브젝트도 Meshy.ai로 생성

#### CameraController
- drei `OrbitControls` 래퍼
- 초기 뷰: 비스듬한 45도 탑다운 (samnet.kr 참고)
- 줌 제한: min 100, max 800
- 팬 제한: 맵 경계 내
- 더블 클릭: 도시 포커스 (카메라 이동 + 줌인)

---

## 5. 데이터 흐름

### 5.1 맵 데이터 로드

```
gameStore.loadMap(mapCode)
  → mapApi.get(mapCode)
  → MapData { cities: CityConst[] }
  → 2D: MapCanvas에 전달 (기존)
  → 3D: Map3dScene에 전달 (새로)
      → Terrain: cities[].region → 지형 타입 → 높이맵
      → CityModel[]: cities[].level → 성곽 규모
      → NationOverlay: renderCities[].nationColor → 영역 색상
```

### 5.2 인터랙션 흐름

```
사용자 클릭
  → Raycaster.intersectObjects(cityMeshes)
  → hit? → cityId 추출
  → onCityClick(cityId) 콜백 (기존 MapViewer와 동일)
  → 툴팁 표시 / 페이지 네비게이션

카메라 조작
  → OrbitControls 이벤트
  → useMap3d 훅에 상태 저장 (sessionStorage)
```

### 5.3 WebGL 폴백

```
WebGL 지원 감지 (초기화 시)
  → 지원: 3D 모드 활성화
  → 미지원: 자동 2D 모드 + 토글 버튼 비활성화 + 안내 메시지
```

---

## 6. 성능 전략

### 6.1 렌더링 최적화

| 기법 | 적용 대상 | 효과 |
|------|----------|------|
| **인스턴싱** | 동일 레벨 성곽, 나무 | 드로 콜 감소 |
| **LOD** | 성곽 모델 (3단계) | 원거리 단순화 |
| **프러스텀 컬링** | R3F 자동 | 화면 밖 미렌더 |
| **지오메트리 병합** | 지형 타일 | 드로 콜 감소 |
| **텍스쳐 아틀라스** | 지형 텍스쳐 | 텍스쳐 스위칭 감소 |
| **FPS 제한** | 모바일 30fps | GPU 부하 감소 |
| **AA 조건부** | PC만 활성화 | 모바일 성능 확보 |

### 6.2 로딩 최적화

| 기법 | 설명 |
|------|------|
| **동적 임포트** | `React.lazy(() => import('./map-3d/map-3d-scene'))` |
| **Three.js tree-shaking** | 필요한 모듈만 import |
| **텍스쳐 압축** | KTX2 포맷 (WebGL 네이티브 압축) |
| **프로그레시브 로드** | 지형 → 도시 → 유닛 순차 표시 |
| **Suspense** | R3F `<Suspense fallback={<Spinner />}>` |

### 6.3 성능 목표

| 환경 | FPS | 메모리 | 로딩 |
|------|-----|--------|------|
| PC Chrome | >= 60 | < 200MB | < 3초 |
| 모바일 Chrome | >= 30 | < 100MB | < 5초 |
| 모바일 Safari | >= 30 | < 100MB | < 5초 |

---

## 7. 리스크 및 대응

| ID | 리스크 | 확률 | 영향 | 대응 |
|----|--------|------|------|------|
| R-01 | 모바일 GPU에서 30fps 미달 | 중 | 높 | LOD 강화, 자동 품질 조절, 2D 폴백 |
| R-02 | 프로시저럴 성곽이 리얼리스틱에 미달 | 중 | 중 | PBR 재질 강화, 텍스쳐 디테일로 보완 |
| R-03 | R3F 번들 사이즈 (Three.js ~150KB gzipped) | 낮 | 중 | 동적 임포트, tree-shaking |
| R-04 | 기존 MapViewer 인터페이스 깨짐 | 낮 | 높 | 동일 props 인터페이스, 2D 100% 유지 |
| R-05 | 레이캐스팅 성능 (50+ 도시) | 낮 | 중 | 공간 분할 (Octree), 이벤트 쓰로틀 |
| R-06 | 좌표 변환 정확도 | 낮 | 높 | 단위 테스트로 2D↔3D 좌표 검증 |

---

## 8. 구현 순서

### Phase 1 상세 (P0 — 핵심)

```
Step 1: 환경 구축
  ├── three, @react-three/fiber, @react-three/drei 설치
  ├── Map3dScene 컴포넌트 스켈레톤
  └── verify: R3F Canvas 렌더링 확인

Step 2: 지형 렌더링
  ├── map-3d-utils.ts (좌표 변환 함수)
  ├── terrain.tsx (PlaneGeometry + displacement)
  ├── terrain-materials.tsx (PBR 재질)
  └── verify: 맵 형상 표시, 고저차 확인

Step 3: 도시/성곽
  ├── Meshy.ai에서 4종 성곽 GLB 생성 (촌락/소성/중성/대성)
  ├── GLB → CDN 업로드 (opensamguk-image 저장소)
  ├── castle-loader.ts (GLB 로더 + 캐시 + 레벨 매핑)
  ├── city-model.tsx (도시 위치에 GLB 배치 + 국가 색상)
  ├── city-label.tsx (도시 이름 빌보드)
  ├── 지형 오브젝트: Meshy.ai로 산/나무/바위 GLB 추가 생성
  └── verify: 50개 도시 표시, 레벨별 규모 차이, 모델 로딩 속도

Step 4: 카메라 + 인터랙션
  ├── camera-controller.tsx (OrbitControls 설정)
  ├── city-interaction.tsx (레이캐스팅 클릭)
  ├── tooltip-overlay.tsx (HTML 오버레이)
  └── verify: 도시 클릭 → 툴팁, 카메라 조작

Step 5: 국가 영역 + 통합
  ├── nation-overlay.tsx (영역 색상)
  ├── MapViewer 수정 (2D/3D 분기)
  ├── WebGL 감지 + 폴백
  └── verify: 전체 흐름 E2E, 성능 프로파일링
```

### Phase 2 (P1 — 인터랙션)

```
Step 6: 유닛 + 애니메이션
  ├── unit-markers-3d.tsx
  ├── unit-animation.tsx
  └── verify: 병력 표시 + 이동 애니메이션

Step 7: 모드 전환 + 모바일
  ├── map-mode-toggle.tsx
  ├── useMap3d.ts (상태 관리 + 모바일 감지)
  └── verify: 토글 동작, 터치 조작
```

### Phase 3 (P2 — 효과)

```
Step 8: 계절 + 이펙트
  ├── season-lighting.tsx
  ├── battle-effects.tsx
  └── verify: 계절 분위기, 전투 시각 효과
```

---

## 9. Success Criteria

| ID | 기준 | 측정 방법 | 우선순위 |
|----|------|----------|---------|
| SC-01 | 3D 맵에서 기존 2D 맵의 모든 기능 동작 | 기능 체크리스트 100% | P0 |
| SC-02 | PC Chrome 60fps 이상 (도시 50개) | Chrome DevTools Performance | P0 |
| SC-03 | 모바일 Safari/Chrome 30fps 이상 | 실기기 테스트 | P1 |
| SC-04 | 초기 로딩 3초 이내 | Performance timing | P0 |
| SC-05 | 2D/3D 모드 전환 시 상태 유지 | E2E 테스트 | P1 |
| SC-06 | WebGL 미지원 시 자동 2D 폴백 | 수동 테스트 | P0 |
| SC-07 | 레이캐스팅 클릭 정확도 100% | 도시 50개 전수 클릭 테스트 | P0 |
| SC-08 | Three.js 번들 추가 200KB 이내 (gzipped) | `next build` 번들 분석 | P0 |

---

## 10. 테스트 전략

### 10.1 단위 테스트

| 대상 | 테스트 내용 |
|------|-----------|
| `map-3d-utils.ts` | 2D↔3D 좌표 변환 정확도 |
| `procedural-castle.ts` | 레벨별 성곽 geometry 생성 |
| `useMap3d.ts` | 상태 관리, 모드 전환 |

### 10.2 통합 테스트

| 대상 | 테스트 내용 |
|------|-----------|
| `Map3dScene` | R3F Canvas 렌더링, 도시 표시 |
| `MapViewer` | 2D/3D 모드 전환, props 전달 |

### 10.3 E2E 테스트

| 시나리오 | 검증 |
|----------|------|
| 3D 맵 로드 | 도시 50개 표시, FPS 측정 |
| 도시 클릭 | 레이캐스팅 → 툴팁 → 네비게이션 |
| 모드 전환 | 2D→3D→2D 상태 유지 |
| 모바일 | 터치 조작, FPS 측정 |

---

## 11. 의존성 및 제약

### 11.1 외부 의존성

- `three` + `@react-three/fiber` + `@react-three/drei` NPM 패키지
- WebGL 2.0 브라우저 지원 필수

### 11.2 내부 의존성

- `gameStore` (맵 데이터, 도시 정보, 국가 정보)
- `generalStore` (유닛 위치)
- `MapViewer` 인터페이스 (onCitySelect, onNationSelect 콜백)
- `CompactTooltip` 컴포넌트 (기존 재활용)
- CDN 이미지 에셋 (배경, 텍스쳐 확장 가능)

### 11.3 제약 사항

- Next.js SSR 환경에서 Three.js는 클라이언트만 가능 (`'use client'` + `dynamic import`)
- iOS Safari WebGL 메모리 제한 (~100MB)
- Konva 의존성 제거 가능 (현재 미사용)
