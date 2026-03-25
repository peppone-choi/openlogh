'use client';

import { useState, useMemo } from 'react';
import * as THREE from 'three';
import { useLoader } from '@react-three/fiber';
import { TextureLoader } from 'three';
import { getCityLevelIcon } from '@/lib/image';
import type { RenderCity } from '@/components/game/map-canvas';
import { getCityHeight } from '@/lib/map3d-utils';

// Phase 2: GLB 모델이 public/models/city-{level}.glb에 있으면 자동 사용
// const MODEL_PATH = (level: number) => `/models/cities/city-${level}.glb`;

interface CityModelProps {
    city: RenderCity;
    position: [number, number, number];
    onClick?: () => void;
}

/** CDN cast_{level}.gif를 3D Billboard Sprite로 표시 */
function CitySprite({ city }: { city: RenderCity }) {
    const url = getCityLevelIcon(city.level);
    const texture = useLoader(TextureLoader, url);

    const height = getCityHeight(city.level);
    const aspect = texture.image ? texture.image.width / texture.image.height : 1;
    const spriteWidth = height * aspect;

    return (
        <sprite position={[0, height / 2, 0]} scale={[spriteWidth, height, 1]}>
            <spriteMaterial map={texture} transparent alphaTest={0.1} />
        </sprite>
    );
}

/** 국가 색상 바닥 링 — 소속 표시 */
function NationRing({ color, scale }: { color: string; scale: number }) {
    const ringColor = useMemo(() => new THREE.Color(color), [color]);
    return (
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.02, 0]}>
            <ringGeometry args={[scale * 0.6, scale * 0.8, 16]} />
            <meshBasicMaterial color={ringColor} transparent opacity={0.7} side={THREE.DoubleSide} />
        </mesh>
    );
}

/** 수도 표시 — 금색 별 마커 */
function CapitalMarker() {
    return (
        <mesh position={[0, 0.05, 0]} rotation={[-Math.PI / 2, 0, 0]}>
            <circleGeometry args={[0.15, 5]} />
            <meshBasicMaterial color="#ffd700" />
        </mesh>
    );
}

export function CityModel({ city, position, onClick }: CityModelProps) {
    const [hovered, setHovered] = useState(false);
    const color = city.nationColor ?? '#888888';
    const s = hovered ? 1.15 : 1;
    const height = getCityHeight(city.level);

    return (
        <group
            position={position}
            scale={[s, s, s]}
            onClick={(e) => {
                e.stopPropagation();
                onClick?.();
            }}
            onPointerOver={() => setHovered(true)}
            onPointerOut={() => setHovered(false)}
        >
            {/* 국가 색상 바닥 링 */}
            <NationRing color={color} scale={height} />

            {/* 수도 마커 */}
            {city.isCapital && <CapitalMarker />}

            {/* CDN 아이콘 Billboard Sprite */}
            <CitySprite city={city} />
        </group>
    );
}
