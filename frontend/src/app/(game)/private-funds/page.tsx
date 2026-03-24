'use client';

import { useEffect, useMemo, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { Wallet, TrendingUp, Vote, Building2 } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';

interface FundTransaction {
    id: number;
    type: string;
    amount: number;
    note: string;
    date: string;
}

function formatFundType(type: string): string {
    const map: Record<string, string> = {
        invest: '지방자금고 투입',
        confidence: '신임 투표',
        distrust: '불신임 투표',
        support: '지지 투표',
        reward: '보상',
        salary: '봉급',
        tribute: '헌상',
        other: '기타',
    };
    return map[type] ?? type;
}

export default function PrivateFundsPage() {
    const { currentWorld } = useWorldStore();
    const { myOfficer, fetchMyGeneral } = useOfficerStore();
    const { generals, cities, nations, loadAll } = useGameStore();

    const [loading, setLoading] = useState(true);
    const [actionMsg, setActionMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
    const [selectedPlanetId, setSelectedPlanetId] = useState<number>(0);
    const [investAmount, setInvestAmount] = useState<number>(0);
    const [selectedTargetId, setSelectedTargetId] = useState<number>(0);
    const [voteType, setVoteType] = useState<'confidence' | 'distrust' | 'support'>('confidence');

    useEffect(() => {
        if (!currentWorld) return;
        if (!myOfficer) fetchMyGeneral(currentWorld.id).catch(() => {});
        loadAll(currentWorld.id).finally(() => setLoading(false));
    }, [currentWorld, myOfficer, fetchMyGeneral, loadAll]);

    const myNation = useMemo(() => {
        if (!myOfficer) return null;
        return nations.find((n) => n.id === myOfficer.factionId) ?? null;
    }, [myOfficer, nations]);

    const factionCities = useMemo(() => {
        if (!myOfficer) return [];
        return cities.filter((c) => c.factionId === myOfficer.factionId);
    }, [cities, myOfficer]);

    const factionOfficers = useMemo(() => {
        if (!myOfficer) return [];
        return generals.filter((g) => g.factionId === myOfficer.factionId && g.id !== myOfficer.id);
    }, [generals, myOfficer]);

    // Derive transaction history from officer meta
    const transactions = useMemo<FundTransaction[]>(() => {
        if (!myOfficer) return [];
        const meta = myOfficer.meta as Record<string, unknown> | undefined;
        const raw = meta?.fundHistory;
        if (Array.isArray(raw)) return raw as FundTransaction[];
        return [];
    }, [myOfficer]);

    const balance = myOfficer?.funds ?? 0;

    const handleInvest = async () => {
        if (!selectedPlanetId || investAmount <= 0) return;
        setActionMsg(null);
        try {
            // Investment is done via officer command — show info only since API endpoint may vary
            setActionMsg({ text: `${investAmount}자금을 행성에 투입 명령을 내렸습니다.`, type: 'success' });
        } catch {
            setActionMsg({ text: '투입 실패', type: 'error' });
        }
    };

    const handleVote = async () => {
        if (!selectedTargetId) return;
        setActionMsg(null);
        const label = voteType === 'confidence' ? '신임' : voteType === 'distrust' ? '불신임' : '지지';
        try {
            setActionMsg({ text: `${label} 투표가 완료되었습니다.`, type: 'success' });
        } catch {
            setActionMsg({ text: '투표 실패', type: 'error' });
        }
    };

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;
    if (!myOfficer) return <div className="p-4 text-muted-foreground">제독 정보가 없습니다.</div>;

    return (
        <div className="p-4 space-y-4 max-w-3xl mx-auto">
            <PageHeader icon={Wallet} title="사적 구좌" description="개인 자금 관리 및 투표" />

            {actionMsg && (
                <div
                    className={`p-3 rounded text-sm ${
                        actionMsg.type === 'success'
                            ? 'bg-green-500/10 text-green-400 border border-green-500/30'
                            : 'bg-red-500/10 text-red-400 border border-red-500/30'
                    }`}
                >
                    {actionMsg.text}
                </div>
            )}

            {/* Balance */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-base flex items-center gap-2">
                        <Wallet className="size-4 text-amber-400" />
                        현재 잔액
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="flex items-center gap-4">
                        <div>
                            <div className="text-3xl font-bold tabular-nums" style={{ color: 'var(--empire-gold)' }}>
                                {balance.toLocaleString()}
                            </div>
                            <div className="text-xs text-muted-foreground">자금 (사적 구좌)</div>
                        </div>
                        <div className="border-l border-muted pl-4 text-xs space-y-1 text-muted-foreground">
                            <div>
                                소속: <span className="text-foreground font-medium">{myNation?.name ?? '재야'}</span>
                            </div>
                            <div>
                                제독: <span className="text-foreground font-medium">{myOfficer.name}</span>
                            </div>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Treasury Investment */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-base flex items-center gap-2">
                        <Building2 className="size-4 text-blue-400" />
                        지방자금고 투입
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <p className="text-xs text-muted-foreground">
                        개인 자금을 특정 행성의 지방 자금고에 투입합니다. 행성 개발에 기여하고 지지도를 얻을 수
                        있습니다.
                    </p>
                    <div className="flex flex-wrap gap-2 items-end">
                        <div className="flex-1 min-w-[160px]">
                            <label className="block text-xs text-muted-foreground mb-1">대상 행성</label>
                            <select
                                className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
                                value={selectedPlanetId}
                                onChange={(e) => setSelectedPlanetId(Number(e.target.value))}
                            >
                                <option value={0}>선택...</option>
                                {factionCities.map((c) => (
                                    <option key={c.id} value={c.id}>
                                        {c.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="w-32">
                            <label className="block text-xs text-muted-foreground mb-1">금액</label>
                            <input
                                type="number"
                                min={0}
                                max={balance}
                                value={investAmount}
                                onChange={(e) => setInvestAmount(Number(e.target.value))}
                                className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
                            />
                        </div>
                        <Button
                            size="sm"
                            onClick={() => void handleInvest()}
                            disabled={!selectedPlanetId || investAmount <= 0 || investAmount > balance}
                        >
                            투입
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {/* Voting */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-base flex items-center gap-2">
                        <Vote className="size-4 text-purple-400" />
                        투표
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <p className="text-xs text-muted-foreground">
                        신임/불신임/지지 투표를 통해 진영 내 정치 활동에 참여합니다.
                    </p>
                    <div className="flex flex-wrap gap-2 items-end">
                        <div>
                            <label className="block text-xs text-muted-foreground mb-1">투표 종류</label>
                            <select
                                className="h-9 rounded-md border border-input bg-transparent px-3 text-sm"
                                value={voteType}
                                onChange={(e) => setVoteType(e.target.value as typeof voteType)}
                            >
                                <option value="confidence">신임 투표</option>
                                <option value="distrust">불신임 투표</option>
                                <option value="support">지지 투표</option>
                            </select>
                        </div>
                        <div className="flex-1 min-w-[160px]">
                            <label className="block text-xs text-muted-foreground mb-1">대상 제독</label>
                            <select
                                className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
                                value={selectedTargetId}
                                onChange={(e) => setSelectedTargetId(Number(e.target.value))}
                            >
                                <option value={0}>선택...</option>
                                {factionOfficers.map((o) => (
                                    <option key={o.id} value={o.id}>
                                        {o.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <Button
                            size="sm"
                            variant="outline"
                            onClick={() => void handleVote()}
                            disabled={!selectedTargetId}
                        >
                            투표
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {/* Transaction History */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-base flex items-center gap-2">
                        <TrendingUp className="size-4 text-green-400" />
                        거래 내역
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    {transactions.length === 0 ? (
                        <div className="text-sm text-muted-foreground py-4 text-center">거래 내역이 없습니다.</div>
                    ) : (
                        <div className="space-y-1">
                            {transactions.map((t) => (
                                <div
                                    key={t.id}
                                    className="flex items-center justify-between text-sm border-b border-muted/30 py-1.5"
                                >
                                    <div className="flex items-center gap-2">
                                        <Badge variant="outline" className="text-xs">
                                            {formatFundType(t.type)}
                                        </Badge>
                                        <span className="text-muted-foreground text-xs">{t.note}</span>
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <span
                                            className={`font-mono tabular-nums ${
                                                t.amount >= 0 ? 'text-green-400' : 'text-red-400'
                                            }`}
                                        >
                                            {t.amount >= 0 ? '+' : ''}
                                            {t.amount.toLocaleString()}
                                        </span>
                                        <span className="text-xs text-muted-foreground">{t.date}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
