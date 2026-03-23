'use client';

import { useState } from 'react';
import type { Formation } from '@/stores/battleStore';
import { cn } from '@/lib/utils';

interface FormationEffect {
    label: string;
    value: string;
    positive: boolean;
}

interface FormationDef {
    id: Formation;
    label: string;
    sublabel: string;
    stat: string;
    svgPath: string;
    effects: FormationEffect[];
    description: string;
}

const FORMATIONS: FormationDef[] = [
    {
        id: 'spindle',
        label: '방추형',
        sublabel: 'Spindle',
        stat: '돌파 ↑ 측면 ↓',
        svgPath: 'M20,3 L37,14 L20,25 L3,14 Z',
        effects: [
            { label: '돌파력', value: '+30%', positive: true },
            { label: '집중 사격', value: '+20%', positive: true },
            { label: '측면 방어', value: '-20%', positive: false },
        ],
        description: '첨두 집중 전진. 적진 돌파에 최적화된 공격 진형.',
    },
    {
        id: 'crane_wing',
        label: '학익진',
        sublabel: 'Crane Wing',
        stat: '포위 ↑ 중앙 ↓',
        svgPath: 'M20,14 L3,3 L10,14 L3,25 M20,14 L37,3 L30,14 L37,25',
        effects: [
            { label: '포위 사거리', value: '+25%', positive: true },
            { label: '측면 공격', value: '+35%', positive: true },
            { label: '중앙 방어', value: '-25%', positive: false },
        ],
        description: '양익 전개 포위 진형. 적 함대 섬멸에 최적.',
    },
    {
        id: 'wheel',
        label: '차륜진',
        sublabel: 'Wheel',
        stat: '균형 회전 기동',
        svgPath: 'M20,14 m-11,0 a11,11 0 1,0 22,0 a11,11 0 1,0 -22,0 M20,3 L20,25 M9,14 L31,14',
        effects: [
            { label: '전방위 사격', value: '+15%', positive: true },
            { label: '회전 기동', value: '+20%', positive: true },
            { label: '돌파력', value: '-10%', positive: false },
        ],
        description: '전방위 균형 전투. 다방면 적에 유연한 대응.',
    },
    {
        id: 'echelon',
        label: '사선진',
        sublabel: 'Echelon',
        stat: '우회 ↑ 정면 ↓',
        svgPath: 'M7,22 L33,6 M7,22 L13,22 M27,6 L33,6 M13,14 L19,14',
        effects: [
            { label: '우회 기동', value: '+30%', positive: true },
            { label: '측면 타격', value: '+15%', positive: true },
            { label: '정면 방어', value: '-20%', positive: false },
        ],
        description: '사각 전개 우회 기동. 적 측면 강타에 적합.',
    },
    {
        id: 'square',
        label: '방진',
        sublabel: 'Square',
        stat: '방어 ↑ 기동 ↓',
        svgPath: 'M6,6 L34,6 L34,22 L6,22 Z M6,6 L34,22 M34,6 L6,22',
        effects: [
            { label: '방어력', value: '+35%', positive: true },
            { label: '피해 감소', value: '+20%', positive: true },
            { label: '이동 속도', value: '-30%', positive: false },
        ],
        description: '전방위 방어 진형. 후퇴전 및 지연전에 적합.',
    },
    {
        id: 'dispersed',
        label: '산개진',
        sublabel: 'Dispersed',
        stat: '피해분산 공격 ↓',
        svgPath:
            'M8,8 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 M32,8 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 M20,14 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 M8,22 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 M32,22 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0',
        effects: [
            { label: '피해 분산', value: '+40%', positive: true },
            { label: '광역 생존율', value: '+25%', positive: true },
            { label: '집중 화력', value: '-35%', positive: false },
        ],
        description: '광역 분산 전개. 대규모 광역 공격에 높은 생존율.',
    },
];

interface FormationSelectorProps {
    value: Formation;
    onChange: (f: Formation) => void;
    onCooldown?: boolean;
    cooldownTurnsLeft?: number;
}

export function FormationSelector({
    value,
    onChange,
    onCooldown = false,
    cooldownTurnsLeft = 0,
}: FormationSelectorProps) {
    const [hoveredId, setHoveredId] = useState<Formation | null>(null);
    const preview = FORMATIONS.find((f) => f.id === (hoveredId ?? value));

    return (
        <div className="space-y-1.5">
            <div className="flex items-center justify-between">
                <span className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">
                    진형 선택 // Formation
                </span>
                {onCooldown && cooldownTurnsLeft > 0 && (
                    <span className="text-[9px] font-mono text-red-400/80 bg-red-900/20 px-1.5 py-0.5 rounded border border-red-900/40">
                        쿨다운 {cooldownTurnsLeft}턴
                    </span>
                )}
            </div>
            <div className="grid grid-cols-3 gap-1.5">
                {FORMATIONS.map((f) => {
                    const active = value === f.id;
                    const disabled = onCooldown && !active;
                    const hovered = hoveredId === f.id;
                    return (
                        <button
                            key={f.id}
                            type="button"
                            disabled={disabled}
                            onClick={() => !disabled && onChange(f.id)}
                            onMouseEnter={() => setHoveredId(f.id)}
                            onMouseLeave={() => setHoveredId(null)}
                            className={cn(
                                'flex flex-col items-center gap-1 p-2 rounded border transition-all duration-150',
                                'text-center select-none',
                                active
                                    ? 'border-amber-400/70 bg-amber-400/10 shadow-[0_0_8px_rgba(255,215,0,0.2)]'
                                    : disabled
                                      ? 'border-gray-800/40 bg-gray-900/20 opacity-35 cursor-not-allowed'
                                      : 'border-gray-700/50 bg-gray-900/40 hover:border-gray-600 hover:bg-gray-800/40'
                            )}
                        >
                            <svg
                                viewBox="0 0 40 28"
                                className={cn(
                                    'w-9 h-6 transition-colors duration-150',
                                    active ? 'text-amber-400' : hovered ? 'text-amber-300/60' : 'text-gray-500'
                                )}
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="1.5"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            >
                                <path d={f.svgPath} />
                            </svg>
                            <span
                                className={cn(
                                    'text-[10px] font-mono font-bold leading-tight',
                                    active ? 'text-amber-400' : hovered ? 'text-amber-300/60' : 'text-gray-400'
                                )}
                            >
                                {f.label}
                            </span>
                            <span className="text-[8px] text-gray-600 leading-tight">{f.stat}</span>
                        </button>
                    );
                })}
            </div>

            {/* Effect preview panel */}
            {preview && (
                <div className="border border-gray-800/50 bg-gray-900/30 rounded p-2 space-y-1.5">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-mono font-bold text-amber-400/80">{preview.label}</span>
                        <span className="text-[8px] font-mono text-gray-600">{preview.sublabel}</span>
                    </div>
                    <p className="text-[9px] text-gray-500 leading-relaxed">{preview.description}</p>
                    <div className="flex flex-wrap gap-1">
                        {preview.effects.map((effect) => (
                            <span
                                key={effect.label}
                                className={cn(
                                    'text-[8px] font-mono px-1.5 py-0.5 rounded border',
                                    effect.positive
                                        ? 'text-emerald-400 border-emerald-900/50 bg-emerald-900/10'
                                        : 'text-red-400 border-red-900/50 bg-red-900/10'
                                )}
                            >
                                {effect.label} {effect.value}
                            </span>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
