'use client';

import { Line } from 'react-konva';

interface StarRouteEdgeProps {
    fromX: number;
    fromY: number;
    toX: number;
    toY: number;
    isHighlighted?: boolean;
}

/**
 * Thin route connection line between star systems.
 * gin7 style: semi-transparent white lines, no dashes.
 * Highlighted when connected to the selected system.
 */
export function StarRouteEdge({
    fromX,
    fromY,
    toX,
    toY,
    isHighlighted = false,
}: StarRouteEdgeProps) {
    return (
        <Line
            points={[fromX, fromY, toX, toY]}
            stroke={isHighlighted ? '#ffffff' : '#ffffff'}
            strokeWidth={isHighlighted ? 1.8 : 1}
            opacity={isHighlighted ? 0.55 : 0.18}
            listening={false}
            perfectDrawEnabled={false}
        />
    );
}
