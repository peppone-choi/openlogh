import type { RenderCity } from '@/components/game/map-canvas';

// ─── 월드 크기 ───

export const WORLD_WIDTH = 70;
export const WORLD_DEPTH = 50;

// ─── 좌표 변환 ───

/** CityConst 2D 좌표 (700x500) → Three.js 월드 좌표 */
export function cityToWorld(
    x: number,
    y: number,
    getHeight?: (wx: number, wz: number) => number
): [number, number, number] {
    const wx = (x / 700 - 0.5) * WORLD_WIDTH;
    const wz = (y / 500 - 0.5) * WORLD_DEPTH;
    const wy = getHeight ? getHeight(wx, wz) + 0.5 : 0.5;
    return [wx, wy, wz];
}

/** 모든 도시 좌표를 한번에 변환 */
export function buildCityPositions(
    cities: RenderCity[],
    getHeight?: (wx: number, wz: number) => number
): Map<number, [number, number, number]> {
    const map = new Map<number, [number, number, number]>();
    for (const city of cities) {
        map.set(city.id, cityToWorld(city.x, city.y, getHeight));
    }
    return map;
}

// ─── 색상 → 높이 변환 ───

/** RGB 값을 지형 높이로 변환 */
export function colorToHeight(r: number, g: number, b: number): number {
    // 파란색 (수역): b 우세
    if (b > r + 30 && b > g + 30) return -0.2;

    // 짙은 녹색 (숲): g 높고 r 낮음
    if (g > 80 && r < 70 && b < 70) return 0.5;

    // 밝은 녹색 (평야): g 우세
    if (g > r && g > b) return 0.1;

    // 갈색/회색 (산악): r ≈ g, b 낮음
    if (r > 100 && Math.abs(r - g) < 40 && b < r - 20) {
        const brightness = (r + g) / 2;
        return 1.0 + (brightness / 255) * 3.0;
    }

    // 기본 (사막/건조)
    return 0.2;
}

/** 높이맵 배열 생성: 이미지 데이터에서 세그먼트 그리드의 높이값 계산 */
export function buildHeightMap(imageData: ImageData, segmentsX: number, segmentsZ: number): Float32Array {
    const { data, width, height } = imageData;
    const heightMap = new Float32Array((segmentsX + 1) * (segmentsZ + 1));

    for (let iz = 0; iz <= segmentsZ; iz++) {
        for (let ix = 0; ix <= segmentsX; ix++) {
            const px = Math.floor((ix / segmentsX) * (width - 1));
            const py = Math.floor((iz / segmentsZ) * (height - 1));
            const idx = (py * width + px) * 4;
            heightMap[iz * (segmentsX + 1) + ix] = colorToHeight(data[idx], data[idx + 1], data[idx + 2]);
        }
    }
    return heightMap;
}

/** 도시 레벨별 평탄화 반경 (세그먼트 단위) */
function getCityFlattenRadius(level: number): number {
    if (level <= 2) return 2; // 수/진: 소형
    if (level <= 4) return 3; // 관/이: 중형
    if (level <= 6) return 4; // 소/중도시
    return 5; // 대/특도시
}

/** 도시 주변 평탄화 — 반경 내 vertex를 부드럽게 낮추고 평탄하게 */
export function flattenAroundCities(
    heightMap: Float32Array,
    segmentsX: number,
    segmentsZ: number,
    cities: RenderCity[]
): void {
    for (const city of cities) {
        const cix = Math.round((city.x / 700) * segmentsX);
        const ciz = Math.round((city.y / 500) * segmentsZ);
        const radius = getCityFlattenRadius(city.level);

        // 도시 위치 높이: 산악이면 낮추고, 수역이면 올림 → 적정 범위로 클램프
        const centerIdx = ciz * (segmentsX + 1) + cix;
        const rawHeight = heightMap[centerIdx] ?? 0.3;
        const cityHeight = Math.max(Math.min(rawHeight, 1.0), 0.2); // 0.2~1.0 범위로 제한

        // 내부 영역: 완전 평탄화
        const innerRadius = Math.max(1, radius - 1);
        for (let dz = -radius; dz <= radius; dz++) {
            for (let dx = -radius; dx <= radius; dx++) {
                const ix = cix + dx;
                const iz = ciz + dz;
                if (ix < 0 || ix > segmentsX || iz < 0 || iz > segmentsZ) continue;
                const dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;

                const idx = iz * (segmentsX + 1) + ix;
                if (dist <= innerRadius) {
                    // 내부: 완전히 cityHeight로
                    heightMap[idx] = cityHeight;
                } else {
                    // 외곽: 기존 높이와 cityHeight 사이를 부드럽게 보간
                    const t = (dist - innerRadius) / (radius - innerRadius);
                    heightMap[idx] = cityHeight + (heightMap[idx] - cityHeight) * t;
                }
            }
        }
    }
}

/** 높이맵에서 월드 좌표의 높이를 보간 조회 */
export function createHeightLookup(
    heightMap: Float32Array,
    segmentsX: number,
    segmentsZ: number
): (wx: number, wz: number) => number {
    return (wx: number, wz: number) => {
        // 월드 좌표 → 0~1 정규화
        const nx = wx / WORLD_WIDTH + 0.5;
        const nz = wz / WORLD_DEPTH + 0.5;

        // 세그먼트 인덱스 (실수)
        const fx = nx * segmentsX;
        const fz = nz * segmentsZ;

        // 정수 인덱스 + 보간 가중치
        const ix = Math.floor(fx);
        const iz = Math.floor(fz);
        const tx = fx - ix;
        const tz = fz - iz;

        // 클램핑
        const ix0 = Math.max(0, Math.min(ix, segmentsX));
        const ix1 = Math.max(0, Math.min(ix + 1, segmentsX));
        const iz0 = Math.max(0, Math.min(iz, segmentsZ));
        const iz1 = Math.max(0, Math.min(iz + 1, segmentsZ));

        const w = segmentsX + 1;
        const h00 = heightMap[iz0 * w + ix0] ?? 0;
        const h10 = heightMap[iz0 * w + ix1] ?? 0;
        const h01 = heightMap[iz1 * w + ix0] ?? 0;
        const h11 = heightMap[iz1 * w + ix1] ?? 0;

        // 이중선형 보간
        const h0 = h00 * (1 - tx) + h10 * tx;
        const h1 = h01 * (1 - tx) + h11 * tx;
        return h0 * (1 - tz) + h1 * tz;
    };
}

// ─── 도시 모델 크기 ───

export function getCityScale(level: number): number {
    if (level <= 2) return 0.2;
    if (level <= 4) return 0.25;
    if (level <= 6) return 0.3;
    return 0.4;
}

export function getCityHeight(level: number): number {
    if (level <= 2) return 0.25;
    if (level <= 4) return 0.35;
    if (level <= 6) return 0.5;
    return 0.6;
}

// ─── 계절 조명 ───

export const SEASON_LIGHT_COLOR: Record<string, string> = {
    spring: '#ffffff',
    summer: '#fffbe6',
    fall: '#ffe4c4',
    winter: '#e6f0ff',
};

export const SEASON_AMBIENT_INTENSITY: Record<string, number> = {
    spring: 0.5,
    summer: 0.6,
    fall: 0.4,
    winter: 0.35,
};
