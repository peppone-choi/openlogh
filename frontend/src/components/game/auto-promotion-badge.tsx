'use client';

import { useEffect, useState } from 'react';
import { Award, Clock } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';

interface AutoPromotionBadgeProps {
    /** Officer's rank ladder position (1 = top of ladder) */
    rankLadderPosition?: number;
    /** Game days until next auto-promotion check */
    gameDaysSinceLastCheck?: number;
    /** Total game days between checks */
    autoPromotionInterval?: number;
}

/**
 * Shows "자동 승진 대상" badge for rank ladder #1 officers
 * and countdown to next auto-promotion check (every 30 game days).
 */
export function AutoPromotionBadge({
    rankLadderPosition = 0,
    gameDaysSinceLastCheck = 0,
    autoPromotionInterval = 30,
}: AutoPromotionBadgeProps) {
    const isEligible = rankLadderPosition === 1;
    const daysRemaining = Math.max(0, autoPromotionInterval - gameDaysSinceLastCheck);

    if (!isEligible && daysRemaining <= 0) return null;

    return (
        <TooltipProvider delayDuration={200}>
            <Tooltip>
                <TooltipTrigger asChild>
                    <div className="inline-flex items-center gap-1">
                        {isEligible && (
                            <Badge variant="default" className="text-[10px] px-1.5 bg-amber-600 hover:bg-amber-700">
                                <Award className="size-3 mr-0.5" />
                                자동 승진 대상
                            </Badge>
                        )}
                        {daysRemaining > 0 && (
                            <Badge variant="outline" className="text-[10px] px-1.5 text-muted-foreground">
                                <Clock className="size-3 mr-0.5" />
                                {daysRemaining}일 후 심사
                            </Badge>
                        )}
                    </div>
                </TooltipTrigger>
                <TooltipContent side="bottom" className="max-w-xs">
                    <div className="text-xs space-y-1">
                        <div className="font-semibold">자동 승진 시스템</div>
                        <div>매 {autoPromotionInterval}일마다 계급 사다리 1위 제독이 자동 승진합니다.</div>
                        {isEligible && (
                            <div className="text-amber-400">현재 계급 사다리 1위 - 다음 심사 시 자동 승진됩니다.</div>
                        )}
                        <div className="text-muted-foreground">다음 심사까지: {daysRemaining}일</div>
                    </div>
                </TooltipContent>
            </Tooltip>
        </TooltipProvider>
    );
}
