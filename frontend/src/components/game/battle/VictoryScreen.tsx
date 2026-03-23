'use client';

import { useEffect, useState } from 'react';
import type { BattleResultData } from '@/stores/battleStore';
import { cn } from '@/lib/utils';

export interface BattleHighlight {
    turn: number;
    description: string;
}

interface VictoryScreenProps {
    result: BattleResultData;
    highlights?: BattleHighlight[];
    onLeave: () => void;
}

export function VictoryScreen({ result, highlights = [], onLeave }: VictoryScreenProps) {
    const isVictory = result.winner === 'my_side';
    const isDraw = result.winner === 'draw';

    const [revealed, setRevealed] = useState(false);
    const [statsVisible, setStatsVisible] = useState(false);

    useEffect(() => {
        const t1 = setTimeout(() => setRevealed(true), 80);
        const t2 = setTimeout(() => setStatsVisible(true), 550);
        return () => {
            clearTimeout(t1);
            clearTimeout(t2);
        };
    }, []);

    const accentHex = isVictory ? '#FFD700' : isDraw ? '#888888' : '#FF4444';
    const bgGradient = isVictory
        ? 'from-amber-950/40 to-gray-950'
        : isDraw
          ? 'from-gray-900/40 to-gray-950'
          : 'from-red-950/30 to-gray-950';

    const resultText = isVictory ? 'VICTORY' : isDraw ? 'CEASEFIRE' : 'DEFEAT';
    const resultSubtext = isVictory
        ? '전투 승리 — 전장을 장악하였다'
        : isDraw
          ? '전투 무승부 — 교전 중단'
          : '전투 패배 — 퇴각 명령 하달';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm">
            {/* Scanline texture */}
            <div
                className="absolute inset-0 pointer-events-none opacity-[0.025]"
                style={{
                    backgroundImage:
                        'repeating-linear-gradient(0deg, transparent, transparent 2px, #fff 2px, #fff 3px)',
                }}
            />

            <div
                className={cn(
                    'relative w-full max-w-md mx-4 rounded-lg overflow-hidden border',
                    'transition-all duration-500 ease-out',
                    revealed ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-6'
                )}
                style={{ borderColor: `${accentHex}2a` }}
            >
                {/* Background layers */}
                <div className={cn('absolute inset-0 bg-gradient-to-b', bgGradient, 'pointer-events-none')} />
                <div className="absolute inset-0 bg-gray-950/60 pointer-events-none" />

                <div className="relative p-6 space-y-5">
                    {/* Result announcement */}
                    <div className="text-center space-y-2">
                        <div
                            className={cn(
                                'text-4xl font-mono font-black tracking-[0.25em] transition-all duration-700',
                                revealed ? 'opacity-100 scale-100' : 'opacity-0 scale-90',
                                isVictory ? 'text-amber-400' : isDraw ? 'text-gray-400' : 'text-red-400'
                            )}
                            style={{ textShadow: `0 0 32px ${accentHex}55` }}
                        >
                            {resultText}
                        </div>
                        <div className="text-[10px] font-mono text-gray-600 tracking-widest">{resultSubtext}</div>
                        <div className="h-px w-20 mx-auto opacity-30" style={{ backgroundColor: accentHex }} />
                    </div>

                    {/* Stats grid */}
                    <div
                        className={cn(
                            'grid grid-cols-2 gap-2.5 transition-all duration-500',
                            statsVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'
                        )}
                    >
                        <StatCard
                            label="격침 함선"
                            value={result.shipsDestroyed.toLocaleString()}
                            unit="척"
                            color="text-red-400"
                        />
                        <StatCard
                            label="아군 피해"
                            value={result.shipsLost.toLocaleString()}
                            unit="척"
                            color="text-amber-300"
                        />
                        <StatCard
                            label="포로"
                            value={result.prisoners.toLocaleString()}
                            unit="명"
                            color="text-sky-400"
                        />
                        <StatCard label="소요 턴" value={String(result.turnCount)} unit="턴" color="text-gray-400" />
                    </div>

                    {/* Merit points */}
                    <div
                        className={cn(
                            'rounded p-3 text-center border transition-all duration-500 delay-100',
                            statsVisible ? 'opacity-100' : 'opacity-0'
                        )}
                        style={{
                            borderColor: `${accentHex}22`,
                            backgroundColor: `${accentHex}08`,
                        }}
                    >
                        <div
                            className="text-[9px] font-mono tracking-widest uppercase mb-1"
                            style={{ color: `${accentHex}88` }}
                        >
                            공적 포인트
                        </div>
                        <div
                            className="text-3xl font-mono font-black tabular-nums"
                            style={{
                                color: accentHex,
                                textShadow: `0 0 16px ${accentHex}33`,
                            }}
                        >
                            +{result.meritPoints.toLocaleString()}
                        </div>
                    </div>

                    {/* Planet captured */}
                    {result.planetCaptured && (
                        <div
                            className={cn(
                                'border border-sky-900/40 bg-sky-900/10 rounded p-2.5 text-center',
                                'transition-all duration-500 delay-150',
                                statsVisible ? 'opacity-100' : 'opacity-0'
                            )}
                        >
                            <div className="text-[9px] font-mono text-sky-500/70 uppercase tracking-widest mb-0.5">
                                행성 점령
                            </div>
                            <div className="text-sm font-mono font-bold text-sky-400">{result.planetCaptured}</div>
                        </div>
                    )}

                    {/* Highlights */}
                    {highlights.length > 0 && (
                        <div
                            className={cn(
                                'space-y-1 transition-all duration-500 delay-200',
                                statsVisible ? 'opacity-100' : 'opacity-0'
                            )}
                        >
                            <div className="text-[9px] font-mono text-gray-600 uppercase tracking-widest">
                                주요 전황
                            </div>
                            {highlights.map((h, i) => (
                                <div key={i} className="flex items-start gap-2 text-[9px] font-mono">
                                    <span className="text-gray-700 shrink-0 tabular-nums">
                                        T{String(h.turn).padStart(2, '0')}
                                    </span>
                                    <span className="text-gray-500">{h.description}</span>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Leave button */}
                    <button
                        type="button"
                        onClick={onLeave}
                        className="w-full py-2.5 rounded border font-mono text-sm font-bold tracking-widest transition-all duration-150 border-gray-700/60 text-gray-400 hover:border-gray-500 hover:text-gray-200 hover:bg-gray-800/40"
                    >
                        전장 이탈 // LEAVE BATTLEFIELD
                    </button>
                </div>
            </div>
        </div>
    );
}

function StatCard({ label, value, unit, color }: { label: string; value: string; unit: string; color: string }) {
    return (
        <div className="bg-gray-900/60 border border-gray-800/60 rounded p-2.5">
            <div className="text-[8px] font-mono text-gray-600 uppercase tracking-wider mb-0.5">{label}</div>
            <div className="flex items-baseline gap-1">
                <span className={cn('text-base font-mono font-black tabular-nums', color)}>{value}</span>
                <span className="text-[8px] font-mono text-gray-600">{unit}</span>
            </div>
        </div>
    );
}
