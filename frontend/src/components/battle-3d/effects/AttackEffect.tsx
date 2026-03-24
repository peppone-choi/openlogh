'use client';

import { useRef, useEffect } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

interface AttackEffectProps {
    from: [number, number, number];
    to: [number, number, number];
    type: 'normal' | 'critical' | 'fire' | 'ice' | 'lightning';
    active: boolean;
}

const TRAVEL_DURATION = 0.3; // seconds

const TYPE_CONFIG = {
    normal: { color: '#ffffff', emissive: '#888888', size: 0.15, geometry: 'sphere' as const },
    critical: { color: '#ff2222', emissive: '#ff4444', size: 0.28, geometry: 'sphere' as const },
    fire: { color: '#ff6600', emissive: '#ff3300', size: 0.22, geometry: 'sphere' as const },
    ice: { color: '#44aaff', emissive: '#0066cc', size: 0.2, geometry: 'icosahedron' as const },
    lightning: { color: '#ffee00', emissive: '#ffcc00', size: 0.12, geometry: 'cylinder' as const },
};

export function AttackEffect({ from, to, type, active }: AttackEffectProps) {
    const meshRef = useRef<THREE.Mesh>(null);
    const lightRef = useRef<THREE.PointLight>(null);
    const progressRef = useRef(0);
    const visibleRef = useRef(false);

    useEffect(() => {
        if (active) {
            progressRef.current = 0;
            visibleRef.current = true;
        }
    }, [active]);

    useFrame((_, delta) => {
        if (!meshRef.current) return;

        if (!visibleRef.current) {
            meshRef.current.visible = false;
            return;
        }

        progressRef.current += delta / TRAVEL_DURATION;

        if (progressRef.current >= 1) {
            visibleRef.current = false;
            meshRef.current.visible = false;
            return;
        }

        meshRef.current.visible = true;
        const t = Math.min(progressRef.current, 1);

        if (type === 'lightning') {
            // Lightning: position at midpoint, scale to connect from→to
            const mid: [number, number, number] = [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2];
            meshRef.current.position.set(mid[0], mid[1], mid[2]);
            const dist = Math.sqrt(
                Math.pow(to[0] - from[0], 2) + Math.pow(to[1] - from[1], 2) + Math.pow(to[2] - from[2], 2)
            );
            meshRef.current.scale.set(0.1, dist / 2, 0.1);
            // Point toward target
            meshRef.current.lookAt(to[0], to[1], to[2]);
            meshRef.current.rotateX(Math.PI / 2);
            // Fade out in last 30%
            const mat = meshRef.current.material as THREE.MeshStandardMaterial;
            mat.opacity = t < 0.7 ? 1 : 1 - (t - 0.7) / 0.3;
        } else {
            // Projectile: interpolate from → to
            const px = from[0] + (to[0] - from[0]) * t;
            const py = from[1] + (to[1] - from[1]) * t + Math.sin(t * Math.PI) * 0.3; // arc
            const pz = from[2] + (to[2] - from[2]) * t;
            meshRef.current.position.set(px, py, pz);
            meshRef.current.scale.setScalar(1);

            if (lightRef.current && (type === 'fire' || type === 'critical')) {
                lightRef.current.position.set(px, py, pz);
                lightRef.current.intensity = t < 0.8 ? 1.5 : 1.5 * (1 - (t - 0.8) / 0.2);
            }
        }
    });

    const cfg = TYPE_CONFIG[type];

    return (
        <group>
            {type === 'lightning' ? (
                <mesh ref={meshRef} visible={false}>
                    <cylinderGeometry args={[0.05, 0.05, 1, 4]} />
                    <meshStandardMaterial
                        color={cfg.color}
                        emissive={cfg.emissive}
                        emissiveIntensity={2}
                        transparent
                        opacity={1}
                    />
                </mesh>
            ) : type === 'ice' ? (
                <mesh ref={meshRef} visible={false}>
                    <icosahedronGeometry args={[cfg.size, 0]} />
                    <meshStandardMaterial
                        color={cfg.color}
                        emissive={cfg.emissive}
                        emissiveIntensity={1.5}
                        transparent
                        opacity={1}
                    />
                </mesh>
            ) : (
                <mesh ref={meshRef} visible={false}>
                    <sphereGeometry args={[cfg.size, 8, 8]} />
                    <meshStandardMaterial
                        color={cfg.color}
                        emissive={cfg.emissive}
                        emissiveIntensity={type === 'critical' ? 2 : 1}
                        transparent
                        opacity={1}
                    />
                </mesh>
            )}
            {(type === 'fire' || type === 'critical') && (
                <pointLight ref={lightRef} color={cfg.color} intensity={0} distance={4} />
            )}
        </group>
    );
}
