'use client';

import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import type { Diplomacy } from '@/types';
import { ImpactEffect } from '@/components/battle-3d/effects/ImpactEffect';

interface WarEffectsProps {
    cities: RenderCity[];
    cityPositions: Map<number, [number, number, number]>;
    diplomacy: Diplomacy[];
}

function WarRing({ position }: { position: [number, number, number] }) {
    const meshRef = useRef<THREE.Mesh>(null);

    useFrame((_, delta) => {
        if (!meshRef.current) return;
        meshRef.current.rotation.z += delta * 1.2;
        const mat = meshRef.current.material as THREE.MeshStandardMaterial;
        // Pulse opacity between 0.3 and 0.8
        mat.opacity = 0.3 + 0.25 * (1 + Math.sin(Date.now() * 0.003));
    });

    return (
        <mesh ref={meshRef} position={[position[0], position[1] + 0.15, position[2]]} rotation={[-Math.PI / 2, 0, 0]}>
            <torusGeometry args={[1.2, 0.15, 8, 32]} />
            <meshStandardMaterial
                color="#ff2200"
                emissive="#ff1100"
                emissiveIntensity={1.2}
                transparent
                opacity={0.5}
                side={THREE.DoubleSide}
            />
        </mesh>
    );
}

export function WarEffects({ cities, cityPositions, diplomacy }: WarEffectsProps) {
    // Collect all nation IDs involved in active wars
    const warNationIds = new Set<number>();
    for (const d of diplomacy) {
        if (d.stateCode === 'war' && !d.isDead) {
            warNationIds.add(d.srcNationId);
            warNationIds.add(d.destNationId);
        }
    }

    // RenderCity doesn't carry nationId, so use city.state > 0 as the proxy for
    // front-line / conflict status. When diplomacy has active wars we also include
    // those cities regardless of state so the ring is always visible.
    const hasActiveWar = warNationIds.size > 0;
    const affectedCities = cities.filter((c) => c.state > 0 || (hasActiveWar && c.nationColor !== null));

    if (warNationIds.size === 0 && affectedCities.length === 0) return null;

    return (
        <>
            {affectedCities.map((city) => {
                const pos = cityPositions.get(city.id);
                if (!pos) return null;
                const effectPos: [number, number, number] = [pos[0], pos[1] + 3, pos[2]];
                return (
                    <group key={city.id}>
                        <WarRing position={pos} />
                        <ImpactEffect position={effectPos} type="spark" active={true} />
                    </group>
                );
            })}
        </>
    );
}
