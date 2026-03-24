'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import dynamic from 'next/dynamic';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';
import { subscribeWebSocket } from '@/lib/websocket';
import type { Nation, General, GeneralLogEntry } from '@/types';
import { Swords, ArrowUpDown, Shield, Flame, User, ScrollText, Zap } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { NationBadge } from '@/components/game/nation-badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useOfficerStore } from '@/stores/officerStore';
import { generalLogApi } from '@/lib/gameApi';
import { formatLog } from '@/lib/formatLog';
import { PLANET_LEVEL_NAMES } from '@/lib/game-utils';
import { useBattleStore, toLegacyFleet } from '@/stores/battleStore';
import { FormationSelector } from '@/components/game/battle/FormationSelector';
import { EnergyAllocator } from '@/components/game/battle/EnergyAllocator';
import { OrderPanel } from '@/components/game/battle/OrderPanel';
import { TurnTimer } from '@/components/game/battle/TurnTimer';
import { BattleResult } from '@/components/game/battle/BattleResult';

// Dynamic import — Konva uses canvas API not available on server
const BattleCanvas = dynamic(() => import('@/components/game/battle/BattleCanvas').then((m) => m.BattleCanvas), {
    ssr: false,
    loading: () => (
        <div className="bg-[#02020a] rounded border border-amber-900/20" style={{ width: '100%', height: 480 }} />
    ),
});

function getNation(nations: Nation[], id: number) {
    return nations.find((n) => n.id === id);
}

type SortKey = 'totalCrew' | 'generalCount' | 'avgTrain' | 'avgAtmos' | 'totalPower';

interface MilitaryRow {
    nation: Nation;
    totalCrew: number;
    generalCount: number;
    avgTrain: number;
    avgAtmos: number;
    totalPower: number;
}

type OldLogType = 'generalHistory' | 'generalAction' | 'battleResult' | 'battleDetail';

const GENERAL_LOG_SECTIONS: { type: OldLogType; title: string; emptyTitle: string }[] = [
    { type: 'generalHistory', title: '제독 열전', emptyTitle: '제독 열전 기록이 없습니다.' },
    { type: 'battleResult', title: '전투 기록', emptyTitle: '전투 기록이 없습니다.' },
    { type: 'battleDetail', title: '전투 결과', emptyTitle: '전투 결과가 없습니다.' },
    { type: 'generalAction', title: '개인 기록', emptyTitle: '개인 기록이 없습니다.' },
];

function SortIndicator({ active }: { active: boolean }) {
    return <ArrowUpDown className={`inline size-3 ml-0.5 ${active ? 'text-white' : 'text-gray-500'}`} />;
}

// ─── Live Battle Panel ───────────────────────────────────────────────────────

function LiveBattlePanel() {
    const {
        battlePhase,
        myFleet,
        enemyFleets,
        alliedFleets,
        turnTimer,
        currentTurn,
        battleLog,
        battleResult,
        pendingFormation,
        pendingEnergy,
        pendingOrder,
        commandSubmitted,
        setFormation,
        setEnergy,
        setOrder,
        submitCommand,
        resetBattle,
        startDemoBattle,
        tickTimer,
    } = useBattleStore();

    const [selectedFleetId, setSelectedFleetId] = useState<string | null>(null);
    const logEndRef = useRef<HTMLDivElement>(null);

    // Auto-scroll battle log
    useEffect(() => {
        logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [battleLog.length]);

    // Turn countdown
    useEffect(() => {
        if (battlePhase !== 'setup' && battlePhase !== 'combat') return;
        const id = setInterval(tickTimer, 1000);
        return () => clearInterval(id);
    }, [battlePhase, tickTimer]);

    const logTypeColor: Record<string, string> = {
        attack: 'text-red-400',
        movement: 'text-sky-400',
        morale: 'text-amber-400',
        result: 'text-emerald-400',
        info: 'text-gray-400',
    };

    if (battlePhase === 'idle') {
        return (
            <div className="flex flex-col items-center justify-center py-16 space-y-4">
                <div className="text-center space-y-2">
                    <div className="text-4xl font-mono font-black text-gray-700 tracking-widest">STANDBY</div>
                    <div className="text-sm text-gray-600 font-mono">대기 중 — 전투 명령을 기다리고 있습니다</div>
                </div>
                <div className="flex gap-3">
                    <Button
                        variant="outline"
                        onClick={startDemoBattle}
                        className="font-mono border-amber-900/40 text-amber-400 hover:border-amber-500/60 hover:bg-amber-900/10"
                    >
                        <Zap className="size-4" />
                        데모 전투 시작
                    </Button>
                </div>
                <p className="text-[10px] text-gray-700 font-mono">
                    실제 전투는 WebSocket /topic/battle/&#123;sessionId&#125; 수신 시 자동 시작됩니다
                </p>
            </div>
        );
    }

    return (
        <div className="space-y-3">
            {/* Turn timer */}
            {(battlePhase === 'setup' || battlePhase === 'combat') && (
                <div className="bg-gray-950/80 border border-gray-800/60 rounded-lg px-4 py-2.5">
                    <TurnTimer seconds={turnTimer} maxSeconds={30} turn={currentTurn} submitted={commandSubmitted} />
                </div>
            )}

            {/* Main battle layout */}
            <div className="overflow-x-auto">
                <div className="flex gap-3" style={{ minWidth: 900 }}>
                    {/* Canvas */}
                    <div className="flex-1 min-w-0 bg-[#02020a] border border-amber-900/20 rounded-lg overflow-hidden">
                        <BattleCanvas
                            myFleet={myFleet}
                            enemyFleets={enemyFleets}
                            alliedFleets={alliedFleets}
                            selectedFleetId={selectedFleetId}
                            onFleetSelect={setSelectedFleetId}
                        />
                    </div>

                    {/* Controls panel */}
                    <div className="w-56 shrink-0 space-y-3">
                        {/* Fleet status cards */}
                        <div className="space-y-1.5">
                            <div className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">
                                함대 현황 // Fleets
                            </div>
                            {myFleet && (
                                <FleetStatusCard
                                    fleet={myFleet}
                                    selected={selectedFleetId === myFleet.id}
                                    onClick={() => setSelectedFleetId(myFleet.id)}
                                />
                            )}
                            {alliedFleets.map((f) => (
                                <FleetStatusCard
                                    key={f.id}
                                    fleet={f}
                                    selected={selectedFleetId === f.id}
                                    onClick={() => setSelectedFleetId(f.id)}
                                />
                            ))}
                            {enemyFleets.length > 0 && (
                                <div className="text-[9px] font-mono text-red-500/60 uppercase tracking-widest pt-1">
                                    적군
                                </div>
                            )}
                            {enemyFleets.map((f) => {
                                const legacy = toLegacyFleet(f, false);
                                return (
                                    <FleetStatusCard
                                        key={legacy.id}
                                        fleet={legacy}
                                        selected={selectedFleetId === legacy.id}
                                        onClick={() => setSelectedFleetId(legacy.id)}
                                    />
                                );
                            })}
                        </div>

                        {/* Tactical controls (my fleet only) */}
                        {myFleet && battlePhase !== 'result' && (
                            <>
                                <div className="border-t border-gray-800/60 pt-3 space-y-3">
                                    <FormationSelector value={pendingFormation} onChange={setFormation} />
                                    <EnergyAllocator value={pendingEnergy} onChange={setEnergy} />
                                    <OrderPanel value={pendingOrder} onChange={setOrder} disabled={commandSubmitted} />
                                </div>

                                {/* Submit button */}
                                <button
                                    type="button"
                                    disabled={commandSubmitted}
                                    onClick={submitCommand}
                                    className="w-full py-2.5 rounded border font-mono text-sm font-bold transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed border-amber-500/50 text-amber-400 hover:bg-amber-900/20 hover:border-amber-400 active:scale-[0.98]"
                                    style={{ boxShadow: commandSubmitted ? 'none' : '0 0 12px rgba(255,215,0,0.15)' }}
                                >
                                    {commandSubmitted ? '✓ 명령 전송 완료' : '⚡ 명령 전송'}
                                </button>
                            </>
                        )}
                    </div>
                </div>
            </div>

            {/* Battle result overlay */}
            {battlePhase === 'result' && battleResult && <BattleResult result={battleResult} onClose={resetBattle} />}

            {/* Battle log */}
            <div className="bg-gray-950 border border-gray-800/60 rounded-lg overflow-hidden">
                <div className="flex items-center justify-between px-3 py-1.5 border-b border-gray-800/60">
                    <span className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">
                        전투 로그 // Battle Log
                    </span>
                    <button
                        type="button"
                        onClick={resetBattle}
                        className="text-[9px] font-mono text-gray-600 hover:text-gray-400 transition-colors"
                    >
                        전투 종료
                    </button>
                </div>
                <div className="h-28 overflow-y-auto p-2 space-y-0.5 font-mono text-[11px]">
                    {battleLog.length === 0 ? (
                        <div className="text-gray-700 text-center py-4">전투 로그가 없습니다</div>
                    ) : (
                        battleLog.map((entry) => (
                            <div key={entry.id} className="flex gap-2">
                                <span className="text-gray-700 shrink-0">T{entry.turn}</span>
                                <span className={logTypeColor[entry.type] ?? 'text-gray-400'}>{entry.message}</span>
                            </div>
                        ))
                    )}
                    <div ref={logEndRef} />
                </div>
            </div>
        </div>
    );
}

const FACTION_COLORS: Record<string, string> = {
    empire: '#FFD700',
    alliance: '#4488FF',
    fezzan: '#CC88FF',
    rebel: '#FF8844',
};

function FleetStatusCard({
    fleet,
    selected,
    onClick,
}: {
    fleet: import('@/stores/battleStore').BattleFleet;
    selected: boolean;
    onClick: () => void;
}) {
    const color = FACTION_COLORS[fleet.faction] ?? '#888888';
    const hpPct = fleet.maxShips > 0 ? fleet.ships / fleet.maxShips : 0;
    return (
        <button
            type="button"
            onClick={onClick}
            className="w-full text-left p-2 rounded border transition-all duration-100"
            style={{
                borderColor: selected ? color + '88' : '#1f2937',
                backgroundColor: selected ? color + '0a' : 'transparent',
            }}
        >
            <div className="flex items-center justify-between mb-1">
                <span className="text-[10px] font-mono font-bold" style={{ color }}>
                    {fleet.name}
                </span>
                <span className="text-[9px] font-mono text-gray-600">사기 {fleet.morale}</span>
            </div>
            <div className="h-1.5 bg-gray-800 rounded-full overflow-hidden">
                <div
                    className="h-full rounded-full transition-all"
                    style={{
                        width: `${hpPct * 100}%`,
                        backgroundColor: hpPct > 0.5 ? '#00cc55' : hpPct > 0.25 ? '#ffaa00' : '#ff4444',
                    }}
                />
            </div>
            <div className="text-[9px] font-mono text-gray-600 mt-0.5 tabular-nums">
                {fleet.ships.toLocaleString()} / {fleet.maxShips.toLocaleString()}
            </div>
        </button>
    );
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function BattlePage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { cities, nations, generals, diplomacy, loading, loadAll } = useGameStore();

    const { myOfficer, fetchMyGeneral } = useOfficerStore();
    const [sortKey, setSortKey] = useState<SortKey>('totalCrew');
    const [sortAsc, setSortAsc] = useState(false);
    const [personalLogs, setPersonalLogs] = useState<{ id: number; message: string; date: string }[]>([]);
    const [personalLogsLoaded, setPersonalLogsLoaded] = useState(false);
    const [personalLoading, setPersonalLoading] = useState(false);
    const [logStyle, setLogStyle] = useState<'modern' | 'legacy'>('modern');
    const [selectedGeneralId, setSelectedGeneralId] = useState<number | null>(null);
    const [generalLogsLoading, setGeneralLogsLoading] = useState(false);
    const [generalLogs, setGeneralLogs] = useState<Record<OldLogType, GeneralLogEntry[]>>({
        generalHistory: [],
        generalAction: [],
        battleResult: [],
        battleDetail: [],
    });

    const { battlePhase, handleBattleEvent } = useBattleStore();

    useEffect(() => {
        if (currentWorld) {
            loadAll(currentWorld.id);
            if (!myOfficer) fetchMyGeneral(currentWorld.id).catch(() => {});
        }
    }, [currentWorld, loadAll, myOfficer, fetchMyGeneral]);

    // Subscribe to real-time battle events
    useEffect(() => {
        if (!currentWorld) return;
        return subscribeWebSocket(`/topic/battle/${currentWorld.id}`, (data) => {
            handleBattleEvent(data);
        });
    }, [currentWorld, handleBattleEvent]);

    const loadPersonalLogs = async () => {
        if (!myOfficer || !currentWorld) return;
        setPersonalLoading(true);
        try {
            const { data } = await generalLogApi.getOldLogs(myOfficer.id, myOfficer.id, 'battleResult');
            setPersonalLogs(data.logs ?? []);
            setPersonalLogsLoaded(true);
        } catch {
            setPersonalLogs([]);
        } finally {
            setPersonalLoading(false);
        }
    };

    const loadGeneralLogs = useCallback(
        async (targetGeneralId: number) => {
            if (!myOfficer) return;
            setGeneralLogsLoading(true);
            try {
                const entries = await Promise.all(
                    GENERAL_LOG_SECTIONS.map(async ({ type }) => {
                        const { data } = await generalLogApi.getOldLogs(myOfficer.id, targetGeneralId, type);
                        return [type, data.logs ?? []] as const;
                    })
                );
                setGeneralLogs({
                    generalHistory: entries.find(([type]) => type === 'generalHistory')?.[1] ?? [],
                    generalAction: entries.find(([type]) => type === 'generalAction')?.[1] ?? [],
                    battleResult: entries.find(([type]) => type === 'battleResult')?.[1] ?? [],
                    battleDetail: entries.find(([type]) => type === 'battleDetail')?.[1] ?? [],
                });
            } catch {
                setGeneralLogs({ generalHistory: [], generalAction: [], battleResult: [], battleDetail: [] });
            } finally {
                setGeneralLogsLoading(false);
            }
        },
        [myOfficer]
    );

    useEffect(() => {
        if (!currentWorld) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/battle`, () => {
            loadAll(currentWorld.id);
        });
    }, [currentWorld, loadAll]);

    useEffect(() => {
        if (!myOfficer || generals.length === 0) return;
        const targetExists = selectedGeneralId !== null && generals.some((general) => general.id === selectedGeneralId);
        if (!targetExists) {
            const defaultGeneralId = generals.some((general) => general.id === myOfficer.id)
                ? myOfficer.id
                : generals[0].id;
            setSelectedGeneralId(defaultGeneralId);
        }
    }, [generals, myOfficer, selectedGeneralId]);

    useEffect(() => {
        if (!myOfficer || selectedGeneralId === null) return;
        void loadGeneralLogs(selectedGeneralId);
    }, [myOfficer, selectedGeneralId, loadGeneralLogs]);

    const wars = useMemo(() => diplomacy.filter((d) => d.stateCode === 'war' && !d.isDead), [diplomacy]);
    const warHistory = useMemo(() => diplomacy.filter((d) => d.stateCode === 'war'), [diplomacy]);
    const ceasefires = useMemo(
        () =>
            diplomacy.filter((d) => (d.stateCode === 'ceasefire' || d.stateCode === 'ceasefire_proposal') && !d.isDead),
        [diplomacy]
    );

    const militaryRows = useMemo(() => {
        const map = new Map<
            number,
            { totalCrew: number; generalCount: number; totalTrain: number; totalAtmos: number }
        >();
        for (const g of generals) {
            if (g.factionId === 0 || g.ships <= 0) continue;
            const entry = map.get(g.factionId) ?? { totalCrew: 0, generalCount: 0, totalTrain: 0, totalAtmos: 0 };
            entry.totalCrew += g.ships;
            entry.generalCount += 1;
            entry.totalTrain += g.training;
            entry.totalAtmos += g.morale;
            map.set(g.factionId, entry);
        }
        const rows: MilitaryRow[] = [];
        for (const n of nations) {
            const m = map.get(n.id);
            if (!m) continue;
            rows.push({
                nation: n,
                totalCrew: m.totalCrew,
                generalCount: m.generalCount,
                avgTrain: m.generalCount > 0 ? Math.round(m.totalTrain / m.generalCount) : 0,
                avgAtmos: m.generalCount > 0 ? Math.round(m.totalAtmos / m.generalCount) : 0,
                totalPower: Math.round(
                    m.totalCrew * (m.totalTrain / m.generalCount / 100) * (m.totalAtmos / m.generalCount / 100)
                ),
            });
        }
        return rows;
    }, [generals, nations]);

    const sortedMilitary = useMemo(() => {
        const sorted = [...militaryRows].sort((a, b) => {
            const diff = a[sortKey] - b[sortKey];
            return sortAsc ? diff : -diff;
        });
        return sorted;
    }, [militaryRows, sortKey, sortAsc]);

    const maxCrew = useMemo(() => Math.max(1, ...militaryRows.map((r) => r.totalCrew)), [militaryRows]);

    const frontCities = useMemo(() => cities.filter((c) => c.frontState > 0), [cities]);

    const generalsByCity = useMemo(() => {
        const map = new Map<number, General[]>();
        for (const g of generals) {
            if (g.ships <= 0) continue;
            const list = map.get(g.planetId) ?? [];
            list.push(g);
            map.set(g.planetId, list);
        }
        return map;
    }, [generals]);

    const handleSort = (key: SortKey) => {
        if (sortKey === key) setSortAsc(!sortAsc);
        else {
            setSortKey(key);
            setSortAsc(false);
        }
    };

    const selectedGeneral = useMemo(
        () => generals.find((general) => general.id === selectedGeneralId) ?? null,
        [generals, selectedGeneralId]
    );

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;

    const isLiveBattle = battlePhase !== 'idle';

    return (
        <div className="space-y-4 max-w-5xl mx-auto">
            <PageHeader icon={Swords} title="감찰부" description="전쟁 현황 · 군사력 · 실시간 전투 지휘" />

            {/* Live battle alert badge */}
            {isLiveBattle && battlePhase !== 'result' && (
                <div className="flex items-center gap-2 px-3 py-1.5 bg-red-950/40 border border-red-900/50 rounded-lg">
                    <span className="size-2 rounded-full bg-red-500 animate-pulse" />
                    <span className="text-xs font-mono text-red-400">LIVE BATTLE IN PROGRESS</span>
                </div>
            )}

            <Tabs defaultValue={isLiveBattle ? 'live' : 'wars'}>
                <TabsList>
                    <TabsTrigger
                        value="live"
                        className={isLiveBattle ? 'text-amber-400 data-[state=active]:text-amber-300' : ''}
                    >
                        {isLiveBattle && <span className="size-1.5 rounded-full bg-red-500 animate-pulse mr-1.5" />}
                        실시간 전투
                    </TabsTrigger>
                    <TabsTrigger value="wars">전쟁 현황</TabsTrigger>
                    <TabsTrigger value="military">군사력</TabsTrigger>
                    <TabsTrigger value="frontline">전선</TabsTrigger>
                    {myOfficer && (
                        <TabsTrigger
                            value="personal"
                            onClick={() => {
                                if (!personalLogsLoaded) loadPersonalLogs();
                            }}
                        >
                            내 전투기록
                        </TabsTrigger>
                    )}
                    {myOfficer && <TabsTrigger value="general-logs">제독 기록</TabsTrigger>}
                </TabsList>

                {/* Tab: Live Battle */}
                <TabsContent value="live" className="mt-4">
                    <LiveBattlePanel />
                </TabsContent>

                {/* Tab 1: War Status */}
                <TabsContent value="wars" className="mt-4 space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <Flame className="size-4 text-red-400" />
                                진행 중인 전쟁
                                {wars.length > 0 && <Badge variant="destructive">{wars.length}</Badge>}
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            {wars.length === 0 ? (
                                <EmptyState icon={Swords} title="현재 전쟁이 없습니다." />
                            ) : (
                                <div className="space-y-2">
                                    {wars.map((w) => {
                                        const src = getNation(nations, w.srcNationId);
                                        const dest = getNation(nations, w.destNationId);
                                        return (
                                            <div
                                                key={w.id}
                                                className="rounded-lg border border-red-900/50 bg-red-950/20 p-3 space-y-2"
                                            >
                                                <div className="flex items-center gap-3">
                                                    <NationBadge name={src?.name} color={src?.color} />
                                                    <Swords className="size-4 text-destructive shrink-0" />
                                                    <NationBadge name={dest?.name} color={dest?.color} />
                                                    <Badge variant="destructive" className="ml-auto">
                                                        {w.term}턴 경과
                                                    </Badge>
                                                </div>
                                                <div className="text-xs text-muted-foreground flex gap-4">
                                                    <span>
                                                        {src?.name ?? '?'}: 도시{' '}
                                                        {cities.filter((c) => c.factionId === w.srcNationId).length}개
                                                    </span>
                                                    <span>
                                                        {dest?.name ?? '?'}: 도시{' '}
                                                        {cities.filter((c) => c.factionId === w.destNationId).length}개
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    {ceasefires.length > 0 && (
                        <Card>
                            <CardHeader>
                                <CardTitle className="flex items-center gap-2">
                                    종전/휴전 현황
                                    <Badge variant="secondary">{ceasefires.length}</Badge>
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                {ceasefires.map((d) => {
                                    const src = getNation(nations, d.srcNationId);
                                    const dest = getNation(nations, d.destNationId);
                                    return (
                                        <div key={d.id} className="flex items-center gap-3 rounded-lg border p-3">
                                            <NationBadge name={src?.name} color={src?.color} />
                                            <Badge variant="outline">
                                                {d.stateCode === 'ceasefire' ? '휴전' : '종전제의'}
                                            </Badge>
                                            <NationBadge name={dest?.name} color={dest?.color} />
                                            <span className="ml-auto text-xs text-muted-foreground">{d.term}턴</span>
                                        </div>
                                    );
                                })}
                            </CardContent>
                        </Card>
                    )}

                    {warHistory.length > 0 && (
                        <Card>
                            <CardHeader>
                                <CardTitle>선전포고 기록</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <div className="space-y-1">
                                    {warHistory.map((w) => {
                                        const src = getNation(nations, w.srcNationId);
                                        const dest = getNation(nations, w.destNationId);
                                        return (
                                            <div
                                                key={w.id}
                                                className="flex items-center gap-3 py-1.5 text-sm border-b border-gray-800 last:border-0"
                                            >
                                                <NationBadge name={src?.name} color={src?.color} />
                                                <span className="text-muted-foreground">→</span>
                                                <NationBadge name={dest?.name} color={dest?.color} />
                                                <span className="ml-auto text-xs text-muted-foreground">
                                                    {w.term}턴
                                                </span>
                                                {w.isDead && (
                                                    <Badge variant="outline" className="text-gray-500">
                                                        종료
                                                    </Badge>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </CardContent>
                        </Card>
                    )}
                </TabsContent>

                {/* Tab 2: Military Power */}
                <TabsContent value="military" className="mt-4 space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>진영별 군사력 비교</CardTitle>
                        </CardHeader>
                        <CardContent>
                            {sortedMilitary.length === 0 ? (
                                <EmptyState icon={Shield} title="군사 데이터가 없습니다." />
                            ) : (
                                <div className="overflow-x-auto">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead>국가</TableHead>
                                                <TableHead
                                                    className="cursor-pointer select-none text-right"
                                                    onClick={() => handleSort('totalCrew')}
                                                >
                                                    총 병력
                                                    <SortIndicator active={sortKey === 'totalCrew'} />
                                                </TableHead>
                                                <TableHead
                                                    className="cursor-pointer select-none text-right"
                                                    onClick={() => handleSort('generalCount')}
                                                >
                                                    제독
                                                    <SortIndicator active={sortKey === 'generalCount'} />
                                                </TableHead>
                                                <TableHead
                                                    className="cursor-pointer select-none text-right"
                                                    onClick={() => handleSort('avgTrain')}
                                                >
                                                    평균훈련
                                                    <SortIndicator active={sortKey === 'avgTrain'} />
                                                </TableHead>
                                                <TableHead
                                                    className="cursor-pointer select-none text-right"
                                                    onClick={() => handleSort('avgAtmos')}
                                                >
                                                    평균사기
                                                    <SortIndicator active={sortKey === 'avgAtmos'} />
                                                </TableHead>
                                                <TableHead
                                                    className="cursor-pointer select-none text-right"
                                                    onClick={() => handleSort('totalPower')}
                                                >
                                                    전투력
                                                    <SortIndicator active={sortKey === 'totalPower'} />
                                                </TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {sortedMilitary.map((row) => (
                                                <TableRow key={row.nation.id}>
                                                    <TableCell>
                                                        <NationBadge name={row.nation.name} color={row.nation.color} />
                                                    </TableCell>
                                                    <TableCell className="text-right">
                                                        <div className="flex items-center justify-end gap-2">
                                                            <div className="w-20 h-2 bg-gray-800 rounded-full overflow-hidden">
                                                                <div
                                                                    className="h-full rounded-full"
                                                                    style={{
                                                                        width: `${(row.totalCrew / maxCrew) * 100}%`,
                                                                        backgroundColor: row.nation.color,
                                                                    }}
                                                                />
                                                            </div>
                                                            <span className="w-16 text-right tabular-nums">
                                                                {row.totalCrew.toLocaleString()}
                                                            </span>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell className="text-right tabular-nums">
                                                        {row.generalCount}명
                                                    </TableCell>
                                                    <TableCell className="text-right tabular-nums">
                                                        {row.avgTrain}
                                                    </TableCell>
                                                    <TableCell className="text-right tabular-nums">
                                                        {row.avgAtmos}
                                                    </TableCell>
                                                    <TableCell className="text-right tabular-nums font-bold">
                                                        {row.totalPower.toLocaleString()}
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Tab 3: Front Lines */}
                <TabsContent value="frontline" className="mt-4 space-y-4">
                    {frontCities.length === 0 ? (
                        <EmptyState icon={Shield} title="전선 도시가 없습니다." />
                    ) : (
                        frontCities.map((c) => {
                            const cityGenerals = generalsByCity.get(c.id) ?? [];
                            const ownerNation = nations.find((n) => n.id === c.factionId);
                            const wallPercent = c.fortressMax > 0 ? Math.round((c.fortress / c.fortressMax) * 100) : 0;
                            const defPercent =
                                c.orbitalDefenseMax > 0
                                    ? Math.round((c.orbitalDefense / c.orbitalDefenseMax) * 100)
                                    : 0;
                            return (
                                <Card key={c.id}>
                                    <CardHeader className="pb-2">
                                        <CardTitle className="flex items-center gap-2 text-base">
                                            {ownerNation && (
                                                <NationBadge name={ownerNation.name} color={ownerNation.color} />
                                            )}
                                            <span>{c.name}</span>
                                            <Badge variant="secondary">{PLANET_LEVEL_NAMES[c.level] ?? c.level}</Badge>
                                            <Badge variant="destructive" className="ml-auto">
                                                전선 {c.frontState}
                                            </Badge>
                                        </CardTitle>
                                    </CardHeader>
                                    <CardContent className="space-y-3">
                                        <div className="grid grid-cols-2 gap-3">
                                            <div className="space-y-1">
                                                <div className="flex justify-between text-xs">
                                                    <span className="text-muted-foreground">성벽</span>
                                                    <span>
                                                        {c.wall}/{c.wallMax} ({wallPercent}%)
                                                    </span>
                                                </div>
                                                <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full bg-blue-500 rounded-full"
                                                        style={{ width: `${wallPercent}%` }}
                                                    />
                                                </div>
                                            </div>
                                            <div className="space-y-1">
                                                <div className="flex justify-between text-xs">
                                                    <span className="text-muted-foreground">방어</span>
                                                    <span>
                                                        {c.def}/{c.defMax} ({defPercent}%)
                                                    </span>
                                                </div>
                                                <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full bg-green-500 rounded-full"
                                                        style={{ width: `${defPercent}%` }}
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                        {cityGenerals.length > 0 && (
                                            <div>
                                                <div className="text-xs text-muted-foreground mb-1">
                                                    주둔 제독 ({cityGenerals.length}명)
                                                </div>
                                                <div className="overflow-x-auto">
                                                    <Table>
                                                        <TableHeader>
                                                            <TableRow>
                                                                <TableHead>이름</TableHead>
                                                                <TableHead className="text-right">병력</TableHead>
                                                                <TableHead className="text-right">훈련</TableHead>
                                                                <TableHead className="text-right">사기</TableHead>
                                                            </TableRow>
                                                        </TableHeader>
                                                        <TableBody>
                                                            {cityGenerals.map((g) => (
                                                                <TableRow key={g.id}>
                                                                    <TableCell className="py-1">
                                                                        <span className="text-sm">{g.name}</span>
                                                                    </TableCell>
                                                                    <TableCell className="text-right py-1 tabular-nums">
                                                                        {g.crew.toLocaleString()}
                                                                    </TableCell>
                                                                    <TableCell className="text-right py-1 tabular-nums">
                                                                        {g.train}
                                                                    </TableCell>
                                                                    <TableCell className="text-right py-1 tabular-nums">
                                                                        {g.atmos}
                                                                    </TableCell>
                                                                </TableRow>
                                                            ))}
                                                        </TableBody>
                                                    </Table>
                                                </div>
                                            </div>
                                        )}
                                    </CardContent>
                                </Card>
                            );
                        })
                    )}
                </TabsContent>

                {/* Tab 4: Personal Battle Log */}
                {myOfficer && (
                    <TabsContent value="personal" className="mt-4 space-y-4">
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="flex items-center gap-2 text-sm">
                                    <User className="size-4" />
                                    {myOfficer.name}의 전투 기록
                                    <div className="ml-auto flex items-center gap-2">
                                        <span className="text-xs text-muted-foreground">로그 스타일:</span>
                                        <select
                                            value={logStyle}
                                            onChange={(e) => setLogStyle(e.target.value as 'modern' | 'legacy')}
                                            className="h-6 border border-gray-600 bg-[#111] px-1 text-[10px] text-white"
                                        >
                                            <option value="modern">현대</option>
                                            <option value="legacy">레거시</option>
                                        </select>
                                    </div>
                                </CardTitle>
                            </CardHeader>
                            <CardContent>
                                {personalLoading ? (
                                    <div className="text-sm text-muted-foreground py-4 text-center">
                                        전투 기록 로딩 중...
                                    </div>
                                ) : personalLogs.length === 0 ? (
                                    <EmptyState icon={ScrollText} title="전투 기록이 없습니다." />
                                ) : (
                                    <div
                                        className={`max-h-96 overflow-y-auto space-y-1 ${logStyle === 'legacy' ? 'font-mono text-[11px] bg-black p-3 rounded border border-gray-800' : 'text-sm'}`}
                                    >
                                        {personalLogs.map((log) => (
                                            <div
                                                key={log.id}
                                                className={`py-1 border-b border-gray-800 last:border-0 ${logStyle === 'legacy' ? 'text-green-400' : ''}`}
                                            >
                                                <span className="text-muted-foreground text-xs mr-2">
                                                    {logStyle === 'legacy'
                                                        ? `[${log.date}]`
                                                        : new Date(log.date).toLocaleDateString('ko-KR', {
                                                              month: 'short',
                                                              day: 'numeric',
                                                              hour: '2-digit',
                                                              minute: '2-digit',
                                                          })}
                                                </span>
                                                {logStyle === 'legacy' ? (
                                                    <span>{log.message.replace(/<[^>]*>/g, '')}</span>
                                                ) : (
                                                    <span>{formatLog(log.message)}</span>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                )}
                                {!personalLoading && (
                                    <Button size="sm" variant="outline" className="mt-2" onClick={loadPersonalLogs}>
                                        새로고침
                                    </Button>
                                )}
                            </CardContent>
                        </Card>
                    </TabsContent>
                )}

                {/* Tab 5: General Logs */}
                {myOfficer && (
                    <TabsContent value="general-logs" className="mt-4 space-y-4">
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="flex items-center gap-2 text-sm">
                                    <User className="size-4" />
                                    제독 선택
                                    <div className="ml-auto flex items-center gap-2">
                                        <span className="text-xs text-muted-foreground">로그 스타일:</span>
                                        <select
                                            value={logStyle}
                                            onChange={(e) => setLogStyle(e.target.value as 'modern' | 'legacy')}
                                            className="h-6 border border-gray-600 bg-[#111] px-1 text-[10px] text-white"
                                        >
                                            <option value="modern">현대</option>
                                            <option value="legacy">레거시</option>
                                        </select>
                                    </div>
                                </CardTitle>
                            </CardHeader>
                            <CardContent>
                                <select
                                    value={selectedGeneralId ?? ''}
                                    onChange={(event) => setSelectedGeneralId(Number(event.target.value))}
                                    className="h-9 w-full border border-gray-600 bg-[#111] px-2 text-sm text-white rounded"
                                >
                                    {generals.map((general) => (
                                        <option key={general.id} value={general.id}>
                                            {general.name}
                                        </option>
                                    ))}
                                </select>
                                {selectedGeneral && (
                                    <div className="mt-2 text-xs text-muted-foreground">
                                        선택된 제독: {selectedGeneral.name}
                                    </div>
                                )}
                            </CardContent>
                        </Card>

                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                            {GENERAL_LOG_SECTIONS.map((section) => {
                                const logs = generalLogs[section.type];
                                return (
                                    <Card key={section.type}>
                                        <CardHeader className="pb-2">
                                            <CardTitle className="text-sm">{section.title}</CardTitle>
                                        </CardHeader>
                                        <CardContent>
                                            {generalLogsLoading ? (
                                                <div className="text-sm text-muted-foreground py-4 text-center">
                                                    로그 로딩 중...
                                                </div>
                                            ) : logs.length === 0 ? (
                                                <EmptyState icon={ScrollText} title={section.emptyTitle} />
                                            ) : (
                                                <div
                                                    className={`max-h-80 overflow-y-auto space-y-1 ${logStyle === 'legacy' ? 'font-mono text-[11px] bg-black p-3 rounded border border-gray-800' : 'text-sm'}`}
                                                >
                                                    {logs.map((log) => (
                                                        <div
                                                            key={log.id}
                                                            className={`py-1 border-b border-gray-800 last:border-0 ${logStyle === 'legacy' ? 'text-green-400' : ''}`}
                                                        >
                                                            <span className="text-muted-foreground text-xs mr-2">
                                                                {logStyle === 'legacy'
                                                                    ? `[${log.date}]`
                                                                    : new Date(log.date).toLocaleDateString('ko-KR', {
                                                                          month: 'short',
                                                                          day: 'numeric',
                                                                          hour: '2-digit',
                                                                          minute: '2-digit',
                                                                      })}
                                                            </span>
                                                            {logStyle === 'legacy' ? (
                                                                <span>{log.message.replace(/<[^>]*>/g, '')}</span>
                                                            ) : (
                                                                <span>{formatLog(log.message)}</span>
                                                            )}
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </CardContent>
                                    </Card>
                                );
                            })}
                        </div>

                        {!generalLogsLoading && selectedGeneralId !== null && (
                            <Button size="sm" variant="outline" onClick={() => void loadGeneralLogs(selectedGeneralId)}>
                                새로고침
                            </Button>
                        )}
                    </TabsContent>
                )}
            </Tabs>
        </div>
    );
}
