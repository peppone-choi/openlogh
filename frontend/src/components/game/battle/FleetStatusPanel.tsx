'use client';

import { useBattleStore } from '@/stores/battleStore';
import type { BattleFleet, TacticalUnit } from '@/stores/battleStore';
import { toLegacyFleet } from '@/stores/battleStore';
import { cn } from '@/lib/utils';

// ─── Formation label map ──────────────────────────────────────────────────────
const FORMATION_LABEL: Record<string, string> = {
    spindle: '紡錘 Spindle',
    crane_wing: '鶴翼 Crane Wing',
    wheel: '輪形 Wheel',
    echelon: '梯形 Echelon',
    square: '方陣 Square',
    dispersed: '疏開 Dispersed',
};

const FORMATION_ICON: Record<string, string> = {
    spindle: '◈',
    crane_wing: '⋈',
    wheel: '◎',
    echelon: '⊿',
    square: '⊞',
    dispersed: '⁘',
};

// ─── HP bar ───────────────────────────────────────────────────────────────────
function HpBar({ pct, color }: { pct: number; color: string }) {
    return (
        <div className="relative h-1.5 rounded-full bg-gray-800/80 overflow-hidden">
            <div
                className="absolute inset-y-0 left-0 rounded-full transition-all duration-300"
                style={{ width: `${Math.max(0, pct * 100).toFixed(1)}%`, background: color }}
            />
        </div>
    );
}

// ─── Single fleet card ────────────────────────────────────────────────────────
interface FleetCardProps {
    fleet: BattleFleet;
    units: TacticalUnit[];
    isSelected: boolean;
}
function FleetCard({ fleet, units, isSelected }: FleetCardProps) {
    const hpPct = fleet.maxShips > 0 ? fleet.ships / fleet.maxShips : 0;
    const moralePct = fleet.morale / 100;
    const moraleColor = fleet.morale > 70 ? '#00cc55' : fleet.morale > 40 ? '#ffaa00' : '#ff4444';
    const factionColor = fleet.faction === 'empire' ? '#FFD700' : fleet.faction === 'alliance' ? '#4488FF' : '#CC88FF';

    const aliveUnits = units.filter((u) => u.ships > 0).length;
    const totalUnits = units.length;

    const energyEntries = Object.entries(fleet.energy).filter(([k]) => k !== 'sensor');

    return (
        <div
            className={cn(
                'rounded border p-2 space-y-1.5 transition-all duration-150',
                isSelected ? 'border-amber-500/50 bg-amber-900/10' : 'border-gray-800/60 bg-gray-900/40'
            )}
        >
            {/* Header: faction stripe + name */}
            <div className="flex items-start gap-1.5">
                <div
                    className="w-1 self-stretch rounded-full flex-shrink-0"
                    style={{ background: factionColor, opacity: 0.7 }}
                />
                <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-1">
                        <span className="text-[10px] font-mono font-bold truncate" style={{ color: factionColor }}>
                            {fleet.name}
                        </span>
                        {fleet.isMyFleet && (
                            <span className="text-[8px] font-mono text-amber-400/70 flex-shrink-0">MY</span>
                        )}
                    </div>
                    <div className="text-[9px] font-mono text-gray-400 truncate">{fleet.commanderName}</div>
                </div>
            </div>

            {/* Ship count */}
            <div className="flex items-center justify-between text-[9px] font-mono">
                <span className="text-gray-500">함선</span>
                <span className="text-gray-300 tabular-nums">
                    {fleet.ships.toLocaleString()}{' '}
                    <span className="text-gray-600">/ {fleet.maxShips.toLocaleString()}</span>
                </span>
            </div>
            <HpBar pct={hpPct} color={hpPct > 0.5 ? '#00cc55' : hpPct > 0.25 ? '#ffaa00' : '#ff4444'} />

            {/* Morale */}
            <div className="flex items-center justify-between text-[9px] font-mono">
                <span className="text-gray-500">사기</span>
                <span className="tabular-nums" style={{ color: moraleColor }}>
                    {fleet.morale}
                </span>
            </div>
            <HpBar pct={moralePct} color={moraleColor} />

            {/* Tactical units */}
            {totalUnits > 0 && (
                <div className="flex items-center justify-between text-[9px] font-mono">
                    <span className="text-gray-500">유닛</span>
                    <span className="text-gray-300 tabular-nums">
                        {aliveUnits}
                        <span className="text-gray-600">/{totalUnits}</span>
                    </span>
                </div>
            )}

            {/* Formation */}
            <div className="flex items-center gap-1.5 text-[9px] font-mono">
                <span className="text-gray-600">{FORMATION_ICON[fleet.formation] ?? '◻'}</span>
                <span className="text-gray-500 truncate">{FORMATION_LABEL[fleet.formation] ?? fleet.formation}</span>
            </div>

            {/* Energy mini-bars */}
            <div className="grid grid-cols-4 gap-0.5">
                {energyEntries.map(([key, val]) => {
                    const color =
                        key === 'beam'
                            ? '#FF6B6B'
                            : key === 'gun'
                              ? '#FFD700'
                              : key === 'shield'
                                ? '#4FC3F7'
                                : '#69F0AE';
                    return (
                        <div key={key} className="space-y-0.5">
                            <div className="text-[7px] font-mono text-center" style={{ color, opacity: 0.7 }}>
                                {key.slice(0, 2).toUpperCase()}
                            </div>
                            <div className="h-1 rounded-full bg-gray-800/80 overflow-hidden">
                                <div
                                    className="h-full rounded-full"
                                    style={{ width: `${val}%`, background: color, opacity: 0.8 }}
                                />
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

// ─── FleetStatusPanel ─────────────────────────────────────────────────────────
export function FleetStatusPanel() {
    const { myFleet, enemyFleets, alliedFleets, tacticalUnits, selectedUnitIds } = useBattleStore();

    const getUnitsForFleet = (fleetId: string) => tacticalUnits.filter((u) => u.fleetId === fleetId);

    const selectedFleetId =
        selectedUnitIds.length > 0 ? tacticalUnits.find((u) => u.id === selectedUnitIds[0])?.fleetId : null;

    const myFleets: BattleFleet[] = [];
    if (myFleet) myFleets.push(myFleet);
    alliedFleets.forEach((f) => myFleets.push(f));

    if (myFleets.length === 0 && enemyFleets.length === 0) {
        return <div className="p-3 text-[10px] font-mono text-gray-600 text-center">전투 대기 중…</div>;
    }

    return (
        <div className="flex flex-col gap-2 p-2 h-full overflow-y-auto">
            {/* My side */}
            {myFleets.length > 0 && (
                <div className="space-y-1.5">
                    <div className="text-[9px] font-mono text-amber-500/60 tracking-widest uppercase px-0.5">
                        아군 // Allied
                    </div>
                    {myFleets.map((fleet) => (
                        <FleetCard
                            key={fleet.id}
                            fleet={fleet}
                            units={getUnitsForFleet(fleet.id)}
                            isSelected={selectedFleetId === fleet.id}
                        />
                    ))}
                </div>
            )}

            {/* Separator */}
            {myFleets.length > 0 && enemyFleets.length > 0 && (
                <div className="flex items-center gap-2 px-0.5">
                    <div className="flex-1 h-px bg-red-900/30" />
                    <span className="text-[8px] font-mono text-red-900/60">ENEMY</span>
                    <div className="flex-1 h-px bg-red-900/30" />
                </div>
            )}

            {/* Enemy side */}
            {enemyFleets.length > 0 && (
                <div className="space-y-1.5">
                    {myFleets.length === 0 && (
                        <div className="text-[9px] font-mono text-red-500/60 tracking-widest uppercase px-0.5">
                            적군 // Enemy
                        </div>
                    )}
                    {enemyFleets.map((fleet) => {
                        const legacy = toLegacyFleet(fleet, false);
                        return (
                            <FleetCard
                                key={legacy.id}
                                fleet={legacy}
                                units={getUnitsForFleet(legacy.id)}
                                isSelected={selectedFleetId === legacy.id}
                            />
                        );
                    })}
                </div>
            )}
        </div>
    );
}
