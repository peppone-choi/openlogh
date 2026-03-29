'use client';

import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { OfficerPositionCard } from '@/types';

interface PositionCardChipProps {
    card: OfficerPositionCard;
    variant?: 'default' | 'vacant';
}

export function PositionCardChip({ card, variant = 'default' }: PositionCardChipProps) {
    if (variant === 'vacant') {
        return (
            <Badge variant="ghost" className="font-game text-muted-foreground px-1 py-0">
                [&#x25A1; &#44277;&#49437;]
            </Badge>
        );
    }

    return (
        <TooltipProvider>
            <Tooltip>
                <TooltipTrigger asChild>
                    <Badge variant="outline" className="font-game px-1 py-0 cursor-default">
                        {card.positionNameKo}
                    </Badge>
                </TooltipTrigger>
                <TooltipContent side="bottom" className="max-w-60">
                    <div className="space-y-1">
                        <div className="font-semibold text-xs">{card.positionNameKo}</div>
                        <div className="text-[10px] text-muted-foreground">{card.positionType} / {card.category}</div>
                        {card.grantedCommands.length > 0 && (
                            <div className="text-[10px]">
                                <span className="text-muted-foreground">commands: </span>
                                {card.grantedCommands.join(', ')}
                            </div>
                        )}
                    </div>
                </TooltipContent>
            </Tooltip>
        </TooltipProvider>
    );
}
