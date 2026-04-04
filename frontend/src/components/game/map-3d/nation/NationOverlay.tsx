'use client';
// Design Ref: §3.4 NationOverlay — 국가 영역 색상 지형 오버레이
// 각 도시 위치에서 반경만큼 국가 색상을 투명 원형으로 표시
import { useMemo } from 'react';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight, WORLD_SCALE } from '@/lib/map-3d-utils';

interface NationOverlayProps {
  cities: RenderCity[];
  heightMap?: Float32Array;
  segments?: number;
}

/** 도시 레벨에 따른 영역 반지름 */
function getInfluenceRadius(level: number): number {
  if (level >= 8) return 28;
  if (level >= 7) return 24;
  if (level >= 6) return 20;
  if (level >= 5) return 16;
  return 12; // 거점 (Lv1-4)
}

export function NationOverlay({ cities, heightMap, segments = 128 }: NationOverlayProps) {
  const overlays = useMemo(() => {
    return cities
      .filter((c) => c.nationColor)
      .map((city) => {
        const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
        const pos = toWorld3d(city.x, city.y, h + 0.2); // 지형 바로 위
        const radius = getInfluenceRadius(city.level) * WORLD_SCALE;
        return { id: city.id, pos, radius, color: city.nationColor! };
      });
  }, [cities, heightMap, segments]);

  return (
    <group>
      {overlays.map(({ id, pos, radius, color }) => (
        <mesh
          key={id}
          position={[pos.x, pos.y, pos.z]}
          rotation={[-Math.PI / 2, 0, 0]}
        >
          <circleGeometry args={[radius, 32]} />
          <meshStandardMaterial
            color={color}
            transparent
            opacity={0.18}
            roughness={1}
            depthWrite={false}
            side={THREE.DoubleSide}
          />
        </mesh>
      ))}
    </group>
  );
}
