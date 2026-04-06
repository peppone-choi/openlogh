'use client';

import { useEffect } from 'react';
import { useVictoryStore } from '@/stores/victoryStore';
import { useWorldStore } from '@/stores/worldStore';
import { VICTORY_CONDITION_LABELS, VICTORY_TIER_LABELS } from '@/types/victory';
import { Trophy, Crown, Swords, Clock } from 'lucide-react';

const CONDITION_ICONS = {
    CAPITAL_CAPTURE: Crown,
    SYSTEM_THRESHOLD: Swords,
    TIME_LIMIT: Clock,
} as const;

export default function VictoryPage() {
    const { victoryResult, rankings, isSessionEnded, fetchVictoryResult, fetchRankings } =
        useVictoryStore();
    const worldId = useWorldStore((s) => s.currentWorld?.id ?? null);

    useEffect(() => {
        if (worldId) {
            fetchVictoryResult(worldId);
            fetchRankings(worldId);
        }
    }, [worldId, fetchVictoryResult, fetchRankings]);

    if (!isSessionEnded || !victoryResult) {
        return (
            <div className="flex items-center justify-center min-h-[60vh]">
                <p className="text-slate-400">세션이 아직 진행 중입니다</p>
            </div>
        );
    }

    const ConditionIcon = CONDITION_ICONS[victoryResult.condition] ?? Trophy;
    const tierLabel = VICTORY_TIER_LABELS[victoryResult.tier] ?? victoryResult.tier;
    const conditionLabel = VICTORY_CONDITION_LABELS[victoryResult.condition] ?? victoryResult.condition;

    return (
        <div className="max-w-4xl mx-auto py-8 px-4 space-y-8">
            {/* Victory header */}
            <div className="text-center space-y-4">
                <div className="flex justify-center">
                    <div className="p-4 rounded-full bg-amber-900/50 border-2 border-amber-500">
                        <Trophy className="size-12 text-amber-400" />
                    </div>
                </div>
                <h1 className="text-3xl font-bold text-amber-100">{tierLabel}</h1>
                <div className="flex items-center justify-center gap-2 text-slate-300">
                    <ConditionIcon className="size-5" />
                    <span>{conditionLabel}</span>
                </div>
            </div>

            {/* Factions result */}
            <div className="grid grid-cols-2 gap-4">
                <div className="bg-blue-900/30 border border-blue-700 rounded-lg p-6 text-center">
                    <p className="text-xs text-blue-400 mb-2">승리</p>
                    <p className="text-xl font-bold text-blue-200">{victoryResult.winnerName}</p>
                </div>
                <div className="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center">
                    <p className="text-xs text-red-400 mb-2">패배</p>
                    <p className="text-xl font-bold text-red-200">{victoryResult.loserName}</p>
                </div>
            </div>

            {/* Rankings table */}
            {rankings.length > 0 && (
                <div>
                    <h2 className="text-lg font-semibold text-slate-200 mb-4">최종 순위</h2>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-slate-700 text-slate-400">
                                    <th className="py-2 px-3 text-left">#</th>
                                    <th className="py-2 px-3 text-left">이름</th>
                                    <th className="py-2 px-3 text-left">진영</th>
                                    <th className="py-2 px-3 text-right">점수</th>
                                    <th className="py-2 px-3 text-right">공적</th>
                                    <th className="py-2 px-3 text-right">격파</th>
                                    <th className="py-2 px-3 text-right">점령</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rankings.map((r) => (
                                    <tr
                                        key={r.officerId}
                                        className={`border-b border-slate-800 ${
                                            r.isPlayer ? 'text-white' : 'text-slate-400'
                                        } ${r.rank <= 3 ? 'bg-amber-900/10' : ''}`}
                                    >
                                        <td className="py-2 px-3">
                                            {r.rank <= 3 ? (
                                                <span className="text-amber-400 font-bold">{r.rank}</span>
                                            ) : (
                                                r.rank
                                            )}
                                        </td>
                                        <td className="py-2 px-3 font-medium">
                                            {r.officerName}
                                            {r.isPlayer && (
                                                <span className="ml-1 text-[10px] text-blue-400">[P]</span>
                                            )}
                                        </td>
                                        <td className="py-2 px-3">{r.factionName}</td>
                                        <td className="py-2 px-3 text-right font-mono">
                                            {r.score.toLocaleString()}
                                        </td>
                                        <td className="py-2 px-3 text-right">{r.meritPoints}</td>
                                        <td className="py-2 px-3 text-right">{r.kills}</td>
                                        <td className="py-2 px-3 text-right">{r.territoryCaptured}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}
