'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { Trophy, Coins } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Input } from '@/components/ui/8bit/input';
import { bettingApi } from '@/lib/gameApi';
import type { BettingInfo, BettingEventSummary } from '@/types';

function formatYearMonth(ym?: number): string {
    if (ym == null) return '-';
    const year = Math.floor(ym / 12);
    const month = (ym % 12) + 1;
    return `${year}년 ${month}월`;
}

export default function NationBettingPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const { fetchMyOfficer } = useOfficerStore();

    const [loading, setLoading] = useState(true);
    const [betting, setBetting] = useState<BettingInfo | null>(null);
    const [history, setHistory] = useState<BettingEventSummary[]>([]);
    const [selectedEventYM, setSelectedEventYM] = useState<string | null>(null);
    const [selectedBetting, setSelectedBetting] = useState<BettingInfo | null>(null);
    const [pickedCandidates, setPickedCandidates] = useState<Set<number>>(new Set());
    const [betAmount, setBetAmount] = useState(10);

    const load = useCallback(async () => {
        if (!currentWorld) return;
        setLoading(true);
        try {
            const [bettingRes, historyRes] = await Promise.all([
                bettingApi.getInfo(currentWorld.id),
                bettingApi.getHistory(currentWorld.id),
            ]);
            setBetting(bettingRes.data);
            setHistory(historyRes.data);
        } catch {
            /* ignore */
        } finally {
            setLoading(false);
        }
    }, [currentWorld]);

    useEffect(() => {
        if (!currentWorld) return;
        fetchMyOfficer(currentWorld.id).catch(() => {});
        load();
    }, [currentWorld, fetchMyOfficer, load]);

    const loadHistoryEvent = useCallback(
        async (yearMonth: string) => {
            if (!currentWorld) return;
            setSelectedEventYM(yearMonth);
            try {
                const res = await bettingApi.getEvent(currentWorld.id, yearMonth);
                setSelectedBetting(res.data);
            } catch {
                setSelectedBetting(null);
            }
        },
        [currentWorld]
    );

    const activeBetting = selectedEventYM ? selectedBetting : betting;
    const isNationBetting = activeBetting?.candidates != null && Object.keys(activeBetting.candidates).length > 0;
    const selectCnt = activeBetting?.selectCnt ?? 1;
    const candidates = activeBetting?.candidates ?? {};
    const candidateEntries = useMemo(() => Object.entries(candidates), [candidates]);

    const toggleCandidatePick = (idx: number) => {
        setPickedCandidates((prev) => {
            const next = new Set(prev);
            if (next.has(idx)) {
                next.delete(idx);
            } else if (next.size < selectCnt) {
                next.add(idx);
            }
            return next;
        });
    };

    const handleNationBet = async () => {
        if (!currentWorld || !myOfficer) return;
        if (pickedCandidates.size !== selectCnt) return;
        const targets = Array.from(pickedCandidates).sort((a, b) => a - b);
        try {
            const perAmount = Math.floor(betAmount / targets.length);
            if (perAmount <= 0) return;
            for (const targetId of targets) {
                await bettingApi.placeBet(currentWorld.id, myOfficer.id, targetId, perAmount);
            }
            setPickedCandidates(new Set());
            await load();
        } catch {
            /* ignore */
        }
    };

    // Compute total bet pool from bets array
    const totalPool = useMemo(() => {
        if (!activeBetting?.bets) return 0;
        return activeBetting.bets.reduce((s, b) => s + b.amount, 0);
    }, [activeBetting]);

    // Per-candidate bet totals
    const candidateBetTotals = useMemo(() => {
        const map = new Map<number, number>();
        if (!activeBetting?.bets) return map;
        for (const b of activeBetting.bets) {
            map.set(b.targetId, (map.get(b.targetId) ?? 0) + b.amount);
        }
        return map;
    }, [activeBetting]);

    // My bets
    const myBets = useMemo(() => {
        const map = new Map<number, number>();
        if (!activeBetting?.bets || !myOfficer) return map;
        for (const b of activeBetting.bets) {
            if (b.generalId === myOfficer.id) {
                map.set(b.targetId, (map.get(b.targetId) ?? 0) + b.amount);
            }
        }
        return map;
    }, [activeBetting, myOfficer]);

    const winnerSet = useMemo(() => new Set(activeBetting?.winner ?? []), [activeBetting]);

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading)
        return (
            <div className="p-4">
                <LoadingState />
            </div>
        );

    const bettingStatusText = (() => {
        if (!activeBetting) return null;
        if (activeBetting.finished) return '종료';
        if (activeBetting.closeYearMonth != null) {
            return `${formatYearMonth(activeBetting.closeYearMonth)}까지`;
        }
        return '베팅 마감';
    })();

    const canBet = activeBetting && !activeBetting.finished && !selectedEventYM;

    return (
        <div className="p-4 space-y-4 max-w-4xl mx-auto">
            <PageHeader icon={Trophy} title="진영 베팅장" />

            {/* Betting Event List */}
            <Card>
                <CardHeader className="py-2 px-4">
                    <CardTitle className="text-sm">베팅 목록</CardTitle>
                </CardHeader>
                <CardContent className="px-4 pb-3 space-y-1">
                    {/* Current active betting */}
                    {betting && isNationBetting && (
                        <button
                            type="button"
                            className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                                !selectedEventYM ? 'bg-[#141c65] text-white' : 'hover:bg-white/5 text-gray-300'
                            }`}
                            onClick={() => {
                                setSelectedEventYM(null);
                                setSelectedBetting(null);
                            }}
                        >
                            [{formatYearMonth(betting.openYearMonth)}] {betting.name ?? '진영 베팅'}
                            {betting.finished ? (
                                <span className="text-muted-foreground ml-1">(종료)</span>
                            ) : (
                                <span className="text-green-400 ml-1">({bettingStatusText})</span>
                            )}
                        </button>
                    )}

                    {/* Historical events */}
                    {history.map((ev) => (
                        <button
                            key={ev.yearMonth}
                            type="button"
                            className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                                selectedEventYM === ev.yearMonth
                                    ? 'bg-[#141c65] text-white'
                                    : 'hover:bg-white/5 text-gray-300'
                            }`}
                            onClick={() => loadHistoryEvent(ev.yearMonth)}
                        >
                            [{ev.yearMonth}] {ev.championName ?? '진영 베팅'}
                            <span className="text-muted-foreground ml-1">(종료)</span>
                        </button>
                    ))}

                    {!isNationBetting && history.length === 0 && <EmptyState title="진행 중인 진영 베팅이 없습니다." />}
                </CardContent>
            </Card>

            {/* Active Betting Detail */}
            {activeBetting && isNationBetting && (
                <>
                    {/* Header */}
                    <Card>
                        <CardHeader className="py-2 px-4">
                            <CardTitle className="text-sm flex items-center gap-2">
                                <Coins className="size-4" />
                                {activeBetting.name ?? '진영 베팅'}
                                {activeBetting.finished && <Badge variant="secondary">종료</Badge>}
                                {selectCnt > 1 && <Badge variant="secondary">{selectCnt}개 선택</Badge>}
                            </CardTitle>
                            <div className="text-xs text-muted-foreground">
                                [{formatYearMonth(activeBetting.openYearMonth)}] {bettingStatusText} (총액:{' '}
                                {totalPool.toLocaleString()})
                            </div>
                        </CardHeader>
                        <CardContent className="px-4 pb-3 space-y-3">
                            {/* Candidate grid */}
                            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                                {candidateEntries.map(([idx, cand]) => {
                                    const numIdx = parseInt(idx, 10);
                                    const isPicked = pickedCandidates.has(numIdx);
                                    const isWinner = winnerSet.has(numIdx);
                                    const betTotal = candidateBetTotals.get(numIdx) ?? 0;
                                    const pickRate = totalPool > 0 ? ((betTotal / totalPool) * 100).toFixed(1) : '0.0';
                                    return (
                                        <button
                                            key={idx}
                                            type="button"
                                            className={`rounded border p-2 text-left text-sm transition-colors ${
                                                isPicked || (activeBetting.finished && isWinner)
                                                    ? 'border-yellow-500 bg-yellow-500/10'
                                                    : 'border-gray-700 hover:border-gray-500'
                                            }`}
                                            onClick={() => canBet && toggleCandidatePick(numIdx)}
                                        >
                                            <div className="font-medium">{cand.title}</div>
                                            {cand.info && !cand.isHtml && (
                                                <div className="text-xs text-muted-foreground mt-1">{cand.info}</div>
                                            )}
                                            {cand.info && cand.isHtml && (
                                                <div
                                                    className="text-xs text-muted-foreground mt-1"
                                                    dangerouslySetInnerHTML={{ __html: cand.info }}
                                                />
                                            )}
                                            <div className="text-xs text-muted-foreground mt-1">
                                                선택율: {pickRate}%
                                            </div>
                                        </button>
                                    );
                                })}
                            </div>

                            {/* Bet controls */}
                            {canBet && myOfficer && (
                                <div className="flex items-center gap-2 flex-wrap">
                                    <span className="text-sm text-muted-foreground">
                                        잔여 {activeBetting.reqInheritancePoint ? '포인트' : '금'}:{' '}
                                        {(activeBetting.remainPoint ?? 0).toLocaleString()}
                                    </span>
                                    <span className="text-sm text-muted-foreground">
                                        내 베팅:{' '}
                                        {Array.from(myBets.values())
                                            .reduce((a, b) => a + b, 0)
                                            .toLocaleString()}
                                    </span>
                                    <Input
                                        type="number"
                                        min={10}
                                        max={1000}
                                        step={10}
                                        value={betAmount}
                                        onChange={(e) => setBetAmount(parseInt(e.target.value, 10) || 10)}
                                        className="w-24"
                                    />
                                    <Button
                                        size="sm"
                                        disabled={pickedCandidates.size !== selectCnt}
                                        onClick={handleNationBet}
                                    >
                                        베팅
                                    </Button>
                                </div>
                            )}

                            {/* Odds / ranking table */}
                            <div className="space-y-1">
                                <div className="text-sm font-medium">
                                    {activeBetting.finished ? '배당 결과' : '배당 순위'}
                                </div>
                                <div className="grid grid-cols-4 text-xs text-muted-foreground border-b border-gray-700 pb-1">
                                    <div>대상</div>
                                    <div className="text-right">베팅액</div>
                                    <div className="text-center">내 베팅</div>
                                    <div className="text-right">{activeBetting.finished ? '배율' : '기대 배율'}</div>
                                </div>
                                {candidateEntries
                                    .sort(([, a], [, b]) => {
                                        const aTotal = candidateBetTotals.get(parseInt(a.title, 10)) ?? 0;
                                        const bTotal = candidateBetTotals.get(parseInt(b.title, 10)) ?? 0;
                                        return bTotal - aTotal;
                                    })
                                    .map(([idx, cand]) => {
                                        const numIdx = parseInt(idx, 10);
                                        const betTotal = candidateBetTotals.get(numIdx) ?? 0;
                                        const myBet = myBets.get(numIdx);
                                        const odds = betTotal > 0 ? (totalPool / betTotal).toFixed(1) : '-';
                                        const isWinner = winnerSet.has(numIdx);
                                        return (
                                            <div
                                                key={idx}
                                                className={`grid grid-cols-4 text-xs py-0.5 ${
                                                    isWinner ? 'text-green-400' : myBet ? 'font-bold' : ''
                                                }`}
                                            >
                                                <div>{cand.title}</div>
                                                <div className="text-right">{betTotal.toLocaleString()}</div>
                                                <div className="text-center">
                                                    {myBet != null ? myBet.toLocaleString() : '-'}
                                                </div>
                                                <div className="text-right">{odds}배</div>
                                            </div>
                                        );
                                    })}
                            </div>
                        </CardContent>
                    </Card>
                </>
            )}
        </div>
    );
}
