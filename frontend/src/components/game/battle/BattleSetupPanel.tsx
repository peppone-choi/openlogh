'use client';

import { useState } from 'react';
import type { Formation, EnergyAllocation, BattleFleet } from '@/stores/battleStore';
import { FormationSelector } from './FormationSelector';
import { EnergyAllocator } from './EnergyAllocator';
import { cn } from '@/lib/utils';

const GRID_COLS = 8;
const GRID_ROWS = 3;

interface UnitPlacement {
    unitId: string;
    col: number;
    row: number;
}

interface BattleSetupPanelProps {
    fleet: BattleFleet;
    alliedFleets?: BattleFleet[];
    formation: Formation;
    energy: EnergyAllocation;
    onFormationChange: (f: Formation) => void;
    onEnergyChange: (key: keyof Omit<EnergyAllocation, 'sensor'>, value: number) => void;
    onEnergyPreset?: (values: Omit<EnergyAllocation, 'sensor'>) => void;
    onBegin: () => void;
    setupSeconds?: number;
    isReady?: boolean;
}

export function BattleSetupPanel({
    fleet,
    alliedFleets = [],
    formation,
    energy,
    onFormationChange,
    onEnergyChange,
    onEnergyPreset,
    onBegin,
    setupSeconds = 30,
    isReady = false,
}: BattleSetupPanelProps) {
    const allUnits = [fleet, ...alliedFleets];

    const [placements, setPlacements] = useState<UnitPlacement[]>(() =>
        allUnits.map((u, i) => ({
            unitId: u.id,
            col: i % GRID_COLS,
            row: Math.floor(i / GRID_COLS),
        }))
    );
    const [selectedUnit, setSelectedUnit] = useState<string | null>(null);

    const timerCritical = setupSeconds <= 5;
    const timerWarning = setupSeconds <= 10 && !timerCritical;
    const timerColor = timerCritical ? 'text-red-400' : timerWarning ? 'text-amber-400' : 'text-emerald-400';

    const getPlacedUnit = (col: number, row: number) => placements.find((p) => p.col === col && p.row === row);

    const getUnit = (id: string) => allUnits.find((u) => u.id === id);

    const handleCellClick = (col: number, row: number) => {
        const placed = getPlacedUnit(col, row);

        if (selectedUnit) {
            if (placed && placed.unitId === selectedUnit) {
                setSelectedUnit(null);
                return;
            }
            if (placed) {
                // Swap positions
                setPlacements((prev) => {
                    const selPlacement = prev.find((p) => p.unitId === selectedUnit);
                    if (!selPlacement) return prev;
                    return prev.map((p) => {
                        if (p.unitId === selectedUnit) return { ...p, col, row };
                        if (p.unitId === placed.unitId) return { ...p, col: selPlacement.col, row: selPlacement.row };
                        return p;
                    });
                });
            } else {
                setPlacements((prev) => prev.map((p) => (p.unitId === selectedUnit ? { ...p, col, row } : p)));
            }
            setSelectedUnit(null);
        } else if (placed) {
            setSelectedUnit(placed.unitId);
        }
    };

    return (
        <div className="space-y-3 bg-gray-950 border border-gray-800/60 rounded-lg p-3">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <div className="text-[11px] font-mono font-bold text-amber-400/90 tracking-widest uppercase">
                        전투 배치 // Battle Setup
                    </div>
                    <div className="text-[9px] font-mono text-gray-600">초기 진형 및 함대 배치 결정</div>
                </div>
                <div className="flex items-center gap-1.5">
                    <span
                        className={cn(
                            'text-2xl font-mono font-black tabular-nums',
                            timerColor,
                            timerCritical && 'animate-pulse'
                        )}
                    >
                        {String(setupSeconds).padStart(2, '0')}
                    </span>
                    <span className="text-[9px] font-mono text-gray-600">초</span>
                </div>
            </div>

            {/* Placement grid */}
            <div className="space-y-1">
                <div className="flex items-center justify-between">
                    <span className="text-[9px] font-mono text-gray-600 uppercase tracking-widest">함대 배치</span>
                    {selectedUnit && <span className="text-[8px] font-mono text-amber-400/70">배치 위치 선택</span>}
                </div>
                <div
                    className="rounded border border-gray-800/50 overflow-hidden"
                    style={{
                        display: 'grid',
                        gridTemplateColumns: `repeat(${GRID_COLS}, 1fr)`,
                        gap: '1px',
                        backgroundColor: '#111827',
                    }}
                >
                    {Array.from({ length: GRID_ROWS * GRID_COLS }).map((_, idx) => {
                        const col = idx % GRID_COLS;
                        const row = Math.floor(idx / GRID_COLS);
                        const placed = getPlacedUnit(col, row);
                        const unit = placed ? getUnit(placed.unitId) : null;
                        const isSelected = placed?.unitId === selectedUnit;
                        const isMyFleet = unit?.id === fleet.id;
                        const isTargetCell = selectedUnit && !placed;

                        return (
                            <button
                                key={idx}
                                type="button"
                                onClick={() => handleCellClick(col, row)}
                                className={cn(
                                    'aspect-square flex flex-col items-center justify-center p-0.5 transition-all duration-100',
                                    'bg-gray-900/60',
                                    isSelected
                                        ? 'bg-amber-900/30 ring-1 ring-inset ring-amber-500/50'
                                        : isTargetCell
                                          ? 'hover:bg-sky-900/20 cursor-crosshair'
                                          : unit
                                            ? 'hover:bg-gray-800/50 cursor-pointer'
                                            : 'cursor-default'
                                )}
                            >
                                {unit && (
                                    <>
                                        <div
                                            className={cn(
                                                'w-2 h-2 rounded-sm shrink-0',
                                                isMyFleet ? 'bg-amber-400/80' : 'bg-sky-400/60'
                                            )}
                                        />
                                        <span className="text-[5px] font-mono text-gray-500 leading-tight mt-px truncate w-full text-center">
                                            {unit.name.slice(0, 3)}
                                        </span>
                                    </>
                                )}
                                {isSelected && (
                                    <div className="absolute inset-0 border border-amber-500/40 rounded pointer-events-none" />
                                )}
                            </button>
                        );
                    })}
                </div>
                <div className="flex items-center gap-3 text-[7px] font-mono text-gray-600">
                    <span className="flex items-center gap-1">
                        <span className="w-1.5 h-1.5 bg-amber-400/80 rounded-sm inline-block" />내 함대
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="w-1.5 h-1.5 bg-sky-400/60 rounded-sm inline-block" />
                        아군 함대
                    </span>
                    <span className="text-gray-700 ml-auto">클릭으로 이동</span>
                </div>
            </div>

            {/* Formation */}
            <FormationSelector value={formation} onChange={onFormationChange} />

            {/* Energy */}
            <EnergyAllocator value={energy} onChange={onEnergyChange} onPreset={onEnergyPreset} />

            {/* Begin battle */}
            <button
                type="button"
                disabled={isReady}
                onClick={onBegin}
                className={cn(
                    'w-full py-2.5 rounded border font-mono text-sm font-bold tracking-widest transition-all duration-200',
                    isReady
                        ? 'border-emerald-900/50 bg-emerald-900/10 text-emerald-400 cursor-default'
                        : 'border-amber-700/60 bg-amber-900/10 text-amber-400 hover:border-amber-500/60 hover:bg-amber-900/20 hover:shadow-[0_0_12px_rgba(255,215,0,0.12)]'
                )}
            >
                {isReady ? '-- 준비 완료 --' : '전투 개시 // BEGIN BATTLE'}
            </button>
        </div>
    );
}
