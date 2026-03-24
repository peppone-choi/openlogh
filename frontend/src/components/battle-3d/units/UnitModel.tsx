'use client';

import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

interface UnitModelProps {
    position: [number, number, number];
    color: string;
    crewType: number;
    isAttacking: boolean;
    isHit: boolean;
    opacity: number;
    scale?: number;
}

export function UnitModel({
    position,
    color,
    crewType: _crewType,
    isAttacking,
    isHit,
    opacity,
    scale = 1,
}: UnitModelProps) {
    const bodyRef = useRef<THREE.Mesh>(null);
    const headRef = useRef<THREE.Mesh>(null);
    const groupRef = useRef<THREE.Group>(null);
    const hitTimerRef = useRef(0);

    useFrame((_, delta) => {
        if (!bodyRef.current || !groupRef.current || !headRef.current) return;

        const bodyMat = bodyRef.current.material as THREE.MeshStandardMaterial;
        const headMat = headRef.current.material as THREE.MeshStandardMaterial;

        // Attack animation: oscillating forward lean
        if (isAttacking) {
            groupRef.current.rotation.x = Math.sin(Date.now() * 0.008) * 0.18;
        } else {
            groupRef.current.rotation.x = THREE.MathUtils.lerp(groupRef.current.rotation.x, 0, delta * 6);
        }

        // Hit animation: flash white + scale reduction
        if (isHit) {
            hitTimerRef.current = 0.35; // seconds to stay flashed
        }
        if (hitTimerRef.current > 0) {
            hitTimerRef.current -= delta;
            const flashT = Math.min(hitTimerRef.current / 0.35, 1);
            bodyMat.color.set(new THREE.Color(color).lerp(new THREE.Color('#ffffff'), flashT));
            headMat.color.set(new THREE.Color(color).lerp(new THREE.Color('#ffffff'), flashT));
            const hitScale = THREE.MathUtils.lerp(scale, scale * 0.88, flashT);
            groupRef.current.scale.setScalar(hitScale);
        } else {
            bodyMat.color.set(color);
            headMat.color.set(color);
            const targetScale = THREE.MathUtils.lerp(groupRef.current.scale.x, scale, delta * 8);
            groupRef.current.scale.setScalar(targetScale);
        }

        // Opacity
        bodyMat.opacity = opacity;
        headMat.opacity = opacity;
        bodyMat.transparent = opacity < 1;
        headMat.transparent = opacity < 1;
    });

    // Units face the enemy: attacker (negative X start) faces +X, defender faces -X.
    // Rotation is applied by UnitManager at group level; here we default to facing +Z = forward.
    // A simple cone on top makes it readable as a soldier silhouette.

    return (
        <group ref={groupRef} position={position} scale={scale}>
            {/* Body */}
            <mesh ref={bodyRef} position={[0, 0.6, 0]}>
                <boxGeometry args={[0.6, 1.2, 0.6]} />
                <meshStandardMaterial color={color} opacity={opacity} transparent={opacity < 1} />
            </mesh>
            {/* Head / helmet cone */}
            <mesh ref={headRef} position={[0, 1.45, 0]} rotation={[0, 0, 0]}>
                <coneGeometry args={[0.25, 0.4, 6]} />
                <meshStandardMaterial color={color} opacity={opacity} transparent={opacity < 1} />
            </mesh>
        </group>
    );
}
