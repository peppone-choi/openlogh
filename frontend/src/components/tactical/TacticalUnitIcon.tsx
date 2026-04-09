'use client';

import { Group, RegularPolygon, Rect, Text, Circle } from 'react-konva';
import type { TacticalUnit } from '@/types/tactical';

// 아이콘 규칙: △ 삼각형 = 기함부대(isFlagship=true)만, □ 사각형 = 나머지 전부
// ◇ 마름모는 사용하지 않음 (제거됨 — Phase 06-08)
//
// Phase 14 Plan 14-16 (D-35, UI-SPEC Section H):
//   Status marker (●/○/🤖) in the top-right corner of every icon.
//   Online  → filled green disc   #10b981
//   Offline → hollow gray ring    #7a8599
//   NPC     → 🤖 glyph (purple)   #a78bfa
//   Shape (filled / hollow / glyph) is the primary discriminator so a
//   deuteranope user reads the marker correctly even without the color.
//   NPC takes priority over online/offline.
//   Opacity of the main icon is NOT gated on isOnline (D-35 — opacity
//   is reserved for destruction / isAlive signalling).

const SHIP_LETTER: Record<string, string> = {
    flagship: '',
    battleship: 'B',
    fast_battleship: 'B',
    cruiser: 'C',
    strike_cruiser: 'S',
    destroyer: 'D',
    fighter_carrier: 'F',
    torpedo_carrier: 'F',
    carrier: 'A',
    engineering: 'E',
    transport: 'T',
    hospital: 'H',
    landing: 'L',
    civilian: 'M',
};

const FACTION_COLORS: Record<string, string> = {
    empire: '#4466ff',
    alliance: '#ff4444',
    neutral: '#888888',
};

// ── Status marker color + shape constants (D-35) ────────────────────────────
export const STATUS_MARKER_COLOR_ONLINE = '#10b981';
export const STATUS_MARKER_COLOR_OFFLINE = '#7a8599';
export const STATUS_MARKER_COLOR_NPC = '#a78bfa';

/** Shape of the status marker as decided by {@link computeStatusMarker}. */
export type StatusMarkerShape = 'filled-disc' | 'hollow-ring' | 'glyph';

/** Variant of the status marker (D-35 tri-state + NPC-takes-priority). */
export type StatusMarkerVariant = 'online' | 'offline' | 'npc';

/**
 * Pure helper computing the D-35 status marker for a unit.
 *
 * Exported so `TacticalUnitIcon.test.tsx` can assert the visual decisions
 * without mounting the react-konva tree (vitest config uses env=node).
 * All colors, shapes, tooltip copy are derived here — the render body
 * below is a thin wrapper that dispatches on `marker.variant`.
 *
 * Priority rules (D-35):
 *   1. `isNpc === true`  → NPC marker (🤖), regardless of online state
 *   2. `isOnline === false` → Offline marker (○ hollow ring)
 *   3. default / `isOnline !== false` → Online marker (● filled disc)
 *
 * Tooltip copy is sourced from UI-SPEC Copywriting Contract — "NPC / Offline Markers".
 */
export interface StatusMarkerStyle {
    variant: StatusMarkerVariant;
    shape: StatusMarkerShape;
    fill: string;
    stroke?: string;
    opacity: 1; // marker is always fully opaque (D-35)
    glyph?: string;
    tooltip: string;
}

export function computeStatusMarker(unit: TacticalUnit): StatusMarkerStyle {
    // NPC takes priority over online/offline (D-35).
    if (unit.isNpc === true) {
        return {
            variant: 'npc',
            shape: 'glyph',
            fill: STATUS_MARKER_COLOR_NPC,
            opacity: 1,
            glyph: '🤖',
            tooltip: `${unit.officerName} — NPC`,
        };
    }
    // Offline (isOnline explicitly false)
    if (unit.isOnline === false) {
        return {
            variant: 'offline',
            shape: 'hollow-ring',
            fill: 'transparent',
            stroke: STATUS_MARKER_COLOR_OFFLINE,
            opacity: 1,
            tooltip: `${unit.officerName} — 오프라인 (AI 위임)`,
        };
    }
    // Default: online (undefined → treat as online)
    return {
        variant: 'online',
        shape: 'filled-disc',
        fill: STATUS_MARKER_COLOR_ONLINE,
        opacity: 1,
        tooltip: `${unit.officerName} — 접속 중`,
    };
}

// Map factionId to faction type — attacker side = alliance (red), defender = empire (blue) as defaults
// In actual game, we compare factionId to known faction types. Here we use side as fallback.
function getFactionColor(unit: TacticalUnit): string {
    // Use side to determine color if no explicit faction mapping
    return unit.side === 'ATTACKER' ? FACTION_COLORS.alliance : FACTION_COLORS.empire;
}

interface TacticalUnitIconProps {
    unit: TacticalUnit;
    x: number;
    y: number;
    isSelected: boolean;
    onClick: (unit: TacticalUnit) => void;
}

export function TacticalUnitIcon({ unit, x, y, isSelected, onClick }: TacticalUnitIconProps) {
    const shipClass = (unit.unitType ?? 'battleship').toLowerCase();
    const letter = SHIP_LETTER[shipClass] ?? '?';
    const fillColor = getFactionColor(unit);
    // △ 삼각형: 기함부대만 / □ 사각형: 나머지 전부
    const isFlagship = unit.isFlagship === true || shipClass === 'flagship';
    const isDamaged = unit.maxShips > 0 && unit.ships < unit.maxShips * 0.5;
    const fillOpacity = isDamaged ? 0.5 : 1;

    // D-35: resolve status marker (isOnline / isNpc read via unit prop, not
    // separate props — matches the rest of the icon where every flag comes
    // from the unit). The marker renders unconditionally so players can
    // always distinguish player / offline / NPC units at a glance.
    const marker = computeStatusMarker(unit);
    // Marker offset: top-right of the 16x16 icon body, at (+size+4, -size-4)
    // per UI-SPEC Section H ("offset (+4, -4) from icon edge").
    const markerOffsetX = 12;
    const markerOffsetY = -12;

    return (
        <Group
            x={x}
            y={y}
            onClick={() => onClick(unit)}
            onTap={() => onClick(unit)}
            opacity={unit.isAlive ? 1 : 0.2}
        >
            {/* Selection glow ring */}
            {isSelected && (
                <Circle
                    radius={14}
                    stroke="white"
                    strokeWidth={1}
                    opacity={0.6}
                    dash={[2, 2]}
                    fill="transparent"
                />
            )}

            {/* △ 기함부대 */}
            {isFlagship && (
                <RegularPolygon
                    sides={3}
                    radius={11}
                    fill={fillColor}
                    stroke="white"
                    strokeWidth={0.5}
                    opacity={fillOpacity}
                />
            )}
            {/* □ 나머지 전부 */}
            {!isFlagship && (
                <Rect
                    width={16}
                    height={16}
                    offsetX={8}
                    offsetY={8}
                    fill={fillColor}
                    stroke="white"
                    strokeWidth={0.5}
                    opacity={fillOpacity}
                />
            )}

            {/* Letter label */}
            {letter && (
                <Text
                    text={letter}
                    fontSize={8}
                    fontStyle="bold"
                    fill="white"
                    align="center"
                    verticalAlign="middle"
                    offsetX={4}
                    offsetY={4}
                    listening={false}
                />
            )}

            {/* ── Phase 14 D-35: status marker (●/○/🤖) ────────────────── */}
            {/*
             * Marker is a child of the icon Group so it follows the unit
             * without a second draw-loop. `listening={false}` on every
             * marker shape — pointer events belong to the main icon so
             * clicking the marker selects the unit (same as clicking the
             * body). Opacity is always 1 per D-35.
             */}
            {marker.variant === 'npc' && (
                <Text
                    x={markerOffsetX}
                    y={markerOffsetY}
                    text={marker.glyph ?? '🤖'}
                    fontSize={10}
                    fill={marker.fill}
                    listening={false}
                    offsetX={5}
                    offsetY={5}
                />
            )}
            {marker.variant === 'online' && (
                <Circle
                    x={markerOffsetX}
                    y={markerOffsetY}
                    radius={4}
                    fill={marker.fill}
                    opacity={marker.opacity}
                    listening={false}
                />
            )}
            {marker.variant === 'offline' && (
                <Circle
                    x={markerOffsetX}
                    y={markerOffsetY}
                    radius={4}
                    stroke={marker.stroke}
                    strokeWidth={1}
                    fill={marker.fill}
                    opacity={marker.opacity}
                    listening={false}
                />
            )}
        </Group>
    );
}
