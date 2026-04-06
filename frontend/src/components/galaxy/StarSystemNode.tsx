'use client';

import { Group, Circle, Text } from 'react-konva';
import type { StarSystem } from '@/types/galaxy';
import { getFactionColor, getFactionGradient, isFortress } from '@/types/galaxy';
import { FortressIndicator } from './FortressIndicator';

interface StarSystemNodeProps {
    system: StarSystem;
    isSelected: boolean;
    isHovered: boolean;
    onSelect: () => void;
    onHover: (hovering: boolean) => void;
}

/** Determine node radius based on system level; capitals are larger */
function getStarRadius(level: number): number {
    switch (level) {
        case 4:
            return 6;
        case 5:
            return 8;
        case 6:
            return 10;
        case 7:
            return 12;
        case 8:
            return 14;
        default:
            return 8;
    }
}

export function StarSystemNode({
    system,
    isSelected,
    isHovered,
    onSelect,
    onHover,
}: StarSystemNodeProps) {
    const radius = getStarRadius(system.level);
    const baseColor = getFactionColor(system.region);
    const gradient = getFactionGradient(system.region);
    const hasFortress = isFortress(system);

    // Glossy orb: radial gradient from bright inner to darker outer
    const gradientStartPoint = { x: -radius * 0.3, y: -radius * 0.3 };
    const gradientEndPoint = { x: 0, y: 0 };
    const gradientColorStops: (string | number)[] = [
        0,
        gradient.inner,
        0.6,
        baseColor,
        1,
        gradient.outer,
    ];

    // Selection / hover ring
    let strokeColor: string | undefined;
    let strokeWidth = 0;
    if (isSelected) {
        strokeColor = '#ffffff';
        strokeWidth = 2;
    } else if (isHovered) {
        strokeColor = '#cccccc';
        strokeWidth = 1.5;
    }

    // Glow intensity
    const glowBlur = isSelected ? 18 : isHovered ? 12 : 6;
    const glowOpacity = isSelected ? 0.8 : isHovered ? 0.6 : 0.35;

    const labelText = system.nameEn;
    const labelWidth = Math.max(labelText.length * 7 + 12, 60);

    return (
        <Group
            x={system.x}
            y={system.y}
            onClick={onSelect}
            onTap={onSelect}
            onMouseEnter={() => onHover(true)}
            onMouseLeave={() => onHover(false)}
        >
            {/* Fortress hexagon indicator behind node */}
            {hasFortress && (
                <FortressIndicator
                    x={0}
                    y={0}
                    fortressType={system.fortressType}
                />
            )}

            {/* Outer glow */}
            <Circle
                radius={radius + 4}
                fill={baseColor}
                opacity={glowOpacity * 0.4}
                shadowColor={baseColor}
                shadowBlur={glowBlur}
                shadowOpacity={glowOpacity}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* Main glossy orb */}
            <Circle
                radius={radius}
                fillRadialGradientStartPoint={gradientStartPoint}
                fillRadialGradientEndPoint={gradientEndPoint}
                fillRadialGradientStartRadius={0}
                fillRadialGradientEndRadius={radius}
                fillRadialGradientColorStops={gradientColorStops}
                stroke={strokeColor}
                strokeWidth={strokeWidth}
                shadowColor={baseColor}
                shadowBlur={glowBlur}
                shadowOpacity={glowOpacity}
            />

            {/* Specular highlight (small bright spot top-left) */}
            <Circle
                x={-radius * 0.25}
                y={-radius * 0.25}
                radius={radius * 0.35}
                fill="#ffffff"
                opacity={0.25}
                listening={false}
                perfectDrawEnabled={false}
            />

            {/* System name label */}
            <Text
                text={labelText}
                fontSize={10}
                fontFamily="'Segoe UI', Arial, sans-serif"
                fill="#dddddd"
                align="center"
                y={radius + 5}
                x={-labelWidth / 2}
                width={labelWidth}
                listening={false}
            />
        </Group>
    );
}
