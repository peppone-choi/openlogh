'use client';

import { useEffect, useMemo, useState } from 'react';
import { TrendingUp, Award, ArrowUp, ArrowDown, Minus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { useWorldStore } from '@/stores/worldStore';

// ─── Influence event types ───────────────────────────────────────────────────

interface InfluenceEvent {
    id: number;
    type: string;
    typeLabel: string;
    change: number;
    date: string;
}

const EVENT_LABELS: Record<string, string> = {
    night_party: '야회',
    hunting: '수렵',
    conference: '회담',
    conversation: '담화',
    speech: '연설',
    battle_victory: '전투 승리',
    promotion: '승진',
    demotion: '강등',
    policy_success: '정책 성공',
    diplomacy: '외교',
};

function getInfluenceRank(influence: number): { label: string; color: string } {
    if (influence >= 90) return { label: '절대적', color: '#FFD700' };
    if (influence >= 70) return { label: '매우 높음', color: '#4FC3F7' };
    if (influence >= 50) return { label: '높음', color: '#69F0AE' };
    if (influence >= 30) return { label: '보통', color: '#FFD54F' };
    if (influence >= 10) return { label: '낮음', color: '#FF8A65' };
    return { label: '미미', color: '#EF5350' };
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function InfluencePage() {
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();
    const officers = useGameStore((s) => s.officers);

    // Derive influence from officer meta (or dedication/experience as proxy)
    const myInfluence = useMemo(() => {
        if (!myOfficer) return 0;
        const meta = myOfficer.meta as Record<string, unknown> | undefined;
        if (meta?.influence != null) return Number(meta.influence);
        // Fallback: use dedication as influence proxy
        return Math.min(100, Math.round((myOfficer.dedication / 10000) * 100));
    }, [myOfficer]);

    const influenceRank = getInfluenceRank(myInfluence);

    // Faction ranking
    const factionRanking = useMemo(() => {
        if (!myOfficer) return [];
        const factionId = myOfficer.factionId ?? myOfficer.nationId;
        if (!factionId || factionId <= 0) return [];

        return officers
            .filter((o) => (o.factionId ?? o.nationId) === factionId)
            .map((o) => {
                const meta = o.meta as Record<string, unknown> | undefined;
                const influence =
                    meta?.influence != null
                        ? Number(meta.influence)
                        : Math.min(100, Math.round((o.dedication / 10000) * 100));
                return { id: o.id, name: o.name, influence, npcState: o.npcState, officerLevel: o.officerLevel };
            })
            .sort((a, b) => b.influence - a.influence);
    }, [officers, myOfficer]);

    const myRankIndex = factionRanking.findIndex((r) => r.id === myOfficer?.id);

    // Simulated recent events from officer meta
    const recentEvents: InfluenceEvent[] = useMemo(() => {
        if (!myOfficer) return [];
        const meta = myOfficer.meta as Record<string, unknown> | undefined;
        const events = (meta?.influenceEvents as InfluenceEvent[] | undefined) ?? [];
        if (events.length > 0) return events.slice(0, 10);
        // Generate placeholder events
        return [
            { id: 1, type: 'speech', typeLabel: '연설', change: 3, date: '최근' },
            { id: 2, type: 'battle_victory', typeLabel: '전투 승리', change: 5, date: '최근' },
        ];
    }, [myOfficer]);

    if (!currentWorld || !myOfficer) return <LoadingState />;

    return (
        <div className="p-4 space-y-4 max-w-3xl mx-auto">
            <PageHeader icon={TrendingUp} title="영향력" description="진영 내 영향력 및 순위" />

            {/* My Influence */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                        <Award className="size-4" style={{ color: influenceRank.color }} />
                        나의 영향력
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <div className="flex items-center justify-between">
                        <div className="space-y-1">
                            <div className="text-3xl font-bold tabular-nums" style={{ color: influenceRank.color }}>
                                {myInfluence}
                            </div>
                            <Badge
                                variant="outline"
                                style={{ borderColor: influenceRank.color, color: influenceRank.color }}
                            >
                                {influenceRank.label}
                            </Badge>
                        </div>
                        {myRankIndex >= 0 && (
                            <div className="text-right">
                                <div className="text-xs text-muted-foreground">진영 내 순위</div>
                                <div className="text-2xl font-bold text-amber-400">
                                    #{myRankIndex + 1}
                                    <span className="text-sm text-muted-foreground">/{factionRanking.length}</span>
                                </div>
                            </div>
                        )}
                    </div>
                    <Progress value={myInfluence} className="h-2" />
                </CardContent>
            </Card>

            {/* Recent Events */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm">최근 영향력 변동</CardTitle>
                </CardHeader>
                <CardContent>
                    {recentEvents.length === 0 ? (
                        <div className="text-sm text-muted-foreground py-4 text-center">최근 변동 내역이 없습니다.</div>
                    ) : (
                        <div className="space-y-2">
                            {recentEvents.map((event) => (
                                <div
                                    key={event.id}
                                    className="flex items-center justify-between rounded px-3 py-2 bg-muted/30"
                                >
                                    <div className="flex items-center gap-2">
                                        <Badge variant="secondary" className="text-[10px]">
                                            {EVENT_LABELS[event.type] ?? event.typeLabel ?? event.type}
                                        </Badge>
                                        <span className="text-xs text-muted-foreground">{event.date}</span>
                                    </div>
                                    <div className="flex items-center gap-1 text-sm font-mono">
                                        {event.change > 0 ? (
                                            <>
                                                <ArrowUp className="size-3 text-green-400" />
                                                <span className="text-green-400">+{event.change}</span>
                                            </>
                                        ) : event.change < 0 ? (
                                            <>
                                                <ArrowDown className="size-3 text-red-400" />
                                                <span className="text-red-400">{event.change}</span>
                                            </>
                                        ) : (
                                            <>
                                                <Minus className="size-3 text-muted-foreground" />
                                                <span className="text-muted-foreground">0</span>
                                            </>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Faction Ranking */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm">진영 영향력 순위</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                    <div className="overflow-x-auto">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead className="w-12 text-center">#</TableHead>
                                    <TableHead>제독</TableHead>
                                    <TableHead className="text-center">직위</TableHead>
                                    <TableHead className="text-right">영향력</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {factionRanking.slice(0, 20).map((entry, idx) => {
                                    const isMe = entry.id === myOfficer?.id;
                                    const rank = getInfluenceRank(entry.influence);
                                    return (
                                        <TableRow key={entry.id} className={isMe ? 'bg-amber-900/10' : ''}>
                                            <TableCell className="text-center font-mono text-xs">
                                                {idx < 3 ? (
                                                    <span className="text-amber-400 font-bold">{idx + 1}</span>
                                                ) : (
                                                    idx + 1
                                                )}
                                            </TableCell>
                                            <TableCell className="font-medium">
                                                {entry.name}
                                                {isMe && (
                                                    <Badge variant="default" className="ml-1 text-[9px] px-1">
                                                        나
                                                    </Badge>
                                                )}
                                                {entry.npcState > 0 && (
                                                    <Badge variant="outline" className="ml-1 text-[9px] px-1">
                                                        NPC
                                                    </Badge>
                                                )}
                                            </TableCell>
                                            <TableCell className="text-center text-xs">
                                                Lv.{entry.officerLevel}
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <span className="font-mono text-sm" style={{ color: rank.color }}>
                                                    {entry.influence}
                                                </span>
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
