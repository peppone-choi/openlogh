'use client';

import { useEffect, useMemo, useState } from 'react';
import { Eye } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { Badge } from '@/components/ui/8bit/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/8bit/table';
import { troopApi } from '@/lib/gameApi';
import { CREW_TYPE_NAMES, getNPCColor } from '@/lib/game-utils';
import { useWorldStore } from '@/stores/worldStore';
import { useGeneralStore } from '@/stores/generalStore';
import { useGameStore } from '@/stores/gameStore';

function formatTurnTime(turnTime: string | null | undefined): string {
    if (!turnTime) return '-';
    return turnTime.slice(-8);
}

export default function SpyPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myGeneral, fetchMyGeneral } = useGeneralStore();
    const { generals, cities, nations, loading, loadAll } = useGameStore();
    const [troopNameMap, setTroopNameMap] = useState<Map<number, string>>(new Map());
    const [troopLoading, setTroopLoading] = useState(false);

    useEffect(() => {
        if (!currentWorld) return;
        fetchMyGeneral(currentWorld.id).catch(() => {});
        loadAll(currentWorld.id);
    }, [currentWorld, fetchMyGeneral, loadAll]);

    useEffect(() => {
        if (!myGeneral?.nationId) {
            setTroopNameMap(new Map());
            return;
        }

        setTroopLoading(true);
        troopApi
            .listByNation(myGeneral.nationId)
            .then(({ data }) => {
                const nextMap = new Map<number, string>();
                data.forEach((row) => {
                    nextMap.set(row.troop.id, row.troop.name);
                });
                setTroopNameMap(nextMap);
            })
            .catch(() => {
                setTroopNameMap(new Map());
            })
            .finally(() => {
                setTroopLoading(false);
            });
    }, [myGeneral?.nationId]);

    const cityMap = useMemo(() => new Map(cities.map((c) => [c.id, c.name])), [cities]);
    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);

    const nationGenerals = useMemo(() => {
        if (!myGeneral?.nationId) return [];
        return generals
            .filter((g) => g.nationId === myGeneral.nationId)
            .sort((a, b) => (a.turnTime ?? '').localeCompare(b.turnTime ?? ''));
    }, [generals, myGeneral?.nationId]);

    const summary = useMemo(() => {
        const effective = nationGenerals.filter((g) => g.npcState !== 5);
        const effCount = effective.length || 1;
        const withCrew = effective.filter((g) => g.crew > 0);
        const totalGold = effective.reduce((sum, g) => sum + g.gold, 0);
        const totalRice = effective.reduce((sum, g) => sum + g.rice, 0);
        const crewTotal = effective.reduce((sum, g) => sum + g.crew, 0);

        const t90 = withCrew.filter((g) => g.train >= 90 && g.atmos >= 90);
        const t80 = withCrew.filter((g) => g.train >= 80 && g.atmos >= 80);
        const t60 = withCrew.filter((g) => g.train >= 60 && g.atmos >= 60);

        return {
            totalGold,
            totalRice,
            avgGold: totalGold / effCount,
            avgRice: totalRice / effCount,
            crewTotal,
            effCount,
            crew90: t90.reduce((sum, g) => sum + g.crew, 0),
            gen90: t90.length,
            crew80: t80.reduce((sum, g) => sum + g.crew, 0),
            gen80: t80.length,
            crew60: t60.reduce((sum, g) => sum + g.crew, 0),
            gen60: t60.length,
        };
    }, [nationGenerals]);

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading || troopLoading)
        return (
            <div className="p-4">
                <LoadingState />
            </div>
        );

    const myNation = myGeneral?.nationId ? nationMap.get(myGeneral.nationId) : null;

    return (
        <div className="p-4 space-y-4 max-w-5xl mx-auto">
            <PageHeader icon={Eye} title="암행부" />

            <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Badge variant="secondary">{myNation?.name ?? '무소속'}</Badge>
                <span>소속 장수 {nationGenerals.length}명</span>
            </div>

            <div className="grid grid-cols-4 gap-px bg-gray-700 rounded overflow-hidden text-xs">
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">전체 금</span>
                    <div className="font-medium">{summary.totalGold.toLocaleString()}</div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">전체 쌀</span>
                    <div className="font-medium">{summary.totalRice.toLocaleString()}</div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">평균 금</span>
                    <div className="font-medium">
                        {summary.avgGold.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                    </div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">평균 쌀</span>
                    <div className="font-medium">
                        {summary.avgRice.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                    </div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">전체 병력/장수</span>
                    <div className="font-medium">
                        {summary.crewTotal.toLocaleString()}/{summary.effCount.toLocaleString()}
                    </div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">훈사90 병력/장수</span>
                    <div className="font-medium text-green-400">
                        {summary.crew90.toLocaleString()}/{summary.gen90.toLocaleString()}
                    </div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">훈사80 병력/장수</span>
                    <div className="font-medium text-yellow-400">
                        {summary.crew80.toLocaleString()}/{summary.gen80.toLocaleString()}
                    </div>
                </div>
                <div className="bg-muted/30 px-2 py-1 text-center">
                    <span className="text-muted-foreground">훈사60 병력/장수</span>
                    <div className="font-medium text-orange-400">
                        {summary.crew60.toLocaleString()}/{summary.gen60.toLocaleString()}
                    </div>
                </div>
            </div>

            <div className="overflow-x-auto">
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>이름</TableHead>
                            <TableHead>도시</TableHead>
                            <TableHead>자금</TableHead>
                            <TableHead>군량</TableHead>
                            <TableHead>병종</TableHead>
                            <TableHead>병사</TableHead>
                            <TableHead>훈련</TableHead>
                            <TableHead>사기</TableHead>
                            <TableHead>삭턴</TableHead>
                            <TableHead>턴</TableHead>
                            <TableHead>부대</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {nationGenerals.map((g) => (
                            <TableRow key={g.id}>
                                <TableCell className="font-medium" style={{ color: getNPCColor(g.npcState) }}>
                                    {g.name}
                                </TableCell>
                                <TableCell>{cityMap.get(g.cityId) ?? '-'}</TableCell>
                                <TableCell>{g.gold.toLocaleString()}</TableCell>
                                <TableCell>{g.rice.toLocaleString()}</TableCell>
                                <TableCell>{CREW_TYPE_NAMES[g.crewType] ?? String(g.crewType)}</TableCell>
                                <TableCell>{g.crew.toLocaleString()}</TableCell>
                                <TableCell>{g.train}</TableCell>
                                <TableCell>{g.atmos}</TableCell>
                                <TableCell>{g.killTurn ?? '-'}</TableCell>
                                <TableCell className="tabular-nums">{formatTurnTime(g.turnTime)}</TableCell>
                                <TableCell>{g.troopId ? (troopNameMap.get(g.troopId) ?? '-') : '-'}</TableCell>
                            </TableRow>
                        ))}
                        {nationGenerals.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={11} className="text-center text-muted-foreground">
                                    소속 장교가 없습니다.
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </div>
        </div>
    );
}
