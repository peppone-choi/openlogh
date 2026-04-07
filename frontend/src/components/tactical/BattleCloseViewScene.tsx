'use client';
// BattleCloseViewScene — top-center combat drama view using React Three Fiber
import { Canvas, useFrame } from '@react-three/fiber';
import { Text, Line } from '@react-three/drei';
import { Suspense, useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { useTacticalStore } from '@/stores/tacticalStore';
import type { TacticalUnit, BattleTickEvent } from '@/types/tactical';

// Faction colors
const EMPIRE_COLOR = '#4466ff';
const ALLIANCE_COLOR = '#ff4444';
const FLAGSHIP_COLOR = '#ffd700';

function getFactionColor(unit: TacticalUnit, attackerFactionId: number): string {
    return unit.factionId === attackerFactionId ? EMPIRE_COLOR : ALLIANCE_COLOR;
}

// Unit block mesh
interface UnitBlockProps {
    unit: TacticalUnit;
    position: [number, number, number];
    color: string;
    isExploding: boolean;
}

function UnitBlock({ unit, position, color, isExploding }: UnitBlockProps) {
    const meshRef = useRef<THREE.Mesh>(null);
    const isFlagship = unit.unitType === 'FLAGSHIP';
    const widthScale = Math.max(0.3, Math.min(3.0, unit.ships / 300));
    const height = isFlagship ? 0.8 : 0.5;

    useFrame((_, delta) => {
        if (!meshRef.current) return;
        if (isExploding) {
            const pulse = 1.0 + Math.sin(Date.now() * 0.02) * 0.15;
            meshRef.current.scale.setScalar(pulse);
        } else {
            meshRef.current.scale.lerp(new THREE.Vector3(1, 1, 1), delta * 5);
        }
    });

    return (
        <group position={position}>
            <mesh ref={meshRef}>
                <boxGeometry args={[1.5 * widthScale, height, 1.0]} />
                <meshStandardMaterial
                    color={color}
                    emissive={isFlagship ? FLAGSHIP_COLOR : undefined}
                    emissiveIntensity={isFlagship ? 0.4 : 0}
                />
            </mesh>
            {isFlagship && (
                <Text
                    position={[0, height / 2 + 0.25, 0]}
                    fontSize={0.3}
                    color="white"
                    anchorX="center"
                    anchorY="bottom"
                >
                    {unit.officerName.toUpperCase()}
                </Text>
            )}
        </group>
    );
}

// BEAM effect: line between two positions, auto-removes after 400ms
interface BeamEffectProps {
    from: [number, number, number];
    to: [number, number, number];
}

function BeamEffect({ from, to }: BeamEffectProps) {
    const [visible, setVisible] = useState(true);

    useEffect(() => {
        const timer = setTimeout(() => setVisible(false), 400);
        return () => clearTimeout(timer);
    }, []);

    if (!visible) return null;

    return (
        <Line
            points={[from, to]}
            color="#ffff00"
            lineWidth={2}
        />
    );
}

// MISSILE effect: sphere moving along lerp path over 500ms
interface MissileEffectProps {
    from: [number, number, number];
    to: [number, number, number];
}

function MissileEffect({ from, to }: MissileEffectProps) {
    const [progress, setProgress] = useState(0);
    const [visible, setVisible] = useState(true);
    const startTime = useRef(Date.now());

    useFrame(() => {
        const elapsed = Date.now() - startTime.current;
        const t = Math.min(elapsed / 500, 1);
        setProgress(t);
        if (t >= 1) setVisible(false);
    });

    if (!visible) return null;

    const pos: [number, number, number] = [
        from[0] + (to[0] - from[0]) * progress,
        from[1] + (to[1] - from[1]) * progress,
        from[2] + (to[2] - from[2]) * progress,
    ];

    return (
        <mesh position={pos}>
            <sphereGeometry args={[0.05, 6, 6]} />
            <meshBasicMaterial color="#ff8800" />
        </mesh>
    );
}

// EXPLOSION: PointLight flash at target position, decaying over 300ms
interface ExplosionEffectProps {
    position: [number, number, number];
}

function ExplosionEffect({ position }: ExplosionEffectProps) {
    const lightRef = useRef<THREE.PointLight>(null);
    const [visible, setVisible] = useState(true);
    const startTime = useRef(Date.now());

    useFrame(() => {
        const elapsed = Date.now() - startTime.current;
        const t = Math.min(elapsed / 300, 1);
        if (lightRef.current) {
            lightRef.current.intensity = 5 * (1 - t);
        }
        if (t >= 1) setVisible(false);
    });

    if (!visible) return null;

    return <pointLight ref={lightRef} position={position} color="#ff6600" intensity={5} distance={8} />;
}

// Key for deduplicating effects
let effectKeyCounter = 0;

interface ActiveEffect {
    key: number;
    type: 'BEAM' | 'MISSILE' | 'EXPLOSION';
    from?: [number, number, number];
    to?: [number, number, number];
    position?: [number, number, number];
}

// Main scene content
function SceneContent() {
    const { units, recentEvents, currentBattle } = useTacticalStore();
    const [effects, setEffects] = useState<ActiveEffect[]>([]);
    const processedEvents = useRef<Set<string>>(new Set());
    const [explodingUnits, setExplodingUnits] = useState<Set<number>>(new Set());

    // Build unit position map
    const unitPositions = useRef<Map<number, [number, number, number]>>(new Map());

    const attackerFactionId = currentBattle?.attackerFactionId ?? -1;

    // Separate ally (attacker) and enemy (defender) units
    const attackerUnits = units.filter((u) => u.factionId === attackerFactionId && u.isAlive);
    const defenderUnits = units.filter((u) => u.factionId !== attackerFactionId && u.isAlive);

    // Assign 3D positions
    const getUnitPosition = (unit: TacticalUnit, index: number, isAttacker: boolean): [number, number, number] => {
        const x = isAttacker ? -3 - (index % 3) * 2 : 3 + (index % 3) * 2;
        const y = Math.floor(index / 3) * 1.2;
        return [x, y, 0];
    };

    attackerUnits.forEach((u, i) => {
        unitPositions.current.set(u.fleetId, getUnitPosition(u, i, true));
    });
    defenderUnits.forEach((u, i) => {
        unitPositions.current.set(u.fleetId, getUnitPosition(u, i, false));
    });

    // Process new events from tacticalStore
    useEffect(() => {
        const newEffects: ActiveEffect[] = [];
        const newExploding = new Set<number>();

        for (const event of recentEvents) {
            const eventKey = `${event.type}-${event.sourceUnitId}-${event.targetUnitId}-${event.value}`;
            if (processedEvents.current.has(eventKey)) continue;
            processedEvents.current.add(eventKey);

            const fromPos = unitPositions.current.get(event.sourceUnitId);
            const toPos = unitPositions.current.get(event.targetUnitId);

            if (event.type === 'BEAM' && fromPos && toPos) {
                newEffects.push({ key: effectKeyCounter++, type: 'BEAM', from: fromPos, to: toPos });
            } else if (event.type === 'MISSILE' && fromPos && toPos) {
                newEffects.push({ key: effectKeyCounter++, type: 'MISSILE', from: fromPos, to: toPos });
            } else if (event.type === 'EXPLOSION' && toPos) {
                newEffects.push({ key: effectKeyCounter++, type: 'EXPLOSION', position: toPos });
                newExploding.add(event.targetUnitId);
            }
        }

        if (newEffects.length > 0) {
            setEffects((prev) => [...prev, ...newEffects].slice(-30));
        }
        if (newExploding.size > 0) {
            setExplodingUnits((prev) => new Set([...prev, ...newExploding]));
            setTimeout(() => {
                setExplodingUnits((prev) => {
                    const next = new Set(prev);
                    newExploding.forEach((id) => next.delete(id));
                    return next;
                });
            }, 300);
        }
    }, [recentEvents]);

    // Trim old effects
    const removeEffect = (key: number) => {
        setEffects((prev) => prev.filter((e) => e.key !== key));
    };

    return (
        <>
            <ambientLight intensity={0.3} />
            <directionalLight position={[5, 10, 5]} intensity={1} castShadow={false} />

            {/* Attacker units (left side) */}
            {attackerUnits.map((unit, i) => {
                const pos = getUnitPosition(unit, i, true);
                return (
                    <UnitBlock
                        key={unit.fleetId}
                        unit={unit}
                        position={pos}
                        color={getFactionColor(unit, attackerFactionId)}
                        isExploding={explodingUnits.has(unit.fleetId)}
                    />
                );
            })}

            {/* Defender units (right side) */}
            {defenderUnits.map((unit, i) => {
                const pos = getUnitPosition(unit, i, false);
                return (
                    <UnitBlock
                        key={unit.fleetId}
                        unit={unit}
                        position={pos}
                        color={getFactionColor(unit, attackerFactionId)}
                        isExploding={explodingUnits.has(unit.fleetId)}
                    />
                );
            })}

            {/* Effects */}
            {effects.map((effect) => {
                if (effect.type === 'BEAM' && effect.from && effect.to) {
                    return (
                        <BeamEffect key={effect.key} from={effect.from} to={effect.to} />
                    );
                }
                if (effect.type === 'MISSILE' && effect.from && effect.to) {
                    return (
                        <MissileEffect key={effect.key} from={effect.from} to={effect.to} />
                    );
                }
                if (effect.type === 'EXPLOSION' && effect.position) {
                    return (
                        <ExplosionEffect key={effect.key} position={effect.position} />
                    );
                }
                return null;
            })}
        </>
    );
}

export function BattleCloseViewScene() {
    return (
        <Canvas
            style={{ width: '100%', height: '100%', background: '#000008' }}
            camera={{ fov: 60, position: [0, 8, 12], near: 0.1, far: 1000 }}
            gl={{ antialias: true }}
        >
            <Suspense fallback={null}>
                <SceneContent />
            </Suspense>
        </Canvas>
    );
}
