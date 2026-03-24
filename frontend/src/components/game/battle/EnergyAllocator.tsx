'use client';

import type { EnergyAllocation } from '@/stores/battleStore';
import { cn } from '@/lib/utils';

interface EnergyChannel {
    key: keyof Omit<EnergyAllocation, 'sensor'>;
    label: string;
    sublabel: string;
    color: string;
    trackColor: string;
    effectFn: (pct: number) => string;
}

const CHANNELS: EnergyChannel[] = [
    {
        key: 'beam',
        label: 'BEAM',
        sublabel: '빔 공격',
        color: '#FF6B6B',
        trackColor: 'bg-red-500',
        effectFn: (p) => `빔 데미지 +${p * 2}%`,
    },
    {
        key: 'gun',
        label: 'GUN',
        sublabel: '포격',
        color: '#FFD700',
        trackColor: 'bg-amber-400',
        effectFn: (p) => `포격 데미지 +${p * 2}%`,
    },
    {
        key: 'shield',
        label: 'SHIELD',
        sublabel: '방어막 (총합)',
        color: '#4FC3F7',
        trackColor: 'bg-sky-400',
        effectFn: (p) => `피해감소 +${(p * 1.5).toFixed(0)}%`,
    },
    {
        key: 'engine',
        label: 'ENGINE',
        sublabel: '추진',
        color: '#69F0AE',
        trackColor: 'bg-emerald-400',
        effectFn: (p) => `이동력 +${p}%, 회피 +${(p * 0.5).toFixed(0)}%`,
    },
    {
        key: 'warp',
        label: 'WARP',
        sublabel: '워프',
        color: '#CE93D8',
        trackColor: 'bg-purple-400',
        effectFn: (p) => `철퇴 속도 +${p}%, 워프 충전 -${(p * 0.8).toFixed(0)}%`,
    },
];

type PresetKey = 'attack' | 'defense' | 'mobility' | 'balanced' | 'recon';

const PRESETS: {
    key: PresetKey;
    label: string;
    values: Omit<EnergyAllocation, 'sensor'>;
}[] = [
    { key: 'attack', label: '공격형', values: { beam: 35, gun: 25, shield: 10, engine: 10, warp: 5 } },
    { key: 'defense', label: '방어형', values: { beam: 10, gun: 10, shield: 40, engine: 10, warp: 10 } },
    { key: 'mobility', label: '기동형', values: { beam: 15, gun: 10, shield: 10, engine: 35, warp: 15 } },
    { key: 'balanced', label: '균형형', values: { beam: 15, gun: 15, shield: 15, engine: 15, warp: 15 } },
    { key: 'recon', label: '색적형', values: { beam: 10, gun: 10, shield: 10, engine: 10, warp: 10 } },
];

interface EnergyAllocatorProps {
    value: EnergyAllocation;
    onChange: (key: keyof Omit<EnergyAllocation, 'sensor'>, value: number) => void;
    onPreset?: (values: Omit<EnergyAllocation, 'sensor'>) => void;
}

export function EnergyAllocator({ value, onChange, onPreset }: EnergyAllocatorProps) {
    const manualTotal = value.beam + value.gun + value.shield + value.engine + value.warp;
    const sensorVal = Math.max(0, 100 - manualTotal);
    const totalOk = manualTotal <= 100;

    return (
        <div className="space-y-1.5">
            <div className="flex items-center justify-between">
                <span className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">
                    에너지 분배 // Energy
                </span>
                <span className={cn('text-[10px] font-mono font-bold', totalOk ? 'text-emerald-400' : 'text-red-400')}>
                    {manualTotal + sensorVal}/100
                </span>
            </div>

            {/* Presets */}
            <div className="flex gap-1 flex-wrap">
                {PRESETS.map((preset) => (
                    <button
                        key={preset.key}
                        type="button"
                        onClick={() => onPreset?.(preset.values)}
                        className="text-[8px] font-mono px-1.5 py-0.5 rounded border border-gray-700/60 text-gray-500 hover:border-amber-700/50 hover:text-amber-400/80 hover:bg-amber-900/10 transition-all duration-100"
                    >
                        {preset.label}
                    </button>
                ))}
            </div>

            <div className="space-y-2">
                {CHANNELS.map((ch) => {
                    const pct = value[ch.key];
                    return (
                        <div key={ch.key} className="space-y-0.5">
                            <div className="flex items-center justify-between gap-1">
                                <div className="flex items-center gap-1.5 min-w-0 flex-1">
                                    <span
                                        className="text-[10px] font-mono font-bold w-14 shrink-0"
                                        style={{ color: ch.color }}
                                    >
                                        {ch.label}
                                    </span>
                                    <span className="text-[8px] text-gray-600 truncate">{ch.effectFn(pct ?? 0)}</span>
                                </div>
                                <span className="text-[11px] font-mono font-bold text-gray-300 w-8 text-right tabular-nums shrink-0">
                                    {pct}%
                                </span>
                            </div>
                            <div className="relative h-4 flex items-center">
                                <div className="absolute inset-y-1 left-0 right-0 bg-gray-800/80 rounded-full" />
                                <div
                                    className={cn(
                                        'absolute inset-y-1 left-0 rounded-full transition-all duration-100',
                                        ch.trackColor
                                    )}
                                    style={{ width: `${pct}%`, opacity: 0.85 }}
                                />
                                <input
                                    type="range"
                                    min={0}
                                    max={80}
                                    step={5}
                                    value={pct}
                                    onChange={(e) => onChange(ch.key, Number(e.target.value))}
                                    className="relative w-full h-4 opacity-0 cursor-pointer z-10"
                                    style={{ WebkitAppearance: 'none' }}
                                />
                            </div>
                        </div>
                    );
                })}

                {/* SENSOR - auto computed */}
                <div className="space-y-0.5 opacity-60">
                    <div className="flex items-center justify-between gap-1">
                        <div className="flex items-center gap-1.5 min-w-0 flex-1">
                            <span className="text-[10px] font-mono font-bold w-14 shrink-0 text-violet-400">
                                SENSOR
                            </span>
                            <span className="text-[8px] text-gray-600 truncate">
                                사거리 +{sensorVal}셀, 명중 +{sensorVal}%
                            </span>
                        </div>
                        <span className="text-[11px] font-mono font-bold text-violet-300 w-8 text-right tabular-nums shrink-0">
                            {sensorVal}%
                        </span>
                    </div>
                    <div className="relative h-2 flex items-center">
                        <div className="absolute inset-0 bg-gray-800/80 rounded-full" />
                        <div
                            className="absolute inset-y-0 left-0 rounded-full bg-violet-500 transition-all duration-100"
                            style={{ width: `${sensorVal}%`, opacity: 0.7 }}
                        />
                    </div>
                    <div className="text-[7px] font-mono text-gray-700">색적 (자동 배분)</div>
                </div>

                {/* 4-Directional Shield Distribution */}
                <DirectionalShields totalShield={value.shield} />
            </div>
        </div>
    );
}

// ─── 4-Directional Shield Sub-component ──────────────────────────────────────

interface DirectionalShieldsProps {
    totalShield: number;
}

interface ShieldDirection {
    key: string;
    label: string;
    labelKr: string;
}

const SHIELD_DIRS: ShieldDirection[] = [
    { key: 'front', label: 'F', labelKr: '전' },
    { key: 'rear', label: 'R', labelKr: '후' },
    { key: 'left', label: 'L', labelKr: '좌' },
    { key: 'right', label: 'Rt', labelKr: '우' },
];

function DirectionalShields({ totalShield }: DirectionalShieldsProps) {
    // Default: even distribution
    const perDir = totalShield > 0 ? Math.round(totalShield / 4) : 0;
    // Remainder goes to front
    const frontVal = totalShield - perDir * 3;

    const values: Record<string, number> = {
        front: Math.max(0, frontVal),
        rear: perDir,
        left: perDir,
        right: perDir,
    };

    if (totalShield <= 0) return null;

    return (
        <div className="space-y-1 pt-1 border-t border-gray-800/50 mt-1">
            <div className="flex items-center justify-between">
                <span className="text-[8px] font-mono text-sky-600 tracking-wider uppercase">방향별 실드 배분</span>
                <span className="text-[8px] font-mono text-gray-600">합계: {totalShield}%</span>
            </div>
            <div className="grid grid-cols-4 gap-1">
                {SHIELD_DIRS.map((dir) => {
                    const val = values[dir.key] ?? 0;
                    const pct = totalShield > 0 ? (val / totalShield) * 100 : 25;
                    return (
                        <div key={dir.key} className="text-center space-y-0.5">
                            <div className="text-[8px] font-mono text-sky-500/70">
                                {dir.labelKr}({dir.label})
                            </div>
                            <div className="relative h-2 rounded-full bg-gray-800/80 overflow-hidden">
                                <div
                                    className="absolute inset-y-0 left-0 rounded-full bg-sky-400 transition-all duration-100"
                                    style={{ width: `${pct}%`, opacity: 0.7 }}
                                />
                            </div>
                            <div className="text-[9px] font-mono text-sky-300/80 tabular-nums">{val}%</div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
