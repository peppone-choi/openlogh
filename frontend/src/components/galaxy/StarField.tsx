'use client';

import { useMemo } from 'react';
import { Circle } from 'react-konva';

interface StarFieldProps {
    width: number;
    height: number;
    count?: number;
    seed?: number;
}

/** Pseudo-random number generator (mulberry32) for deterministic star placement */
function mulberry32(seed: number) {
    let s = seed | 0;
    return () => {
        s = (s + 0x6d2b79f5) | 0;
        let t = Math.imul(s ^ (s >>> 15), 1 | s);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

interface StarPoint {
    x: number;
    y: number;
    radius: number;
    opacity: number;
}

/**
 * Renders a field of small background stars for the galaxy map.
 * Uses deterministic placement so stars don't shift on re-render.
 */
export function StarField({ width, height, count = 300, seed = 42 }: StarFieldProps) {
    const stars = useMemo<StarPoint[]>(() => {
        const rng = mulberry32(seed);
        const result: StarPoint[] = [];
        // Extend star field well beyond visible area for pan/zoom
        const extentX = width * 4;
        const extentY = height * 4;
        const originX = -extentX / 2;
        const originY = -extentY / 2;

        for (let i = 0; i < count; i++) {
            result.push({
                x: originX + rng() * extentX,
                y: originY + rng() * extentY,
                radius: 0.3 + rng() * 1.2,
                opacity: 0.15 + rng() * 0.55,
            });
        }
        return result;
    }, [width, height, count, seed]);

    return (
        <>
            {stars.map((star, i) => (
                <Circle
                    key={i}
                    x={star.x}
                    y={star.y}
                    radius={star.radius}
                    fill="#ffffff"
                    opacity={star.opacity}
                    listening={false}
                    perfectDrawEnabled={false}
                />
            ))}
        </>
    );
}
