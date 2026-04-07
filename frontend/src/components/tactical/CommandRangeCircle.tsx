'use client';

import { useEffect, useRef } from 'react';
import { Circle, Group } from 'react-konva';
import Konva from 'konva';

const FACTION_COLORS: Record<string, string> = {
    empire: '#4466ff',
    alliance: '#ff4444',
    neutral: '#888888',
};

interface CommandRangeCircleProps {
    x: number;
    y: number;
    radius: number;
    maxRadius: number;
    side: 'ATTACKER' | 'DEFENDER';
}

export function CommandRangeCircle({ x, y, radius, maxRadius, side }: CommandRangeCircleProps) {
    const strokeColor = side === 'ATTACKER' ? FACTION_COLORS.alliance : FACTION_COLORS.empire;
    const innerCircleRef = useRef<Konva.Circle>(null);
    const animRef = useRef<Konva.Animation | null>(null);

    useEffect(() => {
        const circle = innerCircleRef.current;
        if (!circle) return;

        // Animate radius from 0 to maxRadius over 3 seconds, then reset
        const startTime = Date.now();
        const duration = 3000;

        animRef.current = new Konva.Animation((frame) => {
            if (!frame) return;
            const elapsed = (Date.now() - startTime) % duration;
            const progress = elapsed / duration;
            circle.radius(progress * maxRadius);
        }, circle.getLayer());

        animRef.current.start();

        return () => {
            animRef.current?.stop();
        };
    }, [maxRadius]);

    return (
        <Group x={x} y={y}>
            {/* Animated inner expanding circle */}
            <Circle
                ref={innerCircleRef}
                radius={radius}
                fill="transparent"
                stroke={strokeColor}
                strokeWidth={1}
                opacity={0.4}
            />
            {/* Static outer boundary circle */}
            <Circle
                radius={maxRadius}
                fill="transparent"
                stroke={strokeColor}
                strokeWidth={0.5}
                dash={[4, 4]}
                opacity={0.2}
            />
        </Group>
    );
}
