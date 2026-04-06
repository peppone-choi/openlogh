'use client';

import { Group, Circle, Rect, Text } from 'react-konva';
import type { StarSystem } from '@/types/galaxy';
import { getFactionColor, isFortress } from '@/types/galaxy';
import { FortressIndicator } from './FortressIndicator';

interface StarSystemNodeProps {
    system: StarSystem;
    isSelected: boolean;
    isHovered: boolean;
    onSelect: () => void;
    onHover: (hovering: boolean) => void;
}

/** Pixel-art style color steps: highlight → base → shadow */
function getDotPalette(faction: string) {
    switch (faction) {
        case 'empire':
            return {
                highlight: '#a0b4ff',
                light: '#7088e0',
                base: '#5a6ee0',
                dark: '#3a4ab0',
                shadow: '#252e70',
            };
        case 'alliance':
            return {
                highlight: '#ffa0b0',
                light: '#e08090',
                base: '#d06878',
                dark: '#a04858',
                shadow: '#602838',
            };
        case 'fezzan':
            return {
                highlight: '#d0d0d8',
                light: '#b0b0b8',
                base: '#9898a0',
                dark: '#686870',
                shadow: '#404048',
            };
        default:
            return {
                highlight: '#c0c0c8',
                light: '#a0a0a8',
                base: '#808088',
                dark: '#585860',
                shadow: '#303038',
            };
    }
}

function getStarRadius(level: number): number {
    switch (level) {
        case 4: return 5;
        case 5: return 6;
        case 6: return 7;
        case 7: return 8;
        case 8: return 9;
        default: return 6;
    }
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
    onSelect,
    onHover,
}: StarSystemNodeProps) {
    const r = getStarRadius(system.level);
    const palette = getDotPalette(system.region);
    const baseColor = getFactionColor(system.region);
    const hasFortress = isFortress(system);

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
            {/* Fortress indicator behind node */}
            {hasFortress && (
                <FortressIndicator x={0} y={0} fortressType={system.fortressType} />
            )}

            {/* Outer glow (subtle) */}
            <Circle
                radius={r + 3}
                fill={baseColor}
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
