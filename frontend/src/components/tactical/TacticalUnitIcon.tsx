'use client';

import { Group, RegularPolygon, Rect, Text, Circle } from 'react-konva';
import type { TacticalUnit } from '@/types/tactical';

type ShapeType = 'triangle' | 'square' | 'diamond';

const SHIP_SHAPE: Record<string, ShapeType> = {
    flagship: 'triangle',
    battleship: 'square',
    fast_battleship: 'square',
    cruiser: 'square',
    strike_cruiser: 'square',
    engineering: 'square',
    transport: 'square',
    hospital: 'square',
    landing: 'square',
    civilian: 'square',
    destroyer: 'diamond',
    fighter_carrier: 'triangle',
    torpedo_carrier: 'triangle',
    carrier: 'triangle',
};

const SHIP_LETTER: Record<string, string> = {
    flagship: '',
    battleship: 'B',
    fast_battleship: 'B',
    cruiser: 'C',
    strike_cruiser: 'S',
    destroyer: 'D',
    fighter_carrier: 'F',
    torpedo_carrier: 'F',
    carrier: 'A',
    engineering: 'E',
    transport: 'T',
    hospital: 'H',
    landing: 'L',
    civilian: 'M',
};

const FACTION_COLORS: Record<string, string> = {
    empire: '#4466ff',
    alliance: '#ff4444',
    neutral: '#888888',
};

// Map factionId to faction type — attacker side = alliance (red), defender = empire (blue) as defaults
// In actual game, we compare factionId to known faction types. Here we use side as fallback.
function getFactionColor(unit: TacticalUnit): string {
    // Use side to determine color if no explicit faction mapping
    return unit.side === 'ATTACKER' ? FACTION_COLORS.alliance : FACTION_COLORS.empire;
}

interface TacticalUnitIconProps {
    unit: TacticalUnit;
    x: number;
    y: number;
    isSelected: boolean;
    onClick: (unit: TacticalUnit) => void;
}

export function TacticalUnitIcon({ unit, x, y, isSelected, onClick }: TacticalUnitIconProps) {
    const shipClass = unit.unitType ?? 'battleship';
    const shape: ShapeType = SHIP_SHAPE[shipClass] ?? 'square';
    const letter = SHIP_LETTER[shipClass] ?? '?';
    const fillColor = getFactionColor(unit);
    const isFlagship = shipClass === 'flagship';
    const isDamaged = unit.maxShips > 0 && unit.ships < unit.maxShips * 0.5;
    const fillOpacity = isDamaged ? 0.5 : 1;

    return (
        <Group
            x={x}
            y={y}
            onClick={() => onClick(unit)}
            onTap={() => onClick(unit)}
            opacity={unit.isAlive ? 1 : 0.2}
        >
            {/* Selection glow ring */}
            {isSelected && (
                <Circle
                    radius={14}
                    stroke="white"
                    strokeWidth={1}
                    opacity={0.6}
                    dash={[2, 2]}
                    fill="transparent"
                />
            )}

            {/* Shape */}
            {shape === 'triangle' && (
                <RegularPolygon
                    sides={3}
                    radius={isFlagship ? 11 : 10}
                    fill={fillColor}
                    stroke="white"
                    strokeWidth={0.5}
                    opacity={fillOpacity}
                />
            )}
            {shape === 'square' && (
                <Rect
                    width={16}
                    height={16}
                    offsetX={8}
                    offsetY={8}
                    fill={fillColor}
                    stroke="white"
                    strokeWidth={0.5}
                    opacity={fillOpacity}
                />
            )}
            {shape === 'diamond' && (
                <Rect
                    width={12}
                    height={12}
                    offsetX={6}
                    offsetY={6}
                    rotation={45}
                    fill={fillColor}
                    stroke="white"
                    strokeWidth={0.5}
                    opacity={fillOpacity}
                />
            )}

            {/* Letter label */}
            {letter && (
                <Text
                    text={letter}
                    fontSize={8}
                    fontStyle="bold"
                    fill="white"
                    align="center"
                    verticalAlign="middle"
                    offsetX={4}
                    offsetY={4}
                    listening={false}
                />
            )}
        </Group>
    );
}
