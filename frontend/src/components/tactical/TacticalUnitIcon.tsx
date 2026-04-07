'use client';

import { Group, RegularPolygon, Rect, Text, Circle } from 'react-konva';
import type { TacticalUnit } from '@/types/tactical';

// 아이콘 규칙: △ 삼각형 = 기함부대(isFlagship=true)만, □ 사각형 = 나머지 전부
// ◇ 마름모는 사용하지 않음 (제거됨 — Phase 06-08)

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
    const shipClass = (unit.unitType ?? 'battleship').toLowerCase();
    const letter = SHIP_LETTER[shipClass] ?? '?';
    const fillColor = getFactionColor(unit);
    // △ 삼각형: 기함부대만 / □ 사각형: 나머지 전부
    const isFlagship = unit.isFlagship === true || shipClass === 'flagship';
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

            {/* △ 기함부대 */}
            {isFlagship && (
                <RegularPolygon
                    sides={3}
                    radius={11}
                    fill={fillColor}
                    stroke="white"
                    strokeWidth={0.5}
                    opacity={fillOpacity}
                />
            )}
            {/* □ 나머지 전부 */}
            {!isFlagship && (
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
