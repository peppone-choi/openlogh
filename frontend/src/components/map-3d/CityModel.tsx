'use client';

import { useState } from 'react';
import type { RenderCity } from '@/components/game/map-canvas';
import { getCityScale, getCityHeight } from '@/lib/map3d-utils';

interface CityModelProps {
    city: RenderCity;
    position: [number, number, number];
    onClick?: () => void;
}

export function CityModel({ city, position, onClick }: CityModelProps) {
    const [hovered, setHovered] = useState(false);

    const scale = getCityScale(city.level);
    const height = getCityHeight(city.level);
    const color = city.nationColor ?? '#888888';
    const wallColor = city.nationColor ? `color-mix(in srgb, ${city.nationColor} 60%, #000000)` : '#555555';

    const bodyW = scale;
    const bodyH = height;
    const bodyD = scale;

    const showTower = city.level >= 7 || city.isCapital;
    const showFlag = city.isCapital;

    return (
        <group
            position={position}
            scale={hovered ? 1.1 : 1.0}
            onClick={(e) => {
                e.stopPropagation();
                onClick?.();
            }}
            onPointerOver={(e) => {
                e.stopPropagation();
                setHovered(true);
            }}
            onPointerOut={(e) => {
                e.stopPropagation();
                setHovered(false);
            }}
        >
            {/* Outer walls — slightly larger box at base */}
            <mesh position={[0, bodyH * 0.15, 0]}>
                <boxGeometry args={[bodyW * 1.2, bodyH * 0.3, bodyD * 1.2]} />
                <meshStandardMaterial color={wallColor} wireframe={false} />
            </mesh>

            {/* Main body */}
            <mesh castShadow position={[0, bodyH * 0.5, 0]}>
                <boxGeometry args={[bodyW, bodyH, bodyD]} />
                <meshStandardMaterial color={color} />
            </mesh>

            {/* Tower (high-level cities or capital) */}
            {showTower && (
                <mesh position={[0, bodyH + scale * 0.4, 0]}>
                    <coneGeometry args={[scale * 0.35, scale * 0.8, 6]} />
                    <meshStandardMaterial color={color} />
                </mesh>
            )}

            {/* Flag pole + flag plane (capital only) */}
            {showFlag && (
                <>
                    {/* Pole */}
                    <mesh position={[bodyW * 0.4, bodyH + scale * 0.9, 0]}>
                        <cylinderGeometry args={[0.04, 0.04, scale * 0.8, 6]} />
                        <meshStandardMaterial color="#cccccc" />
                    </mesh>
                    {/* Flag plane */}
                    <mesh position={[bodyW * 0.4 + scale * 0.2, bodyH + scale * 1.15, 0]}>
                        <planeGeometry args={[scale * 0.4, scale * 0.25]} />
                        <meshStandardMaterial color={color} side={2} />
                    </mesh>
                </>
            )}
        </group>
    );
}
