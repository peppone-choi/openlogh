'use client';

import { useVictoryStore } from '@/stores/victoryStore';
import { VICTORY_CONDITION_LABELS, VICTORY_TIER_LABELS } from '@/types/victory';
import { Trophy } from 'lucide-react';
import Link from 'next/link';

export function VictoryBanner() {
    const { isSessionEnded, victoryResult } = useVictoryStore();

    if (!isSessionEnded || !victoryResult) return null;

    const tierLabel = VICTORY_TIER_LABELS[victoryResult.tier] ?? victoryResult.tier;
    const conditionLabel = VICTORY_CONDITION_LABELS[victoryResult.condition] ?? victoryResult.condition;

    return (
        <div className="fixed top-0 left-0 right-0 z-50 bg-amber-900/95 border-b-2 border-amber-500 text-amber-100 px-4 py-3">
            <div className="max-w-4xl mx-auto flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <Trophy className="size-6 text-amber-400" />
                    <div>
                        <p className="font-bold text-lg">
                            {tierLabel} - {conditionLabel}
                        </p>
                        <p className="text-sm text-amber-200">
                            {victoryResult.winnerName} 승리 / {victoryResult.loserName} 패배
                        </p>
                    </div>
                </div>
                <Link
                    href="/victory"
                    className="px-4 py-2 bg-amber-600 hover:bg-amber-500 rounded text-sm font-medium transition-colors"
                >
                    상세 보기
                </Link>
            </div>
        </div>
    );
}
