'use client';

import { useEffect, useMemo, useState, useCallback } from 'react';
import Link from 'next/link';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';
import { historyApi } from '@/lib/gameApi';
import { Crown, ChevronDown, ChevronRight, BarChart3, ScrollText, Trophy } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { NationBadge } from '@/components/game/nation-badge';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatGameLogDate } from '@/lib/gameLogDate';
import { formatOfficerLevelText, getNationLevelLabel, stripCodePrefix, getNationTypeLabel } from '@/lib/game-utils';
import { formatLog } from '@/lib/formatLog';
import type { Message, YearbookSummary } from '@/types';

export default function EmperorPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { nations, generals, cities, loading, loadAll } = useGameStore();
    const [dynastyLogs, setDynastyLogs] = useState<Message[]>([]);
    const [records, setRecords] = useState<Message[]>([]);
    const [yearbook, setYearbook] = useState<YearbookSummary | null>(null);
    const [selectedYear, setSelectedYear] = useState<number | null>(null);
    const [yearbookLoading, setYearbookLoading] = useState(false);
    const [showTimeline, setShowTimeline] = useState(true);
    const [showRecords, setShowRecords] = useState(false);
    const [showStats, setShowStats] = useState(false);

    useEffect(() => {
        if (!currentWorld) return;
        loadAll(currentWorld.id);
        historyApi
            .getWorldHistory(currentWorld.id)
            .then(({ data }) => setDynastyLogs(data))
            .catch(() => setDynastyLogs([]));
        historyApi
            .getWorldRecords(currentWorld.id)
            .then(({ data }) => setRecords(data))
            .catch(() => setRecords([]));
    }, [currentWorld, loadAll]);

    const emperorNation = useMemo(
        () => nations.find((n) => n.level >= 9 || n.meta?.imperialStatus === 'emperor'),
        [nations]
    );

    const chiefGeneral = useMemo(
        () => (emperorNation ? generals.find((g) => g.id === emperorNation.chiefGeneralId) : null),
        [emperorNation, generals]
    );

    const capitalCity = useMemo(
        () => (emperorNation ? cities.find((c) => c.id === emperorNation.capitalCityId) : null),
        [emperorNation, cities]
    );

    const nationGenerals = useMemo(
        () =>
            emperorNation
                ? generals
                      .filter((g) => g.nationId === emperorNation.id)
                      .sort((a, b) => b.officerLevel - a.officerLevel)
                : [],
        [emperorNation, generals]
    );

    const nationCities = useMemo(
        () => (emperorNation ? cities.filter((c) => c.nationId === emperorNation.id) : []),
        [emperorNation, cities]
    );

    // Dynasty timeline — group logs by type/era
    const dynastyTimeline = useMemo(() => {
        return dynastyLogs.map((log) => {
            const raw = log.payload?.content;
            const text = typeof raw === 'string' ? raw : JSON.stringify(log.payload ?? {});
            const nationColor = (log.payload?.color as string) ?? undefined;
            const nationName = (log.payload?.nationName as string) ?? undefined;
            return { ...log, text, nationColor, nationName };
        });
    }, [dynastyLogs]);

    const emperorHistory = useMemo(() => {
        type EmperorEntry = {
            nationName: string;
            emperorName: string;
            typeCode: string;
            startLabel: string;
            startAt: number;
            endLabel?: string;
            endAt?: number;
        };

        const entries = new Map<string, EmperorEntry>();
        const finished: EmperorEntry[] = [];

        for (const log of dynastyTimeline) {
            const text = log.text;
            if (!text) continue;

            const isEmperorEvent =
                text.includes('황제') ||
                text.includes('즉위') ||
                text.includes('폐위') ||
                text.includes('퇴위') ||
                text.includes('선양') ||
                text.includes('제위');
            if (!isEmperorEvent) continue;

            const nationNameMatch = text.match(/\[([^\]]+)\]/) ?? text.match(/([^\s]+)국/);
            const nationName = log.nationName ?? nationNameMatch?.[1] ?? nationNameMatch?.[0] ?? '미상';

            const emperorNameMatch =
                text.match(/황제\s*([^\s,，.。]+)\s*(즉위|선양|퇴위|폐위|제위|사망)?/) ??
                text.match(/([^\s,，.。]+)\s*(?:이|가)?\s*(즉위|선양|퇴위|폐위|제위|사망)/);
            const emperorName = emperorNameMatch?.[1] ?? '미상';

            const nation = nations.find((n) => n.name === nationName);
            const typeCode = nation?.typeCode ?? '-';

            const date = new Date(log.sentAt).getTime();
            const dateLabel = log.sentAt;
            const key = `${nationName}:${emperorName}`;

            if (text.includes('즉위') || text.includes('선양') || text.includes('제위')) {
                entries.set(key, {
                    nationName,
                    emperorName,
                    typeCode,
                    startLabel: dateLabel,
                    startAt: date,
                });
                continue;
            }

            if (text.includes('폐위') || text.includes('퇴위') || text.includes('사망')) {
                const opened = entries.get(key);
                if (opened) {
                    opened.endLabel = dateLabel;
                    opened.endAt = date;
                    finished.push(opened);
                    entries.delete(key);
                } else {
                    finished.push({
                        nationName,
                        emperorName,
                        typeCode,
                        startLabel: '-',
                        startAt: date,
                        endLabel: dateLabel,
                        endAt: date,
                    });
                }
            }
        }

        const openEntries = Array.from(entries.values());
        return [...finished, ...openEntries]
            .sort((a, b) => (b.endAt ?? b.startAt) - (a.endAt ?? a.startAt))
            .map((item) => ({
                nationName: item.nationName,
                emperorName: item.emperorName,
                typeCode: item.typeCode,
                reign:
                    item.startLabel === '-'
                        ? (item.endLabel ?? '-')
                        : `${item.startLabel} ~ ${item.endLabel ?? '재위중'}`,
            }));
    }, [dynastyTimeline, nations]);

    // Season records — group by type
    const recordsByType = useMemo(() => {
        const map = new Map<string, Message[]>();
        for (const r of records) {
            const type = (r.messageType ?? '기타') as string;
            if (!map.has(type)) map.set(type, []);
            map.get(type)!.push(r);
        }
        return map;
    }, [records]);

    // Nation power stats for histogram
    const nationStats = useMemo(() => {
        return [...nations]
            .sort((a, b) => b.power - a.power)
            .map((n) => ({
                id: n.id,
                name: n.name,
                color: n.color,
                power: n.power,
                generalCount: generals.filter((g) => g.nationId === n.id).length,
                cityCount: cities.filter((c) => c.nationId === n.id).length,
                gold: n.gold,
                rice: n.rice,
                tech: n.tech,
                level: n.level,
            }));
    }, [nations, generals, cities]);

    const maxPower = Math.max(1, ...nationStats.map((n) => n.power));
    const maxGold = Math.max(1, ...nationStats.map((n) => n.gold));
    const maxRice = Math.max(1, ...nationStats.map((n) => n.rice));
    const maxTech = Math.max(1, ...nationStats.map((n) => n.tech));

    const loadYearbook = useCallback(
        async (year: number) => {
            if (!currentWorld) return;
            setYearbookLoading(true);
            try {
                const { data } = await historyApi.getYearbook(currentWorld.id, year);
                setYearbook(data);
                setSelectedYear(year);
            } catch {
                setYearbook(null);
            } finally {
                setYearbookLoading(false);
            }
        },
        [currentWorld]
    );

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading)
        return (
            <div className="p-4">
                <LoadingState />
            </div>
        );

    return (
        <div className="p-4 space-y-6 max-w-4xl mx-auto">
            <PageHeader icon={Crown} title="황제 정보" />

            {/* Current Emperor */}
            {emperorNation ? (
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <Crown className="size-5 text-amber-400" />
                            <NationBadge name={emperorNation.name} color={emperorNation.color} />
                            <Badge variant="secondary">황제국</Badge>
                            <Button asChild size="sm" variant="outline" className="ml-auto">
                                <Link href="/emperor/detail">자세히</Link>
                            </Button>
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                            <StatItem label="국가 레벨" value={String(emperorNation.level)} />
                            <StatItem label="기술력" value={String(emperorNation.tech)} />
                            <StatItem label="국력" value={emperorNation.power.toLocaleString()} />
                            <StatItem label="수도" value={capitalCity?.name ?? '-'} />
                            <StatItem
                                label="금"
                                value={<span className="text-yellow-400">{emperorNation.gold.toLocaleString()}</span>}
                            />
                            <StatItem
                                label="쌀"
                                value={<span className="text-green-400">{emperorNation.rice.toLocaleString()}</span>}
                            />
                            <StatItem label="도시" value={`${nationCities.length}개`} />
                            <StatItem label="장수" value={`${nationGenerals.length}명`} />
                        </div>

                        {chiefGeneral && (
                            <div className="border-t border-muted/30 pt-3">
                                <h3 className="text-sm font-semibold mb-2">군주</h3>
                                <div className="flex items-center gap-3">
                                    <GeneralPortrait
                                        picture={chiefGeneral.picture}
                                        name={chiefGeneral.name}
                                        size="md"
                                    />
                                    <div>
                                        <div className="font-bold">{chiefGeneral.name}</div>
                                        <div className="text-xs text-muted-foreground">
                                            통{chiefGeneral.leadership} 무{chiefGeneral.strength} 지{chiefGeneral.intel}{' '}
                                            정{chiefGeneral.politics} 매{chiefGeneral.charm}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* Full officer hierarchy */}
                        {nationGenerals.filter((g) => g.officerLevel >= 2).length > 0 && (
                            <div className="border-t border-muted/30 pt-3">
                                <h3 className="text-sm font-semibold mb-2">관직 편제</h3>
                                {/* Central officers (level >= 5) */}
                                {nationGenerals.filter((g) => g.officerLevel >= 5).length > 0 && (
                                    <div className="mb-3">
                                        <h4 className="text-xs text-muted-foreground mb-1">수뇌부</h4>
                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                            {nationGenerals
                                                .filter((g) => g.officerLevel >= 5)
                                                .map((g) => (
                                                    <div
                                                        key={g.id}
                                                        className="flex items-center gap-2 rounded border border-muted/30 p-2"
                                                    >
                                                        <GeneralPortrait picture={g.picture} name={g.name} size="sm" />
                                                        <div className="flex-1 min-w-0">
                                                            <span className="font-medium text-sm">{g.name}</span>
                                                            <Badge variant="outline" className="ml-1 text-[10px]">
                                                                {formatOfficerLevelText(
                                                                    g.officerLevel,
                                                                    emperorNation.level,
                                                                    true,
                                                                    emperorNation.typeCode,
                                                                    g.npcState
                                                                )}
                                                            </Badge>
                                                        </div>
                                                        <div className="text-[10px] text-muted-foreground whitespace-nowrap">
                                                            통{g.leadership} 무{g.strength} 지{g.intel}
                                                        </div>
                                                    </div>
                                                ))}
                                        </div>
                                    </div>
                                )}
                                {/* City officers (level 2-4) */}
                                {nationGenerals.filter((g) => g.officerLevel >= 2 && g.officerLevel <= 4).length >
                                    0 && (
                                    <div className="mb-3">
                                        <h4 className="text-xs text-muted-foreground mb-1">도시 관직</h4>
                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                            {nationGenerals
                                                .filter((g) => g.officerLevel >= 2 && g.officerLevel <= 4)
                                                .sort((a, b) => b.officerLevel - a.officerLevel)
                                                .map((g) => {
                                                    const officerCityName = g.officerCity
                                                        ? cities.find((c) => c.id === g.officerCity)?.name
                                                        : undefined;
                                                    return (
                                                        <div
                                                            key={g.id}
                                                            className="flex items-center gap-2 rounded border border-muted/20 p-1.5"
                                                        >
                                                            <GeneralPortrait
                                                                picture={g.picture}
                                                                name={g.name}
                                                                size="sm"
                                                            />
                                                            <div className="flex-1 min-w-0">
                                                                <span className="text-sm">{g.name}</span>
                                                                <Badge variant="outline" className="ml-1 text-[10px]">
                                                                    {formatOfficerLevelText(
                                                                        g.officerLevel,
                                                                        emperorNation.level,
                                                                        true,
                                                                        emperorNation.typeCode,
                                                                        g.npcState
                                                                    )}
                                                                </Badge>
                                                                {officerCityName && (
                                                                    <span className="text-[10px] text-muted-foreground ml-1">
                                                                        【{officerCityName}】
                                                                    </span>
                                                                )}
                                                            </div>
                                                            <div className="text-[10px] text-muted-foreground whitespace-nowrap">
                                                                통{g.leadership} 무{g.strength} 지{g.intel}
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                        </div>
                                    </div>
                                )}
                                {/* General soldiers (level 1) */}
                                <div>
                                    <h4 className="text-xs text-muted-foreground mb-1">
                                        일반 장수 ({nationGenerals.filter((g) => g.officerLevel <= 1).length}
                                        명)
                                    </h4>
                                    <div className="flex flex-wrap gap-1">
                                        {nationGenerals
                                            .filter((g) => g.officerLevel <= 1)
                                            .map((g) => (
                                                <Badge key={g.id} variant="outline" className="text-[10px]">
                                                    {g.name}
                                                </Badge>
                                            ))}
                                    </div>
                                </div>
                            </div>
                        )}
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardContent className="py-4">
                        <p className="text-sm text-muted-foreground text-center">아직 황제를 칭한 국가가 없습니다.</p>
                    </CardContent>
                </Card>
            )}

            {/* Nation levels overview */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-base">국가 작위 현황</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="overflow-x-auto">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>국가</TableHead>
                                    <TableHead>레벨</TableHead>
                                    <TableHead>칭호</TableHead>
                                    <TableHead>성향</TableHead>
                                    <TableHead className="text-right">국력</TableHead>
                                    <TableHead className="text-right">장수</TableHead>
                                    <TableHead className="text-right">속령</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {[...nations]
                                    .sort((a, b) => b.level - a.level || b.power - a.power)
                                    .map((n) => (
                                        <TableRow key={n.id}>
                                            <TableCell>
                                                <NationBadge name={n.name} color={n.color} />
                                            </TableCell>
                                            <TableCell>
                                                <Badge variant="outline">{n.level}</Badge>
                                            </TableCell>
                                            <TableCell className="text-muted-foreground">
                                                {getNationLevelLabel(n.level, n.typeCode)}
                                            </TableCell>
                                            <TableCell>{getNationTypeLabel(n.typeCode)}</TableCell>
                                            <TableCell className="text-right tabular-nums">
                                                {n.power.toLocaleString()}
                                            </TableCell>
                                            <TableCell className="text-right">
                                                {generals.filter((g) => g.nationId === n.id).length}
                                            </TableCell>
                                            <TableCell className="text-right">
                                                {cities.filter((c) => c.nationId === n.id).length}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                            </TableBody>
                        </Table>
                    </div>
                </CardContent>
            </Card>

            {/* Statistics Histogram */}
            <Card>
                <CardHeader className="pb-2 cursor-pointer" onClick={() => setShowStats(!showStats)}>
                    <CardTitle className="text-base flex items-center gap-2">
                        <BarChart3 className="size-4" />
                        통계 히스토그램
                        {showStats ? (
                            <ChevronDown className="size-4 ml-auto" />
                        ) : (
                            <ChevronRight className="size-4 ml-auto" />
                        )}
                    </CardTitle>
                </CardHeader>
                {showStats && (
                    <CardContent className="space-y-4">
                        <BarSection title="국력" stats={nationStats} field="power" max={maxPower} />
                        <BarSection title="금" stats={nationStats} field="gold" max={maxGold} />
                        <BarSection title="쌀" stats={nationStats} field="rice" max={maxRice} />
                        <BarSection title="기술" stats={nationStats} field="tech" max={maxTech} />
                    </CardContent>
                )}
            </Card>

            {/* Dynasty Timeline */}
            <Card>
                <CardHeader className="pb-2 cursor-pointer" onClick={() => setShowTimeline(!showTimeline)}>
                    <CardTitle className="text-base flex items-center gap-2">
                        <ScrollText className="size-4" />
                        왕조 연표 ({dynastyTimeline.length}건)
                        {showTimeline ? (
                            <ChevronDown className="size-4 ml-auto" />
                        ) : (
                            <ChevronRight className="size-4 ml-auto" />
                        )}
                    </CardTitle>
                </CardHeader>
                {showTimeline && (
                    <CardContent>
                        {emperorHistory.length === 0 ? (
                            <p className="text-sm text-muted-foreground">황제 재위 이력 데이터가 없습니다.</p>
                        ) : (
                            <div className="max-h-[400px] overflow-y-auto">
                                <div className="overflow-x-auto">
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead>국가</TableHead>
                                                <TableHead>황제명</TableHead>
                                                <TableHead>성향</TableHead>
                                                <TableHead>재위 기간</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {emperorHistory.map((row, idx) => (
                                                <TableRow key={`${row.nationName}-${row.emperorName}-${idx}`}>
                                                    <TableCell>{row.nationName}</TableCell>
                                                    <TableCell>{row.emperorName}</TableCell>
                                                    <TableCell>{getNationTypeLabel(row.typeCode)}</TableCell>
                                                    <TableCell className="text-xs text-muted-foreground">
                                                        {row.reign}
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </div>
                            </div>
                        )}
                    </CardContent>
                )}
            </Card>

            {/* Season Records */}
            <Card>
                <CardHeader className="pb-2 cursor-pointer" onClick={() => setShowRecords(!showRecords)}>
                    <CardTitle className="text-base flex items-center gap-2">
                        <Trophy className="size-4" />
                        시즌 기록 ({records.length}건)
                        {showRecords ? (
                            <ChevronDown className="size-4 ml-auto" />
                        ) : (
                            <ChevronRight className="size-4 ml-auto" />
                        )}
                    </CardTitle>
                </CardHeader>
                {showRecords && (
                    <CardContent>
                        {records.length === 0 ? (
                            <p className="text-sm text-muted-foreground">기록 데이터가 없습니다.</p>
                        ) : (
                            <div className="space-y-3">
                                {Array.from(recordsByType.entries()).map(([type, msgs]) => (
                                    <div key={type}>
                                        <h4 className="text-sm font-semibold mb-1 text-amber-400">{type}</h4>
                                        <div className="space-y-1">
                                            {msgs.map((msg) => {
                                                const raw = msg.payload?.content;
                                                const text =
                                                    typeof raw === 'string' ? raw : JSON.stringify(msg.payload ?? {});
                                                return (
                                                    <div
                                                        key={msg.id}
                                                        className="rounded border border-muted/20 p-2 text-xs"
                                                    >
                                                        {formatGameLogDate(msg) && (
                                                            <span className="text-muted-foreground mr-2">
                                                                [{formatGameLogDate(msg)}]
                                                            </span>
                                                        )}
                                                        {formatLog(text)}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </CardContent>
                )}
            </Card>

            {/* Yearbook Viewer */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-base">연감 조회</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <div className="flex items-center gap-2">
                        <input
                            type="number"
                            min={1}
                            placeholder="연도 입력"
                            className="h-8 w-24 rounded border border-input bg-background px-2 text-sm"
                            onChange={(e) => {
                                const v = parseInt(e.target.value);
                                if (!isNaN(v) && v > 0) setSelectedYear(v);
                            }}
                        />
                        <Button
                            size="sm"
                            variant="outline"
                            disabled={yearbookLoading || !selectedYear}
                            onClick={() => selectedYear && loadYearbook(selectedYear)}
                        >
                            조회
                        </Button>
                    </div>
                    {yearbookLoading && <LoadingState />}
                    {yearbook && (
                        <div className="space-y-2">
                            <h4 className="text-sm font-semibold">
                                {yearbook.year}년 {yearbook.month}월 현황
                            </h4>
                            <div className="overflow-x-auto">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>국가</TableHead>
                                            <TableHead className="text-right">속령</TableHead>
                                            <TableHead className="text-right">장수</TableHead>
                                            <TableHead>도시</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {yearbook.nations.map((yn) => (
                                            <TableRow key={yn.id}>
                                                <TableCell>
                                                    <NationBadge name={yn.name} color={yn.color} />
                                                </TableCell>
                                                <TableCell className="text-right">{yn.territoryCount}</TableCell>
                                                <TableCell className="text-right">{yn.generalCount ?? '-'}</TableCell>
                                                <TableCell className="text-xs text-muted-foreground">
                                                    {yn.cities.join(', ')}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>
                            <div className="space-y-2">
                                <h5 className="text-xs font-semibold text-amber-400">중원 정세</h5>
                                {yearbook.globalHistory.length === 0 ? (
                                    <div className="text-xs text-muted-foreground">기록 없음</div>
                                ) : (
                                    yearbook.globalHistory.map((text) => (
                                        <div key={`yh-${text}`} className="rounded border border-muted/20 p-2 text-xs">
                                            {formatLog(text)}
                                        </div>
                                    ))
                                )}
                            </div>
                            <div className="space-y-2">
                                <h5 className="text-xs font-semibold text-amber-400">장수 동향</h5>
                                {yearbook.globalAction.length === 0 ? (
                                    <div className="text-xs text-muted-foreground">기록 없음</div>
                                ) : (
                                    yearbook.globalAction.map((text) => (
                                        <div key={`ya-${text}`} className="rounded border border-muted/20 p-2 text-xs">
                                            {formatLog(text)}
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}

function StatItem({ label, value }: { label: string; value: React.ReactNode }) {
    return (
        <div className="flex justify-between">
            <span className="text-muted-foreground">{label}</span>
            <span>{value}</span>
        </div>
    );
}

function BarSection({
    title,
    stats,
    field,
    max,
}: {
    title: string;
    stats: { id: number; name: string; color: string; [k: string]: unknown }[];
    field: string;
    max: number;
}) {
    return (
        <div>
            <h4 className="text-xs font-semibold mb-1 text-muted-foreground">{title}</h4>
            <div className="space-y-1">
                {stats.map((n) => {
                    const val = (n[field] as number) ?? 0;
                    const pct = (val / max) * 100;
                    return (
                        <div key={n.id} className="flex items-center gap-2 text-xs">
                            <span className="w-16 text-right shrink-0 truncate">{n.name}</span>
                            <div className="flex-1 h-4 bg-muted/20 rounded overflow-hidden">
                                <div
                                    className="h-full rounded transition-all"
                                    style={{
                                        width: `${pct}%`,
                                        backgroundColor: n.color || '#666',
                                    }}
                                />
                            </div>
                            <span className="w-16 text-right tabular-nums">{val.toLocaleString()}</span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
