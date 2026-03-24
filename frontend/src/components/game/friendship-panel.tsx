'use client';

import { useMemo } from 'react';
import { Heart, TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import type { Officer } from '@/types';

// ─── Types ───────────────────────────────────────────────────────────────────

interface FriendshipEntry {
    officerId: number;
    officerName: string;
    score: number;
    compatibility: number; // 상성 -100 to 100
    recentChange?: number;
}

function getCompatibilityLabel(compat: number): { label: string; color: string } {
    if (compat >= 60) return { label: '최적', color: '#FFD700' };
    if (compat >= 30) return { label: '양호', color: '#69F0AE' };
    if (compat >= 0) return { label: '보통', color: '#4FC3F7' };
    if (compat >= -30) return { label: '불량', color: '#FFD54F' };
    return { label: '최악', color: '#EF5350' };
}

function getFriendshipLabel(score: number): { label: string; color: string } {
    if (score >= 80) return { label: '절친', color: '#FFD700' };
    if (score >= 60) return { label: '우호', color: '#69F0AE' };
    if (score >= 40) return { label: '보통', color: '#4FC3F7' };
    if (score >= 20) return { label: '냉담', color: '#FFD54F' };
    return { label: '적대', color: '#EF5350' };
}

// ─── Component ───────────────────────────────────────────────────────────────

interface FriendshipPanelProps {
    officer: Officer;
    factionOfficers: Officer[];
}

export function FriendshipPanel({ officer, factionOfficers }: FriendshipPanelProps) {
    const friendships: FriendshipEntry[] = useMemo(() => {
        const meta = officer.meta as Record<string, unknown> | undefined;
        const stored = meta?.friendships as FriendshipEntry[] | undefined;
        if (stored && stored.length > 0) return stored;

        // Generate from affinity/personality heuristic
        return factionOfficers
            .filter((o) => o.id !== officer.id)
            .slice(0, 10)
            .map((o) => {
                // Simple compatibility from affinity difference
                const affinityDiff = Math.abs((officer.affinity ?? 50) - (o.affinity ?? 50));
                const compatibility = Math.round(100 - affinityDiff * 2);
                const score = Math.max(0, Math.min(100, 50 + compatibility / 2));
                return {
                    officerId: o.id,
                    officerName: o.name,
                    score,
                    compatibility,
                };
            })
            .sort((a, b) => b.score - a.score);
    }, [officer, factionOfficers]);

    if (friendships.length === 0) {
        return (
            <Card>
                <CardContent className="py-4 text-center text-sm text-muted-foreground">
                    교류 기록이 없습니다.
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <Heart className="size-4 text-pink-400" />
                    우호도 / 상성
                </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
                {friendships.map((entry) => {
                    const friendLabel = getFriendshipLabel(entry.score);
                    const compatLabel = getCompatibilityLabel(entry.compatibility);
                    return (
                        <div key={entry.officerId} className="flex items-center gap-3 rounded px-2 py-1.5 bg-muted/30">
                            <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2">
                                    <span className="text-sm font-medium truncate">{entry.officerName}</span>
                                    <Badge
                                        variant="outline"
                                        className="text-[9px] px-1"
                                        style={{ borderColor: friendLabel.color, color: friendLabel.color }}
                                    >
                                        {friendLabel.label}
                                    </Badge>
                                    <Badge
                                        variant="outline"
                                        className="text-[9px] px-1"
                                        style={{ borderColor: compatLabel.color, color: compatLabel.color }}
                                    >
                                        상성: {compatLabel.label}
                                    </Badge>
                                </div>
                                <Progress value={entry.score} className="h-1 mt-1" />
                            </div>
                            <div className="text-right shrink-0">
                                <div className="text-xs font-mono tabular-nums" style={{ color: friendLabel.color }}>
                                    {entry.score}
                                </div>
                                {entry.recentChange != null && entry.recentChange !== 0 && (
                                    <div className="flex items-center gap-0.5 text-[10px]">
                                        {entry.recentChange > 0 ? (
                                            <>
                                                <TrendingUp className="size-2.5 text-green-400" />
                                                <span className="text-green-400">+{entry.recentChange}</span>
                                            </>
                                        ) : (
                                            <>
                                                <TrendingDown className="size-2.5 text-red-400" />
                                                <span className="text-red-400">{entry.recentChange}</span>
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
                <div className="text-[10px] text-muted-foreground pt-1">
                    우호도는 회견/수렵/담화 등 교류를 통해 변동됩니다.
                </div>
            </CardContent>
        </Card>
    );
}
