'use client';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Crown, TrendingUp } from 'lucide-react';
import type { Officer } from '@/types';
import { formatOfficerLevelText } from '@/lib/game-utils';

interface RankLadderProps {
    /** Officers to display — should be pre-filtered to a single rank tier */
    officers: Officer[];
    /** The current user's officer ID to highlight */
    myOfficerId?: number;
    /** The faction's rank level for display formatting */
    factionLevel?: number;
    /** The faction's type code for display formatting */
    factionTypeCode?: string;
    /** Title override */
    title?: string;
}

/**
 * Five-rule sort (오규칙): experience desc, then leadership desc, then command desc,
 * then intelligence desc, then officer name asc.
 */
function fiveRuleSort(officers: Officer[]): Officer[] {
    return [...officers].sort((a, b) => {
        const expDiff = (b.experience ?? 0) - (a.experience ?? 0);
        if (expDiff !== 0) return expDiff;
        const ldDiff = (b.leadership ?? 0) - (a.leadership ?? 0);
        if (ldDiff !== 0) return ldDiff;
        const cmdDiff = (b.command ?? b.strength ?? 0) - (a.command ?? a.strength ?? 0);
        if (cmdDiff !== 0) return cmdDiff;
        const intDiff = (b.intelligence ?? b.intel ?? 0) - (a.intelligence ?? a.intel ?? 0);
        if (intDiff !== 0) return intDiff;
        return a.name.localeCompare(b.name, 'ko');
    });
}

export function RankLadder({ officers, myOfficerId, factionLevel, factionTypeCode, title }: RankLadderProps) {
    const sorted = fiveRuleSort(officers);
    const myIndex = myOfficerId != null ? sorted.findIndex((o) => o.id === myOfficerId) : -1;

    // Rank label for the group (use first officer's officerLevel)
    const rankLevel = sorted[0]?.officerLevel;
    const rankLabel =
        rankLevel != null ? formatOfficerLevelText(rankLevel, factionLevel ?? 0, true, factionTypeCode) : '동급';

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <TrendingUp className="size-4 text-blue-400" />
                    {title ?? `${rankLabel} 서열`}
                    <Badge variant="outline" className="ml-auto text-xs">
                        {sorted.length}명
                    </Badge>
                </CardTitle>
            </CardHeader>
            <CardContent>
                {sorted.length === 0 ? (
                    <div className="text-xs text-muted-foreground py-2">해당 계급의 제독이 없습니다.</div>
                ) : (
                    <div className="space-y-1">
                        {sorted.map((o, idx) => {
                            const isMe = o.id === myOfficerId;
                            const isTop = idx === 0;
                            return (
                                <div
                                    key={o.id}
                                    className={`flex items-center gap-2 rounded px-2 py-1.5 text-sm transition-colors ${
                                        isMe ? 'bg-amber-500/15 border border-amber-700/50' : 'hover:bg-muted/30'
                                    }`}
                                >
                                    {/* Rank number */}
                                    <span
                                        className={`w-6 text-center text-xs font-bold tabular-nums shrink-0 ${
                                            isTop ? 'text-amber-400' : 'text-muted-foreground'
                                        }`}
                                    >
                                        {isTop ? <Crown className="size-3 inline" /> : idx + 1}
                                    </span>

                                    {/* Name */}
                                    <span className={`flex-1 font-medium truncate ${isMe ? 'text-amber-300' : ''}`}>
                                        {o.name}
                                    </span>

                                    {/* Promotion candidate label */}
                                    {isTop && (
                                        <Badge
                                            variant="outline"
                                            className="text-[10px] px-1 h-4 text-amber-400 border-amber-700"
                                        >
                                            승진 후보
                                        </Badge>
                                    )}

                                    {isMe && !isTop && (
                                        <Badge
                                            variant="outline"
                                            className="text-[10px] px-1 h-4 text-blue-400 border-blue-700"
                                        >
                                            나
                                        </Badge>
                                    )}

                                    {/* Stats */}
                                    <div className="hidden sm:flex items-center gap-3 text-xs text-muted-foreground shrink-0">
                                        <span>
                                            통
                                            <span className="tabular-nums text-foreground ml-0.5">{o.leadership}</span>
                                        </span>
                                        <span>
                                            지{' '}
                                            <span className="tabular-nums text-foreground">
                                                {o.command ?? o.strength ?? 0}
                                            </span>
                                        </span>
                                        <span>
                                            정{' '}
                                            <span className="tabular-nums text-foreground">
                                                {o.intelligence ?? o.intel ?? 0}
                                            </span>
                                        </span>
                                        <span>
                                            경험{' '}
                                            <span className="tabular-nums text-foreground">
                                                {(o.experience ?? 0).toLocaleString()}
                                            </span>
                                        </span>
                                    </div>
                                </div>
                            );
                        })}
                        {myIndex >= 0 && (
                            <div className="text-xs text-muted-foreground pt-1 text-right">
                                내 서열: {myIndex + 1}위 / {sorted.length}명
                            </div>
                        )}
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
