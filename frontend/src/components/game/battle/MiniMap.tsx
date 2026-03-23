'use client';

import type { TacticalFleet } from '@/types/tactical';

const FACTION_COLORS: Record<string, string> = {
    empire: '#FFD700',
    alliance: '#4488FF',
    fezzan: '#CC88FF',
    rebel: '#FF8844',
};

const MAP_SIZE = 120;
const GRID = 20;
const CELL = MAP_SIZE / GRID;

interface MiniMapProps {
    myFleets: TacticalFleet[];
    enemyFleets: TacticalFleet[];
}

export function MiniMap({ myFleets, enemyFleets }: MiniMapProps) {
    const allUnits = [
        ...myFleets.flatMap((f) => f.units.map((u) => ({ ...u, factionType: f.factionType }))),
        ...enemyFleets.flatMap((f) => f.units.map((u) => ({ ...u, factionType: f.factionType }))),
    ];

    return (
        <div className="space-y-1">
            <div className="text-[9px] font-mono text-gray-600 tracking-widest uppercase">minimap</div>
            <div
                className="bg-[#02020a] border border-gray-800/40 rounded"
                style={{ width: MAP_SIZE, height: MAP_SIZE, position: 'relative' }}
            >
                {/* Grid lines */}
                <svg width={MAP_SIZE} height={MAP_SIZE} className="absolute inset-0">
                    {Array.from({ length: GRID + 1 }, (_, i) => (
                        <line
                            key={`h-${i}`}
                            x1={0}
                            y1={i * CELL}
                            x2={MAP_SIZE}
                            y2={i * CELL}
                            stroke="#FFD70008"
                            strokeWidth={0.5}
                        />
                    ))}
                    {Array.from({ length: GRID + 1 }, (_, i) => (
                        <line
                            key={`v-${i}`}
                            x1={i * CELL}
                            y1={0}
                            x2={i * CELL}
                            y2={MAP_SIZE}
                            stroke="#FFD70008"
                            strokeWidth={0.5}
                        />
                    ))}
                </svg>

                {/* Unit dots */}
                {allUnits.map((unit) => {
                    const color = FACTION_COLORS[unit.factionType] ?? '#888';
                    return (
                        <div
                            key={unit.id}
                            className="absolute rounded-full"
                            style={{
                                width: 3,
                                height: 3,
                                backgroundColor: color,
                                left: (unit.gridX ?? 0) * CELL + CELL / 2 - 1.5,
                                top: (unit.gridY ?? 0) * CELL + CELL / 2 - 1.5,
                                boxShadow: `0 0 3px ${color}`,
                            }}
                        />
                    );
                })}
            </div>
        </div>
    );
}
