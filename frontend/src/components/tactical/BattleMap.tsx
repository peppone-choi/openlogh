'use client';

import { useMemo } from 'react';
import type { TacticalUnit } from '@/types/tactical';

interface BattleMapProps {
    units: TacticalUnit[];
    width?: number;
    height?: number;
    myOfficerId?: number;
    fortressFactionId?: number;
}

/**
 * 2D tactical battle map rendered with SVG.
 * Shows unit positions with faction colors, HP bars, command range circles.
 */
export function BattleMap({
    units,
    width = 800,
    height = 480,
    myOfficerId,
    fortressFactionId,
}: BattleMapProps) {
    // Scale factor from game coords (0-1000, 0-600) to SVG viewport
    const scaleX = width / 1000;
    const scaleY = height / 600;

    const aliveUnits = useMemo(() => units.filter((u) => u.isAlive), [units]);
    const deadUnits = useMemo(() => units.filter((u) => !u.isAlive), [units]);

    return (
        <div className="border border-gray-700 rounded bg-gray-950 overflow-hidden">
            <svg viewBox={`0 0 ${width} ${height}`} width={width} height={height} className="w-full h-auto">
                {/* Background grid */}
                <defs>
                    <pattern id="grid" width={width / 10} height={height / 10} patternUnits="userSpaceOnUse">
                        <path
                            d={`M ${width / 10} 0 L 0 0 0 ${height / 10}`}
                            fill="none"
                            stroke="#1a1a2e"
                            strokeWidth="0.5"
                        />
                    </pattern>
                </defs>
                <rect width={width} height={height} fill="url(#grid)" />

                {/* Side labels */}
                <text x={10} y={20} className="fill-red-400 text-[10px]" fontFamily="monospace">
                    ATTACKER
                </text>
                <text x={width - 80} y={20} className="fill-blue-400 text-[10px]" fontFamily="monospace">
                    DEFENDER
                </text>

                {/* Fortress indicator */}
                {fortressFactionId && fortressFactionId > 0 && (
                    <g>
                        <rect
                            x={width / 2 - 20}
                            y={0}
                            width={40}
                            height={12}
                            fill="#4a1a1a"
                            stroke="#ff6600"
                            strokeWidth={1}
                        />
                        <text
                            x={width / 2}
                            y={9}
                            textAnchor="middle"
                            className="fill-orange-400 text-[7px]"
                            fontFamily="monospace"
                        >
                            FORTRESS
                        </text>
                    </g>
                )}

                {/* Dead units (faded) */}
                {deadUnits.map((unit) => (
                    <g key={`dead-${unit.fleetId}`} opacity={0.2}>
                        <circle
                            cx={unit.posX * scaleX}
                            cy={unit.posY * scaleY}
                            r={6}
                            fill={unit.side === 'ATTACKER' ? '#ef4444' : '#3b82f6'}
                            stroke="#333"
                        />
                        <line
                            x1={unit.posX * scaleX - 5}
                            y1={unit.posY * scaleY - 5}
                            x2={unit.posX * scaleX + 5}
                            y2={unit.posY * scaleY + 5}
                            stroke="#fff"
                            strokeWidth={1}
                        />
                    </g>
                ))}

                {/* Alive units */}
                {aliveUnits.map((unit) => {
                    const cx = unit.posX * scaleX;
                    const cy = unit.posY * scaleY;
                    const isMe = unit.officerId === myOfficerId;
                    const baseColor = unit.side === 'ATTACKER' ? '#ef4444' : '#3b82f6';
                    const hpPercent = unit.maxHp > 0 ? unit.hp / unit.maxHp : 0;
                    const unitRadius = 8 + (unit.ships / 3000) * 4; // Scale with fleet size

                    return (
                        <g key={unit.fleetId}>
                            {/* Command range circle */}
                            {unit.commandRange > 0 && (
                                <circle
                                    cx={cx}
                                    cy={cy}
                                    r={unit.commandRange * scaleX * 0.5}
                                    fill="none"
                                    stroke={baseColor}
                                    strokeWidth={0.5}
                                    strokeDasharray="3 3"
                                    opacity={0.3}
                                />
                            )}

                            {/* Retreat arrow */}
                            {unit.isRetreating && (
                                <line
                                    x1={cx}
                                    y1={cy}
                                    x2={unit.side === 'ATTACKER' ? cx - 30 : cx + 30}
                                    y2={cy}
                                    stroke="#ffff00"
                                    strokeWidth={1}
                                    strokeDasharray="4 2"
                                    markerEnd="url(#arrow)"
                                />
                            )}

                            {/* Unit circle */}
                            <circle
                                cx={cx}
                                cy={cy}
                                r={unitRadius}
                                fill={baseColor}
                                stroke={isMe ? '#ffd700' : '#555'}
                                strokeWidth={isMe ? 2 : 1}
                                opacity={unit.isRetreating ? 0.5 : 0.9}
                            />

                            {/* HP bar below unit */}
                            <rect x={cx - 10} y={cy + unitRadius + 2} width={20} height={2} fill="#333" />
                            <rect
                                x={cx - 10}
                                y={cy + unitRadius + 2}
                                width={20 * hpPercent}
                                height={2}
                                fill={hpPercent > 0.5 ? '#22c55e' : hpPercent > 0.25 ? '#eab308' : '#ef4444'}
                            />

                            {/* Officer name */}
                            <text
                                x={cx}
                                y={cy + unitRadius + 12}
                                textAnchor="middle"
                                className="text-[7px]"
                                fill="#ccc"
                                fontFamily="monospace"
                            >
                                {unit.officerName}
                            </text>

                            {/* Ship count */}
                            <text
                                x={cx}
                                y={cy + 3}
                                textAnchor="middle"
                                className="text-[6px]"
                                fill="#fff"
                                fontFamily="monospace"
                            >
                                {unit.ships}
                            </text>
                        </g>
                    );
                })}
            </svg>
        </div>
    );
}
