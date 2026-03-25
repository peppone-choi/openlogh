'use client';

import { Suspense, useState, useCallback } from 'react';
import { Canvas } from '@react-three/fiber';
import type { MapSeason } from '@/lib/map-constants';
import type { RenderCity } from '@/components/game/map-canvas';
import type { General, Nation, Diplomacy, CityConst } from '@/types';
import { SEASON_LIGHT_COLOR, SEASON_AMBIENT_INTENSITY, buildCityPositions } from '@/lib/map3d-utils';
import { CameraController } from '@/components/battle-3d/camera/CameraController';
import { TerrainFromImage } from './TerrainFromImage';
import { RoadOverlay } from './RoadOverlay';
import { CityInteraction } from './CityInteraction';
import { NationTerritory } from './NationTerritory';
import { SeasonalAtmosphere } from './SeasonalAtmosphere';
import { TroopMarkers } from './TroopMarkers';
import { WarEffects } from './WarEffects';
import { Minimap } from './Minimap';

interface Map3DSceneProps {
    mapCode: string;
    season: MapSeason;
    cities: RenderCity[];
    generals?: General[];
    nations?: Nation[];
    diplomacy?: Diplomacy[];
    mapData?: { cities: CityConst[] };
    onCityClick?: (cityId: number) => void;
    className?: string;
}

export function Map3DScene({
    mapCode,
    season,
    cities,
    generals,
    nations,
    diplomacy,
    mapData,
    onCityClick,
    className,
}: Map3DSceneProps) {
    const [getHeight, setGetHeight] = useState<((wx: number, wz: number) => number) | null>(null);

    const handleHeightMapReady = useCallback((fn: (wx: number, wz: number) => number) => {
        // Wrap in a function so useState stores the function itself, not its return value
        setGetHeight(() => fn);
    }, []);

    const positions = getHeight ? buildCityPositions(cities, getHeight) : null;

    const lightColor = SEASON_LIGHT_COLOR[season] ?? '#ffffff';
    const ambientIntensity = SEASON_AMBIENT_INTENSITY[season] ?? 0.5;

    return (
        <div className={className} style={{ minHeight: '400px', position: 'relative' }}>
            <Canvas shadows dpr={[1, 2]} camera={{ position: [0, 25, 30], fov: 50 }}>
                <SeasonalAtmosphere season={season} />

                <ambientLight color={lightColor} intensity={ambientIntensity} />
                <directionalLight
                    color={lightColor}
                    intensity={1.2}
                    position={[20, 40, 20]}
                    castShadow
                    shadow-mapSize={[2048, 2048]}
                    shadow-camera-left={-50}
                    shadow-camera-right={50}
                    shadow-camera-top={40}
                    shadow-camera-bottom={-40}
                    shadow-camera-near={0.1}
                    shadow-camera-far={120}
                />

                <Suspense fallback={null}>
                    <TerrainFromImage
                        mapCode={mapCode}
                        season={season}
                        cities={cities}
                        onHeightMapReady={handleHeightMapReady}
                    />
                    <RoadOverlay mapCode={mapCode} />
                    {positions && <NationTerritory cities={cities} cityPositions={positions} />}
                    {positions && <CityInteraction cities={cities} positions={positions} onCityClick={onCityClick} />}
                    {positions && generals && generals.length > 0 && (
                        <TroopMarkers
                            generals={generals}
                            cityPositions={positions}
                            nations={nations ?? []}
                            mapData={mapData}
                        />
                    )}
                    {positions && diplomacy && diplomacy.length > 0 && (
                        <WarEffects cities={cities} cityPositions={positions} diplomacy={diplomacy} />
                    )}
                </Suspense>

                <CameraController mode="3d" />
            </Canvas>
            <Minimap mapCode={mapCode} season={season} cities={cities} />
        </div>
    );
}
