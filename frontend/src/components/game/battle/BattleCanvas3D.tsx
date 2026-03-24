// @ts-nocheck
/// <reference types="@react-three/fiber" />
'use client';

import { useCallback, useRef, useState } from 'react';
import { Canvas, type ThreeEvent } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera } from '@react-three/drei';
import * as THREE from 'three';
import { useBattleStore } from '@/stores/battleStore';
import type { FactionType, ShipClass } from '@/stores/battleStore';
import type { TacticalFleet } from '@/types/tactical';
import {
    SpaceBackground,
    TacticalGrid,
    MovementHighlight,
    SelectionHighlight,
    GRID_CONSTANTS,
} from './SpaceEnvironment';
import { Unit3D, OrderArrow3D } from './Unit3D';
import { EffectsManager, type BattleEffect } from './Effects3D';

// ─── Enriched unit (same structure as 2D canvas for compatibility) ──────────
interface EnrichedUnit {
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

function enrichUnits(fleets: TacticalFleet[], isMyUnit: boolean): EnrichedUnit[] {
    return fleets.flatMap((fleet) =>
        fleet.units.map((unit, idx) => ({
            id: unit.id,
            shipClass: unit.shipClass as ShipClass,
            hp: unit.hp,
            maxHp: unit.maxHp,
            gridX: unit.gridX ?? 0,
            gridY: unit.gridY ?? 0,
            factionType: fleet.factionType as FactionType,
            isMyUnit,
            isCommander: idx === 0,
            morale: fleet.morale,
        }))
    );
}

// ─── Movement range calculator ──────────────────────────────────────────────
function getMovementCells(gx: number, gy: number, range: number): Array<[number, number]> {
    const cells: Array<[number, number]> = [];
    for (let dx = -range; dx <= range; dx++) {
        for (let dy = -range; dy <= range; dy++) {
            if (Math.abs(dx) + Math.abs(dy) <= range && (dx !== 0 || dy !== 0)) {
                const nx = gx + dx;
                const ny = gy + dy;
                if (nx >= 0 && nx < 20 && ny >= 0 && ny < 20) {
                    cells.push([nx, ny]);
                }
            }
        }
    }
    return cells;
}

// ─── Faction style lookup ───────────────────────────────────────────────────
const FACTION_GLOW: Record<FactionType, string> = {
    empire: '#FFD700',
    alliance: '#4488FF',
    fezzan: '#CC88FF',
    rebel: '#FF8844',
};

// ─── Props (same interface as 2D BattleCanvas for drop-in replacement) ──────
export interface BattleCanvas3DProps {
    myFleet?: { id: string } | null;
    enemyFleets?: { id: string }[];
    alliedFleets?: { id: string }[];
    selectedFleetId?: string | null;
    onFleetSelect?: (id: string) => void;
}

// ─── 3D Scene content ───────────────────────────────────────────────────────
function BattleScene() {
    const { myFleets, enemyFleets, grid, selectedUnitIds, pendingOrders, selectUnit, deselectAll, issueOrder } =
        useBattleStore();

    // ── Flatten all units ────────────────────────────────────────────────────
    const myEnriched = enrichUnits(myFleets, true);
    const enemyEnriched = enrichUnits(enemyFleets, false);
    const allUnits = [...myEnriched, ...enemyEnriched];

    // ── Selected unit ────────────────────────────────────────────────────────
    const selectedUnit = selectedUnitIds.length === 1 ? allUnits.find((u) => u.id === selectedUnitIds[0]) : undefined;
    const moveCells = selectedUnit ? getMovementCells(selectedUnit.gridX, selectedUnit.gridY, 3) : [];

    // ── Flash set ────────────────────────────────────────────────────────────
    const flashSet = useRef<Set<number>>(new Set());
    const [, forceUpdate] = useState(0);

    const triggerFlash = useCallback((id: number) => {
        flashSet.current.add(id);
        forceUpdate((n) => n + 1);
        setTimeout(() => {
            flashSet.current.delete(id);
            forceUpdate((n) => n + 1);
        }, 300);
    }, []);

    // ── 3D Effects ───────────────────────────────────────────────────────────
    const [effects, setEffects] = useState<BattleEffect[]>([]);

    const addEffect = useCallback(
        (fromUnit: EnrichedUnit, toUnit: EnrichedUnit) => {
            const id = `${Date.now()}-${fromUnit.id}-${toUnit.id}`;
            const type = fromUnit.shipClass === 'battleship' ? 'beam' : 'gun';
            setEffects((prev) => [
                ...prev,
                {
                    id,
                    type: type as 'beam' | 'gun',
                    fromGX: fromUnit.gridX,
                    fromGY: fromUnit.gridY,
                    toGX: toUnit.gridX,
                    toGY: toUnit.gridY,
                },
            ]);
            triggerFlash(toUnit.id);
        },
        [triggerFlash]
    );

    const removeEffect = useCallback((id: string) => {
        setEffects((prev) => prev.filter((e) => e.id !== id));
    }, []);

    // ── Unit click handler ───────────────────────────────────────────────────
    const handleUnitClick = useCallback(
        (id: number, e: ThreeEvent<MouseEvent>) => {
            selectUnit(id, e.nativeEvent.shiftKey);
        },
        [selectUnit]
    );

    // ── Unit right-click -> attack ───────────────────────────────────────────
    const handleUnitRightClick = useCallback(
        (targetUnit: EnrichedUnit, _e: ThreeEvent<MouseEvent>) => {
            if (selectedUnitIds.length === 0) return;
            selectedUnitIds.forEach((uid) => {
                issueOrder({ unitId: uid, type: 'attack', targetX: targetUnit.gridX, targetY: targetUnit.gridY });
                const attacker = allUnits.find((u) => u.id === uid);
                if (attacker) addEffect(attacker, targetUnit);
            });
        },
        [selectedUnitIds, issueOrder, allUnits, addEffect]
    );

    // ── Ground click -> move or deselect ─────────────────────────────────────
    const handleGroundClick = useCallback(
        (_e: ThreeEvent<MouseEvent>) => {
            deselectAll();
        },
        [deselectAll]
    );

    const handleGroundRightClick = useCallback(
        (e: ThreeEvent<MouseEvent>) => {
            e.nativeEvent.preventDefault();
            if (selectedUnitIds.length === 0) return;
            const point = e.point;
            const [gx, gy] = GRID_CONSTANTS.worldToGrid(point.x, point.z);
            selectedUnitIds.forEach((uid) => {
                issueOrder({ unitId: uid, type: 'move', targetX: gx, targetY: gy });
            });
        },
        [selectedUnitIds, issueOrder]
    );

    return (
        <>
            {/* Environment */}
            <SpaceBackground />
            <TacticalGrid grid={grid} />

            {/* Ground plane (invisible, for click detection) */}
            <mesh
                rotation={[-Math.PI / 2, 0, 0]}
                position={[0, -0.02, 0]}
                onClick={handleGroundClick}
                onContextMenu={handleGroundRightClick}
            >
                <planeGeometry args={[GRID_CONSTANTS.GRID_EXTENT + 10, GRID_CONSTANTS.GRID_EXTENT + 10]} />
                <meshBasicMaterial visible={false} />
            </mesh>

            {/* Movement highlight */}
            <MovementHighlight cells={moveCells} />

            {/* Selection highlight */}
            {selectedUnit && (
                <SelectionHighlight
                    gridX={selectedUnit.gridX}
                    gridY={selectedUnit.gridY}
                    color={FACTION_GLOW[selectedUnit.factionType] ?? '#FFD700'}
                />
            )}

            {/* Units */}
            {allUnits.map((unit) => (
                <Unit3D
                    key={unit.id}
                    unit={unit}
                    selected={selectedUnitIds.includes(unit.id)}
                    flash={flashSet.current.has(unit.id)}
                    onClick={(e) => handleUnitClick(unit.id, e)}
                    onContextMenu={(e) => handleUnitRightClick(unit, e)}
                />
            ))}

            {/* Pending order arrows */}
            {pendingOrders.map((order, i) => {
                const unit = allUnits.find((u) => u.id === order.unitId);
                if (!unit || order.targetX == null || order.targetY == null) return null;
                return (
                    <OrderArrow3D
                        key={`order-${i}`}
                        fromGX={unit.gridX}
                        fromGY={unit.gridY}
                        toGX={order.targetX}
                        toGY={order.targetY}
                        type={order.type}
                    />
                );
            })}

            {/* Attack effects */}
            <EffectsManager effects={effects} onEffectComplete={removeEffect} />
        </>
    );
}

// ─── Main BattleCanvas3D (exported, drop-in replacement) ────────────────────
export function BattleCanvas3D(_props: BattleCanvas3DProps) {
    const [mounted, setMounted] = useState(false);

    // SSR guard
    if (typeof window === 'undefined') {
        return null;
    }

    return (
        <div className="relative" style={{ width: 640, height: 580 }} onContextMenu={(e) => e.preventDefault()}>
            <Canvas
                gl={{ antialias: true, alpha: false, powerPreference: 'high-performance' }}
                onCreated={() => setMounted(true)}
                style={{ background: '#02020a' }}
            >
                {/* Camera: gin7-style 45-degree top-down */}
                <PerspectiveCamera makeDefault position={[0, 50, 40]} fov={50} near={0.1} far={500} />

                {/* Orbit controls */}
                <OrbitControls
                    target={[0, 0, 0]}
                    minDistance={15}
                    maxDistance={120}
                    maxPolarAngle={Math.PI / 2.2}
                    minPolarAngle={0.2}
                    enableDamping
                    dampingFactor={0.1}
                    mouseButtons={{
                        LEFT: THREE.MOUSE.LEFT,
                        MIDDLE: THREE.MOUSE.MIDDLE,
                        RIGHT: undefined as unknown as THREE.MOUSE,
                    }}
                />

                {/* Scene */}
                <BattleScene />
            </Canvas>

            {/* HUD overlay (matches 2D canvas) */}
            <div className="absolute top-2 left-2 text-[9px] font-mono text-amber-900/50 space-y-0.5 pointer-events-none select-none">
                <div>SCROLL: zoom DRAG: rotate MIDDLE: pan</div>
                <div>CLICK: select unit R-CLICK: move/attack</div>
                <div>SHIFT+CLICK: multi-select</div>
            </div>
            <div className="absolute bottom-2 left-2 text-[8px] font-mono text-amber-900/25 pointer-events-none select-none">
                3D TACTICAL VIEW // {mounted ? 'READY' : 'LOADING...'}
            </div>
        </div>
    );
}
