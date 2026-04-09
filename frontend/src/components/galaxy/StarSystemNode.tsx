'use client';

import { Group, Circle, Rect, Text, RegularPolygon } from 'react-konva';
import type { StarSystem } from '@/types/galaxy';
import { getFactionShadeColor, isCapitalStar } from '@/types/galaxy';

interface StarSystemNodeProps {
    system: StarSystem;
    isSelected: boolean;
    isHovered: boolean;
    /** Mark this as the player's current star system — white hexagon ring overlay */
    isCurrent?: boolean;
    /** Mark this as an active battle site — red hexagon ring overlay */
    hasBattle?: boolean;
    onSelect: () => void;
    onHover: (hovering: boolean) => void;
}

/** Generate 5-step dot palette from any hex color (pixel-art shading) */
function getDotPaletteFromHex(hex: string) {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    const clamp = (v: number) => Math.max(0, Math.min(255, Math.round(v)));
    const toHex = (rv: number, gv: number, bv: number) =>
        `#${clamp(rv).toString(16).padStart(2, '0')}${clamp(gv).toString(16).padStart(2, '0')}${clamp(bv).toString(16).padStart(2, '0')}`;

    return {
        highlight: toHex(r + 80, g + 80, b + 80),
        light: toHex(r + 30, g + 30, b + 30),
        base: hex,
        dark: toHex(r * 0.65, g * 0.65, b * 0.65),
        shadow: toHex(r * 0.35, g * 0.35, b * 0.35),
    };
}

/**
 * Two-tier sizing: capitals (Valhalla / Bharat) render slightly larger so the
 * two political centers are legible at a glance; everything else is uniform.
 */
function getStarRadius(isCapital: boolean): number {
    return isCapital ? 10 : 6;
}

/**
 * Pixel-art dot style star system node.
 * Uses concentric circles with stepped colors instead of smooth gradients.
 * Highlight top-left, shadow bottom-right.
 */
export function StarSystemNode({
    system,
    isSelected,
    isHovered,
    isCurrent = false,
    hasBattle = false,
    onSelect,
    onHover,
}: StarSystemNodeProps) {
    const isCapital = isCapitalStar(system);
    const r = getStarRadius(isCapital);
    // Use 5-shade faction color based on controlStrength; fall back to factionColor or neutral
    const color = getFactionShadeColor(
        system.factionId,
        null,
        system.controlStrength
    ) || system.factionColor || '#444444';
    const palette = getDotPaletteFromHex(color);

    const labelText = system.nameEn;
    const labelWidth = Math.max(labelText.length * 6 + 8, 50);

    // Selection / hover ring
    let strokeColor: string | undefined;
    let strokeWidth = 0;
    if (isSelected) {
        strokeColor = '#ffffff';
        strokeWidth = 2;
    } else if (isHovered) {
        strokeColor = '#cccccc';
        strokeWidth = 1;
    }

    return (
        <Group
            x={system.x}
            y={system.y}
            onClick={onSelect}
            onTap={onSelect}
            onMouseEnter={() => onHover(true)}
            onMouseLeave={() => onHover(false)}
        >
            {/* Hexagon ring overlay: red for active battle, white for the
                player's current system. Battle takes precedence. */}
            {(hasBattle || isCurrent) && (
                <RegularPolygon
                    sides={6}
                    radius={r + 6}
                    rotation={30}
                    stroke={hasBattle ? '#ff4455' : '#ffffff'}
                    strokeWidth={hasBattle ? 2 : 1.5}
                    fillEnabled={false}
                    listening={false}
                    perfectDrawEnabled={false}
                />
            )}

            {/* Outer glow (subtle) */}
            <Circle
                radius={r + 3}
                fill={color}
                opacity={isSelected ? 0.3 : isHovered ? 0.2 : 0.1}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Shadow layer (bottom-right offset) — darkest ring */}
            <Circle
                x={1}
                y={1}
                radius={r}
                fill={palette.shadow}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Base sphere — main color */}
            <Circle
                radius={r}
                fill={palette.base}
                stroke={strokeColor}
                strokeWidth={strokeWidth}
            />

            {/* Dark crescent (bottom-right) — 3/4 circle effect */}
            <Circle
                x={r * 0.15}
                y={r * 0.15}
                radius={r * 0.85}
                fill={palette.dark}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Mid tone — center */}
            <Circle
                radius={r * 0.75}
                fill={palette.base}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Light area (top-left) */}
            <Circle
                x={-r * 0.15}
                y={-r * 0.15}
                radius={r * 0.55}
                fill={palette.light}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Highlight dot (top-left specular) — brightest pixel */}
            <Circle
                x={-r * 0.3}
                y={-r * 0.3}
                radius={r * 0.25}
                fill={palette.highlight}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Tiny white specular peak */}
            <Rect
                x={-r * 0.4 - 1}
                y={-r * 0.4 - 1}
                width={2}
                height={2}
                fill="#ffffff"
                opacity={0.6}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* System name label — DungGeunMo pixel font */}
            <Text
                text={labelText}
                fontSize={9}
                fontFamily="'DungGeunMo', 'Press Start 2P', monospace"
                fill="#cccccc"
                align="center"
                y={r + 4}
                x={-labelWidth / 2}
                width={labelWidth}
                listening={false}
            />
        </Group>
    );
}
