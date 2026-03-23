'use client';

import { useEffect } from 'react';
import { useBattleStore } from '@/stores/battleStore';
import type { TacticalUnit, ShipClass } from '@/stores/battleStore';

// ─── Ship class labels ────────────────────────────────────────────────────────
const SHIP_CLASS_LABEL: Record<ShipClass, string> = {
    battleship: '전함 Battleship',
    cruiser: '순양함 Cruiser',
    destroyer: '구축함 Destroyer',
    carrier: '항모 Carrier',
    transport: '수송함 Transport',
};

const SHIP_CLASS_ICON: Record<ShipClass, string> = {
    battleship: '▬',
    cruiser: '▲',
    destroyer: '◆',
    carrier: '●',
    transport: '▭',
};

// ─── Stat row ─────────────────────────────────────────────────────────────────
function StatRow({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
    return (
        <div className="flex items-center justify-between text-[9px] font-mono">
            <span className="text-gray-500">{label}</span>
            <span className="text-gray-300 tabular-nums">
                {value}
                {sub && <span className="text-gray-600 ml-1">{sub}</span>}
            </span>
        </div>
    );
}

// ─── Single unit detail card ──────────────────────────────────────────────────
function UnitDetailCard({ unit }: { unit: TacticalUnit }) {
    const { addTacticalOrder, deselectAll } = useBattleStore();
    const hpPct = unit.maxShips > 0 ? unit.ships / unit.maxShips : 0;
    const moralePct = unit.morale / 100;
    const factionColor = unit.faction === 'empire' ? '#FFD700' : unit.faction === 'alliance' ? '#4488FF' : '#CC88FF';
    const hpColor = hpPct > 0.6 ? '#00cc55' : hpPct > 0.3 ? '#ffaa00' : '#ff4444';
    const moraleColor = unit.morale > 60 ? '#00cc55' : unit.morale > 30 ? '#ffaa00' : '#ff4444';

    return (
        <div
            className="rounded border border-gray-700/60 bg-gray-900/60 p-2 space-y-1.5"
            style={{ borderLeftColor: factionColor, borderLeftWidth: 2 }}
        >
            {/* Unit header */}
            <div className="flex items-center gap-1.5">
                <span className="text-base leading-none" style={{ color: factionColor }}>
                    {SHIP_CLASS_ICON[unit.shipClass]}
                </span>
                <div className="min-w-0 flex-1">
                    <div className="text-[10px] font-mono font-bold" style={{ color: factionColor }}>
                        {SHIP_CLASS_LABEL[unit.shipClass]}
                    </div>
                    {unit.commanderName && (
                        <div className="text-[8px] font-mono text-gray-400 truncate">★ {unit.commanderName}</div>
                    )}
                </div>
                <button
                    type="button"
                    onClick={deselectAll}
                    className="text-[9px] text-gray-600 hover:text-gray-400 ml-auto flex-shrink-0"
                    aria-label="선택 해제"
                >
                    ✕
                </button>
            </div>

            {/* Position */}
            <StatRow label="위치" value={`(${unit.gridX}, ${unit.gridY})`} />

            {/* HP */}
            <div className="space-y-0.5">
                <StatRow label="함선" value={unit.ships.toLocaleString()} sub={`/ ${unit.maxShips.toLocaleString()}`} />
                <div className="relative h-1.5 rounded-full bg-gray-800/80 overflow-hidden">
                    <div
                        className="absolute inset-y-0 left-0 rounded-full transition-all duration-300"
                        style={{ width: `${(hpPct * 100).toFixed(1)}%`, background: hpColor }}
                    />
                </div>
            </div>

            {/* Morale */}
            <div className="space-y-0.5">
                <StatRow label="사기" value={unit.morale} />
                <div className="relative h-1 rounded-full bg-gray-800/80 overflow-hidden">
                    <div
                        className="absolute inset-y-0 left-0 rounded-full transition-all duration-300"
                        style={{ width: `${(moralePct * 100).toFixed(1)}%`, background: moraleColor }}
                    />
                </div>
            </div>

            {/* Quick orders (my unit only) */}
            {unit.isMyUnit && (
                <div className="grid grid-cols-2 gap-1 pt-0.5">
                    <button
                        type="button"
                        className="text-[8px] font-mono px-1.5 py-1 rounded border border-sky-900/60 text-sky-400 hover:bg-sky-900/20 transition-colors"
                        onClick={() => {
                            // Move to adjacent cell (placeholder — real targeting via canvas right-click)
                            addTacticalOrder({
                                unitId: unit.id,
                                type: 'move',
                                targetX: unit.gridX,
                                targetY: unit.gridY,
                            });
                        }}
                    >
                        → 이동
                    </button>
                    <button
                        type="button"
                        className="text-[8px] font-mono px-1.5 py-1 rounded border border-red-900/60 text-red-400 hover:bg-red-900/20 transition-colors"
                        onClick={() => {
                            addTacticalOrder({
                                unitId: unit.id,
                                type: 'attack',
                                targetX: unit.gridX,
                                targetY: unit.gridY,
                            });
                        }}
                    >
                        ⚡ 공격
                    </button>
                </div>
            )}
        </div>
    );
}

// ─── Multi-select summary ─────────────────────────────────────────────────────
function MultiSelectSummary({ units }: { units: TacticalUnit[] }) {
    const { deselectAll, addTacticalOrder } = useBattleStore();
    const myUnits = units.filter((u) => u.isMyUnit);
    const totalShips = units.reduce((s, u) => s + u.ships, 0);
    const factions = [...new Set(units.map((u) => u.faction))];

    return (
        <div className="rounded border border-gray-700/60 bg-gray-900/60 p-2 space-y-1.5">
            <div className="flex items-center justify-between">
                <span className="text-[10px] font-mono font-bold text-gray-300">{units.length}개 유닛 선택</span>
                <button
                    type="button"
                    onClick={deselectAll}
                    className="text-[9px] text-gray-600 hover:text-gray-400"
                    aria-label="선택 해제"
                >
                    ✕ 해제
                </button>
            </div>
            <div className="text-[9px] font-mono text-gray-500">
                아군 {myUnits.length} · 총 {totalShips.toLocaleString()}척{factions.length > 1 && ` · 혼성편대`}
            </div>
            {myUnits.length > 0 && (
                <div className="grid grid-cols-2 gap-1">
                    <button
                        type="button"
                        className="text-[8px] font-mono px-1.5 py-1 rounded border border-sky-900/60 text-sky-400 hover:bg-sky-900/20 transition-colors"
                        onClick={() => {
                            myUnits.forEach((u) =>
                                addTacticalOrder({ unitId: u.id, type: 'move', targetX: u.gridX, targetY: u.gridY })
                            );
                        }}
                    >
                        → 이동 명령
                    </button>
                    <button
                        type="button"
                        className="text-[8px] font-mono px-1.5 py-1 rounded border border-red-900/60 text-red-400 hover:bg-red-900/20 transition-colors"
                        onClick={() => {
                            myUnits.forEach((u) =>
                                addTacticalOrder({ unitId: u.id, type: 'attack', targetX: u.gridX, targetY: u.gridY })
                            );
                        }}
                    >
                        ⚡ 공격 명령
                    </button>
                </div>
            )}
        </div>
    );
}

// ─── UnitSelector ─────────────────────────────────────────────────────────────
export function UnitSelector() {
    const { selectedUnitIds, tacticalUnits, deselectAll } = useBattleStore();

    // Keyboard: Esc to deselect, Tab to cycle
    useEffect(() => {
        const handler = (e: KeyboardEvent) => {
            if (e.key === 'Escape') deselectAll();
            if (e.key === 'Tab') {
                e.preventDefault();
                if (tacticalUnits.length === 0) return;
                if (selectedUnitIds.length === 0) {
                    useBattleStore.getState().selectUnit(tacticalUnits[0].id);
                    return;
                }
                const myUnits = tacticalUnits.filter((u) => u.isMyUnit);
                if (myUnits.length === 0) return;
                const cur = myUnits.findIndex((u) => u.id === selectedUnitIds[0]);
                const next = myUnits[(cur + 1) % myUnits.length];
                useBattleStore.getState().selectUnit(next.id);
            }
        };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    }, [selectedUnitIds, tacticalUnits, deselectAll]);

    if (selectedUnitIds.length === 0) return null;

    const selectedUnits = tacticalUnits.filter((u) => selectedUnitIds.includes(u.id));
    if (selectedUnits.length === 0) return null;

    return (
        <div className="space-y-1.5">
            <div className="text-[9px] font-mono text-amber-500/60 tracking-widest uppercase">
                선택 유닛 // Selected
            </div>
            {selectedUnits.length === 1 ? (
                <UnitDetailCard unit={selectedUnits[0]} />
            ) : (
                <MultiSelectSummary units={selectedUnits} />
            )}
            <div className="text-[8px] font-mono text-gray-700 text-center">TAB: 유닛 전환 · ESC: 선택 해제</div>
        </div>
    );
}
