// Design Ref: §3.2 HeightMapGenerator — 배경 이미지 색상 기반 높이맵 생성
import type { CityConst } from '@/types';

const MAP_W = 700;
const MAP_H = 500;

/**
 * 배경 PNG/JPG에서 픽셀 데이터를 추출
 * 이미지 로드 → 오프스크린 캔버스 → getImageData
 */
export async function loadImagePixels(imageUrl: string): Promise<{
  data: Uint8ClampedArray;
  width: number;
  height: number;
}> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d')!;
      ctx.drawImage(img, 0, 0);
      const imageData = ctx.getImageData(0, 0, img.width, img.height);
      resolve({ data: imageData.data, width: img.width, height: img.height });
    };
    img.onerror = reject;
    img.src = imageUrl;
  });
}

/**
 * RGB → 높이값 변환 (완만한 높이 차이)
 * 전체 범위를 0~15 정도로 억제 — 너무 극적이면 성곽 배치가 부자연스러움
 * 배경 이미지의 색상 특성:
 * - 파란색 계열 → 물 (낮음, -1~0)
 * - 밝은 녹색 → 평야 (낮음, 1~3)
 * - 짙은 녹색 → 숲/구릉 (중간, 4~7)
 * - 갈색/어두움 → 산악 (높음, 8~12)
 * - 밝은 색/흰색 → 고산 (최대, 10~15)
 */
function rgbToHeight(r: number, g: number, b: number): number {
  const brightness = (r + g + b) / 3;
  const saturation = Math.max(r, g, b) - Math.min(r, g, b);

  // 물 감지: 파란색 우세
  if (b > r + 20 && b > g + 10) {
    return -1 + (brightness / 255); // -1 ~ 0
  }

  // 물 감지: 어두운 파란/청록
  if (b > 80 && g > 60 && r < 80 && brightness < 100) {
    return -0.5;
  }

  // 밝은 녹색 → 평야
  if (g > r && g > b && brightness > 120) {
    return 1 + ((g - brightness) / 50) * 2; // 1~3
  }

  // 짙은 녹색 → 숲/구릉
  if (g > r && g > b && brightness <= 120) {
    const depth = 1 - brightness / 120;
    return 4 + depth * 3; // 4~7
  }

  // 갈색/적갈색 → 산악
  if (r > g && r > b) {
    const brownness = (r - g) / 255;
    return 6 + brownness * 6; // 6~12
  }

  // 어두운 색 → 높은 산
  if (brightness < 60) {
    return 8 + (1 - brightness / 60) * 5; // 8~13
  }

  // 밝은 색 → 고산/눈
  if (brightness > 200 && saturation < 40) {
    return 10 + (brightness - 200) / 55 * 5; // 10~15
  }

  // 기본: 밝기 반전 (어두울수록 약간 높음)
  return 2 + (1 - brightness / 255) * 8; // 2~10
}

/**
 * 배경 이미지 기반 높이맵 생성 (비동기)
 */
export async function generateHeightMapFromImage(
  imageUrl: string,
  cities: CityConst[],
  segments: number,
): Promise<Float32Array> {
  const { data, width, height } = await loadImagePixels(imageUrl);
  const size = (segments + 1) * (segments + 1);
  const heightMap = new Float32Array(size);

  // 1단계: 이미지 픽셀 → 높이
  for (let iz = 0; iz <= segments; iz++) {
    for (let ix = 0; ix <= segments; ix++) {
      const u = ix / segments; // 0~1
      const v = iz / segments;

      // 이미지 좌표 (바이리니어 샘플링)
      const imgX = Math.min(Math.floor(u * width), width - 1);
      const imgY = Math.min(Math.floor(v * height), height - 1);
      const pixelIdx = (imgY * width + imgX) * 4;

      const r = data[pixelIdx];
      const g = data[pixelIdx + 1];
      const b = data[pixelIdx + 2];

      heightMap[iz * (segments + 1) + ix] = rgbToHeight(r, g, b);
    }
  }

  // 2단계: 가우시안 블러로 부드럽게 (3x3 커널, 2패스)
  smoothHeightMap(heightMap, segments + 1, segments + 1);
  smoothHeightMap(heightMap, segments + 1, segments + 1);

  // 3단계: 행성 주변 평탄화 (성곽 배치용)
  for (const city of cities) {
    const radius = city.level >= 5 ? 20 : 14;
    flattenAroundCity(heightMap, city, radius, segments);
  }

  // 4단계: 행성 간 연결(도로)을 따라 약간 낮추기
  for (const city of cities) {
    for (const connId of city.connections) {
      const target = cities.find((c) => c.id === connId);
      if (target && target.id > city.id) {
        carveRoad(heightMap, city, target, segments);
      }
    }
  }

  // 5단계: 맵 가장자리 서서히 낮아짐
  applyEdgeFalloff(heightMap, segments);

  return heightMap;
}

/**
 * 폴백: 이미지 없이 region 기반 생성 (동기)
 */
export function generateHeightMapFallback(
  cities: CityConst[],
  segments: number,
): Float32Array {
  const REGION_BASE: Record<number, number> = {
    1: 4,   // 하북 — 평야~구릉
    2: 3,   // 은하 — 평야
    3: 10,  // 서북 — 산악
    4: 9,   // 서촉 — 고원/산악
    5: 8,   // 남중 — 산악/정글
    6: 2,   // 초 — 저지대/호수
    7: 1,   // 오월 — 해안/저지대
    8: 5,   // 동이 — 구릉
  };

  const size = (segments + 1) * (segments + 1);
  const heightMap = new Float32Array(size);

  for (let iz = 0; iz <= segments; iz++) {
    for (let ix = 0; ix <= segments; ix++) {
      const x2d = (ix / segments) * MAP_W;
      const y2d = (iz / segments) * MAP_H;

      let totalW = 0;
      let weightedH = 0;

      for (const city of cities) {
        const dx = x2d - city.x;
        const dy = y2d - city.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const influence = 130;
        if (dist < influence) {
          const w = Math.pow(1 - dist / influence, 2);
          weightedH += (REGION_BASE[city.region] ?? 5) * w;
          totalW += w;
        }
      }

      let h = totalW > 0 ? weightedH / totalW : 5;

      // 노이즈 (완만하게)
      h += noise(x2d, y2d, 80, 42) * 2;
      h += noise(x2d, y2d, 40, 17) * 1;

      heightMap[iz * (segments + 1) + ix] = Math.max(0, h);
    }
  }

  // 행성 평탄화 + 도로 + 가장자리
  for (const city of cities) {
    flattenAroundCity(heightMap, city, city.level >= 5 ? 20 : 14, segments);
  }
  for (const city of cities) {
    for (const connId of city.connections) {
      const target = cities.find((c) => c.id === connId);
      if (target && target.id > city.id) carveRoad(heightMap, city, target, segments);
    }
  }
  applyEdgeFalloff(heightMap, segments);

  return heightMap;
}

// --- 유틸리티 ---

function noise(x: number, z: number, scale: number, seed: number): number {
  const sx = x / scale;
  const sz = z / scale;
  const ix = Math.floor(sx);
  const iz = Math.floor(sz);
  const fx = sx - ix;
  const fz = sz - iz;
  const hash = (a: number, b: number) => {
    const n = Math.sin(a * 12.9898 + b * 78.233 + seed) * 43758.5453;
    return n - Math.floor(n);
  };
  const v00 = hash(ix, iz);
  const v10 = hash(ix + 1, iz);
  const v01 = hash(ix, iz + 1);
  const v11 = hash(ix + 1, iz + 1);
  return (v00 * (1 - fx) * (1 - fz) + v10 * fx * (1 - fz) + v01 * (1 - fx) * fz + v11 * fx * fz) * 2 - 1;
}

function smoothHeightMap(map: Float32Array, w: number, h: number) {
  const copy = new Float32Array(map);
  for (let z = 1; z < h - 1; z++) {
    for (let x = 1; x < w - 1; x++) {
      const idx = z * w + x;
      map[idx] =
        (copy[idx] * 4 +
          copy[idx - 1] + copy[idx + 1] +
          copy[idx - w] + copy[idx + w]) / 8;
    }
  }
}

function flattenAroundCity(map: Float32Array, city: CityConst, radius: number, segments: number) {
  const cols = segments + 1;
  const cx = Math.round((city.x / MAP_W) * segments);
  const cz = Math.round((city.y / MAP_H) * segments);
  const centerH = map[cz * cols + cx] ?? 5;
  const r = Math.round((radius / MAP_W) * segments);

  for (let dz = -r; dz <= r; dz++) {
    for (let dx = -r; dx <= r; dx++) {
      const iz = cz + dz;
      const ix = cx + dx;
      if (iz < 0 || iz >= cols || ix < 0 || ix >= cols) continue;
      const dist = Math.sqrt(dx * dx + dz * dz);
      if (dist > r) continue;
      const blend = dist / r;
      const idx = iz * cols + ix;
      // 스무스 스텝으로 부드럽게 블렌딩
      const t = blend * blend * (3 - 2 * blend);
      map[idx] = centerH * (1 - t) + map[idx] * t;
    }
  }
}

function carveRoad(map: Float32Array, from: CityConst, to: CityConst, segments: number) {
  const cols = segments + 1;
  const steps = 60;
  const roadWidth = 2; // 세그먼트 단위 폭

  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const x2d = from.x + (to.x - from.x) * t;
    const y2d = from.y + (to.y - from.y) * t;
    const ix = Math.round((x2d / MAP_W) * segments);
    const iz = Math.round((y2d / MAP_H) * segments);

    for (let dz = -roadWidth; dz <= roadWidth; dz++) {
      for (let dx = -roadWidth; dx <= roadWidth; dx++) {
        const jx = ix + dx;
        const jz = iz + dz;
        if (jx < 0 || jx >= cols || jz < 0 || jz >= cols) continue;
        const idx = jz * cols + jx;
        // 도로는 주변보다 살짝 낮게 (최대 30% 감소)
        map[idx] *= 0.85;
      }
    }
  }
}

function applyEdgeFalloff(map: Float32Array, segments: number) {
  const cols = segments + 1;
  const edgeDist = 8; // 세그먼트 단위
  for (let iz = 0; iz < cols; iz++) {
    for (let ix = 0; ix < cols; ix++) {
      const ex = Math.min(ix, segments - ix) / edgeDist;
      const ez = Math.min(iz, segments - iz) / edgeDist;
      const factor = Math.min(1, Math.min(ex, ez));
      // smoothstep
      const t = factor * factor * (3 - 2 * factor);
      map[iz * cols + ix] *= t;
    }
  }
}
