'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { cityApi, frontApi, generalApi, historyApi, nationApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import type { City, General, GeneralFrontInfo, LastTurnInfo, Message, Nation } from '@/types';
import { User, Users, Swords } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { NationBadge } from '@/components/game/nation-badge';
import { SammoBar } from '@/components/game/sammo-bar';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/8bit/table';
import {
    formatOfficerLevelText,
    formatInjury,
    formatGeneralTypeCall,
    formatDexLevel,
    formatHonor,
    CREW_TYPE_NAMES,
    numberWithCommas,
    ageColor,
    getNPCColor,
    getPersonalityName,
} from '@/lib/game-utils';

const DEX_NAMES = ['보병', '궁병', '기병', '귀병', '차병'];

const EQUIPMENT_KEYS: Array<{ key: keyof GeneralFrontInfo; label: string }> = [
    { key: 'weapon', label: '무기' },
    { key: 'book', label: '서적' },
    { key: 'horse', label: '군마' },
    { key: 'item', label: '도구' },
];

type TabKey = 'profile' | 'nation-generals';

export default function GeneralPage() {
    const router = useRouter();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer, loading: myOfficerLoading, fetchMyOfficer } = useOfficerStore();
    const [frontInfo, setFrontInfo] = useState<GeneralFrontInfo | null>(null);
    const [nation, setNation] = useState<Nation | null>(null);
    const [city, setCity] = useState<City | null>(null);
    const [records, setRecords] = useState<Message[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<TabKey>('profile');
    const [nationGenerals, setNationGenerals] = useState<General[]>([]);
    const [nationGeneralsLoading, setNationGeneralsLoading] = useState(false);
    const [nationCities, setNationCities] = useState<City[]>([]);

    useEffect(() => {
        if (!currentWorld) return;
        fetchMyOfficer(currentWorld.id).catch(() => {
            setError('장교 정보를 불러올 수 없습니다.');
        });
    }, [currentWorld, fetchMyOfficer]);

    const loadGeneralData = useCallback(async () => {
        if (!currentWorld || !myOfficer) return;

        const cityPromise =
            myOfficer.cityId > 0
                ? cityApi
                      .get(myOfficer.cityId)
                      .then((res) => res.data)
                      .catch(() => null)
                : Promise.resolve(null);
        const nationPromise =
            myOfficer.nationId > 0
                ? nationApi
                      .get(myOfficer.nationId)
                      .then((res) => res.data)
                      .catch(() => null)
                : Promise.resolve(null);

        try {
            const [generalFront, history, cityData, nationData] = await Promise.all([
                frontApi.getInfo(currentWorld.id).then((res) => res.data.general),
                historyApi.getGeneralRecords(myOfficer.id).then((res) => res.data),
                cityPromise,
                nationPromise,
            ]);
            setFrontInfo(generalFront);
            setRecords(history);
            setCity(cityData);
            setNation(nationData);
        } catch {
            setError('나의 장교 정보를 불러오지 못했습니다.');
        }
    }, [currentWorld, myOfficer]);

    useEffect(() => {
        loadGeneralData();
    }, [loadGeneralData]);

    const loadNationGenerals = useCallback(async () => {
        if (!myOfficer?.nationId || myOfficer.nationId <= 0) return;
        setNationGeneralsLoading(true);
        try {
            const [genRes, cityRes] = await Promise.all([
                generalApi.listByNation(myOfficer.nationId),
                cityApi.listByNation(myOfficer.nationId),
            ]);
            setNationGenerals(genRes.data);
            setNationCities(cityRes.data);
        } catch {
            // silent
        } finally {
            setNationGeneralsLoading(false);
        }
    }, [myOfficer?.nationId]);

    useEffect(() => {
        if (activeTab === 'nation-generals') {
            loadNationGenerals();
        }
    }, [activeTab, loadNationGenerals]);

    useEffect(() => {
        if (!currentWorld || !myOfficer) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            fetchMyOfficer(currentWorld.id).catch(() => {});
            loadGeneralData();
        });
    }, [currentWorld, myOfficer, fetchMyOfficer, loadGeneralData]);

    const biographyRows = useMemo(
        () =>
            records
                .map((record) => {
                    const content = record.payload.content;
                    const text = typeof content === 'string' ? content : JSON.stringify(record.payload);
                    return {
                        id: record.id,
                        sentAt: record.sentAt,
                        text,
                    };
                })
                .slice(0, 20),
        [records]
    );

    if (!currentWorld) return <LoadingState message="월드를 선택해주세요." />;
    if (myOfficerLoading || (myOfficer && !frontInfo && !error)) {
        return <LoadingState />;
    }
    if (error) return <div className="p-4 text-red-400">{error}</div>;
    if (!myOfficer) return <LoadingState message="장교 정보가 없습니다." />;

    const g = myOfficer;
    const fi = frontInfo;
    const nationLevel = nation?.level ?? 0;
    const commandName = getCurrentCommandName(g.lastTurn);
    const commandTarget = getCurrentCommandTarget(g.lastTurn, city?.name);
    const commandEta = formatEta(g.commandEndTime);
    const officerText = formatOfficerLevelText(
        g.officerLevel,
        nationLevel,
        g.nationId > 0,
        nation?.typeCode,
        g.npcState
    );
    const injuryInfo = formatInjury(g.injury);
    const typeCall = formatGeneralTypeCall(g.leadership, g.strength, g.intel);
    const honorText = formatHonor(g.experience);
    const npcColor = getNPCColor(g.npcState);
    const equipmentValues = [
        fi?.weapon ?? g.weaponCode,
        fi?.book ?? g.bookCode,
        fi?.horse ?? g.horseCode,
        fi?.item ?? g.itemCode,
    ];

    const dexValues = [
        fi?.dex1 ?? g.dex1 ?? 0,
        fi?.dex2 ?? g.dex2 ?? 0,
        fi?.dex3 ?? g.dex3 ?? 0,
        fi?.dex4 ?? g.dex4 ?? 0,
        fi?.dex5 ?? g.dex5 ?? 0,
    ];

    // Battle stats
    const warnum = fi?.warnum ?? g.warnum ?? 0;
    const killnum = fi?.killnum ?? g.killnum ?? 0;
    const deathnum = fi?.deathnum ?? g.deathnum ?? 0;
    const killcrew = fi?.killcrew ?? g.killcrew ?? 0;
    const deathcrew = fi?.deathcrew ?? g.deathcrew ?? 0;
    const firenum = fi?.firenum ?? g.firenum ?? 0;
    const winRate = warnum > 0 ? ((killnum / warnum) * 100).toFixed(1) : '0.0';
    const killRate = deathcrew > 0 ? ((killcrew / Math.max(deathcrew, 1)) * 100).toFixed(1) : '0.0';

    return (
        <div className="p-4 space-y-4 max-w-4xl mx-auto">
            <PageHeader icon={User} title="나의 장교" />

            {/* Tab selector */}
            <div className="flex gap-1 border-b pb-1">
                <Button
                    variant={activeTab === 'profile' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => setActiveTab('profile')}
                >
                    <User className="size-4 mr-1" />
                    개인 프로필
                </Button>
                {myOfficer.nationId > 0 && (
                    <Button
                        variant={activeTab === 'nation-generals' ? 'default' : 'ghost'}
                        size="sm"
                        onClick={() => setActiveTab('nation-generals')}
                    >
                        <Users className="size-4 mr-1" />
                        소속 세력 장교 목록
                    </Button>
                )}
            </div>

            {activeTab === 'nation-generals' ? (
                <NationGeneralsList
                    generals={nationGenerals}
                    cities={nationCities}
                    nation={nation}
                    loading={nationGeneralsLoading}
                    onGeneralClick={(id) => router.push(`/generals/${id}`)}
                />
            ) : (
                <>
                    {/* Profile + Basic Info */}
                    <Card>
                        <CardContent className="pt-6 space-y-4">
                            <div className="flex gap-4 items-start">
                                <GeneralPortrait picture={g.picture} name={g.name} size="lg" />
                                <div className="space-y-1 flex-1">
                                    <p className="text-lg font-bold" style={{ color: npcColor }}>
                                        {g.name}
                                    </p>
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <NationBadge name={nation?.name} color={nation?.color} />
                                        <Badge variant="outline">{officerText}</Badge>
                                    </div>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">유형:</span>{' '}
                                        <span className="text-yellow-400">{typeCall}</span>
                                    </p>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">위치:</span> {city?.name ?? '행성 미상'}
                                    </p>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">나이:</span>{' '}
                                        <span
                                            style={{
                                                color: ageColor(g.age, g.deadYear - g.bornYear),
                                            }}
                                        >
                                            {g.age}세
                                        </span>
                                    </p>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">상태:</span>{' '}
                                        <span style={{ color: injuryInfo.color }}>{injuryInfo.text}</span>
                                        {g.injury > 0 && <span className="text-red-400 ml-1">({g.injury}%)</span>}
                                    </p>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">명성:</span>{' '}
                                        <span className="text-yellow-400">{honorText}</span>
                                        <span className="text-muted-foreground ml-2">
                                            ({g.experience.toLocaleString()})
                                        </span>
                                    </p>
                                    {g.belong != null && g.belong > 0 && (
                                        <p className="text-sm">
                                            <span className="text-muted-foreground">소속턴:</span>{' '}
                                            <span>{g.belong}턴</span>
                                        </p>
                                    )}
                                    {g.betray != null && g.betray > 0 && (
                                        <p className="text-sm">
                                            <span className="text-muted-foreground">배반:</span>{' '}
                                            <span className="text-red-400">{g.betray}회</span>
                                        </p>
                                    )}
                                </div>
                            </div>
                        </CardContent>
                    </Card>

                    <div className="grid gap-4 md:grid-cols-2">
                        {/* 5-Stat with bars */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">능력치</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-1.5">
                                {[
                                    {
                                        label: '통솔',
                                        value: g.leadership,
                                        exp: g.leadershipExp,
                                        color: 'red',
                                    },
                                    {
                                        label: '지휘',
                                        value: g.strength,
                                        exp: g.strengthExp,
                                        color: 'orange',
                                    },
                                    {
                                        label: '정보',
                                        value: g.intel,
                                        exp: g.intelExp,
                                        color: 'dodgerblue',
                                    },
                                    {
                                        label: '정치',
                                        value: g.politics,
                                        exp: 0,
                                        color: 'limegreen',
                                    },
                                    {
                                        label: '운영',
                                        value: g.charm,
                                        exp: 0,
                                        color: 'mediumpurple',
                                    },
                                ].map((s) => (
                                    <div key={s.label} className="flex items-center gap-2">
                                        <span className="w-8 text-xs text-right" style={{ color: s.color }}>
                                            {s.label}
                                        </span>
                                        <span className="w-6 text-xs text-right font-mono">{s.value}</span>
                                        <div className="flex-1">
                                            <SammoBar height={7} percent={s.value} altText={`${s.value}/100`} />
                                        </div>
                                        {s.exp > 0 && (
                                            <span className="text-[10px] text-yellow-500 w-10 text-right">
                                                +{s.exp}
                                            </span>
                                        )}
                                    </div>
                                ))}
                            </CardContent>
                        </Card>

                        {/* Special / Personality / Equipment */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">특성 / 장비</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-3">
                                <div className="flex flex-wrap gap-1">
                                    <Badge variant={g.specialCode === 'None' ? 'outline' : 'secondary'}>
                                        특기: {g.specialCode === 'None' ? '없음' : g.specialCode}
                                    </Badge>
                                    <Badge variant={g.special2Code === 'None' ? 'outline' : 'secondary'}>
                                        특기2: {g.special2Code === 'None' ? '없음' : g.special2Code}
                                    </Badge>
                                    <Badge variant="secondary">성격: {getPersonalityName(g.personalCode)}</Badge>
                                </div>
                                <div className="grid grid-cols-2 gap-2 text-sm">
                                    {EQUIPMENT_KEYS.map((entry, index) => {
                                        const val = equipmentValues[index];
                                        const hasItem = val && val !== 'None' && val !== '';
                                        return (
                                            <div key={entry.key} className="flex items-center justify-between">
                                                <span className="text-muted-foreground">{entry.label}</span>
                                                <span className={hasItem ? 'text-cyan-300' : 'text-gray-500'}>
                                                    {hasItem ? val : '-'}
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>
                            </CardContent>
                        </Card>

                        {/* Military */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">군사 / 자원</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                                    <div>
                                        <span className="text-muted-foreground">병사:</span>{' '}
                                        <span>{g.crew.toLocaleString()}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">함종:</span>{' '}
                                        <span className="text-cyan-300">
                                            {CREW_TYPE_NAMES[g.crewType] ?? g.crewType}
                                        </span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">훈련:</span>{' '}
                                        <span className={g.train >= 80 ? 'text-cyan-400' : ''}>{g.train}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">사기:</span>{' '}
                                        <span className={g.atmos >= 80 ? 'text-cyan-400' : ''}>{g.atmos}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">자금:</span>{' '}
                                        <span className="text-yellow-400">{numberWithCommas(g.gold)}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">군량:</span>{' '}
                                        <span className="text-green-400">{numberWithCommas(g.rice)}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">레벨:</span> <span>{g.expLevel}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">계급:</span>{' '}
                                        <span>
                                            Lv.{g.dedLevel ?? 0} ({g.dedication})
                                        </span>
                                    </div>
                                </div>
                            </CardContent>
                        </Card>

                        {/* Current Command */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">현재 명령</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2 text-sm">
                                <div className="flex items-center justify-between">
                                    <span className="text-muted-foreground">명령</span>
                                    <span>{commandName}</span>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-muted-foreground">목표</span>
                                    <span>{commandTarget}</span>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-muted-foreground">완료 예정</span>
                                    <span>{commandEta}</span>
                                </div>
                            </CardContent>
                        </Card>
                    </div>

                    {/* Proficiency (숙련도) */}
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm">숙련도</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-1.5">
                            {DEX_NAMES.map((name, i) => {
                                const dex = dexValues[i];
                                const info = formatDexLevel(dex);
                                return (
                                    <div key={name} className="flex items-center gap-2">
                                        <span className="w-8 text-xs text-muted-foreground">{name}</span>
                                        <span
                                            className="w-8 text-xs font-mono text-right"
                                            style={{ color: info.color }}
                                        >
                                            {info.name}
                                        </span>
                                        <div className="flex-1">
                                            <SammoBar
                                                height={7}
                                                percent={Math.min((info.level / 26) * 100, 100)}
                                                altText={`${info.name} (${numberWithCommas(dex)})`}
                                            />
                                        </div>
                                        <span className="text-[10px] text-muted-foreground w-14 text-right">
                                            {numberWithCommas(dex)}
                                        </span>
                                    </div>
                                );
                            })}
                        </CardContent>
                    </Card>

                    {/* Battle Stats */}
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm flex items-center gap-2">
                                <Swords className="size-4" />
                                전투 통계
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-2 text-sm">
                                <div>
                                    <span className="text-muted-foreground">전투 횟수:</span>{' '}
                                    <span className="font-mono">{warnum}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">승리:</span>{' '}
                                    <span className="text-cyan-400 font-mono">{killnum}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">패배:</span>{' '}
                                    <span className="text-red-400 font-mono">{deathnum}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">승률:</span>{' '}
                                    <span className={Number(winRate) >= 50 ? 'text-cyan-400' : 'text-orange-400'}>
                                        {winRate}%
                                    </span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">적 사살:</span>{' '}
                                    <span className="text-yellow-400 font-mono">{numberWithCommas(killcrew)}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">아군 피해:</span>{' '}
                                    <span className="text-red-300 font-mono">{numberWithCommas(deathcrew)}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">살상률:</span>{' '}
                                    <span className={Number(killRate) >= 100 ? 'text-cyan-400' : 'text-orange-400'}>
                                        {killRate}%
                                    </span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">계략 성공:</span>{' '}
                                    <span className="text-green-400 font-mono">{firenum}</span>
                                </div>
                            </div>
                        </CardContent>
                    </Card>

                    {/* Biography */}
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-sm">장교 열전</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-2">
                            {biographyRows.length === 0 ? (
                                <div className="text-sm text-muted-foreground">기록이 없습니다.</div>
                            ) : (
                                biographyRows.map((row) => (
                                    <div key={row.id} className="rounded border p-2 text-sm">
                                        <div className="break-all">{row.text}</div>
                                    </div>
                                ))
                            )}
                        </CardContent>
                    </Card>
                </>
            )}
        </div>
    );
}

function NationGeneralsList({
    generals,
    cities,
    nation,
    loading,
    onGeneralClick,
}: {
    generals: General[];
    cities: City[];
    nation: Nation | null;
    loading: boolean;
    onGeneralClick: (id: number) => void;
}) {
    const cityMap = useMemo(() => new Map(cities.map((c) => [c.id, c])), [cities]);

    const sorted = useMemo(
        () =>
            [...generals].sort((a, b) => {
                // Sort by officer level desc, then by name
                if (b.officerLevel !== a.officerLevel) return b.officerLevel - a.officerLevel;
                return a.name.localeCompare(b.name);
            }),
        [generals]
    );

    if (loading) return <LoadingState message="세력 장교 목록 로딩 중..." />;
    if (generals.length === 0) {
        return (
            <Card>
                <CardContent className="py-8 text-center text-muted-foreground">
                    세력에 소속된 장교가 없습니다.
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <Users className="size-4" />
                    {nation?.name ?? '세력'} 장교 목록 ({generals.length}명)
                </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
                <div className="overflow-x-auto">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-10"></TableHead>
                                <TableHead>이름</TableHead>
                                <TableHead>관직</TableHead>
                                <TableHead>행성</TableHead>
                                <TableHead className="text-center">Lv</TableHead>
                                <TableHead className="text-center">통</TableHead>
                                <TableHead className="text-center">무</TableHead>
                                <TableHead className="text-center">지</TableHead>
                                <TableHead className="text-center">정</TableHead>
                                <TableHead className="text-center">매</TableHead>
                                <TableHead className="text-center">병사</TableHead>
                                <TableHead className="text-center">함종</TableHead>
                                <TableHead>특기</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {sorted.map((g) => {
                                const npcColor = getNPCColor(g.npcState);
                                const cityName = cityMap.get(g.cityId)?.name ?? `#${g.cityId}`;
                                const officerText = formatOfficerLevelText(
                                    g.officerLevel,
                                    nation?.level,
                                    g.nationId > 0
                                );
                                return (
                                    <TableRow
                                        key={g.id}
                                        className="cursor-pointer hover:bg-muted/50"
                                        onClick={() => onGeneralClick(g.id)}
                                    >
                                        <TableCell className="p-1">
                                            <GeneralPortrait picture={g.picture} name={g.name} size="sm" />
                                        </TableCell>
                                        <TableCell className="font-medium" style={{ color: npcColor }}>
                                            {g.name}
                                            {g.npcState > 0 && (
                                                <Badge variant="secondary" className="ml-1 text-[10px] px-1">
                                                    NPC
                                                </Badge>
                                            )}
                                        </TableCell>
                                        <TableCell className="text-xs">{officerText}</TableCell>
                                        <TableCell className="text-xs">{cityName}</TableCell>
                                        <TableCell className="text-center">{g.expLevel}</TableCell>
                                        <TableCell className="text-center">{g.leadership}</TableCell>
                                        <TableCell className="text-center">{g.strength}</TableCell>
                                        <TableCell className="text-center">{g.intel}</TableCell>
                                        <TableCell className="text-center">{g.politics}</TableCell>
                                        <TableCell className="text-center">{g.charm}</TableCell>
                                        <TableCell className="text-center text-xs">
                                            {numberWithCommas(g.crew)}
                                        </TableCell>
                                        <TableCell className="text-center text-xs text-cyan-300">
                                            {CREW_TYPE_NAMES[g.crewType] ?? g.crewType}
                                        </TableCell>
                                        <TableCell className="text-xs">
                                            {g.specialCode !== 'None' ? g.specialCode : '-'}
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </div>
            </CardContent>
        </Card>
    );
}

function getRecord(value: Record<string, unknown>, key: string): Record<string, unknown> {
    const raw = value[key];
    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
        return raw as Record<string, unknown>;
    }
    return {};
}

function getCurrentCommandName(lastTurn: LastTurnInfo): string {
    if (lastTurn.command && lastTurn.command.length > 0) {
        return lastTurn.command;
    }
    return '대기';
}

function getCurrentCommandTarget(lastTurn: LastTurnInfo, currentCityName: string | undefined): string {
    const arg = lastTurn.arg;
    const targetCity = arg?.destCityId;
    if (typeof targetCity === 'number') return `행성 #${targetCity}`;
    if (typeof targetCity === 'string' && targetCity.length > 0) {
        return `행성 #${targetCity}`;
    }
    return currentCityName ?? '-';
}

function formatEta(commandEndTime: string | null): string {
    if (!commandEndTime) return '즉시';
    const eta = new Date(commandEndTime);
    if (Number.isNaN(eta.getTime())) return '-';
    return eta.toLocaleString('ko-KR');
}
