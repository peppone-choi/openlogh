'use client';

import type { BattleResultData } from '@/stores/battleStore';
import { cn } from '@/lib/utils';

interface BattleResultProps {
    result: BattleResultData;
    onClose: () => void;
}

export function BattleResult({ result, onClose }: BattleResultProps) {
    const isVictory = result.winner === 'my_side';
    const isDraw = result.winner === 'draw';

    return (
        <div
            className="relative bg-gray-950 border rounded-lg overflow-hidden"
            style={{ borderColor: isVictory ? '#FFD70044' : isDraw ? '#88888844' : '#FF444444' }}
        >
            {/* Background glow */}
            <div
                className={cn(
                    'absolute inset-0 opacity-5 pointer-events-none',
                    isVictory ? 'bg-amber-400' : isDraw ? 'bg-gray-400' : 'bg-red-500'
                )}
            />

            {/* Scanline overlay */}
            <div
                className="absolute inset-0 pointer-events-none opacity-[0.03]"
                style={{
                    backgroundImage:
                        'repeating-linear-gradient(0deg, transparent, transparent 2px, #fff 2px, #fff 3px)',
                }}
            />

            <div className="relative p-6 space-y-5">
                {/* Victory / Defeat announcement */}
                <div className="text-center space-y-1">
                    <div
                        className={cn(
                            'text-3xl font-mono font-black tracking-widest',
                            isVictory ? 'text-amber-400' : isDraw ? 'text-gray-400' : 'text-red-400'
                        )}
                        style={{
                            textShadow: isVictory
                                ? '0 0 20px rgba(255,215,0,0.5)'
                                : isDraw
                                  ? '0 0 20px rgba(128,128,128,0.4)'
                                  : '0 0 20px rgba(255,68,68,0.5)',
                        }}
                    >
                        {isVictory ? '── VICTORY ──' : isDraw ? '── CEASEFIRE ──' : '── DEFEAT ──'}
                    </div>
                    <div className="text-[11px] font-mono text-gray-500">
                        {isVictory ? '전투 승리 — 제국의 영광' : isDraw ? '전투 무승부' : '전투 패배 — 퇴각'}
                    </div>
                </div>

                {/* Stats grid */}
                <div className="grid grid-cols-2 gap-3">
                    <StatRow label="격침 함선" value={result.shipsDestroyed.toLocaleString()} color="text-red-400" />
                    <StatRow label="아군 피해" value={result.shipsLost.toLocaleString()} color="text-amber-300" />
                    <StatRow label="포로" value={`${result.prisoners.toLocaleString()}명`} color="text-sky-400" />
                    <StatRow label="소요 턴" value={`${result.turnCount}턴`} color="text-gray-400" />
                </div>

                {/* Merit points */}
                <div className="border border-amber-900/40 bg-amber-900/10 rounded p-3 text-center">
                    <div className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase mb-1">
                        공적 포인트 획득
                    </div>
                    <div className="text-2xl font-mono font-black text-amber-400">
                        +{result.meritPoints.toLocaleString()}
                    </div>
                </div>

                {/* Planet captured */}
                {result.planetCaptured && (
                    <div className="border border-sky-900/40 bg-sky-900/10 rounded p-2.5 text-center">
                        <div className="text-[10px] font-mono text-sky-500/70 uppercase tracking-widest mb-0.5">
                            행성 점령
                        </div>
                        <div className="text-sm font-mono font-bold text-sky-400">{result.planetCaptured}</div>
                    </div>
                )}

                {/* Close button */}
                <button
                    type="button"
                    onClick={onClose}
                    className="w-full py-2.5 border border-gray-700/60 rounded font-mono text-sm text-gray-400 hover:border-gray-500 hover:text-gray-200 hover:bg-gray-800/40 transition-all duration-150"
                >
                    전략 지도로 복귀
                </button>
            </div>
        </div>
    );
}

function StatRow({ label, value, color }: { label: string; value: string; color: string }) {
    return (
        <div className="bg-gray-900/60 border border-gray-800/60 rounded p-2.5 space-y-0.5">
            <div className="text-[9px] font-mono text-gray-600 uppercase tracking-wider">{label}</div>
            <div className={cn('text-base font-mono font-bold tabular-nums', color)}>{value}</div>
        </div>
    );
}
