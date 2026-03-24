'use client';

import { useState } from 'react';
import type { RenderCity } from '@/components/game/map-canvas';
import { getCityScale, getCityHeight } from '@/lib/map3d-utils';

// Phase 3: Replace each castle with useGLTF('/models/castle-{size}.glb')
// e.g.: const { scene } = useGLTF('/models/castle-small.glb'); return <primitive object={scene} />;

interface CastleProps {
    color: string;
    wallColor: string;
    scale: number;
    height: number;
}

function SmallCastle({ color, wallColor, scale, height }: CastleProps) {
    // Level 1-2: Square wall base + 1 central building + flat roof
    const wallH = height * 0.3;
    const wallW = scale * 1.2;
    const buildH = height * 0.7;

    return (
        <group>
            {/* Wall base */}
            <mesh castShadow position={[0, wallH * 0.5, 0]}>
                <boxGeometry args={[wallW, wallH, wallW]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Wall wireframe outline */}
            <mesh position={[0, wallH * 0.5, 0]}>
                <boxGeometry args={[wallW + 0.01, wallH + 0.01, wallW + 0.01]} />
                <meshStandardMaterial color="#000000" wireframe opacity={0.15} transparent />
            </mesh>
            {/* Central building */}
            <mesh castShadow position={[0, wallH + buildH * 0.5, 0]}>
                <boxGeometry args={[scale * 0.7, buildH, scale * 0.7]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Flat roof */}
            <mesh castShadow position={[0, wallH + buildH, 0]}>
                <boxGeometry args={[scale * 0.75, height * 0.05, scale * 0.75]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
        </group>
    );
}

function MediumCastle({ color, wallColor, scale, height }: CastleProps) {
    // Level 3-4: Wall with 4 corner towers + 2 buildings + gate
    const wallH = height * 0.35;
    const wallW = scale * 1.2;
    const towerR = scale * 0.12;
    const towerH = wallH * 1.4;
    const towerOffset = wallW * 0.5 - towerR;

    const corners: [number, number][] = [
        [towerOffset, towerOffset],
        [-towerOffset, towerOffset],
        [towerOffset, -towerOffset],
        [-towerOffset, -towerOffset],
    ];

    return (
        <group>
            {/* Outer wall */}
            <mesh castShadow position={[0, wallH * 0.5, 0]}>
                <boxGeometry args={[wallW, wallH, wallW]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Gate opening — small box at front wall center (slightly lighter) */}
            <mesh position={[0, wallH * 0.3, wallW * 0.5 - 0.01]}>
                <boxGeometry args={[scale * 0.15, wallH * 0.6, 0.05]} />
                <meshStandardMaterial color="#222222" />
            </mesh>
            {/* 4 corner towers */}
            {corners.map(([cx, cz], i) => (
                <group key={i} position={[cx, 0, cz]}>
                    <mesh castShadow position={[0, towerH * 0.5, 0]}>
                        <cylinderGeometry args={[towerR, towerR * 1.1, towerH, 8]} />
                        <meshStandardMaterial color={wallColor} />
                    </mesh>
                    {/* Cone cap */}
                    <mesh position={[0, towerH + scale * 0.1, 0]}>
                        <coneGeometry args={[towerR * 1.2, scale * 0.2, 8]} />
                        <meshStandardMaterial color={color} />
                    </mesh>
                </group>
            ))}
            {/* Building 1 */}
            <mesh castShadow position={[-scale * 0.15, wallH + height * 0.25, 0]}>
                <boxGeometry args={[scale * 0.4, height * 0.5, scale * 0.4]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Building 2 */}
            <mesh castShadow position={[scale * 0.2, wallH + height * 0.2, 0]}>
                <boxGeometry args={[scale * 0.3, height * 0.4, scale * 0.3]} />
                <meshStandardMaterial color={color} />
            </mesh>
        </group>
    );
}

function LargeCastle({ color, wallColor, scale, height }: CastleProps) {
    // Level 5-6: Taller walls with battlements + 4 corner towers + main keep + 2-3 smaller buildings
    const wallH = height * 0.4;
    const wallW = scale * 1.3;
    const towerR = scale * 0.13;
    const towerH = wallH * 1.6;
    const towerOffset = wallW * 0.5 - towerR;
    const keepH = height * 0.8;
    const battlementSize = scale * 0.06;
    const battlementCount = 5;
    const battlementStep = wallW / (battlementCount + 1);

    const corners: [number, number][] = [
        [towerOffset, towerOffset],
        [-towerOffset, towerOffset],
        [towerOffset, -towerOffset],
        [-towerOffset, -towerOffset],
    ];

    // Battlements along top of front/back walls
    const battlementPositions: [number, number, number][] = [];
    for (let i = 0; i < battlementCount; i++) {
        const bx = -wallW * 0.5 + battlementStep * (i + 1);
        battlementPositions.push([bx, wallH + battlementSize * 0.5, wallW * 0.5]);
        battlementPositions.push([bx, wallH + battlementSize * 0.5, -wallW * 0.5]);
        battlementPositions.push([wallW * 0.5, wallH + battlementSize * 0.5, bx]);
        battlementPositions.push([-wallW * 0.5, wallH + battlementSize * 0.5, bx]);
    }

    return (
        <group>
            {/* Outer wall */}
            <mesh castShadow position={[0, wallH * 0.5, 0]}>
                <boxGeometry args={[wallW, wallH, wallW]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Battlements */}
            {battlementPositions.map(([bx, by, bz], i) => (
                <mesh key={i} castShadow position={[bx, by, bz]}>
                    <boxGeometry args={[battlementSize, battlementSize, battlementSize]} />
                    <meshStandardMaterial color={wallColor} />
                </mesh>
            ))}
            {/* 4 corner towers */}
            {corners.map(([cx, cz], i) => (
                <group key={i} position={[cx, 0, cz]}>
                    <mesh castShadow position={[0, towerH * 0.5, 0]}>
                        <cylinderGeometry args={[towerR, towerR * 1.15, towerH, 8]} />
                        <meshStandardMaterial color={wallColor} />
                    </mesh>
                    <mesh position={[0, towerH + scale * 0.15, 0]}>
                        <coneGeometry args={[towerR * 1.3, scale * 0.28, 8]} />
                        <meshStandardMaterial color={color} />
                    </mesh>
                </group>
            ))}
            {/* Main keep */}
            <mesh castShadow position={[0, wallH + keepH * 0.5, 0]}>
                <boxGeometry args={[scale * 0.55, keepH, scale * 0.55]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Keep roof */}
            <mesh position={[0, wallH + keepH + scale * 0.12, 0]}>
                <coneGeometry args={[scale * 0.38, scale * 0.24, 4]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Side building 1 */}
            <mesh castShadow position={[-scale * 0.3, wallH + height * 0.2, scale * 0.2]}>
                <boxGeometry args={[scale * 0.28, height * 0.4, scale * 0.28]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Side building 2 */}
            <mesh castShadow position={[scale * 0.3, wallH + height * 0.18, -scale * 0.2]}>
                <boxGeometry args={[scale * 0.24, height * 0.36, scale * 0.24]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Side building 3 */}
            <mesh castShadow position={[-scale * 0.1, wallH + height * 0.15, -scale * 0.3]}>
                <boxGeometry args={[scale * 0.22, height * 0.3, scale * 0.22]} />
                <meshStandardMaterial color={color} />
            </mesh>
        </group>
    );
}

function CapitalCastle({ color, wallColor, scale, height }: CastleProps) {
    // Level 7-8: Double wall + 6 towers + central palace + tiered roof + flag + glow
    const outerWallH = height * 0.35;
    const outerWallW = scale * 1.5;
    const innerWallH = height * 0.45;
    const innerWallW = scale * 0.95;
    const towerR = scale * 0.14;
    const outerTowerH = outerWallH * 1.5;
    const innerTowerH = innerWallH * 1.6;
    const outerOffset = outerWallW * 0.5 - towerR;
    const innerOffset = innerWallW * 0.5 - towerR;
    const palaceH = height * 0.9;

    const outerCorners: [number, number][] = [
        [outerOffset, outerOffset],
        [-outerOffset, outerOffset],
        [outerOffset, -outerOffset],
        [-outerOffset, -outerOffset],
    ];

    const innerMidPoints: [number, number][] = [
        [0, innerOffset],
        [0, -innerOffset],
    ];

    return (
        <group>
            {/* Outer wall */}
            <mesh castShadow position={[0, outerWallH * 0.5, 0]}>
                <boxGeometry args={[outerWallW, outerWallH, outerWallW]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Inner wall */}
            <mesh castShadow position={[0, outerWallH + innerWallH * 0.5, 0]}>
                <boxGeometry args={[innerWallW, innerWallH, innerWallW]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* 4 outer corner towers */}
            {outerCorners.map(([cx, cz], i) => (
                <group key={`ot-${i}`} position={[cx, 0, cz]}>
                    <mesh castShadow position={[0, outerTowerH * 0.5, 0]}>
                        <cylinderGeometry args={[towerR, towerR * 1.2, outerTowerH, 8]} />
                        <meshStandardMaterial color={wallColor} />
                    </mesh>
                    <mesh position={[0, outerTowerH + scale * 0.18, 0]}>
                        <coneGeometry args={[towerR * 1.35, scale * 0.32, 8]} />
                        <meshStandardMaterial color={color} />
                    </mesh>
                </group>
            ))}
            {/* 2 inner mid towers */}
            {innerMidPoints.map(([cx, cz], i) => (
                <group key={`it-${i}`} position={[cx, outerWallH, cz]}>
                    <mesh castShadow position={[0, innerTowerH * 0.5, 0]}>
                        <cylinderGeometry args={[towerR * 0.9, towerR * 1.05, innerTowerH, 8]} />
                        <meshStandardMaterial color={wallColor} />
                    </mesh>
                    <mesh position={[0, innerTowerH + scale * 0.15, 0]}>
                        <coneGeometry args={[towerR * 1.1, scale * 0.26, 8]} />
                        <meshStandardMaterial color={color} />
                    </mesh>
                </group>
            ))}
            {/* Central palace base */}
            <mesh castShadow position={[0, outerWallH + innerWallH + palaceH * 0.5, 0]}>
                <boxGeometry args={[scale * 0.6, palaceH, scale * 0.6]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Tiered roof — tier 1 (wide) */}
            <mesh castShadow position={[0, outerWallH + innerWallH + palaceH + scale * 0.05, 0]}>
                <boxGeometry args={[scale * 0.65, scale * 0.1, scale * 0.65]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Tiered roof — tier 2 (narrower) */}
            <mesh castShadow position={[0, outerWallH + innerWallH + palaceH + scale * 0.18, 0]}>
                <boxGeometry args={[scale * 0.45, scale * 0.1, scale * 0.45]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* Tiered roof — tier 3 (top cap) */}
            <mesh castShadow position={[0, outerWallH + innerWallH + palaceH + scale * 0.3, 0]}>
                <boxGeometry args={[scale * 0.28, scale * 0.08, scale * 0.28]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* Flag pole */}
            <mesh position={[0, outerWallH + innerWallH + palaceH + scale * 0.42, 0]}>
                <cylinderGeometry args={[0.03, 0.03, scale * 0.7, 6]} />
                <meshStandardMaterial color="#cccccc" />
            </mesh>
            {/* Flag */}
            <mesh position={[scale * 0.18, outerWallH + innerWallH + palaceH + scale * 0.65, 0]}>
                <planeGeometry args={[scale * 0.35, scale * 0.22]} />
                <meshStandardMaterial color={color} side={2} />
            </mesh>
            {/* Glow point light */}
            <pointLight
                color={color}
                intensity={0.8}
                distance={scale * 5}
                position={[0, outerWallH + innerWallH + palaceH * 0.5, 0]}
            />
        </group>
    );
}

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
    const wallColor = city.nationColor ? `color-mix(in srgb, ${city.nationColor} 55%, #000000)` : '#555555';

    const castleProps: CastleProps = { color, wallColor, scale, height };

    const level = city.level ?? 1;
    const isCapital = city.isCapital ?? false;

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
            {(isCapital || level >= 7) && <CapitalCastle {...castleProps} />}
            {!isCapital && level >= 5 && level <= 6 && <LargeCastle {...castleProps} />}
            {!isCapital && level >= 3 && level <= 4 && <MediumCastle {...castleProps} />}
            {!isCapital && level <= 2 && <SmallCastle {...castleProps} />}
        </group>
    );
}
