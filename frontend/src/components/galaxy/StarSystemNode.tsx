'use client';

import { Group, Circle, Text } from 'react-konva';
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

function getStarRadius(level: number): number {
    switch (level) {
        case 4:
            return 4;
        case 5:
            return 5;
        case 6:
            return 7;
        case 7:
            return 9;
        case 8:
            return 11;
        default:
            return 5;
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
    const fillColor = getFactionColor(system.region);
    const hasFortress = isFortress(system);

    let strokeColor: string | undefined;
    let strokeWidth = 0;
    if (isSelected) {
        strokeColor = '#ffffff';
        strokeWidth = 2;
    } else if (isHovered) {
        strokeColor = '#aaaaaa';
        strokeWidth = 1.5;
    }

    const labelText = system.nameKo;
    const labelWidth = labelText.length * 12 + 8;

    return (
        <Group
            x={system.x}
            y={system.y}
            onClick={onSelect}
            onTap={onSelect}
            onMouseEnter={() => onHover(true)}
            onMouseLeave={() => onHover(false)}
        >
            {hasFortress && (
                <FortressIndicator
                    x={0}
                    y={0}
                    fortressType={system.fortressType}
                />
            )}
            <Circle
                radius={radius}
                fill={fillColor}
                stroke={strokeColor}
                strokeWidth={strokeWidth}
                shadowColor={fillColor}
                shadowBlur={isSelected ? 12 : isHovered ? 8 : 4}
                shadowOpacity={0.6}
            />
            <Text
                text={labelText}
                fontSize={10}
                fill="#cccccc"
                align="center"
                y={radius + 4}
                x={-labelWidth / 2}
                width={labelWidth}
                listening={false}
            />
        </Group>
    );
}
