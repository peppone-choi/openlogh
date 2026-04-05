# Design: 3D 게임 맵 (3d-map)

> **Feature**: 3d-map
> **Created**: 2026-04-03
> **Plan**: `docs/01-plan/features/3d-map.plan.md`
> **PRD**: `docs/00-pm/3d-map.prd.md`
> **Architecture**: Option B — Clean Architecture
> **Phase**: Design

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

## 1. Overview

### 1.1 아키텍처 결정

**Option B — Clean Architecture** 선택:
- 완벽한 관심사 분리 (scene / terrain / city / interaction / camera / effects)
- 각 도메인 독립 테스트 가능
- P1/P2 확장 시 기존 코드 수정 최소화
- 15-20개 새 파일, 기존 파일 수정 최소 (MapViewer에 분기만 추가)

### 1.2 기술 스택

| 패키지 | 버전 | 용도 |
|--------|------|------|
| `three` | ^0.170 | 3D 엔진 코어 |
| `@react-three/fiber` | ^9 | React Three.js 선언적 바인딩 |
| `@react-three/drei` | ^10 | OrbitControls, Html, useGLTF, useTexture 등 |
| `@types/three` | ^0.170 | TypeScript 타입 |

### 1.3 스타일 방향

- **하이폴리 리얼리스틱**: PBR 재질 (MeshStandardMaterial), 텍스쳐 매핑
- **Meshy.ai GLB 에셋**: AI 생성 성곽/지형 오브젝트
- samnet.kr 수준의 시각적 품질 목표

---

## 2. 디렉토리 구조

```
frontend/src/
├── components/game/
│   ├── map-viewer.tsx                    # 기존 — 2D/3D 모드 분기 추가
│   ├── map-canvas.tsx                    # 기존 — 2D 렌더러 (변경 없음)
│   ├── map-mode-toggle.tsx               # NEW: 2D/3D 전환 UI (P1)
│   └── map-3d/
│       ├── index.ts                      # barrel export
│       │
│       ├── scene/                        # 씬 관리
│       │   ├── Map3dScene.tsx            # R3F <Canvas> 루트 + Suspense
│       │   ├── SceneSetup.tsx            # 조명, 안개, 환경 설정
│       │   └── RenderLoop.tsx            # 프레임 제한, 성능 모니터
│       │
│       ├── terrain/                      # 지형 렌더링
│       │   ├── TerrainMesh.tsx           # PlaneGeometry + displacement
│       │   ├── TerrainMaterial.tsx        # PBR 재질 + 지형별 텍스쳐
│       │   ├── HeightMapGenerator.ts     # 도시/region 기반 높이맵 생성
│       │   └── TerrainDecorations.tsx    # 나무, 바위 등 Meshy.ai GLB 배치
│       │
│       ├── city/                         # 도시/성곽
│       │   ├── CityModel.tsx             # 레벨별 GLB 성곽 배치
│       │   ├── CityLabel.tsx             # drei <Html> 도시 이름 빌보드
│       │   ├── CityFlag.tsx              # 국가 깃발 메쉬 (색상 동적)
│       │   └── CastleLoader.ts           # GLB 로더 + 캐시 + 레벨 매핑
│       │
│       ├── nation/                       # 국가 영역
│       │   └── NationOverlay.tsx         # 영역 색상 지형 오버레이
│       │
│       ├── interaction/                  # 사용자 상호작용
│       │   ├── CityRaycaster.tsx         # 도시 클릭 감지
│       │   ├── TooltipOverlay.tsx        # drei <Html> 기반 툴팁
│       │   └── HoverHighlight.tsx        # 호버 시 도시 하이라이트
│       │
│       ├── camera/                       # 카메라 제어
│       │   ├── CameraController.tsx      # OrbitControls 래퍼
│       │   └── CameraPresets.ts          # 초기 뷰, 도시 포커스 프리셋
│       │
│       ├── units/                        # P1: 유닛/병력
│       │   ├── UnitMarkers3d.tsx         # 병력 위치 3D 마커
│       │   └── UnitAnimation.tsx         # 이동 애니메이션
│       │
│       └── effects/                      # P2: 시각 효과
│           ├── SeasonLighting.tsx        # 계절별 조명 변화
│           ├── BattleEffects.tsx         # 전투 파티클
│           └── EventEffects.tsx          # 재난/이벤트 이펙트
│
├── hooks/
│   └── useMap3d.ts                       # 3D 맵 상태/설정/모드 관리
│
├── lib/
│   ├── map-3d-utils.ts                   # 2D↔3D 좌표 변환
│   └── castle-loader.ts                  # → city/CastleLoader.ts로 이동 (lib에 불필요)
│
└── types/
    └── index.ts                          # Map3d 관련 타입 추가
```

---

## 3. 컴포넌트 상세 설계

### 3.1 Scene Layer

#### Map3dScene.tsx — R3F Canvas 루트

```typescript
interface Map3dSceneProps {
  mapCode: string;
  renderCities: RenderCity[];
  units?: GeneralFrontInfo[];
  season: MapSeason;
  onCityClick?: (cityId: number) => void;
  onCityHover?: (cityId: number | null) => void;
  compact?: boolean;
}
```

**핵심 구조:**
```tsx
<Canvas
  gl={{ antialias: !isMobile, powerPreference: 'high-performance' }}
  camera={{ position: [0, 300, 200], fov: 50 }}
  dpr={isMobile ? 1 : [1, 2]}
>
  <Suspense fallback={null}>
    <SceneSetup season={season} />
    <RenderLoop maxFps={isMobile ? 30 : 60} />
    <TerrainMesh mapCode={mapCode} cities={cities} />
    <NationOverlay cities={renderCities} />
    {renderCities.map(city => (
      <group key={city.id}>
        <CityModel city={city} />
        <CityLabel city={city} />
        <CityFlag city={city} />
      </group>
    ))}
    <CameraController compact={compact} />
    <CityRaycaster onCityClick={onCityClick} onCityHover={onCityHover} />
    <TooltipOverlay />
    {/* P1 */}
    {units && <UnitMarkers3d units={units} />}
    {/* P2 */}
    <SeasonLighting season={season} />
  </Suspense>
</Canvas>
```

#### SceneSetup.tsx — 조명/환경

```typescript
// 기본 조명 (samnet.kr 참고)
<ambientLight intensity={0.85} color="#ffeedd" />        // 환경광 (따뜻한 톤)
<directionalLight
  position={[200, 400, 100]}
  intensity={1.4}
  castShadow={false}                                      // 성능: 그림자 OFF
/>
<hemisphereLight
  skyColor="#87CEEB"
  groundColor="#8B7355"
  intensity={0.3}
/>
<fog attach="fog" args={['#d4e4f7', 400, 900]} />        // 원거리 안개
```

#### RenderLoop.tsx — FPS 제한

```typescript
// useFrame 내에서 프레임 스킵으로 FPS 제한
const interval = 1 / maxFps;
useFrame((state, delta) => {
  elapsed += delta;
  if (elapsed < interval) return;
  elapsed = 0;
  // 실제 렌더 로직
});
```

---

### 3.2 Terrain Layer

#### TerrainMesh.tsx — 지형 생성

**접근 방식:** PlaneGeometry의 vertex y좌표를 조작하여 고저차 표현

```typescript
interface TerrainMeshProps {
  mapCode: string;
  cities: CityConst[];
}
```

**지형 생성 알고리즘:**
1. `PlaneGeometry(700, 500, 128, 128)` — 128×128 세그먼트
2. `HeightMapGenerator`가 도시 위치 + region 기반 높이맵 생성
3. 각 vertex의 y값을 높이맵에 따라 조절
4. region별 기본 고도:
   - 산악(region 일부): 높은 고도 (y: 30-60)
   - 평야: 낮은 고도 (y: 0-10)
   - 수역(수군 도시 주변): 음수 고도 (y: -5~0)
5. 도시 위치 주변은 평탄하게 (성곽 배치를 위해)

#### HeightMapGenerator.ts

```typescript
export function generateHeightMap(
  cities: CityConst[],
  width: number,
  height: number,
  segments: number
): Float32Array {
  // 1. region 기반 기본 고도 설정
  // 2. 도시 간 연결선 → 도로 (낮은 고도)
  // 3. 도시 주변 반경 → 평탄화
  // 4. Simplex Noise로 미세 변화 추가
  // 5. 가장자리 → 서서히 낮아짐 (맵 경계)
}
```

#### TerrainMaterial.tsx — PBR 지형 재질

```typescript
// 지형 타입별 텍스쳐
const TERRAIN_TEXTURES = {
  grass: { diffuse, normal, roughness },   // 평야
  mountain: { diffuse, normal, roughness }, // 산악
  forest: { diffuse, normal, roughness },   // 산림
  water: { diffuse, normal, roughness },    // 수역
  desert: { diffuse, normal, roughness },   // 사막/이민족
};

// ShaderMaterial로 region별 블렌딩
// - vertex color 또는 splat map으로 지형 타입 구분
// - 경계 부분 부드러운 블렌딩
```

#### TerrainDecorations.tsx — Meshy.ai 지형 오브젝트

```typescript
// Meshy.ai에서 생성한 GLB 오브젝트 배치
// - 나무 (침엽수, 활엽수) → 산림 region
// - 바위 → 산악 region
// - 갈대/수초 → 수역 주변
// InstancedMesh로 대량 배치 (성능)
```

---

### 3.3 City Layer

#### CityModel.tsx — 성곽 GLB 배치

```typescript
interface CityModelProps {
  city: RenderCity;
}
```

**레벨-모델 매핑:**

Lv1-4는 도시가 아닌 특수 거점 (0레벨), Lv5-8만 실제 성곽 도시.

| 레벨 | 명칭 | 분류 | GLB 파일 | Scale |
|------|------|------|----------|-------|
| 1 | 수 | 특수거점 | `spot_naval.glb` | 1.0 |
| 2 | 진 | 특수거점 | `spot_camp.glb` | 1.0 |
| 3 | 관 | 특수거점 | `spot_gate.glb` | 1.0 |
| 4 | 이 | 특수거점 | `spot_tribal.glb` | 1.0 |
| 5 | 소 | 성곽도시 | `city_small.glb` | 1.0 |
| 6 | 중 | 성곽도시 | `city_medium.glb` | 1.0 |
| 7 | 대 | 성곽도시 | `city_large.glb` | 1.0 |
| 8 | 특 | 성곽도시 | `city_grand.glb` | 1.0 |

**GLB CDN 경로:** `{IMAGE_CDN_BASE}/game/3d/{spot|city}_{type}.glb`

#### CastleLoader.ts — GLB 로더 + 캐시

```typescript
// drei의 useGLTF.preload로 사전 로드
// 4종 GLB만 로드하고 clone으로 재사용
// 특수 거점 (Lv1-4, 0레벨 = 비도시)
const SPOT_MODELS = {
  naval: 'spot_naval.glb',      // Lv1 수군기지
  camp: 'spot_camp.glb',        // Lv2 진영
  gate: 'spot_gate.glb',        // Lv3 관문
  tribal: 'spot_tribal.glb',    // Lv4 이민족
} as const;

// 성곽 도시 (Lv5-8, 실제 도시)
const CITY_MODELS = {
  small: 'city_small.glb',      // Lv5 소도시
  medium: 'city_medium.glb',    // Lv6 중도시
  large: 'city_large.glb',      // Lv7 대도시
  grand: 'city_grand.glb',      // Lv8 특대도시
} as const;

export type SpotModelType = keyof typeof SPOT_MODELS;
export type CityModelType = keyof typeof CITY_MODELS;

export function getLocationConfig(level: number): {
  type: 'spot' | 'city';
  model: string;
  scale: number;
} {
  switch (level) {
    case 1: return { type: 'spot', model: SPOT_MODELS.naval, scale: 1.0 };
    case 2: return { type: 'spot', model: SPOT_MODELS.camp, scale: 1.0 };
    case 3: return { type: 'spot', model: SPOT_MODELS.gate, scale: 1.0 };
    case 4: return { type: 'spot', model: SPOT_MODELS.tribal, scale: 1.0 };
    case 5: return { type: 'city', model: CITY_MODELS.small, scale: 1.0 };
    case 6: return { type: 'city', model: CITY_MODELS.medium, scale: 1.0 };
    case 7: return { type: 'city', model: CITY_MODELS.large, scale: 1.0 };
    case 8: return { type: 'city', model: CITY_MODELS.grand, scale: 1.0 };
    default: return { type: 'spot', model: SPOT_MODELS.camp, scale: 0.5 };
  }
}
```

#### CityLabel.tsx — 도시 이름 빌보드

```typescript
// drei <Html> — 3D 공간에 React DOM 오버레이
<Html
  position={[x3d, y3d + labelOffset, z3d]}
  center
  distanceFactor={200}  // 거리에 따른 스케일
  occlude              // 오브젝트 뒤에 숨김
>
  <div className="text-xs font-bold text-white drop-shadow-md">
    {city.name}
  </div>
</Html>
```

#### CityFlag.tsx — 국가 깃발

```typescript
// 국가 색상 깃발 — 간단한 PlaneGeometry + 바람 애니메이션
// nationColor → MeshBasicMaterial.color
// useFrame으로 미세한 흔들림 (sin 파형)
```

---

### 3.4 Nation Layer

#### NationOverlay.tsx — 영역 시각화

```typescript
// 방법 1: 지형 vertex color에 국가 색상 블렌딩
// 방법 2: 영역별 투명 PlaneGeometry 오버레이
// 방법 3: 경계선 Line (drei <Line>)

// 선택: 방법 1 (지형 색상 블렌딩) — 가장 자연스러움
// - 각 도시 위치에서 반경만큼 국가 색상 적용
// - 중립 도시: 기본 지형 색상
// - 영역 겹침: 가장 가까운 도시의 국가 색상 우선
```

---

### 3.5 Interaction Layer

#### CityRaycaster.tsx — 도시 클릭

```typescript
// R3F의 이벤트 시스템 활용 (mesh에 직접 onClick)
// 각 CityModel에 invisible hitbox (BoxGeometry, visible=false)
// hitbox 크기: 도시 레벨에 비례

<mesh
  position={[x3d, y3d, z3d]}
  onClick={(e) => {
    e.stopPropagation();
    onCityClick(city.id);
  }}
  onPointerOver={() => onCityHover(city.id)}
  onPointerOut={() => onCityHover(null)}
  visible={false}
>
  <boxGeometry args={[hitSize, hitSize, hitSize]} />
</mesh>
```

#### TooltipOverlay.tsx — 툴팁

```typescript
// 호버된 도시의 3D 위치에 기존 CompactTooltip 렌더링
// drei <Html>로 3D→2D 투영
<Html position={hoveredCity3dPos} zIndexRange={[16, 0]}>
  <CompactTooltip city={hoveredCityData} />
</Html>
```

#### HoverHighlight.tsx — 호버 하이라이트

```typescript
// 호버 시 도시 주변 원형 글로우
// RingGeometry + emissive material + 펄스 애니메이션
```

---

### 3.6 Camera Layer

#### CameraController.tsx — OrbitControls

```typescript
import { OrbitControls } from '@react-three/drei';

<OrbitControls
  // 초기 뷰: 비스듬한 탑다운
  target={[0, 0, 0]}
  minDistance={80}
  maxDistance={600}
  maxPolarAngle={Math.PI / 2.2}   // 지면 아래 못 내려감
  minPolarAngle={Math.PI / 8}     // 완전 위에서 못 봄
  enableDamping
  dampingFactor={0.05}
  // 팬 제한 (맵 경계)
  // → onChange 콜백에서 target 클램프
/>
```

#### CameraPresets.ts — 프리셋

```typescript
export const CAMERA_PRESETS = {
  // 초기 전체 맵 뷰
  overview: {
    position: [0, 350, 250] as const,
    target: [0, 0, 0] as const,
  },
  // 도시 포커스 (더블클릭)
  cityFocus: (cityPos: [number, number, number]) => ({
    position: [cityPos[0], cityPos[1] + 80, cityPos[2] + 60],
    target: cityPos,
  }),
};
```

---

### 3.7 Units Layer (P1)

#### UnitMarkers3d.tsx

```typescript
// 각 장수 위치에 3D 마커 배치
// - 깃발 (국가 색상) + 병종 아이콘 (Sprite/Billboard)
// - 적군: 빨간 테두리 글로우
// - 이동 중: 경로선 (drei <Line>)
```

#### UnitAnimation.tsx

```typescript
// 이동 중인 유닛의 위치 보간 애니메이션
// - 출발 도시 → 도착 도시 곡선 경로 (QuadraticBezier)
// - 바운스 (y축 sin 파형)
// - 15초 주기 반복 (samnet.kr 참고)
```

---

### 3.8 Effects Layer (P2)

#### SeasonLighting.tsx

```typescript
// 계절별 조명 색온도/강도
const SEASON_LIGHT = {
  spring: { ambient: '#fff8e7', sun: '#ffe4b5', intensity: 1.2 },
  summer: { ambient: '#fffff0', sun: '#fff8dc', intensity: 1.5 },
  fall:   { ambient: '#ffecd2', sun: '#daa520', intensity: 1.0 },
  winter: { ambient: '#e8eaf6', sun: '#b0c4de', intensity: 0.8 },
};
```

#### BattleEffects.tsx / EventEffects.tsx

```typescript
// 전투 지점: 스파크 파티클 + vs 빌보드
// 이벤트: 화염(약탈), 역병(녹색 안개), 재난(흔들림)
// drei의 Sparkles 또는 커스텀 파티클 시스템
```

---

## 4. 좌표 변환 시스템

### 4.1 map-3d-utils.ts

```typescript
// 2D 맵 좌표 → 3D 월드 좌표
export function toWorld3d(x2d: number, y2d: number, heightMap?: Float32Array): Vector3 {
  const x = x2d - 350;  // 중앙 정렬
  const z = y2d - 250;
  const y = heightMap ? sampleHeight(heightMap, x2d, y2d) : 0;
  return new Vector3(x, y, z);
}

// 3D 월드 좌표 → 2D 맵 좌표
export function toMap2d(world: Vector3): { x: number; y: number } {
  return {
    x: world.x + 350,
    y: world.z + 250,
  };
}

// 높이맵 샘플링 (바이리니어 보간)
export function sampleHeight(
  heightMap: Float32Array,
  x2d: number,
  y2d: number,
  segments: number = 128
): number { ... }
```

---

## 5. 데이터 흐름

### 5.1 맵 로딩

```
gameStore.loadMap(mapCode)
  → mapData: MapData { cities: CityConst[] }

MapViewer
  ├── mode === '2d' → <MapCanvas /> (기존)
  └── mode === '3d' → <Map3dScene />
      → cities → HeightMapGenerator → heightMap (Float32Array)
      → cities → TerrainMesh (heightMap 적용)
      → renderCities → CityModel[] (GLB 배치)
      → renderCities → NationOverlay (영역 색상)
```

### 5.2 인터랙션

```
사용자 마우스/터치
  → R3F 이벤트 시스템
  → CityRaycaster: onClick/onPointerOver
  → Map3dScene: hoveredCity/selectedCity 상태
  → TooltipOverlay: CompactTooltip 렌더
  → 상위 콜백: onCityClick(cityId)
```

### 5.3 모드 전환 (P1)

```
useMap3d hook
  → mapMode: '2d' | '3d' (sessionStorage 기억)
  → setMapMode → MapViewer 리렌더
  → WebGL 미지원 → 강제 '2d' + 토글 비활성화
```

---

## 6. 성능 설계

### 6.1 렌더링 최적화

| 기법 | 대상 | 구현 위치 |
|------|------|----------|
| FPS 제한 | 모바일 30fps | `RenderLoop.tsx` |
| AA 조건부 | PC만 활성화 | `Map3dScene.tsx` Canvas gl 옵션 |
| DPR 제한 | 모바일 1x | `Map3dScene.tsx` dpr 옵션 |
| 인스턴싱 | 나무/바위 데코 | `TerrainDecorations.tsx` InstancedMesh |
| GLB 캐시 | 성곽 모델 4종 | `CastleLoader.ts` useGLTF + clone |
| 프러스텀 컬링 | 모든 오브젝트 | R3F 자동 |
| 지오메트리 병합 | 지형 | 단일 PlaneGeometry |
| 안개 | 원거리 | `SceneSetup.tsx` fog |

### 6.2 로딩 최적화

| 기법 | 구현 |
|------|------|
| 동적 임포트 | `React.lazy(() => import('./map-3d/scene/Map3dScene'))` |
| GLB 사전 로드 | `useGLTF.preload([...CASTLE_MODELS])` |
| Suspense | `<Suspense fallback={<MapCanvas />}>` (2D를 폴백으로) |
| 프로그레시브 | 지형 먼저 → 도시 → 유닛 순차 표시 |

### 6.3 성능 목표

| 환경 | FPS | 메모리 | Draw Calls |
|------|-----|--------|------------|
| PC Chrome | >= 60 | < 200MB | < 100 |
| 모바일 Chrome | >= 30 | < 100MB | < 50 |

---

## 7. 에셋 파이프라인

### 7.1 Meshy.ai 에셋 생성 워크플로

```
1. Meshy.ai에서 텍스트-to-3D 생성
   → 프롬프트: "ancient chinese {type} ..." (§3.3 참고)
   → 스타일: Realistic, High Detail

2. GLB 다운로드 + 최적화
   → glTF-Transform으로 최적화 (draco 압축, 텍스쳐 리사이즈)
   → 파일 크기 목표: 각 GLB < 2MB

3. CDN 업로드
   → opensamguk-image 저장소: /game/3d/{name}.glb
   → jsdelivr CDN으로 배포

4. 코드에서 로드
   → useGLTF(`${CDN_BASE}/game/3d/castle_naval.glb`)
```

### 7.2 에셋 목록

| 카테고리 | 파일명 | 용도 | 예상 크기 |
|----------|--------|------|----------|
| 거점 | `spot_naval.glb` | Lv1 수군기지 | ~1MB |
| 거점 | `spot_camp.glb` | Lv2 진영 | ~1MB |
| 거점 | `spot_gate.glb` | Lv3 관문 | ~1MB |
| 거점 | `spot_tribal.glb` | Lv4 이민족 | ~1MB |
| 도시 | `city_small.glb` | Lv5 소도시 | ~1.5MB |
| 도시 | `city_medium.glb` | Lv6 중도시 | ~1.5MB |
| 도시 | `city_large.glb` | Lv7 대도시 | ~2MB |
| 도시 | `city_grand.glb` | Lv8 특대도시 | ~2MB |
| 유닛 | `unit_infantry.glb` | 보병/청주/근위 | ~1MB |
| 유닛 | `unit_archer.glb` | 궁병/노병/연노 | ~1MB |
| 유닛 | `unit_cavalry.glb` | 기병/근위기/서량 | ~1.5MB |
| 유닛 | `unit_chariot.glb` | 차병 | ~1.5MB |
| 유닛 | `unit_special.glb` | 귀병/무당/등갑 | ~1MB |
| 유닛 | `unit_navy.glb` | 수군 | ~1MB |
| 유닛 | `unit_flag.glb` | 깃발 (공통) | ~200KB |
| 지형 | `tree_pine.glb` | 침엽수 | ~200KB |
| 지형 | `tree_broad.glb` | 활엽수 | ~200KB |
| 지형 | `rock_cluster.glb` | 바위 군 | ~300KB |
| 지형 | `grass_patch.glb` | 풀숲 | ~200KB |
| 지형 | `reed_water.glb` | 갈대 | ~200KB |
| 텍스쳐 | `terrain_grass.jpg` | 평야 | ~500KB |
| 텍스쳐 | `terrain_mountain.jpg` | 산악 | ~500KB |
| 텍스쳐 | `terrain_water.jpg` | 수역 | ~300KB |
| **합계** | **23종** | | **~20MB** |

---

## 8. 타입 정의

### 8.1 새 타입 (types/index.ts에 추가)

```typescript
/** 3D 맵 렌더링 모드 */
export type MapRenderMode = '2d' | '3d';

/** 3D 맵 설정 */
export interface Map3dConfig {
  mode: MapRenderMode;
  quality: 'low' | 'medium' | 'high';  // 자동 감지
  showDecorations: boolean;              // 나무/바위 표시
  showLabels: boolean;                   // 도시 이름 표시
  showNationOverlay: boolean;            // 영역 색상 표시
}

/** 거점 모델 타입 (Lv1-4, 비도시) */
export type SpotModelType = 'naval' | 'camp' | 'gate' | 'tribal';

/** 도시 모델 타입 (Lv5-8, 성곽도시) */
export type CityModelType = 'small' | 'medium' | 'large' | 'grand';

/** 유닛 모델 타입 */
export type UnitModelType = 'infantry' | 'archer' | 'cavalry' | 'chariot' | 'special' | 'navy';

/** 위치 설정 (거점 or 도시) */
export interface LocationConfig {
  type: 'spot' | 'city';
  model: string;
  scale: number;
}

/** 지형 타입 */
export type TerrainType = 'grass' | 'mountain' | 'forest' | 'water' | 'desert';

/** 높이맵 파라미터 */
export interface HeightMapParams {
  width: number;
  height: number;
  segments: number;
  cities: CityConst[];
  regionTerrainMap: Record<number, TerrainType>;
}
```

---

## 9. MapViewer 수정 사항

### 9.1 기존 MapViewer에 추가할 코드

```typescript
// 최소한의 변경: 3D 모드 분기만 추가
import dynamic from 'next/dynamic';
import { useMap3d } from '@/hooks/useMap3d';

// SSR 방지: Three.js는 클라이언트만
const Map3dScene = dynamic(
  () => import('@/components/game/map-3d').then(m => m.Map3dScene),
  { ssr: false, loading: () => <MapCanvas {...props} /> }
);

export function MapViewer(props: MapViewerProps) {
  const { mapMode } = useMap3d();

  // ... 기존 로직 유지 ...

  if (mapMode === '3d') {
    return (
      <Map3dScene
        mapCode={mapCode}
        renderCities={renderCities}
        units={generals}
        season={season}
        onCityClick={handleCityClick}
        onCityHover={setHoveredCity}
        compact={compact}
      />
    );
  }

  // 기존 2D 렌더링 (변경 없음)
  return <MapCanvas ... />;
}
```

---

## 10. 테스트 계획

### 10.1 단위 테스트

| 파일 | 테스트 내용 |
|------|-----------|
| `map-3d-utils.test.ts` | toWorld3d/toMap2d 좌표 변환 정확도 |
| `HeightMapGenerator.test.ts` | 높이맵 생성, 도시 주변 평탄화 |
| `CastleLoader.test.ts` | 레벨-모델 매핑 정확도 |
| `useMap3d.test.ts` | 모드 전환, sessionStorage 저장 |

### 10.2 통합 테스트

| 시나리오 | 검증 |
|----------|------|
| Map3dScene 렌더링 | Canvas 생성, 에러 없음 |
| MapViewer 모드 분기 | 2D/3D 올바른 컴포넌트 렌더 |
| WebGL 미지원 폴백 | 자동 2D + 안내 메시지 |

### 10.3 E2E 테스트

| 시나리오 | 검증 |
|----------|------|
| 3D 맵 로드 | 도시 50개 표시, 콘솔 에러 없음 |
| 도시 클릭 | 3D → 클릭 → 툴팁/네비게이션 |
| 카메라 조작 | 회전/줌/팬 동작 |
| 모드 전환 | 2D↔3D, 상태 유지 |
| 모바일 | 터치 조작, 30fps |

---

## 11. Implementation Guide

### 11.1 구현 순서

| Step | 모듈 | 파일 | 의존성 |
|------|------|------|--------|
| 1 | 환경 구축 | package.json, Map3dScene, SceneSetup | 없음 |
| 2 | 지형 | TerrainMesh, HeightMapGenerator, TerrainMaterial | Step 1 |
| 3 | 도시 | CityModel, CastleLoader, CityLabel, CityFlag | Step 2 |
| 4 | 인터랙션 | CityRaycaster, TooltipOverlay, HoverHighlight | Step 3 |
| 5 | 카메라 | CameraController, CameraPresets | Step 1 |
| 6 | 국가 영역 | NationOverlay | Step 2 |
| 7 | 통합 | MapViewer 수정, useMap3d, WebGL 감지 | Step 1-6 |
| 8 | 유닛 (P1) | UnitMarkers3d, UnitAnimation | Step 7 |
| 9 | 모드전환 (P1) | map-mode-toggle, useMap3d 확장 | Step 7 |
| 10 | 이펙트 (P2) | SeasonLighting, BattleEffects, EventEffects | Step 7 |

### 11.2 의존성 설치

```bash
cd frontend && pnpm add three @react-three/fiber @react-three/drei
cd frontend && pnpm add -D @types/three
```

### 11.3 Session Guide

#### Module Map

| 모듈 | 파일 수 | 복잡도 | 세션 |
|------|---------|--------|------|
| module-1: scene | 3 | 중 | Session 1 |
| module-2: terrain | 4 | 높 | Session 1 |
| module-3: city | 4 | 높 | Session 2 |
| module-4: interaction | 3 | 중 | Session 2 |
| module-5: camera | 2 | 낮 | Session 2 |
| module-6: nation | 1 | 중 | Session 3 |
| module-7: integration | 3 | 중 | Session 3 |
| module-8: units (P1) | 2 | 중 | Session 4 |
| module-9: mode-toggle (P1) | 2 | 낮 | Session 4 |
| module-10: effects (P2) | 3 | 중 | Session 5 |

#### Recommended Session Plan

```
Session 1: 기반 + 지형 (module-1, module-2)
  → 패키지 설치, R3F Canvas, 지형 렌더링
  → verify: 3D 지형 표시, 고저차 확인

Session 2: 도시 + 인터랙션 + 카메라 (module-3, module-4, module-5)
  → Meshy.ai GLB 로드, 도시 배치, 클릭/툴팁, OrbitControls
  → verify: 50개 도시, 클릭→툴팁, 카메라 조작

Session 3: 국가 영역 + 통합 (module-6, module-7)
  → 영역 색상, MapViewer 2D/3D 분기, WebGL 폴백
  → verify: 전체 흐름 E2E, 성능 프로파일링

Session 4: 유닛 + 모드 전환 — P1 (module-8, module-9)
  → 병력 마커, 이동 애니메이션, 토글 UI
  → verify: 유닛 표시, 모드 전환

Session 5: 이펙트 — P2 (module-10)
  → 계절 조명, 전투/이벤트 파티클
  → verify: 시각 효과 확인
```
