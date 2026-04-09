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
import { FlagshipFlash } from './FlagshipFlash';
import { SuccessionCountdownOverlay } from './SuccessionCountdownOverlay';
import { useTacticalStore } from '@/stores/tacticalStore';
import { useGalaxyStore } from '@/stores/galaxyStore';
import { findVisibleCrcCommanders, type VisibleCommander } from '@/lib/commandChain';
import { canCommandUnit } from '@/lib/canCommandUnit';
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
 * Phase 14 Plan 14-16 (D-37) — mission target line clamp helper.
 *
 * The NPC unit's mission target star system may be far outside the tactical
 * viewport. In that case we still want a directional hint from the unit to
 * the viewport edge so players can tell "their target is off that way"
 * without rendering a line into empty space. This helper returns an
 * end-point clamped to the viewport rectangle, or null if the target is
 * on-screen (the caller draws a direct line instead) OR if any required
 * coordinate is missing (data is optional — see TacticalUnit comment).
 *
 * Exported for unit tests (no react-konva mount in vitest env=node).
 */
export interface MissionLineClampArgs {
    unitX: number;
    unitY: number;
    targetX: number;
    targetY: number;
    viewportWidth: number;
    viewportHeight: number;
}

export interface MissionLineClampResult {
    endX: number;
    endY: number;
    isClamped: boolean; // true if target was off-screen
}

export function clampMissionLineEnd({
    unitX,
    unitY,
    targetX,
    targetY,
    viewportWidth,
    viewportHeight,
}: MissionLineClampArgs): MissionLineClampResult {
    const inBounds =
        targetX >= 0 && targetX <= viewportWidth && targetY >= 0 && targetY <= viewportHeight;
    if (inBounds) {
        return { endX: targetX, endY: targetY, isClamped: false };
    }
    // Off-screen — parametric line clip to viewport rectangle.
    const dx = targetX - unitX;
    const dy = targetY - unitY;
    if (dx === 0 && dy === 0) {
        return { endX: unitX, endY: unitY, isClamped: true };
    }
    // Find parameter t in (0, 1] where the ray hits the nearest edge.
    const candidates: number[] = [];
    if (dx !== 0) {
        candidates.push((0 - unitX) / dx);
        candidates.push((viewportWidth - unitX) / dx);
    }
    if (dy !== 0) {
        candidates.push((0 - unitY) / dy);
        candidates.push((viewportHeight - unitY) / dy);
    }
    // Smallest positive t up to 1 is the first edge intersection along the ray.
    const positive = candidates.filter((t) => t > 0 && t <= 1);
    const t = positive.length > 0 ? Math.min(...positive) : 1;
    return {
        endX: unitX + dx * t,
        endY: unitY + dy * t,
        isClamped: true,
    };
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

    // Phase 14 Plan 14-15 (FE-04 / D-14) — active flagship-destroyed flashes.
    // One 0.5s Konva ring per entry, pruned by tacticalStore.onBattleTick.
    const activeFlagshipDestroyedFleetIds = useTacticalStore(
        (s) => s.activeFlagshipDestroyedFleetIds,
    );

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

    // ── Phase 14 D-37: NPC mission target line ─────────────────────────
    // Only rendered when the currently selected unit is NPC-controlled AND
    // has targetStarSystemId populated. The backend TacticalUnitDto does
    // NOT currently surface targetStarSystemId, so in the default DTO shape
    // this line never draws — the scaffolding is in place so a future
    // backend plan can enable it by wiring the field through toUnitDto.
    //
    // The line goes from the unit's canvas position toward the target star
    // system's projected position. Galaxy-map coordinates live in an
    // unrelated projection from tactical-map coordinates, so for the
    // directional hint we project the galaxy posX/posY through the same
    // scale transform and clamp to the viewport edge via
    // clampMissionLineEnd — this produces a stable "target is that way"
    // indicator without requiring a second coordinate-system conversion.
    const getSystem = useGalaxyStore((s) => s.getSystem);
    const missionLine = useMemo(() => {
        if (!selectedUnit || !selectedUnit.isNpc) return null;
        if (!selectedUnit.missionObjective) return null;
        const targetStarSystemId = selectedUnit.targetStarSystemId;
        if (targetStarSystemId == null) return null;
        const targetSystem = getSystem?.(targetStarSystemId);
        if (!targetSystem) return null;
        const unitCanvasX = selectedUnit.posX * scaleX;
        const unitCanvasY = selectedUnit.posY * scaleY;
        const targetCanvasX = ((targetSystem as { posX?: number }).posX ?? unitCanvasX) * scaleX;
        const targetCanvasY = ((targetSystem as { posY?: number }).posY ?? unitCanvasY) * scaleY;
        return {
            startX: unitCanvasX,
            startY: unitCanvasY,
            ...clampMissionLineEnd({
                unitX: unitCanvasX,
                unitY: unitCanvasY,
                targetX: targetCanvasX,
                targetY: targetCanvasY,
                viewportWidth: width,
                viewportHeight: height,
            }),
        };
    }, [selectedUnit, getSystem, scaleX, scaleY, width, height]);

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

    // Phase 14 — Plan 14-14 (FE-03, D-11) — compute the logged-in officer's
    // side + hierarchy ONCE per render so the units layer can cheaply ask
    // canCommandUnit(myOfficerId, myHierarchy, unit) for every allied unit.
    // Enemy units short-circuit to `isUnderMyCommand=false` below so we
    // never even enter the gating function for them.
    const myHierarchy = useMemo(() => {
        if (myOfficerId == null) return null;
        const myUnit = units.find((u) => u.officerId === myOfficerId);
        if (!myUnit) return null;
        return myUnit.side === 'ATTACKER'
            ? currentBattle?.attackerHierarchy ?? null
            : currentBattle?.defenderHierarchy ?? null;
    }, [myOfficerId, units, currentBattle?.attackerHierarchy, currentBattle?.defenderHierarchy]);

    const mySide = useMemo<BattleSide | null>(() => {
        if (myOfficerId == null) return null;
        const myUnit = units.find((u) => u.officerId === myOfficerId);
        return myUnit?.side ?? null;
    }, [myOfficerId, units]);

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
                    {/*
                     * Phase 14 D-37 — NPC mission target dashed line.
                     * Rendered as the first child of the units layer so it
                     * sits beneath the icons. listening={false} so the line
                     * never steals click events from the unit icons above.
                     * Color matches the NPC marker (#a78bfa) per D-35 so
                     * the visual thread is consistent.
                     */}
                    {missionLine && (
                        <Line
                            points={[
                                missionLine.startX,
                                missionLine.startY,
                                missionLine.endX,
                                missionLine.endY,
                            ]}
                            stroke="#a78bfa"
                            strokeWidth={1}
                            dash={[5, 5]}
                            opacity={0.7}
                            listening={false}
                        />
                    )}
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

                {/* ─── Layer 5: Succession FX (14-15) ────────────────────── */}
                {/*
                 * Plan 14-15 mounts <FlagshipFlash /> ring flashes here. Each
                 * entry in `tacticalStore.activeFlagshipDestroyedFleetIds` is
                 * rendered as a 0.5s Konva ring at the destroyed unit's
                 * current screen position (or last-known if already removed).
                 * Kept non-listening so ephemeral FX never steal click events
                 * from the units below.
                 *
                 * The HTML succession countdown overlays render as siblings
                 * of the Stage below this closing tag — they live outside
                 * Konva so the monospaced pill inherits native font
                 * rendering per UI-SPEC Section D Phase 2.
                 */}
                <Layer listening={false} id="succession-fx">
                    {activeFlagshipDestroyedFleetIds.map((entry) => {
                        const unit = units.find((u) => u.fleetId === entry.fleetId);
                        if (!unit) return null;
                        return (
                            <FlagshipFlash
                                key={`flash-${entry.fleetId}-${entry.expiresAt}`}
                                cx={unit.posX * scaleX}
                                cy={unit.posY * scaleY}
                            />
                        );
                    })}
                </Layer>
            </Stage>

            {/*
             * Phase 14 Plan 14-15 (FE-04 / D-13) — HTML succession countdown
             * overlays. Rendered as absolute-positioned siblings of the Stage
             * (inside the same relative-positioned wrapper <div>) so the pill
             * floats above the canvas without competing with Konva picking.
             * One overlay per unit whose successionState === 'PENDING_SUCCESSION'.
             */}
            {units
                .filter(
                    (u) =>
                        u.isAlive &&
                        u.successionState === 'PENDING_SUCCESSION' &&
                        u.successionTicksRemaining != null,
                )
                .map((u) => (
                    <SuccessionCountdownOverlay
                        key={`succ-overlay-${u.fleetId}`}
                        screenX={u.posX * scaleX}
                        screenY={u.posY * scaleY}
                        ticksRemaining={u.successionTicksRemaining ?? 0}
                    />
                ))}
        </div>
    );
}
