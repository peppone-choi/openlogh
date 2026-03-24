'use client';

import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import type { TerrainType } from '@/types/battle3d';
import { TERRAIN_COLORS } from './TerrainMaterials';

interface TerrainGeneratorProps {
    type: TerrainType;
    width?: number;
    depth?: number;
}

function MountainTerrain({ width, depth }: { width: number; depth: number }) {
    const geometry = useMemo(() => {
        const segments = 20;
        const geo = new THREE.PlaneGeometry(width, depth, segments, segments);
        const pos = geo.attributes.position;
        for (let i = 0; i < pos.count; i++) {
            const x = pos.getX(i);
            const y = pos.getY(i);
            const z = Math.sin(x * 0.3) * 1.5 + Math.sin(y * 0.4) * 1.2 + Math.sin((x + y) * 0.2) * 0.8;
            pos.setZ(i, z);
        }
        geo.computeVertexNormals();
        return geo;
    }, [width, depth]);

    return (
        <mesh geometry={geometry} rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
            <meshStandardMaterial color={TERRAIN_COLORS.mountain} />
        </mesh>
    );
}

function WaterAnimatedRiverTerrain({ width, depth }: { width: number; depth: number }) {
    const meshRef = useRef<THREE.Mesh>(null);

    useFrame(({ clock }) => {
        if (meshRef.current) {
            meshRef.current.position.z = Math.sin(clock.getElapsedTime() * 0.5) * 0.02;
        }
    });

    return (
        <>
            <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
                <planeGeometry args={[width, depth]} />
                <meshStandardMaterial color={TERRAIN_COLORS.plain} />
            </mesh>
            <mesh ref={meshRef} rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.05, 0]}>
                <planeGeometry args={[width * 0.2, depth]} />
                <meshStandardMaterial color={TERRAIN_COLORS.river} transparent opacity={0.75} />
            </mesh>
        </>
    );
}

export function TerrainGenerator({ type, width = 40, depth = 30 }: TerrainGeneratorProps) {
    if (type === 'mountain') {
        return <MountainTerrain width={width} depth={depth} />;
    }

    if (type === 'river') {
        return <WaterAnimatedRiverTerrain width={width} depth={depth} />;
    }

    const color = TERRAIN_COLORS[type] ?? TERRAIN_COLORS.plain;

    return (
        <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
            <planeGeometry args={[width, depth]} />
            <meshStandardMaterial color={color} />
        </mesh>
    );
}
