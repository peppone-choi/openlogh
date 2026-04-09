/**
 * Phase 14 D-03 / D-04 / D-11 / FE-01 — Server-driven CRC ring.
 *
 * REMOVED from the pre-Phase-14 implementation:
 *   - Local 3-second Konva imperative animation loop that tweened the inner
 *     radius independently of the server state. D-03 mandates the inner radius
 *     come from the server's `commandRange` tick value (BattleMap interpolates
 *     between consecutive ticks with requestAnimationFrame — see 14-10).
 *   - Single-instance assumption gated on `selectedUnit`. D-04 requires the
 *     component be multi-renderable so BattleMap can draw one CRC per visible
 *     commander (fleet commander + sub-fleet commanders).
 *
 * NEW:
 *   - Hierarchy-aware props: `isMine`, `isCommandable`, `isHovered`,
 *     `isSelected` drive the D-11 gold hint ring and the D-02 HSL-lighten
 *     hover/select variants.
 *   - Pure `computeRingStyle` helper is exported alongside the component so
 *     unit tests can assert visual decisions without mounting Konva under a
 *     `node` vitest environment.
 *
 * Faction color rule (D-02):
 *   empire = #4466ff  (cobalt)
 *   alliance = #ff4444 (signal red)
 *   fezzan = #888888 (neutral gray)
 * Hover / select → HSL L+15 lighten, no new hex literals.
 */
'use client';

import { Circle, Group } from 'react-konva';
import { useMemo } from 'react';
import type { BattleSide } from '@/types/tactical';
import {
    FACTION_TACTICAL_COLORS,
    sideToDefaultColor,
    lightenHex,
} from '@/lib/tacticalColors';

// Re-export so BattleMap + fixtures can read the palette via a single import.
export { FACTION_TACTICAL_COLORS };

export interface CommandRangeCircleProps {
    /** Scaled center X (screen pixels) — typically `unit.posX * scaleX`. */
    cx: number;
    /** Scaled center Y (screen pixels) — typically `unit.posY * scaleY`. */
    cy: number;
    /**
     * Server-provided current radius (scaled to screen pixels).
     * D-03: the interpolated tick value of `TacticalUnit.commandRange`.
     */
    currentRadius: number;
    /**
     * Server-provided maximum radius ceiling (scaled to screen pixels).
     * D-04 / FE-01: comes from `TacticalUnit.maxCommandRange` added in 14-01.
     */
    maxRadius: number;
    /** Which side this commander belongs to. */
    side: BattleSide;
    /** D-11 layer a — commander is the logged-in officer (draws gold hint ring). */
    isMine?: boolean;
    /**
     * D-01 — logged-in officer can issue orders through this commander.
     * Currently used only by BattleMap to decide *whether* to render a CRC at
     * all; reserved here for future per-ring visual cues (e.g. dashed inner
     * when `false`) without breaking the prop contract.
     */
    isCommandable?: boolean;
    /** Hover state relayed from BattleMap (for tooltip + stroke lighten). */
    isHovered?: boolean;
    /** Selected state relayed from BattleMap (for glow + stroke lighten). */
    isSelected?: boolean;
}

interface RingState {
    currentRadius: number;
    maxRadius: number;
    isHovered: boolean;
    isSelected: boolean;
    isMine: boolean;
}

interface InnerRingDescriptor {
    radius: number;
    stroke: string;
    strokeWidth: number;
    opacity: number;
    shadowBlur: number;
    shadowColor: string;
}

interface OuterRingDescriptor {
    radius: number;
    stroke: string;
    strokeWidth: number;
    opacity: number;
    dash: [number, number];
}

interface GoldHintDescriptor {
    radius: number;
    stroke: string;
    strokeWidth: number;
    opacity: number;
}

export interface ComputedRingStyle {
    visible: boolean;
    inner: InnerRingDescriptor;
    outer: OuterRingDescriptor;
    goldHint: GoldHintDescriptor | null;
}

/**
 * Pure helper — computes every visual property of the CRC from server state
 * and UI hover/select flags. Exported so tests can assert visual decisions
 * without mounting react-konva (vitest runs in `environment: 'node'`).
 *
 * Layering note: the returned descriptors are consumed top-to-bottom by the
 * component below. BattleMap (14-10) never touches these — it only passes
 * props.
 */
export function computeRingStyle(
    side: BattleSide,
    state: RingState
): ComputedRingStyle {
    const baseColor = sideToDefaultColor(side);
    const active = state.isHovered || state.isSelected;
    const activeColor = active ? lightenHex(baseColor, 15) : baseColor;

    // Defensive clamp — server may briefly emit 0 after a command reset; the
    // renderer should return `null` in that window so BattleMap doesn't flash
    // a zero-radius Circle. Mirrors D-03 "instantly snap to 0 then resume".
    const visible = state.currentRadius > 0 && state.maxRadius > 0;

    // UI-SPEC A. base-state stroke defaults
    let innerStrokeWidth = 1.5;
    let innerOpacity = 0.5;
    let shadowBlur = 0;

    if (state.isSelected) {
        // UI-SPEC A. selected-state overrides
        innerStrokeWidth = 2.5;
        innerOpacity = 0.9;
        shadowBlur = 6;
    } else if (state.isHovered) {
        // UI-SPEC A. hover-state overrides
        innerStrokeWidth = 2.0;
        innerOpacity = 0.8;
    }

    const inner: InnerRingDescriptor = {
        radius: state.currentRadius,
        stroke: activeColor,
        strokeWidth: innerStrokeWidth,
        opacity: innerOpacity,
        shadowBlur,
        shadowColor: activeColor,
    };

    const outer: OuterRingDescriptor = {
        radius: state.maxRadius,
        stroke: baseColor,
        strokeWidth: 0.5,
        opacity: 0.25,
        dash: [4, 4],
    };

    // D-11 layer a: gold hint ring when this commander is the logged-in
    // officer. A primary 2px gold border lives on `TacticalUnitIcon`; the CRC
    // contributes a subtle 1px-wide 0.3-opacity hint so the command radius
    // itself reads as "yours" without overpowering the faction color.
    const goldHint: GoldHintDescriptor | null = state.isMine
        ? {
              radius: state.currentRadius + 2,
              stroke: '#f59e0b',
              strokeWidth: 1,
              opacity: 0.3,
          }
        : null;

    return { visible, inner, outer, goldHint };
}

export function CommandRangeCircle({
    cx,
    cy,
    currentRadius,
    maxRadius,
    side,
    isMine = false,
    isCommandable: _isCommandable = false,
    isHovered = false,
    isSelected = false,
}: CommandRangeCircleProps) {
    const style = useMemo(
        () =>
            computeRingStyle(side, {
                currentRadius,
                maxRadius,
                isHovered,
                isSelected,
                isMine,
            }),
        [side, currentRadius, maxRadius, isHovered, isSelected, isMine]
    );

    if (!style.visible) return null;

    return (
        <Group>
            {/* Outer dashed max-range ring — D-04 ceiling reference, low contrast. */}
            <Circle
                x={cx}
                y={cy}
                radius={style.outer.radius}
                stroke={style.outer.stroke}
                strokeWidth={style.outer.strokeWidth}
                dash={style.outer.dash}
                opacity={style.outer.opacity}
                listening={false}
            />

            {/* Inner server-driven ring — D-03 current range. Interactive for hover. */}
            <Circle
                x={cx}
                y={cy}
                radius={style.inner.radius}
                stroke={style.inner.stroke}
                strokeWidth={style.inner.strokeWidth}
                opacity={style.inner.opacity}
                shadowBlur={style.inner.shadowBlur}
                shadowColor={style.inner.shadowColor}
                listening={true}
            />

            {/* D-11 layer a (CRC-level hint): faint gold ring if this is MY commander. */}
            {style.goldHint && (
                <Circle
                    x={cx}
                    y={cy}
                    radius={style.goldHint.radius}
                    stroke={style.goldHint.stroke}
                    strokeWidth={style.goldHint.strokeWidth}
                    opacity={style.goldHint.opacity}
                    listening={false}
                />
            )}
        </Group>
    );
}

export default CommandRangeCircle;
