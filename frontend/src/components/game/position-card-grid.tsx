'use client';

import { PositionCardChip } from '@/components/game/position-card-chip';
import type { OfficerPositionCard } from '@/types';

interface PositionCardGridProps {
    cards: OfficerPositionCard[];
    maxCards?: number;
}

export function PositionCardGrid({ cards, maxCards = 16 }: PositionCardGridProps) {
    if (cards.length === 0) {
        return (
            <div className="text-sm text-muted-foreground">
                보유한 직무카드가 없습니다
            </div>
        );
    }

    const visibleCards = cards.slice(0, maxCards);

    return (
        <div className="flex flex-wrap gap-2">
            {visibleCards.map((card) => (
                <PositionCardChip key={card.id} card={card} />
            ))}
        </div>
    );
}
