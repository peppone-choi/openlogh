'use client';

import type { Planet } from '@/types/planet';
import { SHIP_CLASS_NAMES } from './fleet-unit-card';

interface ShipyardQueueEntry {
    shipClass: string;
    count: number;
    turnsRemaining: number;
}

interface PlanetResourcePanelProps {
    planet: Planet;
    shipyardQueue?: ShipyardQueueEntry[];
}

function formatPopulation(value: number): string {
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
    if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`;
    return value.toLocaleString();
}

function ResourceBar({
    label,
    value,
    max,
    color,
    icon,
}: {
    label: string;
    value: number;
    max: number;
    color: string;
    icon?: string;
}) {
    const pct = max > 0 ? Math.min((value / max) * 100, 100) : 0;
    return (
        <div className="space-y-0.5">
            <div className="flex justify-between items-center text-xs">
                <span className="text-gray-400">{label}</span>
                <span className="tabular-nums text-gray-200">
                    {value.toLocaleString()}
                    {icon && <span className="ml-1 text-[10px]">{icon}</span>}
                </span>
            </div>
            <div className="w-full bg-gray-800 h-1.5 rounded-none">
                <div
                    className={`h-1.5 rounded-none transition-all ${color}`}
                    style={{ width: `${pct}%` }}
                />
            </div>
        </div>
    );
}

export function PlanetResourcePanel({ planet, shipyardQueue }: PlanetResourcePanelProps) {
    const isSupplyNormal = planet.supplyState === 1;

    return (
        <div className="border border-gray-700 bg-gray-950/60 rounded-none">
            {/* Header */}
            <div className="border-b border-gray-700 px-4 py-3 flex items-center justify-between">
                <h3 className="font-bold text-sm text-white">{planet.name}</h3>
                <div className="flex items-center gap-2">
                    <span
                        className={`text-[10px] px-2 py-0.5 border rounded-none ${
                            isSupplyNormal
                                ? 'border-green-700 text-green-400 bg-green-950/30'
                                : 'border-red-700 text-red-400 bg-red-950/30'
                        }`}
                    >
                        {isSupplyNormal ? '보급정상' : '보급차단'}
                    </span>
                </div>
            </div>

            {/* Resource grid */}
            <div className="px-4 py-3 grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* 인구 */}
                <div className="space-y-0.5">
                    <div className="flex justify-between items-center text-xs">
                        <span className="text-gray-400">인구</span>
                        <span className="tabular-nums text-gray-200">
                            {formatPopulation(planet.population)}
                            {planet.populationMax && (
                                <span className="text-gray-500">
                                    {' '}/ {formatPopulation(planet.populationMax)}
                                </span>
                            )}
                        </span>
                    </div>
                    {planet.populationMax && (
                        <div className="w-full bg-gray-800 h-1.5 rounded-none">
                            <div
                                className="h-1.5 rounded-none transition-all bg-purple-500"
                                style={{
                                    width: `${Math.min((planet.population / planet.populationMax) * 100, 100)}%`,
                                }}
                            />
                        </div>
                    )}
                </div>

                {/* 생산력 */}
                <ResourceBar
                    label="생산력"
                    value={planet.production}
                    max={planet.productionMax ?? 50000}
                    color="bg-cyan-500"
                />

                {/* 교역 */}
                <ResourceBar
                    label="교역"
                    value={planet.commerce}
                    max={planet.commerceMax ?? 50000}
                    color="bg-yellow-500"
                />

                {/* 치안 */}
                <ResourceBar
                    label="치안"
                    value={planet.security}
                    max={planet.securityMax ?? 100}
                    color="bg-blue-500"
                />

                {/* 지지도 */}
                <ResourceBar
                    label="지지도"
                    value={planet.approval}
                    max={100}
                    color="bg-green-500"
                />

                {/* 궤도방어 */}
                <div className="space-y-0.5">
                    <div className="flex justify-between items-center text-xs">
                        <span className="text-gray-400">궤도방어</span>
                        <span className="text-orange-400 tabular-nums font-bold">
                            {planet.orbital_defense.toLocaleString()}
                        </span>
                    </div>
                    {planet.orbital_defenseMax && (
                        <div className="w-full bg-gray-800 h-1.5 rounded-none">
                            <div
                                className="h-1.5 rounded-none transition-all bg-orange-500"
                                style={{
                                    width: `${Math.min((planet.orbital_defense / planet.orbital_defenseMax) * 100, 100)}%`,
                                }}
                            />
                        </div>
                    )}
                </div>

                {/* 요새방어 */}
                <div className="space-y-0.5">
                    <div className="flex justify-between items-center text-xs">
                        <span className="text-gray-400">요새방어</span>
                        <span className="text-red-400 tabular-nums font-bold">
                            {planet.fortress.toLocaleString()}
                        </span>
                    </div>
                    {planet.fortressMax && (
                        <div className="w-full bg-gray-800 h-1.5 rounded-none">
                            <div
                                className="h-1.5 rounded-none transition-all bg-red-500"
                                style={{
                                    width: `${Math.min((planet.fortress / planet.fortressMax) * 100, 100)}%`,
                                }}
                            />
                        </div>
                    )}
                </div>
            </div>

            {/* Shipyard section */}
            <div className="border-t border-gray-700 px-4 py-3">
                <p className="text-[10px] text-gray-500 mb-2">조병창 생산 현황</p>
                {!shipyardQueue || shipyardQueue.length === 0 ? (
                    <p className="text-xs text-gray-600">생산 대기 없음</p>
                ) : (
                    <ul className="space-y-1">
                        {shipyardQueue.map((entry, idx) => (
                            <li key={idx} className="text-xs text-gray-300">
                                {SHIP_CLASS_NAMES[entry.shipClass] ?? entry.shipClass}{' '}
                                <span className="text-blue-400">× {entry.count.toLocaleString()}</span>
                                <span className="text-gray-500 ml-1">
                                    ({entry.turnsRemaining}턴 후 완료)
                                </span>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}
