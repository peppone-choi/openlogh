// 도시 연결 그래프 기반 최단 경로 탐색 (BFS)
import type { CityConst } from '@/types';

/**
 * BFS로 출발 도시 → 도착 도시 최단 경로 탐색
 * @returns 경유 도시 ID 배열 (출발 포함, 도착 포함) / 경로 없으면 [from, to]
 */
export function findCityPath(
  cities: CityConst[],
  fromId: number,
  toId: number,
): number[] {
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

    for (const neighborId of city.connections) {
      if (visited.has(neighborId)) continue;
      visited.add(neighborId);
      parent.set(neighborId, current);

      if (neighborId === toId) {
        // 경로 복원
        const path: number[] = [toId];
        let node = toId;
        while (parent.has(node)) {
          node = parent.get(node)!;
          path.unshift(node);
        }
        return path;
      }

      queue.push(neighborId);
    }
  }

  // 경로 없음 — 직선 폴백
  return [fromId, toId];
}

/**
 * 경로 ID → 2D 좌표 배열로 변환
 */
export function pathToPositions(
  cities: CityConst[],
  pathIds: number[],
): { x: number; y: number }[] {
  const cityMap = new Map(cities.map((c) => [c.id, c]));
  return pathIds
    .map((id) => cityMap.get(id))
    .filter((c): c is CityConst => c != null)
    .map((c) => ({ x: c.x, y: c.y }));
}
