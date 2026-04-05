'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { Trophy, Coins, TrendingUp, Users, Swords, Shield, Brain, Flame, History, ChevronLeft } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/8bit/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/8bit/table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/8bit/select';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { NationBadge } from '@/components/game/nation-badge';
import { tournamentApi, bettingApi, frontApi, rankingApi } from '@/lib/gameApi';
import { numberWithCommas } from '@/lib/game-utils';
import type { TournamentInfo, BettingInfo, GlobalInfo, BettingEventSummary, BestGeneral } from '@/types';

/* ── Constants ── */

const BET_AMOUNTS = [
    { value: '10', label: '금10' },
    { value: '20', label: '금20' },
    { value: '50', label: '금50' },
    { value: '100', label: '금100' },
    { value: '200', label: '금200' },
    { value: '500', label: '금500' },
    { value: '1000', label: '최대' },
];

const TOURNAMENT_TYPES: {
    code: number;
    name: string;
    stat: string;
    statKey: string;
    icon: typeof Trophy;
}[] = [
    { code: 0, name: '전력전', stat: '종합', statKey: 'total', icon: Flame },
    {
        code: 1,
        name: '통솔전',
        stat: '통솔',
        statKey: 'leadership',
        icon: Shield,
    },
    { code: 2, name: '일기토', stat: '무력', statKey: 'strength', icon: Swords },
    { code: 3, name: '설전', stat: '지력', statKey: 'intel', icon: Brain },
];

const TOURNAMENT_RANKING_CONFIG = [
    {
        key: 'total-war',
        title: '전력전 랭킹',
        sortBy: 'ttw',
        winsKey: 'ttw',
        drawsKey: 'ttd',
        lossesKey: 'ttl',
        championKeys: ['ttc', 'ttchamp', 'ttwinner'],
    },
    {
        key: 'leadership-war',
        title: '통솔전 랭킹',
        sortBy: 'tlw',
        winsKey: 'tlw',
        drawsKey: 'tld',
        lossesKey: 'tll',
        championKeys: ['tlc', 'tlchamp', 'tlwinner'],
    },
    {
        key: 'duel',
        title: '일기토 랭킹',
        sortBy: 'tsw',
        winsKey: 'tsw',
        drawsKey: 'tsd',
        lossesKey: 'tsl',
        championKeys: ['tsc', 'tschamp', 'tswinner'],
    },
    {
        key: 'debate',
        title: '설전 랭킹',
        sortBy: 'tiw',
        winsKey: 'tiw',
        drawsKey: 'tid',
        lossesKey: 'til',
        championKeys: ['tic', 'tichamp', 'tiwinner'],
    },
] as const;

function metaNum(meta: Record<string, unknown> | undefined, key: string): number {
    const value = meta?.[key];
    return typeof value === 'number' ? value : 0;
}

function parseLegacyYearMonth(yearMonth: number): [number, number] {
    return [Math.floor(yearMonth / 12), (yearMonth % 12) + 1];
}

function formatLegacyYearMonth(yearMonth?: number): string {
    if (yearMonth == null) return '-';
    const [year, month] = parseLegacyYearMonth(yearMonth);
    return `${year}년 ${month}월`;
}

function stripHtmlTags(value: string): string {
    return value
        .replace(/<[^>]+>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

/* ── Page ── */

export default function BettingPage() {
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();
    const { generals, nations, loadAll } = useGameStore();
    const [tournament, setTournament] = useState<TournamentInfo | null>(null);
    const [betting, setBetting] = useState<BettingInfo | null>(null);
    const [globalInfo, setGlobalInfo] = useState<GlobalInfo | null>(null);
    const [loading, setLoading] = useState(true);
    const [betAmounts, setBetAmounts] = useState<Record<number, string>>({});

    // Historical browsing state
    const [bettingHistory, setBettingHistory] = useState<BettingEventSummary[]>([]);
    const [selectedHistoryEvent, setSelectedHistoryEvent] = useState<string | null>(null);
    const [historyBetting, setHistoryBetting] = useState<BettingInfo | null>(null);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [phaseGateOpen, setPhaseGateOpen] = useState(false);
    const [tournamentRankings, setTournamentRankings] = useState<Record<string, BestGeneral[]>>({});

    const load = useCallback(async () => {
        if (!currentWorld) return;
        try {
            loadAll(currentWorld.id);
            const [tRes, bRes, fRes] = await Promise.all([
                tournamentApi.getInfo(currentWorld.id),
                bettingApi.getInfo(currentWorld.id),
                frontApi.getInfo(currentWorld.id),
            ]);
            setTournament(tRes.data);
            setBetting(bRes.data);
            setGlobalInfo(fRes.data.global);

            // Load history from betting response or separate endpoint
            if (bRes.data.history) {
                setBettingHistory(bRes.data.history);
            } else {
                try {
                    const hRes = await bettingApi.getHistory(currentWorld.id);
                    setBettingHistory(hRes.data);
                } catch {
                    // History endpoint may not exist yet
                }
            }
        } catch {
            /* ignore */
        } finally {
            setLoading(false);
        }
    }, [currentWorld, loadAll]);

    useEffect(() => {
        load();
    }, [load]);

    useEffect(() => {
        if (!currentWorld) return;
        let active = true;
        Promise.all(
            TOURNAMENT_RANKING_CONFIG.map((config) =>
                rankingApi
                    .bestGenerals(currentWorld.id, config.sortBy, 200)
                    .then((res) => ({ key: config.key, data: res.data ?? [] }))
                    .catch(() => ({ key: config.key, data: [] as BestGeneral[] }))
            )
        ).then((entries) => {
            if (!active) return;
            const next: Record<string, BestGeneral[]> = {};
            for (const entry of entries) {
                next[entry.key] = entry.data;
            }
            setTournamentRankings(next);
        });

        return () => {
            active = false;
        };
    }, [currentWorld]);

    // Sync phaseGateOpen with globalInfo.isBettingActive
    useEffect(() => {
        if (globalInfo) {
            setPhaseGateOpen(globalInfo.isBettingActive ?? false);
        }
    }, [globalInfo]);

    const handleTogglePhaseGate = useCallback(() => {
        setPhaseGateOpen((prev) => !prev);
    }, []);

    const loadHistoryEvent = useCallback(
        async (yearMonth: string) => {
            if (!currentWorld) return;
            setHistoryLoading(true);
            setSelectedHistoryEvent(yearMonth);
            try {
                const res = await bettingApi.getEvent(currentWorld.id, yearMonth);
                setHistoryBetting(res.data);
            } catch {
                setHistoryBetting(null);
            } finally {
                setHistoryLoading(false);
            }
        },
        [currentWorld]
    );

    const generalMap = useMemo(() => new Map(generals.map((g) => [g.id, g])), [generals]);
    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);

    // Multi-candidate selection state (for selectCnt > 1 nation betting)
    const selectCnt = betting?.selectCnt ?? 1;
    const isNationBetting = betting?.candidates != null && Object.keys(betting.candidates).length > 0;
    const reqInheritancePoint = betting?.reqInheritancePoint ?? false;
    const [pickedCandidates, setPickedCandidates] = useState<Set<number>>(new Set());
    const [nationBetAmount, setNationBetAmount] = useState(10);

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
            const perAmount = Math.floor(nationBetAmount / targets.length);
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

    const handleBet = async (targetId: number) => {
        if (!currentWorld || !myOfficer) return;
        const amount = parseInt(betAmounts[targetId] ?? '10', 10);
        if (!amount || amount <= 0) return;
        try {
            await bettingApi.placeBet(currentWorld.id, myOfficer.id, targetId, amount);
            await load();
        } catch {
            /* ignore */
        }
    };

    // Tournament progress phases
    const tournamentPhases = useMemo(() => {
        if (!tournament) return [];
        const phases: {
            label: string;
            matchCount: number;
            completedCount: number;
        }[] = [];
        const bracket = tournament.bracket ?? [];
        const roundSizes = [8, 4, 2, 1]; // 16강, 8강, 4강, 결승
        const roundLabels = ['16강', '8강', '4강', '결승'];
        let offset = 0;
        for (let i = 0; i < roundSizes.length; i++) {
            const size = roundSizes[i];
            const matches = bracket.slice(offset, offset + size);
            if (matches.length === 0) break;
            const completed = matches.filter((m) => m.winner != null).length;
            phases.push({
                label: roundLabels[i],
                matchCount: matches.length,
                completedCount: completed,
            });
            offset += size;
        }
        return phases;
    }, [tournament]);

    const tournamentType =
        TOURNAMENT_TYPES.find((t) => t.code === (globalInfo?.tournamentType ?? 0)) ?? TOURNAMENT_TYPES[0];

    const isBettingActive = globalInfo?.isBettingActive ?? false;

    // Helper to compute betting data from a BettingInfo object
    function computeBettingData(bettingData: BettingInfo) {
        const myBetsMap = new Map<number, number>();
        const globalBetsMap = new Map<number, number>();

        for (const bet of bettingData.bets) {
            if (myOfficer && bet.generalId === myOfficer.id) {
                myBetsMap.set(bet.targetId, (myBetsMap.get(bet.targetId) ?? 0) + bet.amount);
            }
            globalBetsMap.set(bet.targetId, (globalBetsMap.get(bet.targetId) ?? 0) + bet.amount);
        }

        const myTotal = Array.from(myBetsMap.values()).reduce((s, v) => s + v, 0);
        const globalTotal = Array.from(globalBetsMap.values()).reduce((s, v) => s + v, 0);

        return { myBetsMap, globalBetsMap, myTotal, globalTotal };
    }

    // Current betting data
    const currentData = betting ? computeBettingData(betting) : null;
    const myBets = currentData?.myBetsMap ?? new Map<number, number>();
    const globalBets = currentData?.globalBetsMap ?? new Map<number, number>();
    const myBetTotal = currentData?.myTotal ?? 0;
    const globalBetTotal = currentData?.globalTotal ?? 0;

    // Build candidate list from tournament bracket participants
    const r16Participants = new Set<number>();
    if (tournament) {
        for (const m of tournament.bracket) {
            if (m.p1) r16Participants.add(m.p1);
            if (m.p2) r16Participants.add(m.p2);
        }
        if (r16Participants.size === 0) {
            for (const pid of tournament.participants) {
                r16Participants.add(pid);
            }
        }
    }

    const candidates = Array.from(r16Participants).map((pid) => {
        const gen = generalMap.get(pid);
        const nat = gen ? nationMap.get(gen.nationId) : null;
        const poolAmount = globalBets.get(pid) ?? 0;
        const myBetAmount = myBets.get(pid) ?? 0;
        const odds =
            betting?.odds[String(pid)] ??
            (poolAmount > 0 && globalBetTotal > 0 ? Math.round((globalBetTotal / poolAmount) * 100) / 100 : 0);
        const potentialPayout = Math.round(odds * myBetAmount);

        let statVal = 0;
        if (gen) {
            switch (tournamentType.statKey) {
                case 'leadership':
                    statVal = gen.leadership;
                    break;
                case 'strength':
                    statVal = gen.strength;
                    break;
                case 'intel':
                    statVal = gen.intel;
                    break;
                case 'total':
                    statVal = gen.leadership + gen.strength + gen.intel;
                    break;
            }
        }

        return {
            pid,
            gen,
            nat,
            poolAmount,
            myBetAmount,
            odds,
            potentialPayout,
            statVal,
        };
    });

    // Nation-level betting aggregation
    const nationBetMap = new Map<number, { total: number; count: number }>();
    for (const c of candidates) {
        const nid = c.gen?.nationId ?? 0;
        if (nid === 0) continue;
        const prev = nationBetMap.get(nid) ?? { total: 0, count: 0 };
        nationBetMap.set(nid, {
            total: prev.total + c.poolAmount,
            count: prev.count + 1,
        });
    }
    const nationBetStats = Array.from(nationBetMap.entries())
        .map(([nid, stats]) => ({ nation: nationMap.get(nid), ...stats }))
        .filter((s) => s.nation)
        .sort((a, b) => b.total - a.total);

    const rankingTables = useMemo(() => {
        return TOURNAMENT_RANKING_CONFIG.map((config) => {
            const source = tournamentRankings[config.key] ?? [];
            const rows = source
                .map((g) => {
                    const wins = metaNum(g.meta, config.winsKey);
                    const draws = metaNum(g.meta, config.drawsKey);
                    const losses = metaNum(g.meta, config.lossesKey);
                    const games = wins + draws + losses;
                    const points = wins * 3 + draws;
                    const championships = config.championKeys.reduce((acc, key) => acc || metaNum(g.meta, key), 0);
                    return {
                        general: g,
                        games,
                        wins,
                        losses,
                        points,
                        championships,
                        winRate: games > 0 ? wins / games : 0,
                    };
                })
                .filter((row) => row.games > 0)
                .sort(
                    (a, b) =>
                        b.wins - a.wins ||
                        b.winRate - a.winRate ||
                        b.points - a.points ||
                        b.championships - a.championships
                )
                .slice(0, 30);

            return {
                ...config,
                rows,
            };
        });
    }, [tournamentRankings]);

    const myActiveBets = candidates.filter((c) => c.myBetAmount > 0);

    const TypeIcon = tournamentType.icon;

    const currentYearMonth = globalInfo ? globalInfo.year * 12 + globalInfo.month - 1 : null;
    const nationBettingStatusText = (() => {
        if (!betting) return null;
        if (betting.finished) return '종료';
        if (betting.closeYearMonth != null && currentYearMonth != null && currentYearMonth <= betting.closeYearMonth) {
            return `${formatLegacyYearMonth(betting.closeYearMonth)}까지`;
        }
        return '베팅 마감';
    })();

    // Historical event detail view
    const historyData = historyBetting ? computeBettingData(historyBetting) : null;

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;

    return (
        <div className="space-y-0 max-w-4xl mx-auto">
            <PageHeader icon={Coins} title="베팅장" />

            <div className="legacy-page-wrap space-y-2 py-2">
                {/* ── Tournament Info Header ── */}
                <Card>
                    <CardContent className="py-3 px-4">
                        <div className="flex items-center justify-between flex-wrap gap-2">
                            <div className="flex items-center gap-2">
                                <TypeIcon className="size-5 text-cyan-400" />
                                <span className="text-sm font-bold text-cyan-400">{tournamentType.name}</span>
                                {isBettingActive ? (
                                    <Badge variant="default">베팅 가능</Badge>
                                ) : (
                                    <Badge variant="outline">베팅 마감</Badge>
                                )}
                            </div>
                            <div className="flex items-center gap-3 text-xs">
                                <span className="text-orange-400">전체 금액: {numberWithCommas(globalBetTotal)}</span>
                                <span className="text-muted-foreground">/</span>
                                <span className="text-amber-400">내 투자: {numberWithCommas(myBetTotal)}</span>
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {/* Tournament Progress */}
                {tournamentPhases.length > 0 && (
                    <Card>
                        <CardContent className="py-2 px-4">
                            <div className="flex items-center gap-1 text-xs mb-1 text-muted-foreground">
                                토너먼트 진행 현황
                            </div>
                            <div className="flex items-center gap-2">
                                {tournamentPhases.map((phase, idx) => {
                                    const pct =
                                        phase.matchCount > 0
                                            ? Math.round((phase.completedCount / phase.matchCount) * 100)
                                            : 0;
                                    const isDone = pct === 100;
                                    return (
                                        <div key={phase.label} className="flex items-center gap-1">
                                            {idx > 0 && <span className="text-gray-600">→</span>}
                                            <div
                                                className={`px-2 py-0.5 rounded text-[10px] border ${isDone ? 'bg-green-900/30 border-green-700 text-green-400' : pct > 0 ? 'bg-yellow-900/30 border-yellow-700 text-yellow-400' : 'border-gray-700 text-gray-500'}`}
                                            >
                                                {phase.label} {phase.completedCount}/{phase.matchCount}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </CardContent>
                    </Card>
                )}

                {/* Phase Gate Control (admin) */}
                {myOfficer && (myOfficer.officerLevel ?? 0) >= 20 && (
                    <Card>
                        <CardContent className="py-2 px-4">
                            <div className="flex items-center justify-between">
                                <div className="text-xs text-muted-foreground">위상 게이트 컨트롤 (운영자)</div>
                                <div className="flex items-center gap-2">
                                    <Badge variant={phaseGateOpen ? 'default' : 'destructive'}>
                                        {phaseGateOpen ? '베팅 개방' : '베팅 마감'}
                                    </Badge>
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        className="h-6 text-xs"
                                        onClick={handleTogglePhaseGate}
                                    >
                                        {phaseGateOpen ? '베팅 마감하기' : '베팅 개방하기'}
                                    </Button>
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                )}

                <Tabs defaultValue="betting">
                    <TabsList>
                        <TabsTrigger value="betting">베팅</TabsTrigger>
                        <TabsTrigger value="mybets">내 베팅</TabsTrigger>
                        <TabsTrigger value="stats">통계</TabsTrigger>
                        <TabsTrigger value="history">
                            <History className="size-3 mr-1" />
                            과거 기록
                        </TabsTrigger>
                    </TabsList>

                    {/* ── Betting Tab ── */}
                    <TabsContent value="betting" className="space-y-2">
                        {candidates.length > 0 ? (
                            <Card>
                                <CardHeader className="py-2 px-4">
                                    <CardTitle className="text-sm flex items-center gap-2">
                                        <TrendingUp className="size-4 text-sky-400" />
                                        16강 베팅
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="px-2 pb-3">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="text-xs">장수</TableHead>
                                                <TableHead className="text-xs">국가</TableHead>
                                                <TableHead className="text-xs text-right">
                                                    {tournamentType.stat}
                                                </TableHead>
                                                <TableHead className="text-xs text-right text-sky-400">배당</TableHead>
                                                <TableHead className="text-xs text-right text-orange-400">
                                                    내 베팅
                                                </TableHead>
                                                <TableHead className="text-xs text-right text-cyan-400">
                                                    환수금
                                                </TableHead>
                                                {isBettingActive && (
                                                    <TableHead className="text-xs text-center">베팅</TableHead>
                                                )}
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {candidates.map((c) => (
                                                <TableRow key={c.pid}>
                                                    <TableCell>
                                                        <div className="flex items-center gap-1.5">
                                                            <GeneralPortrait
                                                                picture={c.gen?.picture}
                                                                name={c.gen?.name ?? `#${c.pid}`}
                                                                size="sm"
                                                            />
                                                            <span className="text-xs">
                                                                {c.gen?.name ?? `#${c.pid}`}
                                                            </span>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell>
                                                        {c.nat ? (
                                                            <NationBadge name={c.nat.name} color={c.nat.color} />
                                                        ) : (
                                                            <span className="text-xs text-muted-foreground">재야</span>
                                                        )}
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono">
                                                        {c.statVal}
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-sky-400">
                                                        {c.odds > 0 ? `${c.odds}x` : '-'}
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-orange-400">
                                                        {c.myBetAmount > 0 ? numberWithCommas(c.myBetAmount) : '-'}
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-cyan-400">
                                                        {c.potentialPayout > 0
                                                            ? numberWithCommas(c.potentialPayout)
                                                            : '-'}
                                                    </TableCell>
                                                    {isBettingActive && (
                                                        <TableCell>
                                                            <div className="flex items-center gap-1">
                                                                <Select
                                                                    value={betAmounts[c.pid] ?? '10'}
                                                                    onValueChange={(v) =>
                                                                        setBetAmounts((prev) => ({
                                                                            ...prev,
                                                                            [c.pid]: v,
                                                                        }))
                                                                    }
                                                                >
                                                                    <SelectTrigger
                                                                        size="sm"
                                                                        className="h-6 text-[10px] w-16"
                                                                    >
                                                                        <SelectValue />
                                                                    </SelectTrigger>
                                                                    <SelectContent>
                                                                        {BET_AMOUNTS.map((a) => (
                                                                            <SelectItem key={a.value} value={a.value}>
                                                                                {a.label}
                                                                            </SelectItem>
                                                                        ))}
                                                                    </SelectContent>
                                                                </Select>
                                                                <Button
                                                                    size="sm"
                                                                    variant="outline"
                                                                    className="h-6 text-[10px] px-2"
                                                                    onClick={() => handleBet(c.pid)}
                                                                >
                                                                    베팅
                                                                </Button>
                                                            </div>
                                                        </TableCell>
                                                    )}
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>

                                    {/* Legend */}
                                    <div className="mt-3 px-2 text-[10px] text-center text-muted-foreground">
                                        <span className="text-sky-400">배당률</span> ×{' '}
                                        <span className="text-orange-400">베팅금</span> ={' '}
                                        <span className="text-cyan-400">적중시 환수금</span>
                                    </div>
                                </CardContent>
                            </Card>
                        ) : (
                            <EmptyState icon={Coins} title="현재 베팅 가능한 토너먼트가 없습니다." />
                        )}
                    </TabsContent>

                    {/* ── My Bets Tab ── */}
                    <TabsContent value="mybets" className="space-y-2">
                        {myActiveBets.length > 0 ? (
                            <Card>
                                <CardHeader className="py-2 px-4">
                                    <CardTitle className="text-sm flex items-center gap-2">
                                        <Coins className="size-4 text-amber-400" />내 베팅 현황
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="px-4 pb-3">
                                    <div className="mb-3 flex items-center justify-between text-xs">
                                        <span className="text-muted-foreground">총 투자금</span>
                                        <span className="text-amber-400 font-mono font-bold">
                                            금 {numberWithCommas(myBetTotal)}
                                        </span>
                                    </div>

                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="text-xs">대상</TableHead>
                                                <TableHead className="text-xs text-right">베팅금</TableHead>
                                                <TableHead className="text-xs text-right">배당</TableHead>
                                                <TableHead className="text-xs text-right">예상 환수금</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {myActiveBets.map((c) => (
                                                <TableRow key={c.pid}>
                                                    <TableCell>
                                                        <div className="flex items-center gap-1.5">
                                                            <GeneralPortrait
                                                                picture={c.gen?.picture}
                                                                name={c.gen?.name ?? `#${c.pid}`}
                                                                size="sm"
                                                            />
                                                            <span className="text-xs">
                                                                {c.gen?.name ?? `#${c.pid}`}
                                                            </span>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-orange-400">
                                                        {numberWithCommas(c.myBetAmount)}
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-sky-400">
                                                        {c.odds > 0 ? `${c.odds}x` : '-'}
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-cyan-400">
                                                        {numberWithCommas(c.potentialPayout)}
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </CardContent>
                            </Card>
                        ) : (
                            <EmptyState icon={Coins} title="아직 베팅한 내역이 없습니다." />
                        )}
                    </TabsContent>

                    {/* ── Stats Tab ── */}
                    <TabsContent value="stats" className="space-y-2">
                        {/* Pool summary */}
                        {candidates.length > 0 && (
                            <Card>
                                <CardHeader className="py-2 px-4">
                                    <CardTitle className="text-sm flex items-center gap-2">
                                        <TrendingUp className="size-4" />
                                        베팅 풀 현황
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="px-4 pb-3">
                                    <div className="grid grid-cols-2 gap-2 mb-3">
                                        <div className="border border-gray-700 rounded p-2 text-center">
                                            <div className="text-[10px] text-muted-foreground">전체 베팅 풀</div>
                                            <div className="text-sm font-bold text-amber-400 font-mono">
                                                금 {numberWithCommas(globalBetTotal)}
                                            </div>
                                        </div>
                                        <div className="border border-gray-700 rounded p-2 text-center">
                                            <div className="text-[10px] text-muted-foreground">참여자 수</div>
                                            <div className="text-sm font-bold font-mono">
                                                {new Set(betting?.bets.map((b) => b.generalId) ?? []).size}명
                                            </div>
                                        </div>
                                    </div>

                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="text-xs">장수</TableHead>
                                                <TableHead className="text-xs text-right">베팅 풀</TableHead>
                                                <TableHead className="text-xs text-right">비율</TableHead>
                                                <TableHead className="text-xs text-right">배당</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {candidates
                                                .slice()
                                                .sort((a, b) => b.poolAmount - a.poolAmount)
                                                .map((c) => {
                                                    const pct =
                                                        globalBetTotal > 0
                                                            ? Math.round((c.poolAmount / globalBetTotal) * 100)
                                                            : 0;
                                                    return (
                                                        <TableRow key={c.pid}>
                                                            <TableCell>
                                                                <div className="flex items-center gap-1.5">
                                                                    <GeneralPortrait
                                                                        picture={c.gen?.picture}
                                                                        name={c.gen?.name ?? `#${c.pid}`}
                                                                        size="sm"
                                                                    />
                                                                    <span className="text-xs">
                                                                        {c.gen?.name ?? `#${c.pid}`}
                                                                    </span>
                                                                </div>
                                                            </TableCell>
                                                            <TableCell className="text-right text-xs font-mono">
                                                                {c.poolAmount > 0
                                                                    ? numberWithCommas(c.poolAmount)
                                                                    : '-'}
                                                            </TableCell>
                                                            <TableCell className="text-right text-xs">
                                                                {pct > 0 ? (
                                                                    <div className="flex items-center justify-end gap-1">
                                                                        <div className="w-12 h-1.5 bg-gray-800 rounded-full overflow-hidden">
                                                                            <div
                                                                                className="h-full bg-sky-500 rounded-full"
                                                                                style={{ width: `${pct}%` }}
                                                                            />
                                                                        </div>
                                                                        <span className="text-muted-foreground">
                                                                            {pct}%
                                                                        </span>
                                                                    </div>
                                                                ) : (
                                                                    <span className="text-muted-foreground">-</span>
                                                                )}
                                                            </TableCell>
                                                            <TableCell className="text-right text-xs font-mono text-sky-400">
                                                                {c.odds > 0 ? `${c.odds}x` : '-'}
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })}
                                        </TableBody>
                                    </Table>
                                </CardContent>
                            </Card>
                        )}

                        {/* Nation-level summary */}
                        {nationBetStats.length > 0 && (
                            <Card>
                                <CardHeader className="py-2 px-4">
                                    <CardTitle className="text-sm flex items-center gap-2">
                                        <Users className="size-4" />
                                        국가별 베팅 현황
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="px-4 pb-3">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="text-xs">국가</TableHead>
                                                <TableHead className="text-xs text-right">참가 장수</TableHead>
                                                <TableHead className="text-xs text-right">베팅 총액</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {nationBetStats.map((s) => (
                                                <TableRow key={s.nation!.id}>
                                                    <TableCell>
                                                        <NationBadge name={s.nation!.name} color={s.nation!.color} />
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono">
                                                        {s.count}명
                                                    </TableCell>
                                                    <TableCell className="text-right text-xs font-mono text-amber-400">
                                                        {numberWithCommas(s.total)}
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </CardContent>
                            </Card>
                        )}

                        <Card>
                            <CardHeader className="py-2 px-4">
                                <CardTitle className="text-sm flex items-center gap-2">
                                    <Trophy className="size-4 text-amber-400" />
                                    토너먼트 랭킹
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="px-2 pb-3 space-y-2">
                                <div className="grid grid-cols-1 xl:grid-cols-2 gap-2">
                                    {rankingTables.map((table) => (
                                        <div key={table.key} className="border border-gray-700 rounded">
                                            <div className="px-2 py-1 text-xs font-semibold bg-black/40 border-b border-gray-700">
                                                {table.title}
                                            </div>
                                            <Table>
                                                <TableHeader>
                                                    <TableRow>
                                                        <TableHead className="text-[10px] px-2 py-1 w-10">
                                                            순위
                                                        </TableHead>
                                                        <TableHead className="text-[10px] px-2 py-1">이름</TableHead>
                                                        <TableHead className="text-[10px] px-2 py-1 text-right">
                                                            경기수
                                                        </TableHead>
                                                        <TableHead className="text-[10px] px-2 py-1 text-right">
                                                            승리
                                                        </TableHead>
                                                        <TableHead className="text-[10px] px-2 py-1 text-right">
                                                            패배
                                                        </TableHead>
                                                        <TableHead className="text-[10px] px-2 py-1 text-right">
                                                            점수
                                                        </TableHead>
                                                        <TableHead className="text-[10px] px-2 py-1 text-right">
                                                            우승횟수
                                                        </TableHead>
                                                    </TableRow>
                                                </TableHeader>
                                                <TableBody>
                                                    {table.rows.length > 0 ? (
                                                        table.rows.map((row, idx) => {
                                                            const nation = nationMap.get(row.general.nationId);
                                                            return (
                                                                <TableRow key={`${table.key}-${row.general.id}`}>
                                                                    <TableCell className="text-[10px] px-2 py-1 font-mono">
                                                                        {idx + 1}
                                                                    </TableCell>
                                                                    <TableCell className="px-2 py-1">
                                                                        <div className="flex items-center gap-1.5 min-w-0">
                                                                            <GeneralPortrait
                                                                                picture={row.general.picture}
                                                                                name={row.general.name}
                                                                                size="sm"
                                                                            />
                                                                            <div className="min-w-0">
                                                                                <div className="text-[10px] truncate">
                                                                                    {row.general.name}
                                                                                </div>
                                                                                {nation && (
                                                                                    <NationBadge
                                                                                        name={nation.name}
                                                                                        color={nation.color}
                                                                                    />
                                                                                )}
                                                                            </div>
                                                                        </div>
                                                                    </TableCell>
                                                                    <TableCell className="text-[10px] px-2 py-1 text-right font-mono">
                                                                        {row.games}
                                                                    </TableCell>
                                                                    <TableCell className="text-[10px] px-2 py-1 text-right font-mono text-green-400">
                                                                        {row.wins}
                                                                    </TableCell>
                                                                    <TableCell className="text-[10px] px-2 py-1 text-right font-mono text-red-400">
                                                                        {row.losses}
                                                                    </TableCell>
                                                                    <TableCell className="text-[10px] px-2 py-1 text-right font-mono font-bold">
                                                                        {row.points}
                                                                    </TableCell>
                                                                    <TableCell className="text-[10px] px-2 py-1 text-right font-mono text-amber-400">
                                                                        {row.championships}
                                                                    </TableCell>
                                                                </TableRow>
                                                            );
                                                        })
                                                    ) : (
                                                        <TableRow>
                                                            <TableCell
                                                                colSpan={7}
                                                                className="text-[10px] text-center text-muted-foreground py-2"
                                                            >
                                                                데이터 없음
                                                            </TableCell>
                                                        </TableRow>
                                                    )}
                                                </TableBody>
                                            </Table>
                                        </div>
                                    ))}
                                </div>
                            </CardContent>
                        </Card>
                    </TabsContent>

                    {/* ── History Tab ── */}
                    <TabsContent value="history" className="space-y-2">
                        {selectedHistoryEvent ? (
                            // Detailed view of a historical event
                            <div className="space-y-2">
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => {
                                        setSelectedHistoryEvent(null);
                                        setHistoryBetting(null);
                                    }}
                                >
                                    <ChevronLeft className="size-3 mr-1" />
                                    목록으로
                                </Button>

                                <Card>
                                    <CardHeader className="py-2 px-4">
                                        <CardTitle className="text-sm flex items-center gap-2">
                                            <History className="size-4 text-amber-400" />
                                            {selectedHistoryEvent} 베팅 기록
                                        </CardTitle>
                                    </CardHeader>
                                    <CardContent className="px-4 pb-3">
                                        {historyLoading ? (
                                            <LoadingState />
                                        ) : historyBetting ? (
                                            <div className="space-y-3">
                                                <div className="grid grid-cols-2 gap-2">
                                                    <div className="border border-gray-700 rounded p-2 text-center">
                                                        <div className="text-[10px] text-muted-foreground">
                                                            총 베팅 풀
                                                        </div>
                                                        <div className="text-sm font-bold text-amber-400 font-mono">
                                                            금 {numberWithCommas(historyData?.globalTotal ?? 0)}
                                                        </div>
                                                    </div>
                                                    <div className="border border-gray-700 rounded p-2 text-center">
                                                        <div className="text-[10px] text-muted-foreground">참여자</div>
                                                        <div className="text-sm font-bold font-mono">
                                                            {new Set(historyBetting.bets.map((b) => b.generalId)).size}
                                                            명
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Per-target breakdown */}
                                                <Table>
                                                    <TableHeader>
                                                        <TableRow>
                                                            <TableHead className="text-xs">대상 장수</TableHead>
                                                            <TableHead className="text-xs text-right">
                                                                베팅 풀
                                                            </TableHead>
                                                            <TableHead className="text-xs text-right">비율</TableHead>
                                                            <TableHead className="text-xs text-right">배당</TableHead>
                                                        </TableRow>
                                                    </TableHeader>
                                                    <TableBody>
                                                        {Array.from(historyData?.globalBetsMap.entries() ?? [])
                                                            .sort(([, a], [, b]) => b - a)
                                                            .map(([targetId, amount]) => {
                                                                const gen = generalMap.get(targetId);
                                                                const total = historyData?.globalTotal ?? 1;
                                                                const pct = Math.round((amount / total) * 100);
                                                                const odds =
                                                                    historyBetting.odds[String(targetId)] ??
                                                                    (amount > 0
                                                                        ? Math.round((total / amount) * 100) / 100
                                                                        : 0);
                                                                return (
                                                                    <TableRow key={targetId}>
                                                                        <TableCell>
                                                                            <div className="flex items-center gap-1.5">
                                                                                <GeneralPortrait
                                                                                    picture={gen?.picture}
                                                                                    name={gen?.name ?? `#${targetId}`}
                                                                                    size="sm"
                                                                                />
                                                                                <span className="text-xs">
                                                                                    {gen?.name ?? `#${targetId}`}
                                                                                </span>
                                                                            </div>
                                                                        </TableCell>
                                                                        <TableCell className="text-right text-xs font-mono">
                                                                            {numberWithCommas(amount)}
                                                                        </TableCell>
                                                                        <TableCell className="text-right text-xs">
                                                                            <div className="flex items-center justify-end gap-1">
                                                                                <div className="w-12 h-1.5 bg-gray-800 rounded-full overflow-hidden">
                                                                                    <div
                                                                                        className="h-full bg-sky-500 rounded-full"
                                                                                        style={{ width: `${pct}%` }}
                                                                                    />
                                                                                </div>
                                                                                <span className="text-muted-foreground">
                                                                                    {pct}%
                                                                                </span>
                                                                            </div>
                                                                        </TableCell>
                                                                        <TableCell className="text-right text-xs font-mono text-sky-400">
                                                                            {odds > 0 ? `${odds}x` : '-'}
                                                                        </TableCell>
                                                                    </TableRow>
                                                                );
                                                            })}
                                                    </TableBody>
                                                </Table>
                                            </div>
                                        ) : (
                                            <div className="text-sm text-muted-foreground text-center py-4">
                                                데이터를 불러올 수 없습니다.
                                            </div>
                                        )}
                                    </CardContent>
                                </Card>
                            </div>
                        ) : (
                            // History list
                            <Card>
                                <CardHeader className="py-2 px-4">
                                    <CardTitle className="text-sm flex items-center gap-2">
                                        <History className="size-4 text-amber-400" />
                                        과거 베팅 이벤트
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="px-4 pb-3">
                                    {bettingHistory.length > 0 ? (
                                        <Table>
                                            <TableHeader>
                                                <TableRow>
                                                    <TableHead className="text-xs">시기</TableHead>
                                                    <TableHead className="text-xs">토너먼트</TableHead>
                                                    <TableHead className="text-xs">우승자</TableHead>
                                                    <TableHead className="text-xs text-right">총 베팅 풀</TableHead>
                                                    <TableHead className="text-xs text-right">참여자</TableHead>
                                                    <TableHead className="text-xs" />
                                                </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                                {bettingHistory.map((ev) => {
                                                    const tType =
                                                        TOURNAMENT_TYPES.find((t) => t.code === ev.tournamentType) ??
                                                        TOURNAMENT_TYPES[0];
                                                    const EvIcon = tType.icon;
                                                    return (
                                                        <TableRow key={ev.yearMonth}>
                                                            <TableCell className="text-xs">{ev.yearMonth}</TableCell>
                                                            <TableCell>
                                                                <div className="flex items-center gap-1">
                                                                    <EvIcon className="size-3 text-cyan-400" />
                                                                    <span className="text-xs">{tType.name}</span>
                                                                </div>
                                                            </TableCell>
                                                            <TableCell className="text-xs">
                                                                {ev.championName ? (
                                                                    <div className="flex items-center gap-1">
                                                                        <Trophy className="size-3 text-amber-400" />
                                                                        <span>{ev.championName}</span>
                                                                    </div>
                                                                ) : (
                                                                    <span className="text-muted-foreground">-</span>
                                                                )}
                                                            </TableCell>
                                                            <TableCell className="text-right text-xs font-mono text-amber-400">
                                                                {numberWithCommas(ev.totalPool)}
                                                            </TableCell>
                                                            <TableCell className="text-right text-xs font-mono">
                                                                {ev.participantCount}명
                                                            </TableCell>
                                                            <TableCell>
                                                                <Button
                                                                    size="sm"
                                                                    variant="ghost"
                                                                    className="h-6 text-[10px] px-2"
                                                                    onClick={() => loadHistoryEvent(ev.yearMonth)}
                                                                >
                                                                    상세
                                                                </Button>
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })}
                                            </TableBody>
                                        </Table>
                                    ) : (
                                        <div className="text-sm text-muted-foreground text-center py-8">
                                            과거 베팅 기록이 없습니다.
                                        </div>
                                    )}
                                </CardContent>
                            </Card>
                        )}
                    </TabsContent>
                </Tabs>

                {/* ── Nation Betting (multi-candidate / inheritance point mode) ── */}
                {isNationBetting && betting?.candidates && (
                    <Card>
                        <CardHeader className="py-2 px-4">
                            <CardTitle className="text-sm flex items-center gap-2">
                                <Trophy className="size-4 text-amber-400" />
                                {betting.name ?? '국가 베팅'}
                                {betting.finished && <Badge variant="outline">종료</Badge>}
                                {reqInheritancePoint && <Badge variant="secondary">포인트 베팅</Badge>}
                                {selectCnt > 1 && <Badge variant="secondary">{selectCnt}개 선택</Badge>}
                            </CardTitle>
                            <div className="text-xs text-muted-foreground">
                                [{formatLegacyYearMonth(betting.openYearMonth)}] {nationBettingStatusText}
                            </div>
                        </CardHeader>
                        <CardContent className="px-4 pb-3 space-y-3">
                            {/* Candidate grid */}
                            <div className="grid grid-cols-3 lg:grid-cols-6 gap-1">
                                {Object.entries(betting.candidates).map(([idxStr, cand]) => {
                                    const idx = parseInt(idxStr);
                                    const isPicked = pickedCandidates.has(idx);
                                    const isWinner = betting.winner?.includes(idx) ?? false;
                                    return (
                                        <button
                                            key={idx}
                                            type="button"
                                            className={`rounded border p-2 text-xs text-left transition-colors ${
                                                isPicked
                                                    ? 'border-cyan-500 bg-cyan-900/30'
                                                    : isWinner && betting.finished
                                                      ? 'border-green-500 bg-green-900/20'
                                                      : 'border-gray-700 bg-[#111] hover:bg-gray-900'
                                            }`}
                                            onClick={() => !betting.finished && toggleCandidatePick(idx)}
                                            disabled={betting.finished}
                                        >
                                            <div className="font-medium text-center">{cand.title}</div>
                                            {cand.info &&
                                                (cand.isHtml ? (
                                                    <div className="text-[10px] text-muted-foreground mt-1">
                                                        {stripHtmlTags(cand.info)}
                                                    </div>
                                                ) : (
                                                    <div className="text-[10px] text-muted-foreground mt-1">
                                                        {cand.info}
                                                    </div>
                                                ))}
                                        </button>
                                    );
                                })}
                            </div>

                            {/* Bet controls */}
                            {!betting.finished && (
                                <div className="flex items-center gap-2 flex-wrap">
                                    <span className="text-xs text-muted-foreground">
                                        잔여 {reqInheritancePoint ? '포인트' : '금'}:{' '}
                                        {(betting.remainPoint ?? 0).toLocaleString()}
                                    </span>
                                    <span className="text-xs text-muted-foreground">
                                        선택: {pickedCandidates.size}/{selectCnt}
                                    </span>
                                    <input
                                        type="number"
                                        min={10}
                                        max={1000}
                                        step={10}
                                        value={nationBetAmount}
                                        onChange={(e) => setNationBetAmount(Number(e.target.value))}
                                        className="h-7 w-20 px-2 bg-background border border-input rounded text-xs"
                                    />
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        className="h-7 text-xs"
                                        disabled={pickedCandidates.size !== selectCnt}
                                        onClick={handleNationBet}
                                    >
                                        베팅
                                    </Button>
                                </div>
                            )}
                        </CardContent>
                    </Card>
                )}

                {/* ── Betting Rules ── */}
                <Card>
                    <CardHeader className="py-2 px-4">
                        <CardTitle className="text-sm">베팅 안내</CardTitle>
                    </CardHeader>
                    <CardContent className="px-4 pb-3 text-xs text-muted-foreground space-y-1">
                        <p>16강 대진표가 완성되면 베팅 기간이 주어집니다.</p>
                        <p>유저들의 베팅 상황에 따라 배당률이 실시간 결정되며, 예상 환급금을 확인할 수 있습니다.</p>
                        <p>16슬롯에 각각 베팅 가능하며, 도합 최대 금 1000씩 베팅 가능합니다.</p>
                        <p>소지금 500 이하일 때는 베팅이 불가능합니다.</p>
                        <div className="mt-2 pt-2 border-t border-gray-800 text-center">
                            <span className="text-sky-400">배당률</span>이 낮을수록 베팅된 금액이 많고 유저들이{' '}
                            <span className="text-amber-400">우승후보</span>로 많이 선택한 장수입니다.
                        </div>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
