'use client';

import { MapPin, AlertTriangle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';

interface CommanderLocationBadgeProps {
    commanderLocation?: string;
    fleetLocation?: string;
    /** Show even when locations match (default: false) */
    showWhenSame?: boolean;
}

/**
 * Shows when a commander is on a different planet/location than their fleet.
 * e.g. "사령관: 오딘 / 함대: 그리드 B-7"
 */
export function CommanderLocationBadge({
    commanderLocation,
    fleetLocation,
    showWhenSame = false,
}: CommanderLocationBadgeProps) {
    if (!commanderLocation && !fleetLocation) return null;

    const isSeparated = commanderLocation !== fleetLocation && commanderLocation && fleetLocation;

    if (!isSeparated && !showWhenSame) return null;

    if (!isSeparated) {
        return (
            <div className="flex items-center gap-1 text-[10px] text-muted-foreground">
                <MapPin className="size-3" />
                {commanderLocation ?? fleetLocation}
            </div>
        );
    }

    return (
        <TooltipProvider delayDuration={200}>
            <Tooltip>
                <TooltipTrigger asChild>
                    <Badge variant="outline" className="text-[10px] px-1.5 border-orange-400/40 text-orange-400 gap-1">
                        <AlertTriangle className="size-3" />
                        분리 상태
                    </Badge>
                </TooltipTrigger>
                <TooltipContent side="bottom">
                    <div className="text-xs space-y-1">
                        <div className="flex items-center gap-2">
                            <span className="text-muted-foreground">사령관:</span>
                            <span className="text-amber-400">{commanderLocation}</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="text-muted-foreground">함대:</span>
                            <span className="text-blue-400">{fleetLocation}</span>
                        </div>
                        <div className="text-orange-400 text-[10px]">사령관과 함대가 다른 위치에 있습니다.</div>
                    </div>
                </TooltipContent>
            </Tooltip>
        </TooltipProvider>
    );
}
