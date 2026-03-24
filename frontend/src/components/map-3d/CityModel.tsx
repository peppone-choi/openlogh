'use client';

import { useState } from 'react';
import type { RenderCity } from '@/components/game/map-canvas';

interface CityModelProps {
    city: RenderCity;
    position: [number, number, number];
    onClick?: () => void;
}

// ─── Shared helpers ───

function darken(hex: string): string {
    const c = hex.replace('#', '');
    const r = Math.max(0, parseInt(c.substring(0, 2), 16) - 40);
    const g = Math.max(0, parseInt(c.substring(2, 4), 16) - 40);
    const b = Math.max(0, parseInt(c.substring(4, 6), 16) - 40);
    return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
}

/** 기와지붕 — 넓적한 피라미드형 (중국 전통 건축) */
function TiledRoof({ width, depth, y, color }: { width: number; depth: number; y: number; color: string }) {
    return (
        <mesh position={[0, y, 0]} castShadow>
            <boxGeometry args={[width * 1.3, 0.12, depth * 1.3]} />
            <meshStandardMaterial color={color} />
        </mesh>
    );
}

/** 누각 — 다층 탑 (각 층이 점점 작아지며 기와지붕) */
function Pavilion({
    floors,
    baseWidth,
    floorHeight,
    y,
    color,
    roofColor,
}: {
    floors: number;
    baseWidth: number;
    floorHeight: number;
    y: number;
    color: string;
    roofColor: string;
}) {
    const layers = [];
    for (let i = 0; i < floors; i++) {
        const w = baseWidth * (1 - i * 0.15);
        const fy = y + i * (floorHeight + 0.1);
        layers.push(
            <group key={i}>
                {/* 층 본체 */}
                <mesh position={[0, fy + floorHeight / 2, 0]} castShadow>
                    <boxGeometry args={[w, floorHeight, w]} />
                    <meshStandardMaterial color={color} />
                </mesh>
                {/* 기와지붕 */}
                <TiledRoof width={w} depth={w} y={fy + floorHeight} color={roofColor} />
            </group>
        );
    }
    return <group>{layers}</group>;
}

/** 성벽 치첩 (성가퀴) — 톱니 패턴 */
function Battlements({ length, y, color }: { length: number; y: number; color: string }) {
    const count = Math.floor(length / 0.25);
    const merlons = [];
    for (let i = 0; i < count; i += 2) {
        const x = -length / 2 + i * 0.25 + 0.125;
        merlons.push(
            <mesh key={i} position={[x, y + 0.1, 0]} castShadow>
                <boxGeometry args={[0.2, 0.2, 0.15]} />
                <meshStandardMaterial color={color} />
            </mesh>
        );
    }
    return <group>{merlons}</group>;
}

/** 깃발 (번/旗 — 세로로 긴 직사각형) */
function Banner({ y, color }: { y: number; color: string }) {
    return (
        <group position={[0, y, 0]}>
            <mesh position={[0, 0.6, 0]}>
                <cylinderGeometry args={[0.03, 0.03, 1.2, 6]} />
                <meshStandardMaterial color="#4a3728" />
            </mesh>
            <mesh position={[0.15, 0.9, 0]}>
                <boxGeometry args={[0.25, 0.5, 0.02]} />
                <meshStandardMaterial color={color} />
            </mesh>
        </group>
    );
}

// ─── Level 1: 수(水) 수군진지 ───

function NavalBase({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 선착장 바닥 */}
            <mesh position={[0, 0.1, 0]} castShadow>
                <boxGeometry args={[1.8, 0.15, 1.2]} />
                <meshStandardMaterial color="#5c4033" />
            </mesh>
            {/* 수상 망루 */}
            <mesh position={[0, 0.55, 0]} castShadow>
                <boxGeometry args={[0.5, 0.7, 0.5]} />
                <meshStandardMaterial color={color} />
            </mesh>
            <TiledRoof width={0.5} depth={0.5} y={0.9} color={wallColor} />
            {/* 정박 기둥들 */}
            {[-0.7, 0.7].map((x) => (
                <mesh key={x} position={[x, 0.35, 0.5]}>
                    <cylinderGeometry args={[0.04, 0.04, 0.5, 6]} />
                    <meshStandardMaterial color="#4a3728" />
                </mesh>
            ))}
            <Banner y={0.9} color={color} />
        </group>
    );
}

// ─── Level 2: 진(鎭) 군사진지 ───

function MilitaryGarrison({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 울타리 (사각형) */}
            {[
                [0, 0.25, -0.7, 1.6, 0.4, 0.08],
                [0, 0.25, 0.7, 1.6, 0.4, 0.08],
                [-0.8, 0.25, 0, 0.08, 0.4, 1.4],
                [0.8, 0.25, 0, 0.08, 0.4, 1.4],
            ].map(([x, y, z, w, h, d], i) => (
                <mesh key={i} position={[x, y, z]} castShadow>
                    <boxGeometry args={[w, h, d]} />
                    <meshStandardMaterial color="#5c4033" />
                </mesh>
            ))}
            {/* 병영 */}
            <mesh position={[0, 0.3, 0]} castShadow>
                <boxGeometry args={[0.8, 0.5, 0.6]} />
                <meshStandardMaterial color={color} />
            </mesh>
            <TiledRoof width={0.8} depth={0.6} y={0.55} color={wallColor} />
            {/* 군기 */}
            <Banner y={0.6} color={color} />
            <Banner y={0} color={color} />
        </group>
    );
}

// ─── Level 3: 관(關) 관문 ───

function GatePass({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 높은 성벽 (좌우) */}
            <mesh position={[-0.6, 0.6, 0]} castShadow>
                <boxGeometry args={[0.5, 1.2, 1.4]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            <mesh position={[0.6, 0.6, 0]} castShadow>
                <boxGeometry args={[0.5, 1.2, 1.4]} />
                <meshStandardMaterial color={wallColor} />
            </mesh>
            {/* 관문루 (문 위 누각) */}
            <Pavilion floors={2} baseWidth={0.7} floorHeight={0.35} y={1.2} color={color} roofColor={wallColor} />
            {/* 문 아치 */}
            <mesh position={[0, 0.35, 0]}>
                <boxGeometry args={[0.5, 0.7, 0.3]} />
                <meshStandardMaterial color="#1a1a1a" />
            </mesh>
            {/* 치첩 */}
            <group position={[-0.6, 1.2, 0.7]}>
                <Battlements length={0.5} y={0} color={wallColor} />
            </group>
            <group position={[0.6, 1.2, 0.7]}>
                <Battlements length={0.5} y={0} color={wallColor} />
            </group>
        </group>
    );
}

// ─── Level 4: 이(夷) 이민족 ───

function BarbarianCamp({ color }: { color: string }) {
    return (
        <group>
            {/* 목책 울타리 (원형) */}
            {Array.from({ length: 12 }).map((_, i) => {
                const angle = (i / 12) * Math.PI * 2;
                const x = Math.cos(angle) * 0.9;
                const z = Math.sin(angle) * 0.9;
                return (
                    <mesh key={i} position={[x, 0.25, z]} rotation={[0, angle, 0]}>
                        <boxGeometry args={[0.08, 0.5, 0.45]} />
                        <meshStandardMaterial color="#5c4033" />
                    </mesh>
                );
            })}
            {/* 중앙 천막 (큰 원뿔) */}
            <mesh position={[0, 0.4, 0]} castShadow>
                <coneGeometry args={[0.5, 0.8, 8]} />
                <meshStandardMaterial color="#c4a97d" />
            </mesh>
            {/* 소형 천막들 */}
            {[
                [0.4, 0, 0.4],
                [-0.4, 0, -0.3],
            ].map(([x, _, z], i) => (
                <mesh key={i} position={[x, 0.2, z]} castShadow>
                    <coneGeometry args={[0.25, 0.4, 6]} />
                    <meshStandardMaterial color="#b8956a" />
                </mesh>
            ))}
            {/* 토템 기둥 */}
            <mesh position={[0, 0.5, -0.5]}>
                <cylinderGeometry args={[0.05, 0.07, 1, 6]} />
                <meshStandardMaterial color="#3d2b1f" />
            </mesh>
            <mesh position={[0, 1, -0.5]}>
                <sphereGeometry args={[0.1, 6, 6]} />
                <meshStandardMaterial color={color} />
            </mesh>
        </group>
    );
}

// ─── Level 5: 소(小) 소도시 ───

function SmallCity({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 낮은 성벽 */}
            {[
                [0, 0.25, -0.65, 1.4, 0.5, 0.1],
                [0, 0.25, 0.65, 1.4, 0.5, 0.1],
                [-0.7, 0.25, 0, 0.1, 0.5, 1.3],
                [0.7, 0.25, 0, 0.1, 0.5, 1.3],
            ].map(([x, y, z, w, h, d], i) => (
                <mesh key={i} position={[x, y, z]} castShadow>
                    <boxGeometry args={[w, h, d]} />
                    <meshStandardMaterial color={wallColor} />
                </mesh>
            ))}
            {/* 건물 */}
            <mesh position={[0, 0.3, 0]} castShadow>
                <boxGeometry args={[0.6, 0.5, 0.5]} />
                <meshStandardMaterial color={color} />
            </mesh>
            <TiledRoof width={0.6} depth={0.5} y={0.55} color={wallColor} />
            {/* 성문 */}
            <mesh position={[0, 0.15, 0.65]}>
                <boxGeometry args={[0.3, 0.3, 0.12]} />
                <meshStandardMaterial color="#1a1a1a" />
            </mesh>
        </group>
    );
}

// ─── Level 6: 중(中) 중도시 ───

function MediumCity({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 성벽 */}
            {[
                [0, 0.35, -0.85, 1.8, 0.7, 0.12],
                [0, 0.35, 0.85, 1.8, 0.7, 0.12],
                [-0.9, 0.35, 0, 0.12, 0.7, 1.7],
                [0.9, 0.35, 0, 0.12, 0.7, 1.7],
            ].map(([x, y, z, w, h, d], i) => (
                <mesh key={i} position={[x, y, z]} castShadow>
                    <boxGeometry args={[w, h, d]} />
                    <meshStandardMaterial color={wallColor} />
                </mesh>
            ))}
            {/* 성문루 */}
            <Pavilion floors={1} baseWidth={0.5} floorHeight={0.35} y={0.7} color={color} roofColor={wallColor} />
            {/* 건물들 */}
            <mesh position={[-0.25, 0.25, -0.2]} castShadow>
                <boxGeometry args={[0.5, 0.4, 0.4]} />
                <meshStandardMaterial color={color} />
            </mesh>
            <TiledRoof width={0.5} depth={0.4} y={0.45} color={wallColor} />
            <mesh position={[0.3, 0.2, 0.2]} castShadow>
                <boxGeometry args={[0.4, 0.3, 0.35]} />
                <meshStandardMaterial color={color} />
            </mesh>
            {/* 성문 */}
            <mesh position={[0, 0.2, 0.85]}>
                <boxGeometry args={[0.35, 0.4, 0.14]} />
                <meshStandardMaterial color="#1a1a1a" />
            </mesh>
        </group>
    );
}

// ─── Level 7: 대(大) 대도시 ───

function LargeCity({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 높은 성벽 */}
            {[
                [0, 0.45, -1.05, 2.2, 0.9, 0.14],
                [0, 0.45, 1.05, 2.2, 0.9, 0.14],
                [-1.1, 0.45, 0, 0.14, 0.9, 2.1],
                [1.1, 0.45, 0, 0.14, 0.9, 2.1],
            ].map(([x, y, z, w, h, d], i) => (
                <mesh key={i} position={[x, y, z]} castShadow>
                    <boxGeometry args={[w, h, d]} />
                    <meshStandardMaterial color={wallColor} />
                </mesh>
            ))}
            {/* 모서리 망루 */}
            {[
                [-1.1, 0, -1.05],
                [1.1, 0, -1.05],
                [-1.1, 0, 1.05],
                [1.1, 0, 1.05],
            ].map(([x, _, z], i) => (
                <group key={i} position={[x, 0, z]}>
                    <mesh position={[0, 0.55, 0]} castShadow>
                        <boxGeometry args={[0.25, 1.1, 0.25]} />
                        <meshStandardMaterial color={wallColor} />
                    </mesh>
                    <TiledRoof width={0.25} depth={0.25} y={1.1} color={color} />
                </group>
            ))}
            {/* 다층 누각 (중앙) */}
            <Pavilion floors={2} baseWidth={0.6} floorHeight={0.4} y={0} color={color} roofColor={wallColor} />
            {/* 궁전 */}
            <mesh position={[0, 0.25, -0.4]} castShadow>
                <boxGeometry args={[0.8, 0.4, 0.5]} />
                <meshStandardMaterial color={color} />
            </mesh>
            <TiledRoof width={0.8} depth={0.5} y={0.45} color={wallColor} />
            {/* 성문루 */}
            <group position={[0, 0, 1.05]}>
                <Pavilion floors={1} baseWidth={0.45} floorHeight={0.3} y={0.9} color={color} roofColor={wallColor} />
            </group>
            {/* 성문 */}
            <mesh position={[0, 0.25, 1.05]}>
                <boxGeometry args={[0.4, 0.5, 0.16]} />
                <meshStandardMaterial color="#1a1a1a" />
            </mesh>
        </group>
    );
}

// ─── Level 8: 특(特) 특대도시 ───

function SpecialCity({ color, wallColor }: { color: string; wallColor: string }) {
    return (
        <group>
            {/* 외성벽 */}
            {[
                [0, 0.45, -1.35, 2.8, 0.9, 0.14],
                [0, 0.45, 1.35, 2.8, 0.9, 0.14],
                [-1.4, 0.45, 0, 0.14, 0.9, 2.7],
                [1.4, 0.45, 0, 0.14, 0.9, 2.7],
            ].map(([x, y, z, w, h, d], i) => (
                <mesh key={`outer-${i}`} position={[x, y, z]} castShadow>
                    <boxGeometry args={[w, h, d]} />
                    <meshStandardMaterial color={wallColor} />
                </mesh>
            ))}
            {/* 내성벽 */}
            {[
                [0, 0.35, -0.75, 1.6, 0.7, 0.1],
                [0, 0.35, 0.75, 1.6, 0.7, 0.1],
                [-0.8, 0.35, 0, 0.1, 0.7, 1.5],
                [0.8, 0.35, 0, 0.1, 0.7, 1.5],
            ].map(([x, y, z, w, h, d], i) => (
                <mesh key={`inner-${i}`} position={[x, y, z]} castShadow>
                    <boxGeometry args={[w, h, d]} />
                    <meshStandardMaterial color={darken(wallColor)} />
                </mesh>
            ))}
            {/* 모서리 망루 (외성) */}
            {[
                [-1.4, 0, -1.35],
                [1.4, 0, -1.35],
                [-1.4, 0, 1.35],
                [1.4, 0, 1.35],
            ].map(([x, _, z], i) => (
                <group key={i} position={[x, 0, z]}>
                    <mesh position={[0, 0.6, 0]} castShadow>
                        <boxGeometry args={[0.3, 1.2, 0.3]} />
                        <meshStandardMaterial color={wallColor} />
                    </mesh>
                    <TiledRoof width={0.3} depth={0.3} y={1.2} color={color} />
                </group>
            ))}
            {/* 대궁전 (다층 누각) */}
            <Pavilion floors={3} baseWidth={0.7} floorHeight={0.4} y={0} color={color} roofColor={wallColor} />
            {/* 부속 건물들 */}
            {[
                [-0.4, 0.2, -0.35, 0.5, 0.3],
                [0.4, 0.2, 0.3, 0.45, 0.25],
            ].map(([x, y, z, w, h], i) => (
                <group key={`bld-${i}`}>
                    <mesh position={[x, y, z]} castShadow>
                        <boxGeometry args={[w, h, w * 0.8]} />
                        <meshStandardMaterial color={color} />
                    </mesh>
                    <TiledRoof width={w} depth={w * 0.8} y={y + h / 2} color={wallColor} />
                </group>
            ))}
            {/* 대성문루 */}
            <group position={[0, 0, 1.35]}>
                <Pavilion floors={2} baseWidth={0.55} floorHeight={0.35} y={0.9} color={color} roofColor={wallColor} />
            </group>
            <mesh position={[0, 0.3, 1.35]}>
                <boxGeometry args={[0.5, 0.6, 0.16]} />
                <meshStandardMaterial color="#1a1a1a" />
            </mesh>
            {/* 수도 발광 */}
            <pointLight position={[0, 2, 0]} intensity={0.3} distance={5} color={color} />
        </group>
    );
}

// ─── Main CityModel ───

export function CityModel({ city, position, onClick }: CityModelProps) {
    const [hovered, setHovered] = useState(false);
    const color = city.nationColor ?? '#888888';
    const wallColor = darken(color);
    const s = hovered ? 1.08 : 1;

    const renderCity = () => {
        switch (city.level) {
            case 1:
                return <NavalBase color={color} wallColor={wallColor} />;
            case 2:
                return <MilitaryGarrison color={color} wallColor={wallColor} />;
            case 3:
                return <GatePass color={color} wallColor={wallColor} />;
            case 4:
                return <BarbarianCamp color={color} />;
            case 5:
                return <SmallCity color={color} wallColor={wallColor} />;
            case 6:
                return <MediumCity color={color} wallColor={wallColor} />;
            case 7:
                return <LargeCity color={color} wallColor={wallColor} />;
            case 8:
                return <SpecialCity color={color} wallColor={wallColor} />;
            default:
                return <SmallCity color={color} wallColor={wallColor} />;
        }
    };

    return (
        <group
            position={position}
            scale={[s, s, s]}
            onClick={(e) => {
                e.stopPropagation();
                onClick?.();
            }}
            onPointerOver={() => setHovered(true)}
            onPointerOut={() => setHovered(false)}
        >
            {renderCity()}
        </group>
    );
}
