'use client';

import { useEffect, useMemo, useState } from 'react';
import * as THREE from 'three';
import { TextureLoader } from 'three';
import { useLoader } from '@react-three/fiber';
import { getMapBgUrl } from '@/lib/image';
import { buildHeightMap, flattenAroundCities, createHeightLookup, WORLD_WIDTH, WORLD_DEPTH } from '@/lib/map3d-utils';
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
    cities,
    width = WORLD_WIDTH,
    depth = WORLD_DEPTH,
    segments = 50,
    onHeightMapReady,
}: TerrainFromImageProps) {
    const url = getMapBgUrl(mapCode, season);
    const [heightMap, setHeightMap] = useState<Float32Array | null>(null);

    const segmentsZ = Math.round(segments * (depth / width));

    useEffect(() => {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.src = url;
        img.onload = () => {
            const canvas = document.createElement('canvas');
            canvas.width = segments + 1;
            canvas.height = segmentsZ + 1;
            const ctx = canvas.getContext('2d');
            if (!ctx) return;
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const hm = buildHeightMap(imageData, segments, segmentsZ);
            flattenAroundCities(hm, segments, segmentsZ, cities);
            setHeightMap(hm);
        };
    }, [url, segments, segmentsZ, cities]);

    useEffect(() => {
        if (!heightMap || !onHeightMapReady) return;
        onHeightMapReady(createHeightLookup(heightMap, segments, segmentsZ));
    }, [heightMap, segments, segmentsZ, onHeightMapReady]);

    const texture = useLoader(TextureLoader, url);

    const geometry = useMemo(() => {
        const geo = new THREE.PlaneGeometry(width, depth, segments, segmentsZ);
        const pos = geo.attributes.position;

        if (heightMap) {
            for (let i = 0; i < pos.count; i++) {
                const px = pos.getX(i);
                const py = pos.getY(i);
                const ix = Math.round((px / width + 0.5) * segments);
                const iz = Math.round((py / depth + 0.5) * segmentsZ);
                const hi = iz * (segments + 1) + ix;
                const h = heightMap[hi] ?? 0;
                pos.setXYZ(i, px, h, -py);
            }
        } else {
            for (let i = 0; i < pos.count; i++) {
                const px = pos.getX(i);
                const py = pos.getY(i);
                pos.setXYZ(i, px, 0, -py);
            }
        }

        pos.needsUpdate = true;
        geo.computeVertexNormals();
        return geo;
    }, [heightMap, width, depth, segments, segmentsZ]);

    return (
        <mesh geometry={geometry} receiveShadow>
            <meshStandardMaterial map={texture} />
        </mesh>
    );
}
