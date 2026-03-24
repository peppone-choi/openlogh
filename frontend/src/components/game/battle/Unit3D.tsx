/// <reference types="@react-three/fiber" />
'use client';

import { useRef, useMemo, useCallback } from 'react';
import { useFrame, type ThreeEvent } from '@react-three/fiber';
import { Billboard, Text } from '@react-three/drei';
import * as THREE from 'three';
import type { ShipClass, FactionType } from '@/stores/battleStore';
import { GRID_CONSTANTS } from './SpaceEnvironment';

// ─── Faction visual styles ──────────────────────────────────────────────────
interface FactionVisual {
    primary: string;
    glow: string;
    emissive: string;
}

const FACTION_VISUALS: Record<FactionType, FactionVisual> = {
    empire: { primary: '#cc2200', glow: '#FFD700', emissive: '#551100' },
    alliance: { primary: '#1144aa', glow: '#4488FF', emissive: '#002255' },
    fezzan: { primary: '#442266', glow: '#CC88FF', emissive: '#220044' },
    rebel: { primary: '#884400', glow: '#FF8844', emissive: '#442200' },
};

// ─── Ship geometry definitions ──────────────────────────────────────────────
// Each ship class has a distinct silhouette for quick identification

interface UnitData {
    id: number;
    shipClass: ShipClass;
    hp: number;
    maxHp: number;
    gridX: number;
    gridY: number;
    factionType: FactionType;
    isMyUnit: boolean;
    isCommander: boolean;
    morale: number;
}

// ─── Ship mesh by class ─────────────────────────────────────────────────────
function ShipMesh({
    shipClass,
    color,
    emissive,
    selected,
    flash,
}: {
    shipClass: ShipClass;
    color: string;
    emissive: string;
    selected: boolean;
    flash: boolean;
}) {
    const meshRef = useRef<THREE.Mesh>(null);
    const matColor = flash ? '#ff2222' : color;
    const intensity = selected ? 0.8 : flash ? 1.2 : 0.3;

    useFrame((_, delta) => {
        if (meshRef.current && selected) {
            meshRef.current.rotation.y += delta * 0.3;
        }
    });

    switch (shipClass) {
        case 'battleship':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <boxGeometry args={[1.6, 0.5, 0.8]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive={emissive}
                        emissiveIntensity={intensity}
                        metalness={0.7}
                        roughness={0.3}
                    />
                </mesh>
            );
        case 'cruiser':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>} rotation={[0, 0, Math.PI / 2]}>
                    <coneGeometry args={[0.5, 1.4, 3]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive={emissive}
                        emissiveIntensity={intensity}
                        metalness={0.6}
                        roughness={0.4}
                    />
                </mesh>
            );
        case 'destroyer':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <octahedronGeometry args={[0.5]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive={emissive}
                        emissiveIntensity={intensity}
                        metalness={0.8}
                        roughness={0.2}
                    />
                </mesh>
            );
        case 'carrier':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <cylinderGeometry args={[0.6, 0.6, 0.4, 16]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive={emissive}
                        emissiveIntensity={intensity}
                        metalness={0.5}
                        roughness={0.5}
                    />
                </mesh>
            );
        case 'transport':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <boxGeometry args={[1.4, 0.3, 0.6]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive={emissive}
                        emissiveIntensity={intensity * 0.5}
                        metalness={0.4}
                        roughness={0.6}
                        wireframe
                    />
                </mesh>
            );
        case 'hospital':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <sphereGeometry args={[0.45, 12, 12]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive="#ff4488"
                        emissiveIntensity={intensity}
                        metalness={0.3}
                        roughness={0.7}
                    />
                </mesh>
            );
        case 'fortress':
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <dodecahedronGeometry args={[0.8]} />
                    <meshStandardMaterial
                        color={matColor}
                        emissive={emissive}
                        emissiveIntensity={intensity + 0.2}
                        metalness={0.9}
                        roughness={0.1}
                    />
                </mesh>
            );
        default:
            return (
                <mesh ref={meshRef as React.RefObject<THREE.Mesh>}>
                    <sphereGeometry args={[0.4, 8, 8]} />
                    <meshStandardMaterial color={matColor} emissive={emissive} emissiveIntensity={intensity} />
                </mesh>
            );
    }
}

// ─── HP Bar (billboard, always faces camera) ────────────────────────────────
function HpBar({ hp, maxHp }: { hp: number; maxHp: number }) {
    const pct = maxHp > 0 ? hp / maxHp : 0;
    const barColor = pct > 0.6 ? '#00cc55' : pct > 0.3 ? '#ffaa00' : '#ff3333';
    const barWidth = 1.2;

    return (
        <Billboard position={[0, 1.0, 0]} follow lockX={false} lockY={false} lockZ={false}>
            {/* Background bar */}
            <mesh position={[0, 0, 0]}>
                <planeGeometry args={[barWidth, 0.1]} />
                <meshBasicMaterial color="#111122" transparent opacity={0.8} />
            </mesh>
            {/* HP fill */}
            <mesh position={[(pct - 1) * barWidth * 0.5, 0, 0.001]}>
                <planeGeometry args={[Math.max(0.01, barWidth * pct), 0.08]} />
                <meshBasicMaterial color={barColor} />
            </mesh>
        </Billboard>
    );
}

// ─── Unit label (ship count) ────────────────────────────────────────────────
function UnitLabel({ hp, color }: { hp: number; color: string }) {
    const label = hp >= 1000 ? `${Math.round(hp / 100) / 10}k` : `${hp}`;
    return (
        <Billboard position={[0, 1.4, 0]}>
            <Text fontSize={0.3} color={color} anchorX="center" anchorY="middle" font={undefined}>
                {label}
            </Text>
        </Billboard>
    );
}

// ─── Commander indicator ────────────────────────────────────────────────────
function CommanderStar({ color }: { color: string }) {
    const ref = useRef<THREE.Mesh>(null);

    useFrame((_, delta) => {
        if (ref.current) {
            ref.current.rotation.y += delta * 1.5;
        }
    });

    return (
        <mesh ref={ref as React.RefObject<THREE.Mesh>} position={[0.7, 0.7, 0]}>
            <octahedronGeometry args={[0.12]} />
            <meshBasicMaterial color={color} />
        </mesh>
    );
}

// ─── Glow ring (point light for selected units) ─────────────────────────────
function GlowRing({ color, selected }: { color: string; selected: boolean }) {
    if (!selected) return null;
    return <pointLight position={[0, 0.5, 0]} color={color} intensity={2} distance={4} />;
}

// ─── Complete 3D tactical unit ──────────────────────────────────────────────
export interface Unit3DProps {
    unit: UnitData;
    selected: boolean;
    flash: boolean;
    onClick: (e: ThreeEvent<MouseEvent>) => void;
    onContextMenu: (e: ThreeEvent<MouseEvent>) => void;
}

export function Unit3D({ unit, selected, flash, onClick, onContextMenu }: Unit3DProps) {
    const groupRef = useRef<THREE.Group>(null);
    const targetPos = useRef(new THREE.Vector3());
    const currentPos = useRef(new THREE.Vector3());
    const initialized = useRef(false);

    const visual = FACTION_VISUALS[unit.factionType] ?? FACTION_VISUALS.empire;
    const [tx, , tz] = GRID_CONSTANTS.gridToWorld(unit.gridX, unit.gridY);

    targetPos.current.set(tx, 0.5, tz);

    // Smooth movement animation
    useFrame((_, delta) => {
        if (!groupRef.current) return;

        if (!initialized.current) {
            groupRef.current.position.copy(targetPos.current);
            currentPos.current.copy(targetPos.current);
            initialized.current = true;
            return;
        }

        // Lerp toward target
        currentPos.current.lerp(targetPos.current, Math.min(1, delta * 4));
        groupRef.current.position.copy(currentPos.current);

        // Hover bob
        groupRef.current.position.y = 0.5 + Math.sin(Date.now() * 0.002 + unit.id) * 0.05;
    });

    const handleClick = useCallback(
        (e: ThreeEvent<MouseEvent>) => {
            e.stopPropagation();
            onClick(e);
        },
        [onClick]
    );

    const handleContext = useCallback(
        (e: ThreeEvent<MouseEvent>) => {
            e.stopPropagation();
            onContextMenu(e);
        },
        [onContextMenu]
    );

    return (
        <group ref={groupRef}>
            {/* Ship body */}
            <group onClick={handleClick} onContextMenu={handleContext}>
                <ShipMesh
                    shipClass={unit.shipClass}
                    color={visual.primary}
                    emissive={visual.emissive}
                    selected={selected}
                    flash={flash}
                />
            </group>

            {/* HP bar */}
            <HpBar hp={unit.hp} maxHp={unit.maxHp} />

            {/* Ship count label */}
            <UnitLabel hp={unit.hp} color={visual.glow} />

            {/* Commander star */}
            {unit.isCommander && <CommanderStar color={visual.glow} />}

            {/* Selection glow */}
            <GlowRing color={visual.glow} selected={selected} />
        </group>
    );
}

// ─── Order arrow (3D line from unit to target) ──────────────────────────────
export function OrderArrow3D({
    fromGX,
    fromGY,
    toGX,
    toGY,
    type,
}: {
    fromGX: number;
    fromGY: number;
    toGX: number;
    toGY: number;
    type: 'move' | 'attack';
}) {
    const [fx, , fz] = GRID_CONSTANTS.gridToWorld(fromGX, fromGY);
    const [tx, , tz] = GRID_CONSTANTS.gridToWorld(toGX, toGY);
    const color = type === 'attack' ? '#FF4444' : '#44AAFF';

    const geometry = useMemo(() => {
        const points = [new THREE.Vector3(fx, 0.5, fz), new THREE.Vector3(tx, 0.5, tz)];
        return new THREE.BufferGeometry().setFromPoints(points);
    }, [fx, fz, tx, tz]);

    return (
        <group>
            <line geometry={geometry}>
                <lineBasicMaterial color={color} transparent opacity={0.7} linewidth={2} />
            </line>
            {/* Target marker */}
            <mesh position={[tx, 0.3, tz]} rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[0.3, 0.5, 16]} />
                <meshBasicMaterial color={color} transparent opacity={0.6} side={THREE.DoubleSide} />
            </mesh>
        </group>
    );
}
