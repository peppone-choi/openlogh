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
        sublabel: '방어막',
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
];

type PresetKey = 'attack' | 'defense' | 'mobility' | 'balanced' | 'recon';

const PRESETS: {
    key: PresetKey;
    label: string;
    values: Omit<EnergyAllocation, 'sensor'>;
}[] = [
    { key: 'attack', label: '공격형', values: { beam: 40, gun: 30, shield: 10, engine: 10 } },
    { key: 'defense', label: '방어형', values: { beam: 10, gun: 10, shield: 50, engine: 10 } },
    { key: 'mobility', label: '기동형', values: { beam: 20, gun: 10, shield: 10, engine: 50 } },
    { key: 'balanced', label: '균형형', values: { beam: 20, gun: 20, shield: 20, engine: 20 } },
    { key: 'recon', label: '색적형', values: { beam: 15, gun: 15, shield: 10, engine: 10 } },
];

interface EnergyAllocatorProps {
    value: EnergyAllocation;
    onChange: (key: keyof Omit<EnergyAllocation, 'sensor'>, value: number) => void;
    onPreset?: (values: Omit<EnergyAllocation, 'sensor'>) => void;
}

export function EnergyAllocator({ value, onChange, onPreset }: EnergyAllocatorProps) {
    const manualTotal = value.beam + value.gun + value.shield + value.engine;
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
                                    <span className="text-[8px] text-gray-600 truncate">{ch.effectFn(pct)}</span>
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
            </div>
        </div>
    );
}
