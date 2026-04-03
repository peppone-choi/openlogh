'use client';
// Design Ref: §3.1 Map3dScene — R3F Canvas 루트 + Suspense
import { Canvas } from '@react-three/fiber';
import { Suspense } from 'react';
import type { RenderCity } from '@/components/game/map-canvas';
import type { MapSeason } from '@/lib/map-constants';
import type { CityConst } from '@/types';
import { isMobileDevice } from '@/lib/map-3d-utils';
import { SceneSetup } from './SceneSetup';
import { TerrainMesh } from '../terrain/TerrainMesh';
import { CameraController } from '../camera/CameraController';
import { CityModel } from '../city/CityModel';
import { NationOverlay } from '../nation/NationOverlay';

interface Map3dSceneProps {
  mapCode: string;
  cities: CityConst[];
  renderCities: RenderCity[];
  season: MapSeason;
  onCityClick?: (cityId: number) => void;
  onCityHover?: (cityId: number | null) => void;
  compact?: boolean;
}

export function Map3dScene({
  mapCode,
  cities,
  renderCities,
  season,
  onCityClick,
  onCityHover,
  compact = false,
}: Map3dSceneProps) {
  const mobile = isMobileDevice();

  return (
    <div className="relative h-full w-full">
      <Canvas
        gl={{
          antialias: !mobile,
          powerPreference: 'high-performance',
        }}
        camera={{ position: [0, 300, 250], fov: 50, near: 1, far: 2000 }}
        dpr={mobile ? 1 : [1, 1.5]}
        frameloop="always"
        performance={{ min: 0.5 }}
      >
        <Suspense fallback={null}>
          <SceneSetup season={season} />
          <TerrainMesh cities={cities} mapCode={mapCode} season={season} />
          <NationOverlay cities={renderCities} />
          {renderCities.map((city) => (
            <CityModel
              key={city.id}
              city={city}
              onClick={onCityClick}
              onHover={onCityHover}
            />
          ))}
          <CameraController compact={compact} />
        </Suspense>
      </Canvas>
    </div>
  );
}
