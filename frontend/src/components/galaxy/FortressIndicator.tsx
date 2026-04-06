'use client';

import { RegularPolygon } from 'react-konva';
import type { FortressType } from '@/types/galaxy';

interface FortressIndicatorProps {
    x: number;
    y: number;
    fortressType: FortressType;
    size?: number;
}

function getFortressSize(fortressType: FortressType, baseSize?: number): number {
    if (baseSize != null) return baseSize;
    switch (fortressType) {
        case 'ISERLOHN':
        case 'GEIERSBURG':
            return 18;
        case 'RENTENBERG':
        case 'GARMISCH':
            return 14;
        default:
            return 12;
    }
}

export function FortressIndicator({
    x,
    y,
    fortressType,
    size,
}: FortressIndicatorProps) {
    const radius = getFortressSize(fortressType, size);

    return (
        <RegularPolygon
            x={x}
            y={y}
            sides={6}
            radius={radius}
            fill="#ffaa00"
            opacity={0.5}
            rotation={30}
            listening={false}
        />
    );
}
