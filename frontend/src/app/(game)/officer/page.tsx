'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { planetApi, frontApi, officerApi, historyApi, factionApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import type { StarSystem, Officer, OfficerFrontInfo, LastTurnInfo, Message, Faction } from '@/types';
import { User, Users, Swords } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { OfficerPortrait } from '@/components/game/officer-portrait';
import { FactionBadge } from '@/components/game/faction-badge';
import { LoghBar } from '@/components/game/logh-bar';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
    formatOfficerLevelText,
    formatInjury,
    formatGeneralTypeCall,
    formatDexLevel,
    formatHonor,
    SHIP_CLASS_NAMES,
    numberWithCommas,
    ageColor,
    getNPCColor,
    getPersonalityName,
} from '@/lib/game-utils';

// Fleet specialization names (formerly DEX names for troop types)
const DEX_NAMES = ['전함', '순양함', '구축함', '항공모함', '수송함'];

const EQUIPMENT_KEYS: Array<{ key: keyof OfficerFrontInfo; label: string }> = [
    { key: 'flagship', label: '기함' },
    { key: 'equipment', label: '특수장비' },
    { key: 'engine', label: '기관' },
    { key: 'accessory', label: '부속품' },
];

type TabKey = 'profile' | 'nation-generals';

export default function GeneralPage() {
    const router = useRouter();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer, loading: myOfficerLoading, fetchMyGeneral } = useOfficerStore();
    const [frontInfo, setFrontInfo] = useState<OfficerFrontInfo | null>(null);
    const [faction, setFaction] = useState<Faction | null>(null);
    const [starSystem, setStarSystem] = useState<StarSystem | null>(null);
    const [records, setRecords] = useState<Message[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<TabKey>('profile');
    const [factionOfficers, setFactionOfficers] = useState<Officer[]>([]);
    const [factionOfficersLoading, setFactionOfficersLoading] = useState(false);
    const [factionStarSystems, setFactionStarSystems] = useState<StarSystem[]>([]);

    useEffect(() => {
        if (!currentWorld) return;
        fetchMyGeneral(currentWorld.id).catch(() => {
            setError('제독 정보를 불러올 수 없습니다.');
        });
    }, [currentWorld, fetchMyGeneral]);

    const loadGeneralData = useCallback(async () => {
        if (!currentWorld || !myOfficer) return;

        const starSystemId = myOfficer.starSystemId ?? myOfficer.cityId;
        const factionId = myOfficer.factionId ?? myOfficer.nationId;

        const starSystemPromise =
            starSystemId > 0
                ? planetApi
                      .get(starSystemId)
                      .then((res) => res.data)
                      .catch(() => null)
                : Promise.resolve(null);
        const factionPromise =
            factionId > 0
                ? factionApi
                      .get(factionId)
                      .then((res) => res.data)
                      .catch(() => null)
                : Promise.resolve(null);

        try {
            const [officerFront, history, starSystemData, factionData] = await Promise.all([
                frontApi.getInfo(currentWorld.id).then((res) => res.data.general ?? res.data.officer),
                historyApi.getGeneralRecords(myOfficer.id).then((res) => res.data),
                starSystemPromise,
                factionPromise,
            ]);
            setFrontInfo(officerFront);
            setRecords(history);
            setStarSystem(starSystemData);
            setFaction(factionData);
        } catch {
            setError('나의 제독 정보를 불러오지 못했습니다.');
        }
    }, [currentWorld, myOfficer]);

    useEffect(() => {
        loadGeneralData();
    }, [loadGeneralData]);

    const loadFactionOfficers = useCallback(async () => {
        const factionId = myOfficer?.factionId ?? myOfficer?.nationId;
        if (!factionId || factionId <= 0) return;
        setFactionOfficersLoading(true);
        try {
            const [genRes, cityRes] = await Promise.all([
                officerApi.listByFaction(factionId),
                planetApi.listByFaction(factionId),
            ]);
            setFactionOfficers(genRes.data);
            setFactionStarSystems(cityRes.data);
        } catch {
            // silent
        } finally {
            setFactionOfficersLoading(false);
        }
    }, [myOfficer?.factionId, myOfficer?.nationId]);

    useEffect(() => {
        if (activeTab === 'nation-generals') {
            loadFactionOfficers();
        }
    }, [activeTab, loadFactionOfficers]);

    useEffect(() => {
        if (!currentWorld || !myOfficer) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            fetchMyGeneral(currentWorld.id).catch(() => {});
            loadGeneralData();
        });
    }, [currentWorld, myOfficer, fetchMyGeneral, loadGeneralData]);

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
    if (!myOfficer) return <LoadingState message="제독 정보가 없습니다." />;

    const g = myOfficer;
    const fi = frontInfo;
    const factionRank = faction?.faction_rank ?? 0;
    const commandName = getCurrentCommandName(g.lastTurn);
    const commandTarget = getCurrentCommandTarget(g.lastTurn, starSystem?.name);
    const commandEta = formatEta(g.commandEndTime);
    const officerText = formatOfficerLevelText(
        g.officerLevel,
        factionRank,
        g.factionId > 0 || g.nationId > 0,
        faction?.faction_type,
        g.npcState
    );
    const injuryInfo = formatInjury(g.injury);
    const typeCall = formatGeneralTypeCall(g.leadership, g.command, g.intelligence);
    const honorText = formatHonor(g.experience);
    const npcColor = getNPCColor(g.npcState);
    const equipmentValues = [
        fi?.flagship ?? g.flagshipCode,
        fi?.equipment ?? g.equipCode,
        fi?.engine ?? g.engineCode,
        fi?.accessory ?? g.accessoryCode,
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
            <PageHeader icon={User} title="나의 제독" />

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
                {(g.factionId > 0 || g.nationId > 0) && (
                    <Button
                        variant={activeTab === 'nation-generals' ? 'default' : 'ghost'}
                        size="sm"
                        onClick={() => setActiveTab('nation-generals')}
                    >
                        <Users className="size-4 mr-1" />
                        소속 진영 제독 목록
                    </Button>
                )}
            </div>

            {activeTab === 'nation-generals' ? (
                <FactionOfficersList
                    officers={factionOfficers}
                    starSystems={factionStarSystems}
                    faction={faction}
                    loading={factionOfficersLoading}
                    onOfficerClick={(id) => router.push(`/officers/${id}`)}
                />
            ) : (
                <>
                    {/* Profile + Basic Info */}
                    <Card>
                        <CardContent className="pt-6 space-y-4">
                            <div className="flex gap-4 items-start">
                                <OfficerPortrait picture={g.picture} name={g.name} size="lg" />
                                <div className="space-y-1 flex-1">
                                    <p className="text-lg font-bold" style={{ color: npcColor }}>
                                        {g.name}
                                    </p>
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <FactionBadge name={faction?.name} color={faction?.color} />
                                        <Badge variant="outline">{officerText}</Badge>
                                    </div>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">유형:</span>{' '}
                                        <span className="text-yellow-400">{typeCall}</span>
                                    </p>
                                    <p className="text-sm">
                                        <span className="text-muted-foreground">위치:</span>{' '}
                                        {starSystem?.name ?? '성계 미상'}
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
                        {/* 8-Stat with bars */}
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
                                        value: g.command,
                                        exp: g.commandExp,
                                        color: 'orange',
                                    },
                                    {
                                        label: '정보',
                                        value: g.intelligence,
                                        exp: g.intelligenceExp,
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
                                        value: g.administration,
                                        exp: 0,
                                        color: 'mediumpurple',
                                    },
                                    {
                                        label: '기동',
                                        value: g.mobility,
                                        exp: g.mobilityExp,
                                        color: 'cyan',
                                    },
                                    {
                                        label: '공격',
                                        value: g.attack,
                                        exp: g.attackExp,
                                        color: '#ff6b6b',
                                    },
                                    {
                                        label: '방어',
                                        value: g.defense,
                                        exp: g.defenseExp,
                                        color: '#74c0fc',
                                    },
                                ].map((s) => (
                                    <div key={s.label} className="flex items-center gap-2">
                                        <span className="w-8 text-xs text-right" style={{ color: s.color }}>
                                            {s.label}
                                        </span>
                                        <span className="w-6 text-xs text-right font-mono">{s.value}</span>
                                        <div className="flex-1">
                                            <LoghBar height={7} percent={s.value} altText={`${s.value}/100`} />
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

                        {/* Fleet / Resources */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">함대 / 자원</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                                    <div>
                                        <span className="text-muted-foreground">함선:</span>{' '}
                                        <span>{g.ships.toLocaleString()}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">함종:</span>{' '}
                                        <span className="text-cyan-300">
                                            {SHIP_CLASS_NAMES[g.shipClass] ?? g.shipClass}
                                        </span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">훈련:</span>{' '}
                                        <span className={g.training >= 80 ? 'text-cyan-400' : ''}>{g.training}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">사기:</span>{' '}
                                        <span className={g.morale >= 80 ? 'text-cyan-400' : ''}>{g.morale}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">자금:</span>{' '}
                                        <span className="text-yellow-400">{numberWithCommas(g.funds)}</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">물자:</span>{' '}
                                        <span className="text-green-400">{numberWithCommas(g.supplies)}</span>
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

                    {/* Fleet Proficiency (함종 숙련도) */}
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm">함종 숙련도</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-1.5">
                            {DEX_NAMES.map((name, i) => {
                                const dex = dexValues[i];
                                const info = formatDexLevel(dex);
                                return (
                                    <div key={name} className="flex items-center gap-2">
                                        <span className="w-12 text-xs text-muted-foreground">{name}</span>
                                        <span
                                            className="w-8 text-xs font-mono text-right"
                                            style={{ color: info.color }}
                                        >
                                            {info.name}
                                        </span>
                                        <div className="flex-1">
                                            <LoghBar
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
                                    <span className="text-muted-foreground">적함 격침:</span>{' '}
                                    <span className="text-yellow-400 font-mono">{numberWithCommas(killcrew)}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">아군 피해:</span>{' '}
                                    <span className="text-red-300 font-mono">{numberWithCommas(deathcrew)}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">격침률:</span>{' '}
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
                            <CardTitle className="text-sm">제독 열전</CardTitle>
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

function FactionOfficersList({
    officers,
    starSystems,
    faction,
    loading,
    onOfficerClick,
}: {
    officers: Officer[];
    starSystems: StarSystem[];
    faction: Faction | null;
    loading: boolean;
    onOfficerClick: (id: number) => void;
}) {
    const starSystemMap = useMemo(() => new Map(starSystems.map((s) => [s.id, s])), [starSystems]);

    const sorted = useMemo(
        () =>
            [...officers].sort((a, b) => {
                if (b.officerLevel !== a.officerLevel) return b.officerLevel - a.officerLevel;
                return a.name.localeCompare(b.name);
            }),
        [officers]
    );

    if (loading) return <LoadingState message="진영 제독 목록 로딩 중..." />;
    if (officers.length === 0) {
        return (
            <Card>
                <CardContent className="py-8 text-center text-muted-foreground">
                    진영에 소속된 제독이 없습니다.
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <Users className="size-4" />
                    {faction?.name ?? '진영'} 제독 목록 ({officers.length}명)
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
                                <TableHead>성계</TableHead>
                                <TableHead className="text-center">Lv</TableHead>
                                <TableHead className="text-center">통</TableHead>
                                <TableHead className="text-center">지휘</TableHead>
                                <TableHead className="text-center">정보</TableHead>
                                <TableHead className="text-center">정</TableHead>
                                <TableHead className="text-center">운영</TableHead>
                                <TableHead className="text-center">함선</TableHead>
                                <TableHead className="text-center">함종</TableHead>
                                <TableHead>특기</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {sorted.map((g) => {
                                const npcColor = getNPCColor(g.npcState);
                                const factionRank = faction?.faction_rank;
                                const starSystemId = g.starSystemId ?? g.cityId;
                                const starSystemName = starSystemMap.get(starSystemId)?.name ?? `#${starSystemId}`;
                                const officerText = formatOfficerLevelText(
                                    g.officerLevel,
                                    factionRank,
                                    (g.factionId ?? g.nationId) > 0
                                );
                                return (
                                    <TableRow
                                        key={g.id}
                                        className="cursor-pointer hover:bg-muted/50"
                                        onClick={() => onOfficerClick(g.id)}
                                    >
                                        <TableCell className="p-1">
                                            <OfficerPortrait picture={g.picture} name={g.name} size="sm" />
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
                                        <TableCell className="text-xs">{starSystemName}</TableCell>
                                        <TableCell className="text-center">{g.expLevel}</TableCell>
                                        <TableCell className="text-center">{g.leadership}</TableCell>
                                        <TableCell className="text-center">{g.command}</TableCell>
                                        <TableCell className="text-center">{g.intelligence}</TableCell>
                                        <TableCell className="text-center">{g.politics}</TableCell>
                                        <TableCell className="text-center">{g.administration}</TableCell>
                                        <TableCell className="text-center text-xs">
                                            {numberWithCommas(g.ships)}
                                        </TableCell>
                                        <TableCell className="text-center text-xs text-cyan-300">
                                            {SHIP_CLASS_NAMES[g.shipClass] ?? g.shipClass}
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

function getCurrentCommandName(lastTurn: LastTurnInfo): string {
    if (lastTurn.command && lastTurn.command.length > 0) {
        return lastTurn.command;
    }
    return '대기';
}

function getCurrentCommandTarget(lastTurn: LastTurnInfo, currentStarSystemName: string | undefined): string {
    const arg = lastTurn.arg;
    const targetCity = arg?.destCityId;
    if (typeof targetCity === 'number') return `성계 #${targetCity}`;
    if (typeof targetCity === 'string' && targetCity.length > 0) {
        return `성계 #${targetCity}`;
    }
    return currentStarSystemName ?? '-';
}

function formatEta(commandEndTime: string | null): string {
    if (!commandEndTime) return '즉시';
    const eta = new Date(commandEndTime);
    if (Number.isNaN(eta.getTime())) return '-';
    return eta.toLocaleString('ko-KR');
}
