'use client';

import { useRef, useEffect } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

interface ImpactEffectProps {
    position: [number, number, number];
    type: 'hit' | 'dust' | 'spark';
    active: boolean;
}

const IMPACT_DURATION = 0.5; // seconds

// Spark: 6 small dots expanding outward
const SPARK_COUNT = 6;
const SPARK_DIRECTIONS: [number, number, number][] = Array.from({ length: SPARK_COUNT }, (_, i) => {
    const angle = (i / SPARK_COUNT) * Math.PI * 2;
    return [Math.cos(angle), 0.4, Math.sin(angle)];
});

function HitRing({ position, progress }: { position: [number, number, number]; progress: number }) {
    const meshRef = useRef<THREE.Mesh>(null);
    useFrame(() => {
        if (!meshRef.current) return;
        const scale = 0.2 + progress * 2.5;
        meshRef.current.scale.setScalar(scale);
        const mat = meshRef.current.material as THREE.MeshStandardMaterial;
        mat.opacity = Math.max(0, 1 - progress);
    });
    return (
        <mesh ref={meshRef} position={position} rotation={[-Math.PI / 2, 0, 0]}>
            <ringGeometry args={[0.35, 0.5, 16]} />
            <meshStandardMaterial
                color="#ff2222"
                emissive="#ff0000"
                emissiveIntensity={1.5}
                transparent
                opacity={1}
                side={THREE.DoubleSide}
            />
        </mesh>
    );
}

function DustCloud({ position, progress }: { position: [number, number, number]; progress: number }) {
    const meshRef = useRef<THREE.Mesh>(null);
    useFrame(() => {
        if (!meshRef.current) return;
        const scale = 0.3 + progress * 2.0;
        meshRef.current.scale.setScalar(scale);
        const mat = meshRef.current.material as THREE.MeshStandardMaterial;
        mat.opacity = Math.max(0, 0.55 * (1 - progress));
    });
    return (
        <mesh ref={meshRef} position={[position[0], position[1] + 0.3, position[2]]}>
            <sphereGeometry args={[0.6, 8, 8]} />
            <meshStandardMaterial color="#d4b483" transparent opacity={0.55} />
        </mesh>
    );
}

function SparkDots({ position, progress }: { position: [number, number, number]; progress: number }) {
    return (
        <>
            {SPARK_DIRECTIONS.map((dir, i) => {
                const dist = progress * 1.8;
                const px = position[0] + dir[0] * dist;
                const py = position[1] + dir[1] * dist;
                const pz = position[2] + dir[2] * dist;
                const opacity = Math.max(0, 1 - progress);
                const scale = (1 - progress * 0.7) * 0.12;
                return (
                    <mesh key={i} position={[px, py, pz]}>
                        <sphereGeometry args={[scale, 4, 4]} />
                        <meshStandardMaterial
                            color="#ffffff"
                            emissive="#ffffff"
                            emissiveIntensity={2}
                            transparent
                            opacity={opacity}
                        />
                    </mesh>
                );
            })}
        </>
    );
}

export function ImpactEffect({ position, type, active }: ImpactEffectProps) {
    const progressRef = useRef(0);
    const activeRef = useRef(false);
    const renderCountRef = useRef(0);

    useEffect(() => {
        if (active) {
            progressRef.current = 0;
            activeRef.current = true;
        }
    }, [active]);

    useFrame((_, delta) => {
        if (!activeRef.current) return;
        progressRef.current += delta / IMPACT_DURATION;
        if (progressRef.current >= 1) {
            progressRef.current = 1;
            activeRef.current = false;
        }
        renderCountRef.current += 1;
    });

    if (!activeRef.current && progressRef.current >= 1) return null;
    if (!active && progressRef.current === 0) return null;

    const progress = progressRef.current;

    if (type === 'hit') return <HitRing position={position} progress={progress} />;
    if (type === 'dust') return <DustCloud position={position} progress={progress} />;
    return <SparkDots position={position} progress={progress} />;
}
