/**
 * Phase 14 FE-05 / D-17 / D-20 — single enemy ghost icon.
 *
 * Renders a stale last-seen enemy as a dashed-outline shape with a tick-age
 * stamp below it. Shape mirrors TacticalUnitIcon's rule: △ triangle for
 * flagship-class units, □ square for everything else. Color is grayscale
 * regardless of side (per UI-SPEC Section E — identity deduced from shape
 * + last-known position, not color).
 *
 * Opacity is computed by the caller via `ghostOpacity(currentTick, entry.tick)`
 * and passed in as a prop so this component stays pure / stateless.
 */
'use client';

import { Group, Text, RegularPolygon, Rect } from 'react-konva';
import type { GhostEntry } from '@/lib/fogOfWar';

export interface EnemyGhostIconProps {
    /** Enemy fleetId this ghost represents (used as React key by the parent). */
    fleetId: number;
    /** The stored last-seen entry (position, ships, unitType, tick). */
    entry: GhostEntry;
    /** Pre-scaled screen x coordinate (caller multiplies by scaleX). */
    cx: number;
    /** Pre-scaled screen y coordinate (caller multiplies by scaleY). */
    cy: number;
    /** 0..1 opacity from `ghostOpacity()` — caller-computed. */
    opacity: number;
    /** Tick age displayed in the Korean stamp ("{n}틱 전"). */
    ticksAgo: number;
    /** True when age > GHOST_OPACITY_RAMP_START — appends " · 정보 노후". */
    isStale?: boolean;
}

// D-17 / UI-SPEC Section E — all ghosts render in neutral gray regardless
// of side. Identity is conveyed by shape + last-known position, not color.
const GHOST_STROKE_COLOR = '#888888';
// --muted-foreground token from globals.css (mirrored here so the Konva
// Text node doesn't need CSS-variable resolution at render time).
const GHOST_STAMP_COLOR = '#7a8599';
const GHOST_SHAPE_SIZE = 12;

export function EnemyGhostIcon({
    fleetId: _fleetId,
    entry,
    cx,
    cy,
    opacity,
    ticksAgo,
    isStale = false,
}: EnemyGhostIconProps) {
    // Shape rule — mirror TacticalUnitIcon: △ for flagship-class,
    // □ for everything else. 'battleship' also renders as △ per UI-SPEC
    // "flagship or capital" reading.
    const unitType = (entry.unitType ?? '').toLowerCase();
    const isFlagshipShape = unitType === 'flagship' || unitType === 'battleship';

    return (
        <Group x={cx} y={cy} opacity={opacity} listening={false}>
            {isFlagshipShape ? (
                <RegularPolygon
                    sides={3}
                    radius={GHOST_SHAPE_SIZE}
                    stroke={GHOST_STROKE_COLOR}
                    strokeWidth={1}
                    dash={[3, 3]}
                    fillEnabled={false}
                />
            ) : (
                <Rect
                    x={-GHOST_SHAPE_SIZE}
                    y={-GHOST_SHAPE_SIZE}
                    width={GHOST_SHAPE_SIZE * 2}
                    height={GHOST_SHAPE_SIZE * 2}
                    stroke={GHOST_STROKE_COLOR}
                    strokeWidth={1}
                    dash={[3, 3]}
                    fillEnabled={false}
                />
            )}
            <Text
                x={-30}
                y={GHOST_SHAPE_SIZE + 3}
                width={60}
                align="center"
                text={`${ticksAgo}틱 전${isStale ? ' · 정보 노후' : ''}`}
                fontSize={9}
                fill={GHOST_STAMP_COLOR}
            />
        </Group>
    );
}

export default EnemyGhostIcon;
