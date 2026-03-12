'use client';

import { Map as MapIcon } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { NationBadge } from '@/components/game/nation-badge';
import { MapViewer } from '@/components/game/map-viewer';
import type { City, Diplomacy, Nation } from '@/types';

const STATE_LABELS: Record<string, string> = {
    war: '전쟁',
    ceasefire: '휴전',
    ceasefire_proposal: '종전제의',
    alliance: '동맹',
    nonaggression: '불가침',
    neutral: '중립',
};

const STATE_COLORS: Record<string, string> = {
    war: '#dc2626',
    alliance: '#16a34a',
    nonaggression: '#2563eb',
    ceasefire: '#ca8a04',
    ceasefire_proposal: '#ca8a04',
    neutral: '#555',
};

const INFORMATIVE_STATE_CHAR: Record<string, string> = {
    war: '★',
    ceasefire: '△',
    ceasefire_proposal: '▽',
    alliance: '@',
    nonaggression: '●',
    neutral: 'ㆍ',
};

const NEUTRAL_STATE_CHAR: Record<string, string> = {
    war: '★',
    ceasefire: '△',
    ceasefire_proposal: '△',
    alliance: '@',
    nonaggression: 'ㆍ',
    neutral: 'ㆍ',
};

interface NationStat {
    nation: Nation;
    genCount: number;
    cityCount: number;
    totalPop: number;
    totalCrew: number;
}

export function GlobalDiplomacyOverview({
    worldId,
    nations,
    diplomacy,
    cities,
    myNationId,
    nationStats,
}: {
    worldId: number;
    nations: Nation[];
    diplomacy: Diplomacy[];
    cities: City[];
    myNationId?: number;
    nationStats: NationStat[];
}) {
    const activeDiplomacy = diplomacy.filter((entry) => !entry.isDead);
    const diplomacyLookup = new Map<string, string>();
    for (const entry of activeDiplomacy) {
        diplomacyLookup.set(`${entry.srcNationId}-${entry.destNationId}`, entry.stateCode);
        diplomacyLookup.set(`${entry.destNationId}-${entry.srcNationId}`, entry.stateCode);
    }
    const nationMap = new Map(nations.map((nation) => [nation.id, nation]));

    return (
        <div className="space-y-4 px-2">
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm">외교 관계도</CardTitle>
                </CardHeader>
                <CardContent>
                    <DiplomacyMatrix nations={nations} diplomacyLookup={diplomacyLookup} myNationId={myNationId} />
                </CardContent>
            </Card>

            {cities.length > 0 && (
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="flex items-center gap-1 text-sm">
                            <MapIcon className="size-3.5" />
                            세력 지도
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <MapViewer worldId={worldId} compact />
                    </CardContent>
                </Card>
            )}

            <ConflictAreaCard nations={nations} cities={cities} nationMap={nationMap} diplomacy={diplomacy} />

            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm">국력 비교</CardTitle>
                </CardHeader>
                <CardContent>
                    {nationStats.length === 0 ? (
                        <p className="text-xs text-muted-foreground">국가가 없습니다.</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-xs">
                                <thead>
                                    <tr className="border-b border-gray-700">
                                        <th className="px-2 py-1.5 text-left">국가</th>
                                        <th className="px-2 py-1.5 text-right">장수</th>
                                        <th className="px-2 py-1.5 text-right">도시</th>
                                        <th className="px-2 py-1.5 text-right">인구</th>
                                        <th className="px-2 py-1.5 text-right">병력</th>
                                        <th className="px-2 py-1.5 text-right">기술</th>
                                        <th className="px-2 py-1.5 text-right">국력</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {nationStats
                                        .slice()
                                        .sort((a, b) => b.nation.power - a.nation.power)
                                        .map(({ nation, genCount, cityCount, totalPop, totalCrew }) => (
                                            <tr key={nation.id} className="border-b border-gray-800 hover:bg-gray-900/50">
                                                <td className="px-2 py-1.5">
                                                    <NationBadge name={nation.name} color={nation.color} />
                                                </td>
                                                <td className="px-2 py-1.5 text-right">{genCount}</td>
                                                <td className="px-2 py-1.5 text-right">{cityCount}</td>
                                                <td className="px-2 py-1.5 text-right">{totalPop.toLocaleString()}</td>
                                                <td className="px-2 py-1.5 text-right">{totalCrew.toLocaleString()}</td>
                                                <td className="px-2 py-1.5 text-right">{nation.tech}</td>
                                                <td className="px-2 py-1.5 text-right font-bold">
                                                    {nation.power.toLocaleString()}
                                                </td>
                                            </tr>
                                        ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}

function DiplomacyMatrix({
    nations,
    diplomacyLookup,
    myNationId,
}: {
    nations: Nation[];
    diplomacyLookup: Map<string, string>;
    myNationId?: number;
}) {
    if (nations.length === 0) return <p className="text-xs text-muted-foreground">국가가 없습니다.</p>;

    return (
        <div className="overflow-x-auto">
            <table className="border-collapse text-xs">
                <thead>
                    <tr>
                        <th className="p-1" />
                        {nations.map((nation) => (
                            <th
                                key={nation.id}
                                className="p-1 text-center font-normal"
                                style={{ color: nation.color || undefined }}
                            >
                                <span className="[writing-mode:vertical-lr] whitespace-nowrap">{nation.name}</span>
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {nations.map((row) => (
                        <tr key={row.id}>
                            <td
                                className="whitespace-nowrap p-1 pr-2 text-right font-bold"
                                style={{ color: row.color || undefined }}
                            >
                                {row.name}
                            </td>
                            {nations.map((col) => {
                                if (row.id === col.id) {
                                    return (
                                        <td key={col.id} className="p-1 text-center" style={{ backgroundColor: '#222' }}>
                                            -
                                        </td>
                                    );
                                }
                                const state = diplomacyLookup.get(`${row.id}-${col.id}`);
                                const bg = state ? (STATE_COLORS[state] ?? '#555') : '#1a1a1a';
                                const label = state ? (STATE_LABELS[state] ?? state) : '';
                                const isMyRelation = myNationId != null && (row.id === myNationId || col.id === myNationId);
                                const charMap = isMyRelation ? INFORMATIVE_STATE_CHAR : NEUTRAL_STATE_CHAR;
                                const stateChar = state ? (charMap[state] ?? label.charAt(0)) : '';
                                return (
                                    <td
                                        key={col.id}
                                        className="border border-gray-800 p-1 text-center"
                                        style={{ backgroundColor: `${bg}33`, color: bg }}
                                        title={`${row.name} → ${col.name}: ${label || '중립'}`}
                                    >
                                        {stateChar ? <span className="font-bold">{stateChar}</span> : <span className="text-gray-600">-</span>}
                                    </td>
                                );
                            })}
                        </tr>
                    ))}
                </tbody>
            </table>
            <div className="mt-3 flex flex-wrap gap-3 text-xs">
                {Object.entries(STATE_LABELS).map(([code, label]) => (
                    <span key={code} className="flex items-center gap-1">
                        <span className="inline-block size-2.5 rounded-sm" style={{ backgroundColor: STATE_COLORS[code] ?? '#555' }} />
                        <span className="font-bold" style={{ color: STATE_COLORS[code] ?? '#555' }}>
                            {INFORMATIVE_STATE_CHAR[code] ?? ''}
                        </span>
                        {label}
                    </span>
                ))}
            </div>
        </div>
    );
}

function ConflictAreaCard({
    nations,
    cities,
    nationMap,
    diplomacy,
}: {
    nations: Nation[];
    cities: City[];
    nationMap: Map<number, Nation>;
    diplomacy: Diplomacy[];
}) {
    const nationCities = new Map<number, City[]>();
    for (const city of cities) {
        if (!city.nationId) continue;
        const list = nationCities.get(city.nationId) ?? [];
        list.push(city);
        nationCities.set(city.nationId, list);
    }

    const warPairs: { src: Nation; dest: Nation; srcCount: number; destCount: number }[] = [];
    for (const relation of diplomacy) {
        if (relation.stateCode === 'war' && !relation.isDead) {
            const src = nationMap.get(relation.srcNationId);
            const dest = nationMap.get(relation.destNationId);
            if (src && dest) {
                warPairs.push({
                    src,
                    dest,
                    srcCount: nationCities.get(src.id)?.length ?? 0,
                    destCount: nationCities.get(dest.id)?.length ?? 0,
                });
            }
        }
    }

    const nationList = nations.filter((nation) => (nationCities.get(nation.id)?.length ?? 0) > 0);
    if (nationList.length < 2) return null;

    const totalCities = cities.filter((city) => city.nationId > 0).length;

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm">세력 영토 분쟁 현황</CardTitle>
            </CardHeader>
            <CardContent>
                {warPairs.length > 0 && (
                    <div className="mb-3 space-y-2">
                        <div className="flex items-center gap-1 text-xs font-medium text-red-400">⚔️ 교전 중인 세력</div>
                        {warPairs.map((pair) => (
                            <div
                                key={`${pair.src.id}-${pair.dest.id}`}
                                className="flex items-center gap-2 rounded border border-red-900/50 bg-red-950/20 px-3 py-2 text-xs"
                            >
                                <NationBadge name={pair.src.name} color={pair.src.color} />
                                <span className="text-muted-foreground">({pair.srcCount}도시)</span>
                                <span className="font-bold text-red-400">⚔</span>
                                <NationBadge name={pair.dest.name} color={pair.dest.color} />
                                <span className="text-muted-foreground">({pair.destCount}도시)</span>
                            </div>
                        ))}
                    </div>
                )}

                <div className="space-y-2">
                    {nationList
                        .slice()
                        .sort((a, b) => (nationCities.get(b.id)?.length ?? 0) - (nationCities.get(a.id)?.length ?? 0))
                        .map((nation) => {
                            const count = nationCities.get(nation.id)?.length ?? 0;
                            const pct = totalCities > 0 ? Math.round((count / totalCities) * 100) : 0;
                            const atWar = warPairs.some((pair) => pair.src.id === nation.id || pair.dest.id === nation.id);
                            return (
                                <div key={nation.id} className="space-y-1">
                                    <div className="flex items-center gap-2 text-xs">
                                        <NationBadge name={nation.name} color={nation.color} />
                                        <span className="text-muted-foreground">{count}개 도시</span>
                                        <span className="text-muted-foreground">({pct}%)</span>
                                        {atWar && <span className="text-[10px] text-red-400">⚔ 교전</span>}
                                    </div>
                                    <div className="h-2 overflow-hidden rounded-full bg-gray-800">
                                        <div
                                            className={`h-full rounded-full ${atWar ? 'animate-pulse' : ''}`}
                                            style={{ width: `${pct}%`, backgroundColor: nation.color }}
                                        />
                                    </div>
                                </div>
                            );
                        })}
                </div>
            </CardContent>
        </Card>
    );
}
