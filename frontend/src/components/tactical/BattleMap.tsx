'use client';

import { useMemo, useCallback } from 'react';
import { Stage, Layer, Rect, Line, Circle } from 'react-konva';
import { TacticalUnitIcon } from './TacticalUnitIcon';
import { CommandRangeCircle } from './CommandRangeCircle';
import type { TacticalUnit } from '@/types/tactical';

interface BattleMapProps {
    units: TacticalUnit[];
    width?: number;
    height?: number;
    myOfficerId?: number;
    selectedUnitId?: number | null;
    onSelectUnit?: (unitId: number | null) => void;
}

// Game coordinate range
const GAME_W = 1000;
const GAME_H = 1000;

// Number of random stars in background
const STAR_COUNT = 200;

function generateStars(count: number, w: number, h: number) {
    const stars: { x: number; y: number; r: number }[] = [];
    // Use a seeded pattern so stars don't shift on re-render
    for (let i = 0; i < count; i++) {
        const seed = i * 2654435761;
        stars.push({
            x: ((seed >>> 0) % w),
            y: (((seed * 1234567) >>> 0) % h),
            r: 0.5 + (((seed * 987654) >>> 0) % 10) / 10, // 0.5 - 1.4
        });
    }
    return stars;
}

export function BattleMap({
    units,
    width = 1000,
    height = 1000,
    myOfficerId,
    selectedUnitId,
    onSelectUnit,
}: BattleMapProps) {
    const scaleX = width / GAME_W;
    const scaleY = height / GAME_H;

    const stars = useMemo(() => generateStars(STAR_COUNT, width, height), [width, height]);

    // Grid lines every 50px
    const gridLines = useMemo(() => {
        const lines: { points: number[]; key: string }[] = [];
        for (let x = 50; x < width; x += 50) {
            lines.push({ points: [x, 0, x, height], key: `v-${x}` });
        }
        for (let y = 50; y < height; y += 50) {
            lines.push({ points: [0, y, width, y], key: `h-${y}` });
        }
        return lines;
    }, [width, height]);

    const selectedUnit = useMemo(
        () => (selectedUnitId != null ? units.find((u) => u.fleetId === selectedUnitId) : undefined),
        [units, selectedUnitId]
    );

    const handleStageClick = useCallback(
        (e: { target: { getLayer: () => unknown } }) => {
            // Click on stage background → deselect
            if (e.target === e.target.getLayer()) {
                onSelectUnit?.(null);
            }
        },
        [onSelectUnit]
    );

    const handleBackgroundClick = useCallback(() => {
        onSelectUnit?.(null);
    }, [onSelectUnit]);

    return (
        <div style={{ position: 'relative', display: 'inline-block' }}>
            <Stage
                width={width}
                height={height}
                onClick={handleStageClick}
            >
                {/* Background layer */}
                <Layer>
                    {/* Space background */}
                    <Rect
                        x={0}
                        y={0}
                        width={width}
                        height={height}
                        fill="#000008"
                        onClick={handleBackgroundClick}
                    />

                    {/* Stars */}
                    {stars.map((star, i) => (
                        <Circle
                            key={i}
                            x={star.x}
                            y={star.y}
                            radius={star.r}
                            fill="white"
                            listening={false}
                        />
                    ))}

                    {/* Grid */}
                    {gridLines.map((line) => (
                        <Line
                            key={line.key}
                            points={line.points}
                            stroke="#1a2040"
                            strokeWidth={0.5}
                            opacity={0.3}
                            listening={false}
                        />
                    ))}
                </Layer>

                {/* Command range layer */}
                <Layer>
                    {selectedUnit && selectedUnit.commandRange > 0 && (
                        <CommandRangeCircle
                            x={selectedUnit.posX * scaleX}
                            y={selectedUnit.posY * scaleY}
                            radius={0}
                            maxRadius={selectedUnit.commandRange * Math.min(scaleX, scaleY)}
                            side={selectedUnit.side}
                        />
                    )}
                </Layer>

                {/* Units layer */}
                <Layer>
                    {units.map((unit) => (
                        <TacticalUnitIcon
                            key={unit.fleetId}
                            unit={unit}
                            x={unit.posX * scaleX}
                            y={unit.posY * scaleY}
                            isSelected={unit.fleetId === selectedUnitId}
                            onClick={(u) => onSelectUnit?.(u.fleetId)}
                        />
                    ))}
                </Layer>
            </Stage>
        </div>
    );
}
