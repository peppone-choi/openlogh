import { Badge } from '@/components/ui/8bit/badge';

interface RankBadgeProps {
    rankTier: number;
    rankTitle: string;
    rankTitleKo: string;
    meritPoints?: number;
    showMerit?: boolean;
    size?: 'sm' | 'md';
}

/** Rank tier color mapping - higher ranks get more distinguished colors */
function getRankColor(tier: number): string {
    if (tier >= 10) return '#ffd700'; // gold - marshal
    if (tier >= 8) return '#c0c0c0'; // silver - admiral
    if (tier >= 5) return '#cd7f32'; // bronze - flag officers
    return '#6b7280'; // gray - junior officers
}

export function RankBadge({
    rankTier,
    rankTitle,
    rankTitleKo,
    meritPoints,
    showMerit = false,
    size = 'sm',
}: RankBadgeProps) {
    const color = getRankColor(rankTier);
    const starSize = size === 'sm' ? 'text-xs' : 'text-sm';

    return (
        <div className="inline-flex items-center gap-1.5">
            <Badge
                variant="outline"
                className="gap-1"
                style={{ borderColor: color }}
            >
                <span className={starSize} style={{ color }}>
                    {'★'.repeat(Math.min(rankTier + 1, 5))}
                </span>
                <span style={{ color }}>
                    {rankTitleKo}
                </span>
            </Badge>
            {showMerit && meritPoints !== undefined && (
                <span className="text-xs text-muted-foreground">
                    Merit {meritPoints}
                </span>
            )}
        </div>
    );
}

interface MeritProgressBarProps {
    meritPoints: number;
    maxMerit?: number;
}

/** Visual progress bar showing merit point accumulation */
export function MeritProgressBar({
    meritPoints,
    maxMerit = 1000,
}: MeritProgressBarProps) {
    const percentage = Math.min((meritPoints / maxMerit) * 100, 100);

    return (
        <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground whitespace-nowrap">
                Merit
            </span>
            <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                <div
                    className="h-full bg-amber-500 rounded-full transition-all duration-300"
                    style={{ width: `${percentage}%` }}
                />
            </div>
            <span className="text-xs font-mono text-muted-foreground">
                {meritPoints}
            </span>
        </div>
    );
}
