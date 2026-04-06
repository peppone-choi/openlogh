'use client';

import { Line } from 'react-konva';

interface StarRouteEdgeProps {
    fromX: number;
    fromY: number;
    toX: number;
    toY: number;
    isHighlighted?: boolean;
}

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
            stroke={isHighlighted ? '#6699cc' : '#334455'}
            strokeWidth={isHighlighted ? 2 : 1}
            opacity={isHighlighted ? 0.8 : 0.4}
            dash={[6, 4]}
            listening={false}
        />
    );
}
