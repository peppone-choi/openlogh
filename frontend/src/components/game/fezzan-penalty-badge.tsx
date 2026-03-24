'use client';

import type { Faction } from '@/types';
import { Badge } from '@/components/ui/badge';
import { AlertTriangle } from 'lucide-react';

interface FezzanPenaltyBadgeProps {
    faction: Faction | null | undefined;
}

/**
 * Displays a warning badge when the faction is under Fezzan neutrality penalty.
 * Reads `meta.fezzanPenalty` (months remaining) and `meta.fezzanPenaltyTradeReduction` (reduction %).
 */
export function FezzanPenaltyBadge({ faction }: FezzanPenaltyBadgeProps) {
    if (!faction) return null;

    const meta = faction.meta ?? {};
    const monthsRemaining = typeof meta.fezzanPenalty === 'number' ? meta.fezzanPenalty : 0;
    const tradeReduction = typeof meta.fezzanPenaltyTradeReduction === 'number' ? meta.fezzanPenaltyTradeReduction : 50;

    if (monthsRemaining <= 0) return null;

    return (
        <Badge
            variant="destructive"
            className="flex items-center gap-1 text-xs px-2 py-1 bg-red-700/80 border border-red-500 text-red-100"
        >
            <AlertTriangle className="size-3 shrink-0" />
            페잔 제재 중 (교역 -{tradeReduction}%, {monthsRemaining}개월 남음)
        </Badge>
    );
}
