// @ts-nocheck
/// <reference types="@react-three/fiber" />
'use client';

import { useRef, useState, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { GRID_CONSTANTS } from './SpaceEnvironment';

// ─── Beam attack effect (laser line with glow) ─────────────────────────────
export function BeamEffect({
    fromGX,
    fromGY,
    toGX,
    toGY,
    onComplete,
}: {
    fromGX: number;
    fromGY: number;
    toGX: number;
    toGY: number;
    onComplete: () => void;
}) {
    const groupRef = useRef<THREE.Group>(null);
    const progress = useRef(0);
    const [opacity, setOpacity] = useState(1);

    const [fx, , fz] = GRID_CONSTANTS.gridToWorld(fromGX, fromGY);
    const [tx, , tz] = GRID_CONSTANTS.gridToWorld(toGX, toGY);

    const geometry = useMemo(() => {
        const points = [new THREE.Vector3(fx, 0.5, fz), new THREE.Vector3(tx, 0.5, tz)];
        return new THREE.BufferGeometry().setFromPoints(points);
    }, [fx, fz, tx, tz]);

    useFrame((_, delta) => {
        progress.current += delta * 3;
        if (progress.current > 1) {
            setOpacity(Math.max(0, 1 - (progress.current - 1) * 4));
            if (progress.current > 1.5) {
                onComplete();
            }
        }
    });

    return (
        <group ref={groupRef}>
            {/* Main beam */}
            <line geometry={geometry}>
                <lineBasicMaterial color="#FFE066" transparent opacity={opacity} linewidth={3} />
            </line>
            {/* Glow beam (wider, dimmer) */}
            <line geometry={geometry}>
                <lineBasicMaterial color="#FFD700" transparent opacity={opacity * 0.4} linewidth={6} />
            </line>
            {/* Impact point glow */}
            <pointLight position={[tx, 0.5, tz]} color="#FFD700" intensity={opacity * 3} distance={3} />
        </group>
    );
}

// ─── Gun attack effect (projectile trajectory) ─────────────────────────────
export function GunEffect({
    fromGX,
    fromGY,
    toGX,
    toGY,
    onComplete,
}: {
    fromGX: number;
    fromGY: number;
    toGX: number;
    toGY: number;
    onComplete: () => void;
}) {
    const meshRef = useRef<THREE.Mesh>(null);
    const progress = useRef(0);

    const [fx, , fz] = GRID_CONSTANTS.gridToWorld(fromGX, fromGY);
    const [tx, , tz] = GRID_CONSTANTS.gridToWorld(toGX, toGY);

    useFrame((_, delta) => {
        progress.current += delta * 4;
        if (meshRef.current) {
            const t = Math.min(progress.current, 1);
            meshRef.current.position.x = fx + (tx - fx) * t;
            meshRef.current.position.z = fz + (tz - fz) * t;
            meshRef.current.position.y = 0.5 + Math.sin(t * Math.PI) * 0.5; // arc
        }
        if (progress.current > 1.2) {
            onComplete();
        }
    });

    return (
        <group>
            {/* Projectile */}
            <mesh ref={meshRef as React.RefObject<THREE.Mesh>} position={[fx, 0.5, fz]}>
                <sphereGeometry args={[0.08, 6, 6]} />
                <meshBasicMaterial color="#FF8822" />
            </mesh>
            {/* Trail particles - simplified as a line */}
            {progress.current < 1 && (
                <line
                    geometry={new THREE.BufferGeometry().setFromPoints([
                        new THREE.Vector3(fx, 0.5, fz),
                        new THREE.Vector3(
                            fx + (tx - fx) * Math.min(progress.current, 1),
                            0.5,
                            fz + (tz - fz) * Math.min(progress.current, 1)
                        ),
                    ])}
                >
                    <lineBasicMaterial color="#FF8822" transparent opacity={0.5} />
                </line>
            )}
        </group>
    );
}

// ─── Explosion effect (expanding sphere burst) ──────────────────────────────
export function ExplosionEffect({
    gridX,
    gridY,
    onComplete,
}: {
    gridX: number;
    gridY: number;
    onComplete: () => void;
}) {
    const meshRef = useRef<THREE.Mesh>(null);
    const lightRef = useRef<THREE.PointLight>(null);
    const progress = useRef(0);
    const [wx, , wz] = GRID_CONSTANTS.gridToWorld(gridX, gridY);

    useFrame((_, delta) => {
        progress.current += delta * 2.5;
        const t = progress.current;

        if (meshRef.current) {
            const scale = t * 1.5;
            meshRef.current.scale.setScalar(scale);
            const mat = meshRef.current.material as THREE.MeshBasicMaterial;
            mat.opacity = Math.max(0, 1 - t);
        }
        if (lightRef.current) {
            lightRef.current.intensity = Math.max(0, 5 * (1 - t));
        }
        if (t > 1.2) {
            onComplete();
        }
    });

    return (
        <group position={[wx, 0.5, wz]}>
            {/* Expanding sphere */}
            <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                <sphereGeometry args={[0.3, 12, 12]} />
                <meshBasicMaterial color="#ff4400" transparent opacity={1} />
            </mesh>
            {/* Flash light */}
            <pointLight
                ref={lightRef as React.RefObject<THREE.PointLight>}
                color="#ff6622"
                intensity={5}
                distance={6}
            />
            {/* Debris ring */}
            <DebrisRing progress={progress} />
        </group>
    );
}

function DebrisRing({ progress }: { progress: React.MutableRefObject<number> }) {
    const particles = useMemo(() => {
        const count = 8;
        const items: { angle: number; speed: number; size: number }[] = [];
        for (let i = 0; i < count; i++) {
            items.push({
                angle: (i / count) * Math.PI * 2,
                speed: 0.8 + Math.random() * 0.6,
                size: 0.03 + Math.random() * 0.04,
            });
        }
        return items;
    }, []);

    const groupRef = useRef<THREE.Group>(null);

    useFrame(() => {
        if (!groupRef.current) return;
        const t = progress.current;
        groupRef.current.children.forEach((child, i) => {
            const p = particles[i];
            const r = t * p.speed * 1.5;
            child.position.x = Math.cos(p.angle) * r;
            child.position.z = Math.sin(p.angle) * r;
            child.position.y = Math.sin(t * 3 + p.angle) * 0.2;
            child.scale.setScalar(Math.max(0, 1 - t * 0.8));
        });
    });

    return (
        <group ref={groupRef}>
            {particles.map((p, i) => (
                <mesh key={i}>
                    <boxGeometry args={[p.size, p.size, p.size]} />
                    <meshBasicMaterial color="#ff6633" />
                </mesh>
            ))}
        </group>
    );
}

// ─── Shield effect (semi-transparent sphere) ────────────────────────────────
export function ShieldEffect({ gridX, gridY, color }: { gridX: number; gridY: number; color: string }) {
    const meshRef = useRef<THREE.Mesh>(null);
    const [wx, , wz] = GRID_CONSTANTS.gridToWorld(gridX, gridY);

    useFrame(() => {
        if (meshRef.current) {
            const mat = meshRef.current.material as THREE.MeshStandardMaterial;
            mat.opacity = 0.15 + Math.sin(Date.now() * 0.003) * 0.05;
        }
    });

    return (
        <mesh ref={meshRef as React.RefObject<THREE.Mesh>} position={[wx, 0.5, wz]}>
            <sphereGeometry args={[1.0, 16, 16]} />
            <meshStandardMaterial
                color={color}
                transparent
                opacity={0.15}
                side={THREE.BackSide}
                emissive={color}
                emissiveIntensity={0.3}
            />
        </mesh>
    );
}

// ─── Effects manager ────────────────────────────────────────────────────────
export interface BattleEffect {
    id: string;
    type: 'beam' | 'gun' | 'explosion' | 'shield';
    fromGX?: number;
    fromGY?: number;
    toGX?: number;
    toGY?: number;
    gridX?: number;
    gridY?: number;
    color?: string;
}

export function EffectsManager({
    effects,
    onEffectComplete,
}: {
    effects: BattleEffect[];
    onEffectComplete: (id: string) => void;
}) {
    return (
        <group>
            {effects.map((effect) => {
                switch (effect.type) {
                    case 'beam':
                        return (
                            <BeamEffect
                                key={effect.id}
                                fromGX={effect.fromGX ?? 0}
                                fromGY={effect.fromGY ?? 0}
                                toGX={effect.toGX ?? 0}
                                toGY={effect.toGY ?? 0}
                                onComplete={() => onEffectComplete(effect.id)}
                            />
                        );
                    case 'gun':
                        return (
                            <GunEffect
                                key={effect.id}
                                fromGX={effect.fromGX ?? 0}
                                fromGY={effect.fromGY ?? 0}
                                toGX={effect.toGX ?? 0}
                                toGY={effect.toGY ?? 0}
                                onComplete={() => onEffectComplete(effect.id)}
                            />
                        );
                    case 'explosion':
                        return (
                            <ExplosionEffect
                                key={effect.id}
                                gridX={effect.gridX ?? 0}
                                gridY={effect.gridY ?? 0}
                                onComplete={() => onEffectComplete(effect.id)}
                            />
                        );
                    case 'shield':
                        return (
                            <ShieldEffect
                                key={effect.id}
                                gridX={effect.gridX ?? 0}
                                gridY={effect.gridY ?? 0}
                                color={effect.color ?? '#4488FF'}
                            />
                        );
                    default:
                        return null;
                }
            })}
        </group>
    );
}
