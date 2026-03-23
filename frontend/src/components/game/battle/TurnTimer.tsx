'use client';

import { cn } from '@/lib/utils';

export interface PlayerReadyState {
    id: string;
    name: string;
    ready: boolean;
}

interface TurnTimerProps {
    seconds: number;
    maxSeconds?: number;
    turn: number;
    maxTurn?: number;
    submitted: boolean;
    onReady?: () => void;
    playerStates?: PlayerReadyState[];
}

export function TurnTimer({
    seconds,
    maxSeconds = 30,
    turn,
    maxTurn,
    submitted,
    onReady,
    playerStates = [],
}: TurnTimerProps) {
    const pct = Math.max(0, Math.min(100, (seconds / maxSeconds) * 100));
    const critical = seconds <= 5;
    const warning = seconds <= 10 && seconds > 5;
    const cautious = seconds <= 20 && seconds > 10;

    const strokeColor = critical ? '#f87171' : warning ? '#fbbf24' : cautious ? '#facc15' : '#34d399';

    const textColorClass = critical
        ? 'text-red-400'
        : warning
          ? 'text-amber-400'
          : cautious
            ? 'text-yellow-400'
            : 'text-emerald-400';

    // SVG circular progress
    const radius = 16;
    const circumference = 2 * Math.PI * radius;
    const strokeDash = (pct / 100) * circumference;

    const readyCount = playerStates.filter((p) => p.ready).length;
    const allReady = playerStates.length > 0 && readyCount === playerStates.length;

    return (
        <div className="space-y-1.5">
            <div className="flex items-center gap-3">
                {/* Circular timer */}
                <div className="relative shrink-0 w-11 h-11">
                    <svg width="44" height="44" className="rotate-[-90deg]">
                        {/* Track */}
                        <circle cx="22" cy="22" r={radius} fill="none" stroke="#1f2937" strokeWidth="3" />
                        {/* Progress */}
                        <circle
                            cx="22"
                            cy="22"
                            r={radius}
                            fill="none"
                            stroke={strokeColor}
                            strokeWidth="3"
                            strokeLinecap="round"
                            strokeDasharray={`${strokeDash} ${circumference}`}
                            className="transition-all duration-1000 ease-linear"
                            style={{
                                filter: critical ? `drop-shadow(0 0 3px ${strokeColor})` : undefined,
                            }}
                        />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                        <span
                            className={cn(
                                'text-[11px] font-mono font-black tabular-nums',
                                textColorClass,
                                critical && 'animate-pulse'
                            )}
                        >
                            {String(seconds).padStart(2, '0')}
                        </span>
                    </div>
                </div>

                {/* Turn info + player states */}
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                        <span className="text-[11px] font-mono font-bold text-amber-400/80">
                            Turn {turn}
                            {maxTurn ? ` / ${maxTurn}` : ''}
                        </span>
                        {submitted && (
                            <span className="text-[8px] font-mono text-emerald-400 animate-pulse">✓ 전송됨</span>
                        )}
                    </div>

                    {playerStates.length > 0 && (
                        <div className="flex items-center gap-1 mt-0.5 flex-wrap">
                            {playerStates.map((p) => (
                                <span
                                    key={p.id}
                                    className={cn(
                                        'text-[7px] font-mono px-1 py-0.5 rounded border',
                                        p.ready
                                            ? 'text-emerald-400 border-emerald-900/50 bg-emerald-900/10'
                                            : 'text-gray-600 border-gray-800/50 bg-gray-900/30'
                                    )}
                                >
                                    {p.name}
                                </span>
                            ))}
                        </div>
                    )}

                    {allReady && <div className="text-[8px] font-mono text-emerald-400 mt-0.5">전원 준비 완료</div>}
                </div>

                {/* Ready button */}
                {onReady && (
                    <button
                        type="button"
                        disabled={submitted}
                        onClick={onReady}
                        className={cn(
                            'shrink-0 px-2 py-1.5 rounded border text-[10px] font-mono font-bold transition-all duration-150',
                            submitted
                                ? 'border-emerald-900/50 bg-emerald-900/10 text-emerald-400 cursor-default'
                                : 'border-amber-700/60 bg-amber-900/10 text-amber-400 hover:border-amber-500/60 hover:bg-amber-900/20'
                        )}
                    >
                        {submitted ? '완료' : '준비'}
                    </button>
                )}
            </div>

            {/* Linear bar */}
            <div className="relative h-1.5 bg-gray-800/80 rounded-full overflow-hidden">
                <div
                    className="h-full rounded-full transition-all duration-1000 ease-linear"
                    style={{
                        width: `${pct}%`,
                        backgroundColor: strokeColor,
                        boxShadow: critical ? `0 0 6px ${strokeColor}` : undefined,
                    }}
                />
                {critical && (
                    <div
                        className="absolute inset-0 animate-pulse rounded-full"
                        style={{
                            width: `${pct}%`,
                            backgroundColor: `${strokeColor}33`,
                        }}
                    />
                )}
            </div>
        </div>
    );
}
