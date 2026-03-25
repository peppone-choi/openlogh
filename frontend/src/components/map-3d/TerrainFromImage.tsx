'use client';

import { useEffect } from 'react';
import { TextureLoader } from 'three';
import { useLoader } from '@react-three/fiber';
import { getMapBgUrl } from '@/lib/image';
import { WORLD_WIDTH, WORLD_DEPTH } from '@/lib/map3d-utils';
import type { MapSeason } from '@/lib/map-constants';
import type { RenderCity } from '@/components/game/map-canvas';

interface TerrainFromImageProps {
    mapCode: string;
    season: MapSeason;
    cities: RenderCity[];
    width?: number;
    depth?: number;
    segments?: number;
    onHeightMapReady?: (getHeight: (wx: number, wz: number) => number) => void;
}

export function TerrainFromImage({
    mapCode,
    season,
    width = WORLD_WIDTH,
    depth = WORLD_DEPTH,
    onHeightMapReady,
}: TerrainFromImageProps) {
    const url = getMapBgUrl(mapCode, season);
    const texture = useLoader(TextureLoader, url);

    // Flat terrain — height is always 0
    useEffect(() => {
        onHeightMapReady?.(() => 0);
    }, [onHeightMapReady]);

    return (
        <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
            <planeGeometry args={[width, depth]} />
            <meshStandardMaterial map={texture} />
        </mesh>
    );
}
