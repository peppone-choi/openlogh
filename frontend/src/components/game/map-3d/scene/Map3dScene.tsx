'use client';
// Design Ref: §3.1 Map3dScene — R3F Canvas 루트 + Suspense
import { Canvas } from '@react-three/fiber';
import { Suspense, useCallback, useMemo, useState } from 'react';
import type { RenderCity } from '@/components/game/map-canvas';
import type { MapSeason } from '@/lib/map-constants';
import type { CityConst } from '@/types';
import { isMobileDevice } from '@/lib/map-3d-utils';
import { SceneSetup } from './SceneSetup';
import { TerrainMesh } from '../terrain/TerrainMesh';
import { CameraController } from '../camera/CameraController';
import { CityModel } from '../city/CityModel';
import { NationOverlay } from '../nation/NationOverlay';
import { RoadOverlay } from '../terrain/RoadOverlay';
import { HoverTooltip } from '../interaction/HoverTooltip';
import { UnitMarkers3d } from '../units/UnitMarkers3d';
import type { UnitMarker } from '@/components/game/unit-markers';

interface Map3dSceneProps {
  mapCode: string;
  cities: CityConst[];
  renderCities: RenderCity[];
  season: MapSeason;
  onCityClick?: (cityId: number) => void;
  onCityHover?: (cityId: number | null) => void;
  unitMarkers?: UnitMarker[];
  onUnitClick?: (generalId: number) => void;
  compact?: boolean;
}

export function Map3dScene({
  mapCode,
  cities,
  renderCities,
  season,
  onCityClick,
  onCityHover,
  unitMarkers,
  onUnitClick,
  compact = false,
}: Map3dSceneProps) {
  const mobile = isMobileDevice();
  const [hoveredCityId, setHoveredCityId] = useState<number | null>(null);

  const cityMap = useMemo(
    () => new Map(renderCities.map((c) => [c.id, c])),
    [renderCities],
  );
  const hoveredCity = hoveredCityId != null ? cityMap.get(hoveredCityId) ?? null : null;

  const handleHover = useCallback(
    (cityId: number | null) => {
      setHoveredCityId(cityId);
      onCityHover?.(cityId);
    },
    [onCityHover],
  );

  return (
    <div className="relative h-full w-full" style={{ minHeight: compact ? 200 : 500 }}>
      <Canvas
        gl={{
          antialias: !mobile,
          powerPreference: 'high-performance',
          alpha: false,
          stencil: false,
          depth: true,
          failIfMajorPerformanceCaveat: false,
        }}
        camera={{ position: [0, 900, 750], fov: 45, near: 1, far: 8000 }}
        dpr={mobile ? 1 : [1, 1.5]}
        frameloop="always"
        performance={{ min: 0.5 }}
      >
        <Suspense fallback={null}>
          <SceneSetup season={season} />
          <TerrainMesh cities={cities} mapCode={mapCode} season={season} />
          <RoadOverlay mapCode={mapCode} />
          <NationOverlay cities={renderCities} />
          {renderCities.map((city) => (
            <CityModel
              key={city.id}
              city={city}
              onClick={onCityClick}
              onHover={handleHover}
            />
          ))}
          {unitMarkers && unitMarkers.length > 0 && (
            <UnitMarkers3d markers={unitMarkers} cities={cities} mapCode={mapCode} onMarkerClick={onUnitClick} />
          )}
          <HoverTooltip city={hoveredCity} />
          <CameraController compact={compact} />
        </Suspense>
      </Canvas>
    </div>
  );
}
