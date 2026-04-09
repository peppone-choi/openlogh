/**
 * Phase 14 — Plan 14-10 — BattleMap 5-layer restructure (FE-01, D-01, D-04).
 *
 * Previous structure (pre-14-10): 3 Konva layers — background, command-range,
 * units — with a single CRC gated on `selectedUnit`. The CRC was therefore
 * invisible unless the player clicked their own fleet icon, which broke FE-01
 * (multi-CRC under the logged-in officer's command) and left no layer seats
 * for the fog-of-war ghosts (14-11) or the succession FX pulses (14-14).
 *
 * New structure (UI-SPEC Section A + Section E):
 *
 *   Layer 1 — background     (stars, grid) — listening disabled
 *   Layer 2 — fog-ghosts     (placeholder; filled by 14-11's FogLayer)
 *   Layer 3 — command-range  (multi-CRC driven by hierarchy)
 *   Layer 4 — units          (existing TacticalUnitIcon render)
 *   Layer 5 — succession-fx  (placeholder; filled by 14-14's FlagshipFlash)
 *
 * The CRC layer now multi-renders one CommandRangeCircle per entry returned
 * by `findVisibleCrcCommanders(myOfficerId, hierarchy, units, side)` — not per
 * selected-unit click. `selectedUnit` still drives the inner-ring glow via the
 * `isSelected` prop so a clicked friendly commander still reads as the
 * currently-focused CRC.
 *
 * The hierarchy comes from `useTacticalStore` instead of a prop so the fog
 * layer (14-11) and succession layer (14-14) can subscribe to the same slice
 * without redundantly passing it through BattleMap. `tactical/page.tsx` keeps
 * its existing prop call site unchanged.
 */
'use client';

import { useMemo, useCallback } from 'react';
import { Stage, Layer, Rect, Line, Circle } from 'react-konva';
import { TacticalUnitIcon } from './TacticalUnitIcon';
import { CommandRangeCircle } from './CommandRangeCircle';
import { FogLayer } from './FogLayer';
import { useTacticalStore } from '@/stores/tacticalStore';
import { findVisibleCrcCommanders, type VisibleCommander } from '@/lib/commandChain';
import type { TacticalUnit, BattleSide } from '@/types/tactical';

interface BattleMapProps {
    units: TacticalUnit[];
    width?: number;
    height?: number;
    myOfficerId?: number;
    selectedUnitId?: number | null;
    onSelectUnit?: (unitId: number | null) => void;
}

// Game coordinate range
const GAME_W = 1000;
const GAME_H = 1000;

// Number of random stars in background
const STAR_COUNT = 200;

function generateStars(count: number, w: number, h: number) {
    const stars: { x: number; y: number; r: number }[] = [];
    // Use a seeded pattern so stars don't shift on re-render
    for (let i = 0; i < count; i++) {
        const seed = i * 2654435761;
        stars.push({
            x: ((seed >>> 0) % w),
            y: (((seed * 1234567) >>> 0) % h),
            r: 0.5 + (((seed * 987654) >>> 0) % 10) / 10, // 0.5 - 1.4
        });
    }
    return stars;
}

/**
 * Pure helper — computes the list of CRCs to draw for the logged-in officer.
 * Exported for unit tests (BattleMap renders Konva, which is fragile under a
 * `node` vitest environment; this helper is pure and easy to assert on).
 */
export function computeBattleMapVisibleCommanders(
    myOfficerId: number,
    units: TacticalUnit[],
    attackerHierarchy: Parameters<typeof findVisibleCrcCommanders>[1],
    defenderHierarchy: Parameters<typeof findVisibleCrcCommanders>[1],
): VisibleCommander[] {
    if (myOfficerId < 0) return [];
    const myUnit = units.find((u) => u.officerId === myOfficerId);
    if (!myUnit) return [];
    const side: BattleSide = myUnit.side;
    const hierarchy = side === 'ATTACKER' ? attackerHierarchy : defenderHierarchy;
    return findVisibleCrcCommanders(myOfficerId, hierarchy, units, side);
}

export function BattleMap({
    units,
    width = 1000,
    height = 1000,
    myOfficerId,
    selectedUnitId,
    onSelectUnit,
}: BattleMapProps) {
    const scaleX = width / GAME_W;
    const scaleY = height / GAME_H;

    const stars = useMemo(() => generateStars(STAR_COUNT, width, height), [width, height]);

    // Pull the hierarchy directly from the store so 14-11 and 14-14 can subscribe
    // to the same slice without BattleMap becoming a prop-relay. The store field
    // is populated by 14-10's onBattleTick reducer.
    const currentBattle = useTacticalStore((s) => s.currentBattle);

    // Grid lines every 50px
    const gridLines = useMemo(() => {
        const lines: { points: number[]; key: string }[] = [];
        for (let x = 50; x < width; x += 50) {
            lines.push({ points: [x, 0, x, height], key: `v-${x}` });
        }
        for (let y = 50; y < height; y += 50) {
            lines.push({ points: [0, y, width, y], key: `h-${y}` });
        }
        return lines;
    }, [width, height]);

    const selectedUnit = useMemo(
        () => (selectedUnitId != null ? units.find((u) => u.fleetId === selectedUnitId) : undefined),
        [units, selectedUnitId]
    );

    // D-01 — multi-CRC: one ring per visible commander in the logged-in
    // officer's command chain. Pure compute; fog layer (14-11) also calls
    // `findAlliesInMyChain` from the same `commandChain.ts` source of truth.
    const visibleCommanders: VisibleCommander[] = useMemo(
        () =>
            computeBattleMapVisibleCommanders(
                myOfficerId ?? -1,
                units,
                currentBattle?.attackerHierarchy ?? null,
                currentBattle?.defenderHierarchy ?? null,
            ),
        [myOfficerId, units, currentBattle?.attackerHierarchy, currentBattle?.defenderHierarchy],
    );

    const handleStageClick = useCallback(
        (e: { target: { getLayer: () => unknown } }) => {
            // Click on stage background → deselect
            if (e.target === e.target.getLayer()) {
                onSelectUnit?.(null);
            }
        },
        [onSelectUnit]
    );

    const handleBackgroundClick = useCallback(() => {
        onSelectUnit?.(null);
    }, [onSelectUnit]);

    return (
        <div style={{ position: 'relative', display: 'inline-block' }}>
            <Stage
                width={width}
                height={height}
                onClick={handleStageClick}
            >
                {/* ─── Layer 1: Background ──────────────────────────────── */}
                <Layer listening={true} id="background">
                    {/* Space background */}
                    <Rect
                        x={0}
                        y={0}
                        width={width}
                        height={height}
                        fill="#000008"
                        onClick={handleBackgroundClick}
                    />

                    {/* Stars */}
                    {stars.map((star, i) => (
                        <Circle
                            key={i}
                            x={star.x}
                            y={star.y}
                            radius={star.r}
                            fill="white"
                            listening={false}
                        />
                    ))}

                    {/* Grid */}
                    {gridLines.map((line) => (
                        <Line
                            key={line.key}
                            points={line.points}
                            stroke="#1a2040"
                            strokeWidth={0.5}
                            opacity={0.3}
                            listening={false}
                        />
                    ))}
                </Layer>

                {/* ─── Layer 2: Fog ghosts (14-11) ───────────────────────── */}
                {/*
                 * FogLayer reads `tacticalStore.lastSeenEnemyPositions` +
                 * `currentBattle.{attacker,defender}Hierarchy` and renders one
                 * `EnemyGhostIcon` per stale enemy per D-17/D-18/D-20. Non-
                 * listening so ghost icons never steal click events from the
                 * live units / CRCs rendered above.
                 */}
                <Layer listening={false} id="fog-ghosts">
                    <FogLayer
                        myOfficerId={myOfficerId ?? -1}
                        scaleX={scaleX}
                        scaleY={scaleY}
                    />
                </Layer>

                {/* ─── Layer 3: Command-range (multi-CRC) ────────────────── */}
                <Layer listening={true} id="command-range">
                    {visibleCommanders.map((cmd) => {
                        const unit = units.find((u) => u.fleetId === cmd.flagshipFleetId);
                        if (!unit || !unit.isAlive) return null;
                        const minScale = Math.min(scaleX, scaleY);
                        const currentRadius = unit.commandRange * minScale;
                        const maxRadius =
                            (unit.maxCommandRange ?? unit.commandRange) * minScale;
                        return (
                            <CommandRangeCircle
                                key={`crc-${cmd.flagshipFleetId}`}
                                cx={unit.posX * scaleX}
                                cy={unit.posY * scaleY}
                                currentRadius={currentRadius}
                                maxRadius={maxRadius}
                                side={cmd.side}
                                isMine={cmd.isMine}
                                isCommandable={cmd.isCommandable}
                                isSelected={selectedUnit?.fleetId === cmd.flagshipFleetId}
                            />
                        );
                    })}
                </Layer>

                {/* ─── Layer 4: Units ────────────────────────────────────── */}
                <Layer listening={true} id="units">
                    {units.map((unit) => (
                        <TacticalUnitIcon
                            key={unit.fleetId}
                            unit={unit}
                            x={unit.posX * scaleX}
                            y={unit.posY * scaleY}
                            isSelected={unit.fleetId === selectedUnitId}
                            onClick={(u) => onSelectUnit?.(u.fleetId)}
                        />
                    ))}
                </Layer>

                {/* ─── Layer 5: Succession FX (14-14 placeholder) ────────── */}
                {/*
                 * Plan 14-14 mounts <FlagshipFlash /> and succession pulse
                 * rings here. `tacticalStore.activeFlagshipDestroyedFleetIds`
                 * and `activeSuccessionFleetIds` (initialised by 14-10) drive
                 * the render. Kept non-listening so ephemeral FX never steal
                 * click events from the units below.
                 */}
                <Layer listening={false} id="succession-fx">
                    {/* intentional: filled in 14-14 */}
                </Layer>
            </Stage>
        </div>
    );
}
