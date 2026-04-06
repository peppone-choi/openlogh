// 도로 PNG 픽셀 트레이싱 기반 경로 탐색
import type { CityConst } from '@/types';

/**
 * 도로 PNG에서 픽셀 데이터를 로드하고 도로 마스크 생성
 */
export async function loadRoadMask(roadImageUrl: string): Promise<{
  mask: Uint8Array;
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
      const { data, width, height } = imageData;

      // 도로 마스크: alpha > 30인 픽셀 = 도로
      const mask = new Uint8Array(width * height);
      for (let i = 0; i < width * height; i++) {
        mask[i] = data[i * 4 + 3] > 30 ? 1 : 0;
      }

      resolve({ mask, width, height });
    };
    img.onerror = reject;
    img.src = roadImageUrl;
  });
}

/**
 * A* 알고리즘으로 도로 픽셀 위에서 최단 경로 탐색
 * @returns 픽셀 좌표 배열 [{x, y}, ...]
 */
function aStarPixelPath(
  mask: Uint8Array,
  w: number,
  h: number,
  sx: number,
  sy: number,
  ex: number,
  ey: number,
): { x: number; y: number }[] | null {
  // 시작/끝 좌표를 가장 가까운 도로 픽셀로 스냅
  const snap = (px: number, py: number): [number, number] => {
    const cx = Math.round(px);
    const cy = Math.round(py);
    if (cx >= 0 && cx < w && cy >= 0 && cy < h && mask[cy * w + cx]) return [cx, cy];
    // 주변 탐색 (반경 확대)
    for (let r = 1; r <= 20; r++) {
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          const nx = cx + dx;
          const ny = cy + dy;
          if (nx >= 0 && nx < w && ny >= 0 && ny < h && mask[ny * w + nx]) {
            return [nx, ny];
          }
        }
      }
    }
    return [cx, cy]; // 폴백
  };

  const [startX, startY] = snap(sx, sy);
  const [endX, endY] = snap(ex, ey);

  // A* with 8방향 이동
  const key = (x: number, y: number) => y * w + x;
  const heuristic = (x: number, y: number) =>
    Math.abs(x - endX) + Math.abs(y - endY);

  const gScore = new Map<number, number>();
  const cameFrom = new Map<number, number>();
  const openSet = new Set<number>();
  const fScore = new Map<number, number>();

  const startKey = key(startX, startY);
  const endKey = key(endX, endY);
  gScore.set(startKey, 0);
  fScore.set(startKey, heuristic(startX, startY));
  openSet.add(startKey);

  const dirs = [
    [-1, 0], [1, 0], [0, -1], [0, 1],
    [-1, -1], [-1, 1], [1, -1], [1, 1],
  ];
  const costs = [1, 1, 1, 1, 1.414, 1.414, 1.414, 1.414];

  let iterations = 0;
  const MAX_ITERATIONS = 200000;

  while (openSet.size > 0 && iterations++ < MAX_ITERATIONS) {
    // 최소 fScore 노드
    let currentKey = -1;
    let minF = Infinity;
    for (const k of openSet) {
      const f = fScore.get(k) ?? Infinity;
      if (f < minF) { minF = f; currentKey = k; }
    }

    if (currentKey === endKey) {
      // 경로 복원
      const path: { x: number; y: number }[] = [];
      let node = endKey;
      while (node !== startKey) {
        path.unshift({ x: node % w, y: Math.floor(node / w) });
        node = cameFrom.get(node)!;
      }
      path.unshift({ x: startX, y: startY });
      return path;
    }

    openSet.delete(currentKey);
    const cx = currentKey % w;
    const cy = Math.floor(currentKey / w);
    const currentG = gScore.get(currentKey) ?? Infinity;

    for (let d = 0; d < 8; d++) {
      const nx = cx + dirs[d][0];
      const ny = cy + dirs[d][1];
      if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
      if (!mask[ny * w + nx]) continue;

      const nk = key(nx, ny);
      const tentG = currentG + costs[d];

      if (tentG < (gScore.get(nk) ?? Infinity)) {
        cameFrom.set(nk, currentKey);
        gScore.set(nk, tentG);
        fScore.set(nk, tentG + heuristic(nx, ny));
        openSet.add(nk);
      }
    }
  }

  return null; // 경로 없음
}

/**
 * Douglas-Peucker 알고리즘으로 경로 단순화
 */
function simplifyPath(
  points: { x: number; y: number }[],
  epsilon: number,
): { x: number; y: number }[] {
  if (points.length <= 2) return points;

  const first = points[0];
  const last = points[points.length - 1];

  let maxDist = 0;
  let maxIdx = 0;
  for (let i = 1; i < points.length - 1; i++) {
    const d = pointLineDistance(points[i], first, last);
    if (d > maxDist) { maxDist = d; maxIdx = i; }
  }

  if (maxDist > epsilon) {
    const left = simplifyPath(points.slice(0, maxIdx + 1), epsilon);
    const right = simplifyPath(points.slice(maxIdx), epsilon);
    return [...left.slice(0, -1), ...right];
  }

  return [first, last];
}

function pointLineDistance(
  p: { x: number; y: number },
  a: { x: number; y: number },
  b: { x: number; y: number },
): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) return Math.hypot(p.x - a.x, p.y - a.y);
  const t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq));
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
}

/** 캐시: 도로 마스크 */
let cachedMask: { mask: Uint8Array; width: number; height: number } | null = null;
let cachedUrl = '';

/** 캐시: 행성 간 경로 */
const pathCache = new Map<string, { x: number; y: number }[]>();

/**
 * 행성 간 도로 픽셀 경로를 계산 (캐시됨)
 * @returns 2D 맵 좌표 배열 (단순화됨)
 */
export async function findRoadPath(
  roadImageUrl: string,
  cities: CityConst[],
  fromId: number,
  toId: number,
): Promise<{ x: number; y: number }[]> {
  const cacheKey = `${fromId}-${toId}`;
  if (pathCache.has(cacheKey)) return pathCache.get(cacheKey)!;

  const cityMap = new Map(cities.map((c) => [c.id, c]));
  const fromCity = cityMap.get(fromId);
  const toCity = cityMap.get(toId);
  if (!fromCity || !toCity) return [];

  // 도로 마스크 로드 (캐시)
  if (!cachedMask || cachedUrl !== roadImageUrl) {
    cachedMask = await loadRoadMask(roadImageUrl);
    cachedUrl = roadImageUrl;
  }

  const { mask, width, height } = cachedMask;

  // 2D 맵 좌표 → 이미지 픽셀 좌표
  const scaleX = width / 700;
  const scaleY = height / 500;

  const pixelPath = aStarPixelPath(
    mask, width, height,
    fromCity.x * scaleX, fromCity.y * scaleY,
    toCity.x * scaleX, toCity.y * scaleY,
  );

  if (!pixelPath || pixelPath.length === 0) {
    // 폴백: 직선
    const fallback = [
      { x: fromCity.x, y: fromCity.y },
      { x: toCity.x, y: toCity.y },
    ];
    pathCache.set(cacheKey, fallback);
    return fallback;
  }

  // 픽셀 → 맵 좌표 변환 + 단순화
  const mapPath = pixelPath.map((p) => ({ x: p.x / scaleX, y: p.y / scaleY }));
  const simplified = simplifyPath(mapPath, 2); // epsilon=2px 허용 오차

  pathCache.set(cacheKey, simplified);
  return simplified;
}

/**
 * BFS 폴백 (행성 연결 기반 — 도로 이미지 없을 때)
 */
export function findCityPath(cities: CityConst[], fromId: number, toId: number): number[] {
  if (fromId === toId) return [fromId];
  const cityMap = new Map(cities.map((c) => [c.id, c]));
  const visited = new Set<number>();
  const parent = new Map<number, number>();
  const queue: number[] = [fromId];
  visited.add(fromId);

  while (queue.length > 0) {
    const current = queue.shift()!;
    const city = cityMap.get(current);
    if (!city) continue;
    for (const nid of city.connections) {
      if (visited.has(nid)) continue;
      visited.add(nid);
      parent.set(nid, current);
      if (nid === toId) {
        const path: number[] = [toId];
        let node = toId;
        while (parent.has(node)) { node = parent.get(node)!; path.unshift(node); }
        return path;
      }
      queue.push(nid);
    }
  }
  return [fromId, toId];
}

export function pathToPositions(cities: CityConst[], pathIds: number[]): { x: number; y: number }[] {
  const cityMap = new Map(cities.map((c) => [c.id, c]));
  return pathIds
    .map((id) => cityMap.get(id))
    .filter((c): c is CityConst => c != null)
    .map((c) => ({ x: c.x, y: c.y }));
}
