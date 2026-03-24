'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { factionApi, planetApi, officerApi, nationPolicyApi, diplomacyApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import type { Nation, City, General, Diplomacy } from '@/types';
import { Landmark } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { NationBadge } from '@/components/game/nation-badge';
import { getNationLevelLabel } from '@/lib/game-utils';
import {
    calcPlanetFundsIncome,
    calcPlanetSuppliesIncome,
    calcPlanetFortressSuppliesIncome,
    calcPlanetWarFundsIncome,
    getBill,
    countCityOfficers,
} from '@/lib/income-calc';
import { toast } from 'sonner';

const DIP_STATE_LABELS: Record<string, { label: string; color: string }> = {
    normal: { label: '통상', color: 'gray' },
    nowar: { label: '불가침', color: 'cyan' },
    alliance: { label: '동맹', color: 'limegreen' },
    war: { label: '교전', color: 'red' },
};

export default function NationFinancePage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const { fetchMyGeneral } = useOfficerStore();

    const [loading, setLoading] = useState(true);
    const [nation, setNation] = useState<Nation | null>(null);
    const [cities, setCities] = useState<City[]>([]);
    const [generals, setGenerals] = useState<General[]>([]);
    const [allNations, setAllNations] = useState<Nation[]>([]);
    const [diplomacy, setDiplomacy] = useState<Diplomacy[]>([]);
    // Editable policy fields
    const [editRate, setEditRate] = useState(15);
    const [editBill, setEditBill] = useState(100);
    const [editSecretLimit, setEditSecretLimit] = useState(12);

    const isLeader = myOfficer && nation && (myOfficer.officerLevel >= 5 || myOfficer.id === nation.supremeCommanderId);

    const loadData = useCallback(async () => {
        if (!currentWorld || !myOfficer || !myOfficer.factionId) return;
        setLoading(true);
        try {
            const [nationRes, citiesRes, generalsRes, allNationsRes, dipRes] = await Promise.all([
                factionApi.get(myOfficer.factionId),
                planetApi.listByFaction(myOfficer.factionId),
                officerApi.listByFaction(myOfficer.factionId),
                factionApi.listByWorld(currentWorld.id),
                diplomacyApi.listByNation(currentWorld.id, myOfficer.factionId),
            ]);
            setNation(nationRes.data);
            setCities(citiesRes.data);
            setGenerals(generalsRes.data);
            setAllNations(allNationsRes.data);
            setDiplomacy(dipRes.data);
            setEditRate(nationRes.data.conscriptionRate);
            setEditBill(nationRes.data.taxRate);
            setEditSecretLimit(nationRes.data.secretLimit);
        } catch {
            /* ignore */
        } finally {
            setLoading(false);
        }
    }, [currentWorld, myOfficer]);

    useEffect(() => {
        if (!currentWorld) return;
        fetchMyGeneral(currentWorld.id).catch(() => {});
    }, [currentWorld, fetchMyGeneral]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    useEffect(() => {
        if (!currentWorld || !myOfficer) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            loadData();
        });
    }, [currentWorld, myOfficer, loadData]);

    // Income calculations
    const officerCntByCity = useMemo(() => {
        const map = new Map<number, number>();
        if (!generals.length) return map;
        for (const c of cities) {
            map.set(c.id, countCityOfficers(generals, c.id));
        }
        return map;
    }, [cities, generals]);

    const income = useMemo(() => {
        if (!nation || !cities.length) {
            return { goldCity: 0, goldWar: 0, riceCity: 0, riceWall: 0, outcome: 0 };
        }
        let gcBase = 0;
        let rcBase = 0;
        let rwBase = 0;
        let gw = 0;
        cities.forEach((c) => {
            const cnt = officerCntByCity.get(c.id) || 0;
            const cap = c.id === nation.capitalPlanetId;
            gcBase += calcPlanetFundsIncome(c, cnt, cap, nation.factionRank, nation.factionType);
            rcBase += calcPlanetSuppliesIncome(c, cnt, cap, nation.factionRank, nation.factionType);
            rwBase += calcPlanetFortressSuppliesIncome(c, cnt, cap, nation.factionRank, nation.factionType);
            gw += calcPlanetWarFundsIncome(c, nation.factionType);
        });
        const goldCity = Math.round((gcBase * nation.conscriptionRate) / 20);
        const riceCity = Math.round((rcBase * nation.conscriptionRate) / 20);
        const riceWall = Math.round((rwBase * nation.conscriptionRate) / 20);
        const baseOut = generals.filter((g) => g.npcState !== 5).reduce((s, g) => s + getBill(g.dedication), 0);
        const outcome = Math.round((baseOut * nation.taxRate) / 100);
        return { goldCity, goldWar: gw, riceCity, riceWall, outcome };
    }, [nation, cities, generals, officerCntByCity]);

    const { goldCity, goldWar, riceCity, riceWall, outcome } = income;
    const totalGoldIncome = goldCity + goldWar;
    const totalRiceIncome = riceCity + riceWall;

    // Diplomacy map keyed by the other nation's id
    const dipMap = useMemo(() => {
        const map = new Map<number, Diplomacy>();
        for (const d of diplomacy) {
            const otherId = d.srcNationId === myOfficer?.nationId ? d.destNationId : d.srcNationId;
            map.set(otherId, d);
        }
        return map;
    }, [diplomacy, myOfficer]);

    const otherNations = useMemo(() => allNations.filter((n) => n.id !== myOfficer?.nationId), [allNations, myOfficer]);

    // Policy save handlers
    const saveRate = async () => {
        if (!nation) return;
        const clamped = Math.min(30, Math.max(5, editRate));
        try {
            await nationPolicyApi.updatePolicy(nation.id, { rate: clamped });
            toast.success('세율을 변경했습니다.');
            await loadData();
        } catch {
            toast.error('세율 변경에 실패했습니다.');
        }
    };

    const saveBill = async () => {
        if (!nation) return;
        const clamped = Math.min(200, Math.max(20, editBill));
        try {
            await nationPolicyApi.updatePolicy(nation.id, { bill: clamped });
            toast.success('지급률을 변경했습니다.');
            await loadData();
        } catch {
            toast.error('지급률 변경에 실패했습니다.');
        }
    };

    const saveSecretLimit = async () => {
        if (!nation) return;
        const clamped = Math.min(99, Math.max(1, editSecretLimit));
        try {
            await nationPolicyApi.updatePolicy(nation.id, { secretLimit: clamped });
            toast.success('기밀 권한을 변경했습니다.');
            await loadData();
        } catch {
            toast.error('기밀 권한 변경에 실패했습니다.');
        }
    };

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (!myOfficer || !myOfficer.factionId)
        return <div className="p-4 text-muted-foreground">진영에 소속되어있지 않습니다.</div>;
    if (loading)
        return (
            <div className="p-4">
                <LoadingState />
            </div>
        );
    if (!nation) return <div className="p-4 text-muted-foreground">진영 정보를 불러올 수 없습니다.</div>;

    return (
        <div className="p-4 space-y-4 max-w-5xl mx-auto">
            <PageHeader icon={Landmark} title="내무부" />

            {/* Diplomacy Table */}
            <Card>
                <CardHeader className="py-2 px-4">
                    <CardTitle className="text-sm">외교관계</CardTitle>
                </CardHeader>
                <CardContent className="px-4 pb-3">
                    <div className="overflow-x-auto">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>국가명</TableHead>
                                    <TableHead className="text-right">국력</TableHead>
                                    <TableHead className="text-right">속령</TableHead>
                                    <TableHead>상태</TableHead>
                                    <TableHead className="text-right">기간</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {otherNations.map((n) => {
                                    const dip = dipMap.get(n.id);
                                    const stateKey = dip?.stateCode ?? 'normal';
                                    const stateInfo = DIP_STATE_LABELS[stateKey] ?? DIP_STATE_LABELS.normal;
                                    return (
                                        <TableRow key={n.id}>
                                            <TableCell>
                                                <NationBadge name={n.name} color={n.color} />
                                            </TableCell>
                                            <TableCell className="text-right">{n.power.toLocaleString()}</TableCell>
                                            <TableCell className="text-right">-</TableCell>
                                            <TableCell style={{ color: stateInfo.color }}>{stateInfo.label}</TableCell>
                                            <TableCell className="text-right">
                                                {dip?.term && dip.term > 0 ? `${dip.term}개월` : '-'}
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                                {otherNations.length === 0 && (
                                    <TableRow>
                                        <TableCell colSpan={5} className="text-center text-muted-foreground">
                                            다른 진영가 없습니다.
                                        </TableCell>
                                    </TableRow>
                                )}
                            </TableBody>
                        </Table>
                    </div>
                </CardContent>
            </Card>

            {/* Budget */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Gold Budget */}
                <Card>
                    <CardHeader className="py-2 px-4">
                        <CardTitle className="text-sm">자금 예산</CardTitle>
                    </CardHeader>
                    <CardContent className="px-4 pb-3 space-y-1 text-sm">
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">현재</span>
                            <span>{nation.funds.toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">단기수입</span>
                            <span>{goldWar.toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">세금</span>
                            <span>{goldCity.toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">수입/지출</span>
                            <span>
                                +{totalGoldIncome.toLocaleString()} / {(-outcome).toLocaleString()}
                            </span>
                        </div>
                        <div className="flex justify-between font-medium border-t border-gray-700 pt-1">
                            <span>국고 예산</span>
                            <span>
                                {(nation.funds + totalGoldIncome - outcome).toLocaleString()} (
                                {totalGoldIncome >= outcome ? '+' : ''}
                                {(totalGoldIncome - outcome).toLocaleString()})
                            </span>
                        </div>
                    </CardContent>
                </Card>

                {/* Rice Budget */}
                <Card>
                    <CardHeader className="py-2 px-4">
                        <CardTitle className="text-sm">물자 예산</CardTitle>
                    </CardHeader>
                    <CardContent className="px-4 pb-3 space-y-1 text-sm">
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">현재</span>
                            <span>{nation.supplies.toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">둔전수입</span>
                            <span>{riceWall.toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">세금</span>
                            <span>{riceCity.toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">수입/지출</span>
                            <span>
                                +{totalRiceIncome.toLocaleString()} / {(-outcome).toLocaleString()}
                            </span>
                        </div>
                        <div className="flex justify-between font-medium border-t border-gray-700 pt-1">
                            <span>국고 예산</span>
                            <span>
                                {(nation.supplies + totalRiceIncome - outcome).toLocaleString()} (
                                {totalRiceIncome >= outcome ? '+' : ''}
                                {(totalRiceIncome - outcome).toLocaleString()})
                            </span>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Policy Settings */}
            <Card>
                <CardHeader className="py-2 px-4">
                    <CardTitle className="text-sm">정책 설정</CardTitle>
                </CardHeader>
                <CardContent className="px-4 pb-3 space-y-3">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        {/* Tax Rate */}
                        <div className="space-y-1">
                            <div className="text-sm text-muted-foreground">세율 (5~30%)</div>
                            <div className="flex items-center gap-2">
                                <Input
                                    type="number"
                                    min={5}
                                    max={30}
                                    value={editRate}
                                    onChange={(e) => setEditRate(parseInt(e.target.value, 10) || 5)}
                                    className="w-20"
                                    disabled={!isLeader}
                                />
                                <span className="text-sm">%</span>
                                {isLeader && (
                                    <>
                                        <Button size="sm" onClick={saveRate}>
                                            변경
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() => setEditRate(nation.conscriptionRate)}
                                        >
                                            취소
                                        </Button>
                                    </>
                                )}
                            </div>
                        </div>

                        {/* Pay Rate */}
                        <div className="space-y-1">
                            <div className="text-sm text-muted-foreground">지급률 (20~200%)</div>
                            <div className="flex items-center gap-2">
                                <Input
                                    type="number"
                                    min={20}
                                    max={200}
                                    value={editBill}
                                    onChange={(e) => setEditBill(parseInt(e.target.value, 10) || 20)}
                                    className="w-20"
                                    disabled={!isLeader}
                                />
                                <span className="text-sm">%</span>
                                {isLeader && (
                                    <>
                                        <Button size="sm" onClick={saveBill}>
                                            변경
                                        </Button>
                                        <Button size="sm" variant="outline" onClick={() => setEditBill(nation.taxRate)}>
                                            취소
                                        </Button>
                                    </>
                                )}
                            </div>
                        </div>

                        {/* Secret Limit */}
                        <div className="space-y-1">
                            <div className="text-sm text-muted-foreground">기밀 권한 (1~99년)</div>
                            <div className="flex items-center gap-2">
                                <Input
                                    type="number"
                                    min={1}
                                    max={99}
                                    value={editSecretLimit}
                                    onChange={(e) => setEditSecretLimit(parseInt(e.target.value, 10) || 1)}
                                    className="w-20"
                                    disabled={!isLeader}
                                />
                                <span className="text-sm">년</span>
                                {isLeader && (
                                    <>
                                        <Button size="sm" onClick={saveSecretLimit}>
                                            변경
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() => setEditSecretLimit(nation.secretLimit)}
                                        >
                                            취소
                                        </Button>
                                    </>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Summary badges */}
                    <div className="flex flex-wrap gap-2 text-xs">
                        <Badge variant="secondary">
                            국력: {getNationLevelLabel(nation.factionRank, nation.factionType)}
                        </Badge>
                        <Badge variant="secondary">행성: {cities.length}개</Badge>
                        <Badge variant="secondary">제독: {generals.filter((g) => g.npcState !== 5).length}명</Badge>
                        <Badge variant="secondary">기술: {nation.techLevel}</Badge>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
