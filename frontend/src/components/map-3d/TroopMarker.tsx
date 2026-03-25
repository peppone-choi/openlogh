'use client';

import { useRef } from 'react';
import { Html } from '@react-three/drei';
import * as THREE from 'three';

interface TroopMarkerProps {
    generalName: string;
    nationColor: string;
    position: [number, number, number];
    direction: [number, number, number];
    crew: number;
    isEnemy?: boolean;
}

function crewBrightness(crew: number): string {
    if (crew > 2000) return '1';
    if (crew >= 500) return '0.75';
    return '0.45';
}

function formatCrew(crew: number): string {
    return crew.toLocaleString('ko-KR');
}

/** Compute the tilt angle (radians) from direction vector. Tilts along XZ plane. */
function directionTiltAngle(dir: [number, number, number]): number {
    const len = Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2]);
    if (len < 0.001) return 0;
    // Tilt up to ~15 degrees in the direction of travel
    return Math.atan2(dir[0] / len, 1) * 0.25;
}

export function TroopMarker({
    generalName,
    nationColor,
    position,
    direction,
    crew,
    isEnemy = false,
}: TroopMarkerProps) {
    const poleRef = useRef<THREE.Mesh>(null);
    const bannerRef = useRef<THREE.Mesh>(null);

    const [px, py, pz] = position;
    const color = nationColor || '#ffffff';
    const brightness = crewBrightness(crew);
    const tiltAngle = directionTiltAngle(direction);

    // Arrow direction: rotate cone to point in movement direction (XZ plane)
    const arrowAngle = Math.atan2(direction[0], direction[2]);

    // Enemy outline color
    const outlineColor = isEnemy ? '#ff2222' : '#333333';

    return (
        <group position={[px, py, pz]}>
            {/* Shadow on ground */}
            <mesh position={[0, -py + 0.05, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <circleGeometry args={[0.3, 12]} />
                <meshBasicMaterial color="#000000" transparent opacity={0.25} />
            </mesh>

            {/* Pole — slight tilt in movement direction */}
            <group rotation={[0, 0, tiltAngle]}>
                <mesh ref={poleRef} position={[0, 0, 0]}>
                    <cylinderGeometry args={[0.06, 0.06, 2, 6]} />
                    <meshStandardMaterial color="#8B6914" />
                </mesh>

                {/* Banner */}
                <mesh ref={bannerRef} position={[0.35, 0.6, 0]} renderOrder={isEnemy ? 1 : 0}>
                    <planeGeometry args={[0.7, 0.5]} />
                    <meshStandardMaterial
                        color={color}
                        side={THREE.DoubleSide}
                        opacity={parseFloat(brightness)}
                        transparent={parseFloat(brightness) < 1}
                    />
                </mesh>

                {/* Enemy red border ring */}
                {isEnemy && (
                    <mesh position={[0.35, 0.6, -0.01]}>
                        <planeGeometry args={[0.82, 0.62]} />
                        <meshBasicMaterial color={outlineColor} side={THREE.DoubleSide} />
                    </mesh>
                )}
            </group>

            {/* Directional arrow — small cone pointing toward destination */}
            <group rotation={[0, arrowAngle, 0]} position={[0, -0.8, 0]}>
                <mesh rotation={[Math.PI / 2, 0, 0]}>
                    <coneGeometry args={[0.15, 0.35, 6]} />
                    <meshStandardMaterial color={isEnemy ? '#ff4444' : color} />
                </mesh>
            </group>

            {/* General name + crew label */}
            <Html position={[0, 1.4, 0]} center distanceFactor={8} style={{ pointerEvents: 'none' }}>
                <div
                    style={{
                        fontSize: '10px',
                        color: '#ffffff',
                        background: isEnemy ? 'rgba(180,0,0,0.75)' : 'rgba(0,0,0,0.6)',
                        padding: '1px 4px',
                        borderRadius: '2px',
                        whiteSpace: 'nowrap',
                        userSelect: 'none',
                        opacity: brightness,
                    }}
                >
                    {generalName} ({formatCrew(crew)})
                </div>
            </Html>
        </group>
    );
}
