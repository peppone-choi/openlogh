'use client';
// 호버 시 행성 정보 툴팁 — 단 1개의 Html DOM만 사용 (성능)
import { Html } from '@react-three/drei';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight, WORLD_SCALE } from '@/lib/map-3d-utils';
import { CITY_LEVEL_NAMES } from '@/lib/game-utils';
import { getLocationConfig } from '../city/CastleLoader';

interface HoverTooltipProps {
  city: RenderCity | null;
  heightMap?: Float32Array;
  segments?: number;
}

export function HoverTooltip({ city, heightMap, segments = 64 }: HoverTooltipProps) {
  if (!city) return null;

  const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
  const pos = toWorld3d(city.x, city.y, h);
  const config = getLocationConfig(city.level);
  const yOffset = config.targetScale * WORLD_SCALE * 1.5 + WORLD_SCALE * 6;

  return (
    <Html
      position={[pos.x, pos.y + yOffset, pos.z]}
      center
      zIndexRange={[100, 0]}
      style={{ pointerEvents: 'none' }}
    >
      <div className="rounded-lg border border-zinc-600 bg-zinc-900/95 px-3 py-2 text-xs text-white shadow-xl backdrop-blur-sm min-w-[120px]">
        <div className="flex items-center gap-2 mb-1">
          {city.nationColor && (
            <span
              className="inline-block h-2.5 w-2.5 rounded-full"
              style={{ backgroundColor: city.nationColor }}
            />
          )}
          <span className="font-bold text-sm">{city.name}</span>
          <span className="text-zinc-400">
            {CITY_LEVEL_NAMES[city.level] ?? city.level}
          </span>
        </div>
        {city.nationName && (
          <div className="text-zinc-300">
            {city.nationName}
            {city.isCapital && ' (수도)'}
          </div>
        )}
      </div>
    </Html>
  );
}
