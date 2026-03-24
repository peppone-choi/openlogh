'use client';

import { useRef } from 'react';
import { Html } from '@react-three/drei';
import * as THREE from 'three';

interface TroopMarkerProps {
    generalName: string;
    nationColor: string;
    from: [number, number, number];
    to: [number, number, number];
    progress: number;
}

export function TroopMarker({ generalName, nationColor, from, to, progress }: TroopMarkerProps) {
    const poleRef = useRef<THREE.Mesh>(null);
    const bannerRef = useRef<THREE.Mesh>(null);

    const px = from[0] + (to[0] - from[0]) * progress;
    const py = from[1] + (to[1] - from[1]) * progress + 1;
    const pz = from[2] + (to[2] - from[2]) * progress;

    const color = nationColor || '#ffffff';

    return (
        <group position={[px, py, pz]}>
            {/* Shadow on ground */}
            <mesh position={[0, -py + from[1] + 0.05, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <circleGeometry args={[0.3, 12]} />
                <meshBasicMaterial color="#000000" transparent opacity={0.25} />
            </mesh>

            {/* Pole */}
            <mesh ref={poleRef} position={[0, 0, 0]}>
                <cylinderGeometry args={[0.06, 0.06, 2, 6]} />
                <meshStandardMaterial color="#8B6914" />
            </mesh>

            {/* Banner */}
            <mesh ref={bannerRef} position={[0.35, 0.6, 0]}>
                <planeGeometry args={[0.7, 0.5]} />
                <meshStandardMaterial color={color} side={THREE.DoubleSide} />
            </mesh>

            {/* General name label */}
            <Html position={[0, 1.4, 0]} center distanceFactor={8} style={{ pointerEvents: 'none' }}>
                <div
                    style={{
                        fontSize: '10px',
                        color: '#ffffff',
                        background: 'rgba(0,0,0,0.6)',
                        padding: '1px 4px',
                        borderRadius: '2px',
                        whiteSpace: 'nowrap',
                        userSelect: 'none',
                    }}
                >
                    {generalName}
                </div>
            </Html>
        </group>
    );
}
