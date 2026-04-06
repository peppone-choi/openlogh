'use client';

import { Suspense, useEffect, useState, useMemo, useCallback } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { cityApi, generalApi, mapApi, nationApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import type { City, General, Nation } from '@/types';
import { useSearchParams } from 'next/navigation';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { SammoBar } from '@/components/game/sammo-bar';
import {
    getNPCColor,
    REGION_NAMES,
    CITY_LEVEL_NAMES,
    CREW_TYPE_NAMES,
    isBrightColor,
    formatInjury,
    formatOfficerLevelText,
    formatDefenceTrain,
} from '@/lib/game-utils';
import { calcCityGoldIncome, calcCityRiceIncome, calcCityWallRiceIncome, countCityOfficers } from '@/lib/income-calc';

// --- Sort ---

type SortKey =
    | 'default'
    | 'pop'
    | 'popRate'
    | 'trust'
    | 'agri'
    | 'comm'
    | 'secu'
    | 'def'
    | 'wall'
    | 'trade'
    | 'region'
    | 'level';

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
    { value: 'default', label: '기본' },
    { value: 'pop', label: '인구' },
    { value: 'popRate', label: '인구율' },
    { value: 'trust', label: '지지도' },
    { value: 'agri', label: '생산' },
    { value: 'comm', label: '교역' },
    { value: 'secu', label: '치안' },
    { value: 'def', label: '수비' },
    { value: 'wall', label: '요새' },
    { value: 'trade', label: '시세' },
    { value: 'region', label: '지역' },
    { value: 'level', label: '규모' },
];

function sortCities(cities: City[], sortKey: SortKey): City[] {
    const sorted = [...cities];
    switch (sortKey) {
        case 'pop':
            sorted.sort((a, b) => b.pop - a.pop);
            break;
        case 'popRate':
            sorted.sort((a, b) => (b.popMax > 0 ? b.pop / b.popMax : 0) - (a.popMax > 0 ? a.pop / a.popMax : 0));
            break;
        case 'trust':
            sorted.sort((a, b) => b.trust - a.trust);
            break;
        case 'agri':
            sorted.sort((a, b) => b.agri - a.agri);
            break;
        case 'comm':
            sorted.sort((a, b) => b.comm - a.comm);
            break;
        case 'secu':
            sorted.sort((a, b) => b.secu - a.secu);
            break;
        case 'def':
            sorted.sort((a, b) => b.def - a.def);
            break;
        case 'wall':
            sorted.sort((a, b) => b.wall - a.wall);
            break;
        case 'trade':
            sorted.sort((a, b) => (b.trade ?? 0) - (a.trade ?? 0));
            break;
        case 'region':
            sorted.sort((a, b) => {
                const r = a.region - b.region;
                return r !== 0 ? r : b.level - a.level;
            });
            break;
        case 'level':
            sorted.sort((a, b) => {
                const l = b.level - a.level;
                return l !== 0 ? l : a.region - b.region;
            });
            break;
    }
    return sorted;
}

// --- Main Component ---

function CityPageContent() {
    const searchParams = useSearchParams();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const fetchMyOfficer = useOfficerStore((s) => s.fetchMyOfficer);

    const [nation, setNation] = useState<Nation | null>(null);
    const [nations, setNations] = useState<Nation[]>([]);
    const [cities, setCities] = useState<City[]>([]);
    const [generals, setGenerals] = useState<General[]>([]);
    const [adjacencyMap, setAdjacencyMap] = useState<Map<number, number[]>>(new Map());
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [sortKey, setSortKey] = useState<SortKey>('trade');
    const [expandedCityId, setExpandedCityId] = useState<number | null>(null);
    const [filterCityId, setFilterCityId] = useState<number>(0); // 0 = all

    const requestedCityId = useMemo(() => {
        const raw = searchParams.get('id');
        if (!raw) return null;
        const parsed = Number(raw);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
    }, [searchParams]);

    useEffect(() => {
        if (!currentWorld) return;
        if (!myOfficer) {
            fetchMyOfficer(currentWorld.id).catch(() => setError('장교 정보를 불러올 수 없습니다.'));
        }
    }, [currentWorld, myOfficer, fetchMyOfficer]);

    useEffect(() => {
        if (requestedCityId == null) return;
        setFilterCityId(requestedCityId);
        setExpandedCityId(requestedCityId);
    }, [requestedCityId]);

    useEffect(() => {
        if (requestedCityId != null) return;
        if (!myOfficer?.cityId || myOfficer.cityId <= 0) return;
        setFilterCityId(myOfficer.cityId);
        setExpandedCityId(myOfficer.cityId);
    }, [requestedCityId, myOfficer?.cityId]);

    const loadCityData = useCallback(async () => {
        if (!currentWorld) return;

        const mapCode = (currentWorld.config as Record<string, string>)?.mapCode ?? 'che';

        try {
            const hasGeneral = !!myOfficer;
            const [nationsRes, citiesRes, generalsRes, mapRes, myNationRes] = await Promise.all([
                nationApi.listByWorld(currentWorld.id),
                hasGeneral
                    ? cityApi.listVisibleByWorld(currentWorld.id)
                    : requestedCityId != null
                      ? cityApi.get(requestedCityId).then((r) => ({ data: r.data ? [r.data] : [] }))
                      : Promise.resolve({ data: [] as City[] }),
                generalApi.listByWorld(currentWorld.id),
                mapApi.get(mapCode),
                hasGeneral && myOfficer.nationId > 0
                    ? nationApi.get(myOfficer.nationId)
                    : Promise.resolve({ data: null }),
            ]);

            let allCities = citiesRes.data;
            if (hasGeneral && requestedCityId != null && !allCities.some((c) => c.id === requestedCityId)) {
                try {
                    const singleRes = await cityApi.get(requestedCityId);
                    if (singleRes.data) allCities = [singleRes.data, ...allCities];
                } catch {
                    /* city not found — ignore */
                }
            }

            setNations(nationsRes.data);
            setCities(allCities);
            setGenerals(generalsRes.data);
            setNation(myNationRes.data);

            const next = new Map<number, number[]>();
            for (const cityConst of mapRes.data.cities) {
                next.set(cityConst.id, cityConst.connections ?? []);
            }
            setAdjacencyMap(next);
        } catch {
            setError('행성 정보를 불러올 수 없습니다.');
        } finally {
            setLoading(false);
        }
    }, [currentWorld, myOfficer, requestedCityId]);

    useEffect(() => {
        loadCityData();
    }, [loadCityData]);

    useEffect(() => {
        if (!currentWorld || !myOfficer) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            loadCityData();
        });
    }, [currentWorld, myOfficer, loadCityData]);

    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);

    const spyVisibleCityIds = useMemo(() => {
        const result = new Set<number>();
        const raw = nation?.spy;
        if (!raw || typeof raw !== 'object') return result;
        for (const [k, v] of Object.entries(raw)) {
            const numericKey = Number(k.replace(/\D/g, ''));
            if (!Number.isNaN(numericKey) && numericKey > 0 && v) {
                result.add(numericKey);
            }
            if (typeof v === 'number' && v > 0) result.add(v);
            if (Array.isArray(v)) {
                for (const item of v) {
                    if (typeof item === 'number' && item > 0) result.add(item);
                }
            }
        }
        return result;
    }, [nation?.spy]);

    const canSeeMilitary = useCallback(
        (city: City) => {
            if (!myOfficer) return false;
            if (city.id === myOfficer.cityId) return true;
            if (city.nationId === 0 || city.nationId === myOfficer.nationId) return true;
            if (myOfficer.permission === 'spy') return true;
            return spyVisibleCityIds.has(city.id);
        },
        [myOfficer, spyVisibleCityIds]
    );

    // Officers grouped by officerCity (level 2-4: 종사, 군사, 태수)
    const officersByCity = useMemo(() => {
        const map: Record<number, Record<number, General>> = {};
        for (const gen of generals) {
            if (gen.officerLevel >= 2 && gen.officerLevel <= 4 && gen.officerCity > 0) {
                const cityId = gen.officerCity;
                if (!map[cityId]) map[cityId] = {};
                map[cityId][gen.officerLevel] = gen;
            }
        }
        return map;
    }, [generals]);

    // Generals grouped by city
    const generalsByCity = useMemo(() => {
        const map: Record<number, General[]> = {};
        for (const gen of generals) {
            if (!map[gen.cityId]) map[gen.cityId] = [];
            map[gen.cityId].push(gen);
        }
        return map;
    }, [generals]);

    const sortedCities = useMemo(() => {
        const base = filterCityId > 0 ? cities.filter((c) => c.id === filterCityId) : cities;
        return sortCities(base, sortKey);
    }, [cities, sortKey, filterCityId]);

    const toggleExpand = useCallback((cityId: number) => {
        setExpandedCityId((prev) => (prev === cityId ? null : cityId));
    }, []);

    if (!currentWorld) return <LoadingState message="월드를 선택해주세요." />;
    if (loading) return <LoadingState />;
    if (error) return <div className="p-4 text-red-400">{error}</div>;

    return (
        <div className="legacy-page-wrap pb-16 lg:pb-0">
            <PageHeader title={requestedCityId != null ? '행성정보' : '현재 행성'} />

            {/* Controls: sort + city filter */}
            <div className="legacy-bg0 border-b border-gray-600 px-2 py-1.5 text-sm flex flex-wrap items-center gap-3">
                <span>
                    정렬 :{' '}
                    <select
                        className="bg-[#222] text-white border border-gray-600 px-1 py-0.5 text-sm"
                        value={sortKey}
                        onChange={(e) => setSortKey(e.target.value as SortKey)}
                    >
                        {SORT_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                </span>
                <span>
                    행성 :{' '}
                    <select
                        className="bg-[#222] text-white border border-gray-600 px-1 py-0.5 text-sm"
                        value={filterCityId}
                        onChange={(e) => setFilterCityId(Number(e.target.value))}
                    >
                        <option value={0}>전체 ({cities.length})</option>
                        {cities.map((c) => (
                            <option key={c.id} value={c.id}>
                                {c.name}
                                {nation?.capitalCityId === c.id ? ' [수도]' : ''}
                            </option>
                        ))}
                    </select>
                </span>
            </div>

            {/* City list */}
            {sortedCities.map((city, idx) => {
                const isCapital = nation?.capitalCityId === city.id;
                const owner = nationMap.get(city.nationId);
                const isMyNationCity = city.nationId === myOfficer?.nationId;
                const isVacant = city.nationId === 0;
                const isMyCity = city.id === myOfficer?.cityId;
                const isVisible = isMyCity || (canSeeMilitary(city) && !isVacant);
                const nationColor = owner?.color ?? '#888';
                const textColor = isBrightColor(nationColor) ? 'black' : 'white';
                const regionText = REGION_NAMES[city.region] ?? '';
                const levelText = CITY_LEVEL_NAMES[city.level] ?? `Lv.${city.level}`;
                const popRate = city.popMax > 0 ? ((city.pop / city.popMax) * 100).toFixed(1) : '0';
                const tradeText = city.trade != null ? `${city.trade}%` : '-';

                const officers = officersByCity[city.id] ?? {};
                const cityGens = generalsByCity[city.id] ?? [];
                const cityIncomeOfficerCnt = countCityOfficers(cityGens, city.id);
                const defendingGeneral =
                    officers[4] ?? [...cityGens].sort((a, b) => b.leadership - a.leadership || b.crew - a.crew)[0];
                const isExpanded = expandedCityId === city.id;
                const adjacentIds = adjacencyMap.get(city.id) ?? [];
                const adjacentCities = adjacentIds
                    .map((id) => cities.find((c) => c.id === id))
                    .filter((c): c is City => Boolean(c));

                const prevRegion = idx > 0 ? sortedCities[idx - 1].region : -1;
                const prevLevel = idx > 0 ? sortedCities[idx - 1].level : -1;
                const showSeparator =
                    idx > 0 &&
                    ((sortKey === 'region' && city.region !== prevRegion) ||
                        (sortKey === 'level' && city.level !== prevLevel));

                return (
                    <div key={city.id}>
                        {showSeparator && <div className="h-3" />}
                        <div className="legacy-bg2 border-r border-b border-gray-600 text-sm">
                            {/* ===== City overview grid ===== */}
                            <div
                                style={{
                                    display: 'grid',
                                    gridTemplateColumns: 'repeat(10, 1fr)',
                                }}
                            >
                                {/* City name header (clickable to expand) */}
                                <button
                                    type="button"
                                    className="border-t border-l border-gray-600 font-bold text-center col-span-10 cursor-pointer select-none"
                                    style={{
                                        color: textColor,
                                        backgroundColor: nationColor,
                                        lineHeight: '1.8em',
                                        fontSize: '13px',
                                    }}
                                    onClick={() => toggleExpand(city.id)}
                                    title="클릭하여 장교 상세 보기"
                                >
                                    <span>
                                        {'【 '}
                                        {regionText} | {levelText}
                                        {' 】 '}
                                    </span>
                                    {isCapital ? (
                                        <span style={{ color: 'cyan' }}>[{city.name}]</span>
                                    ) : (
                                        <span>{city.name}</span>
                                    )}
                                    <span className="ml-2 text-xs opacity-90">{owner?.name ?? '공백지'}</span>
                                    {!isMyNationCity && !isVisible && (
                                        <span className="ml-1 text-xs text-yellow-300">[첩보 제한]</span>
                                    )}
                                    <span className="ml-1 text-xs opacity-70">{isExpanded ? '▲' : '▼'}</span>
                                </button>

                                {/* Row 1: pop, popRate, trust, trade, supply */}
                                <LabelCell>주민</LabelCell>
                                <ValueCell>
                                    {isVisible ? `${city.pop.toLocaleString()}/${city.popMax.toLocaleString()}` : '?'}
                                </ValueCell>
                                <LabelCell>인구율</LabelCell>
                                <ValueCell>
                                    {isVisible ? (
                                        <>
                                            <SammoBar
                                                height={7}
                                                percent={city.popMax > 0 ? (city.pop / city.popMax) * 100 : 0}
                                            />
                                            <span>{popRate}%</span>
                                        </>
                                    ) : (
                                        <span>?</span>
                                    )}
                                </ValueCell>
                                <LabelCell>지지도</LabelCell>
                                <ValueCell>
                                    {isVisible ? (
                                        <>
                                            <SammoBar height={7} percent={city.trust} />
                                            <span>
                                                {city.trust.toLocaleString(undefined, {
                                                    maximumFractionDigits: 1,
                                                })}
                                            </span>
                                        </>
                                    ) : (
                                        <span>?</span>
                                    )}
                                </ValueCell>
                                <LabelCell>시세</LabelCell>
                                <ValueCell>{isVisible ? tradeText : '?'}</ValueCell>
                                <LabelCell>보급</LabelCell>
                                <SupplyCell state={city.supplyState} hidden={!isVisible} />

                                {isMyNationCity && nation && (
                                    <>
                                        <LabelCell>자자금 수입</LabelCell>
                                        <ValueCell>
                                            {(
                                                (calcCityGoldIncome(
                                                    city,
                                                    cityIncomeOfficerCnt,
                                                    isCapital,
                                                    nation.level,
                                                    nation.typeCode
                                                ) *
                                                    nation.rate) /
                                                20
                                            ).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                                        </ValueCell>
                                        <LabelCell>물자 수입</LabelCell>
                                        <ValueCell>
                                            {(
                                                (calcCityRiceIncome(
                                                    city,
                                                    cityIncomeOfficerCnt,
                                                    isCapital,
                                                    nation.level,
                                                    nation.typeCode
                                                ) *
                                                    nation.rate) /
                                                20
                                            ).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                                        </ValueCell>
                                        <LabelCell>둔전 수입</LabelCell>
                                        <ValueCell>
                                            {(
                                                (calcCityWallRiceIncome(
                                                    city,
                                                    cityIncomeOfficerCnt,
                                                    isCapital,
                                                    nation.level,
                                                    nation.typeCode
                                                ) *
                                                    nation.rate) /
                                                20
                                            ).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                                        </ValueCell>
                                    </>
                                )}

                                {/* Row 2: agri, comm, secu, def, wall */}
                                <LabelCell>생산</LabelCell>
                                <StatValueCell
                                    val={city.agri}
                                    max={city.agriMax}
                                    hidden={!isVisible}
                                    kind="agri"
                                    perTurn={100}
                                />
                                <LabelCell>교역</LabelCell>
                                <StatValueCell
                                    val={city.comm}
                                    max={city.commMax}
                                    hidden={!isVisible}
                                    kind="comm"
                                    perTurn={100}
                                />
                                <LabelCell>치안</LabelCell>
                                <StatValueCell
                                    val={city.secu}
                                    max={city.secuMax}
                                    hidden={!isVisible}
                                    kind="secu"
                                    perTurn={100}
                                />
                                <LabelCell>수비</LabelCell>
                                <StatValueCell
                                    val={city.def}
                                    max={city.defMax}
                                    hidden={!isVisible}
                                    kind="def"
                                    perTurn={70}
                                />
                                <LabelCell>요새</LabelCell>
                                <StatValueCell
                                    val={city.wall}
                                    max={city.wallMax}
                                    hidden={!isVisible}
                                    kind="wall"
                                    perTurn={70}
                                />

                                {/* Row 3: officers + front */}
                                <LabelCell>태수</LabelCell>
                                <OfficerValue gen={officers[4]} hidden={!isVisible} />
                                <LabelCell>군사</LabelCell>
                                <OfficerValue gen={officers[3]} hidden={!isVisible} />
                                <LabelCell>종사</LabelCell>
                                <OfficerValue gen={officers[2]} hidden={!isVisible} />
                                <LabelCell>전선</LabelCell>
                                <FrontCell state={city.frontState} hidden={!isVisible} />
                                <LabelCell>사망</LabelCell>
                                <ValueCell>{isVisible ? city.dead.toLocaleString() : '?'}</ValueCell>

                                <LabelCell>수비장</LabelCell>
                                <ValueCell>{isVisible && defendingGeneral ? defendingGeneral.name : '?'}</ValueCell>
                                <LabelCell>주둔함선</LabelCell>
                                <ValueCell>
                                    {isVisible ? cityGens.reduce((sum, g) => sum + g.crew, 0).toLocaleString() : '?'}
                                </ValueCell>
                                <LabelCell>요새/수비</LabelCell>
                                <ValueCell>{isVisible ? `${city.wall}/${city.def}` : '?'}</ValueCell>
                                <LabelCell>인접 행성</LabelCell>
                                <div className="border-t border-l border-gray-600 px-1 py-0.5 col-span-4 text-xs">
                                    {adjacentCities.length === 0 ? (
                                        <span className="text-gray-500">-</span>
                                    ) : (
                                        adjacentCities.map((adj, index) => (
                                            <button
                                                key={adj.id}
                                                type="button"
                                                className="text-cyan-300 hover:text-cyan-200"
                                                onClick={() => {
                                                    setFilterCityId(adj.id);
                                                    setExpandedCityId(adj.id);
                                                }}
                                            >
                                                {index > 0 ? ', ' : ''}
                                                {adj.name}
                                            </button>
                                        ))
                                    )}
                                </div>

                                {/* Row 4: generals summary */}
                                <LabelCell>장교({cityGens.length})</LabelCell>
                                <div
                                    className="border-t border-l border-gray-600 px-1 py-0.5 col-span-9"
                                    style={{ lineHeight: '1.6em' }}
                                >
                                    {!isVisible ? (
                                        <span className="text-gray-500">첩보 부족</span>
                                    ) : cityGens.length === 0 ? (
                                        <span className="text-gray-500">-</span>
                                    ) : (
                                        cityGens.map((gen, i) => {
                                            const color = getNPCColor(gen.npcState);
                                            return (
                                                <span key={gen.id}>
                                                    {i > 0 && ', '}
                                                    <span style={{ color }}>{gen.name}</span>
                                                </span>
                                            );
                                        })
                                    )}
                                </div>

                                {/* Row 5: Troop readiness summary (legacy parity: b_currentCity.php) */}
                                {isVisible &&
                                    isMyNationCity &&
                                    (() => {
                                        const myNationId = myOfficer?.nationId ?? 0;
                                        const enemyGens = cityGens.filter(
                                            (g) => g.nationId !== 0 && g.nationId !== myNationId
                                        );
                                        const enemyCrew = enemyGens.reduce((s, g) => s + g.crew, 0);
                                        const enemyArmed = enemyGens.filter((g) => g.crew > 0).length;
                                        const allyGens = cityGens.filter((g) => g.nationId === myNationId);
                                        const crewTotal = allyGens.reduce((s, g) => s + g.crew, 0);
                                        const armedTotal = allyGens.filter((g) => g.crew > 0).length;
                                        const withCrew = allyGens.filter((g) => g.crew > 0);
                                        const t90 = withCrew.filter((g) => Math.min(g.train, g.atmos) >= 90);
                                        const t60 = withCrew.filter((g) => Math.min(g.train, g.atmos) >= 60);
                                        const crew90 = t90.reduce((s, g) => s + g.crew, 0);
                                        const crew60 = t60.reduce((s, g) => s + g.crew, 0);
                                        const defReady = withCrew.filter(
                                            (g) => Math.min(g.train, g.atmos) >= g.defenceTrain
                                        );
                                        const crewDef = defReady.reduce((s, g) => s + g.crew, 0);
                                        const totalCrew = cityGens.reduce((s, g) => s + g.crew, 0);
                                        return (
                                            <>
                                                <LabelCell>적 진영 함선</LabelCell>
                                                <ValueCell>
                                                    <span className={enemyCrew > 0 ? 'text-red-400' : ''}>
                                                        {enemyCrew.toLocaleString()}/{enemyArmed}({enemyGens.length})
                                                    </span>
                                                </ValueCell>
                                                <LabelCell>아국 함선</LabelCell>
                                                <ValueCell>
                                                    {crewTotal.toLocaleString()}/{armedTotal}({allyGens.length})
                                                </ValueCell>
                                                <LabelCell>훈련 90+</LabelCell>
                                                <ValueCell>
                                                    <span className="text-green-400">
                                                        {crew90.toLocaleString()}/{t90.length}
                                                    </span>
                                                </ValueCell>
                                                <LabelCell>훈련 60+</LabelCell>
                                                <ValueCell>
                                                    <span className="text-yellow-400">
                                                        {crew60.toLocaleString()}/{t60.length}
                                                    </span>
                                                </ValueCell>
                                                <LabelCell>수비 가능</LabelCell>
                                                <ValueCell>
                                                    <span className="text-cyan-400">
                                                        {crewDef.toLocaleString()}/{defReady.length}
                                                    </span>
                                                </ValueCell>
                                                <LabelCell>총 주둔 함선</LabelCell>
                                                <ValueCell>
                                                    <span className="text-blue-400">{totalCrew.toLocaleString()}</span>
                                                </ValueCell>
                                            </>
                                        );
                                    })()}
                            </div>

                            {/* ===== Expanded garrison table ===== */}
                            {isExpanded && cityGens.length > 0 && isVisible && (
                                <GarrisonTable
                                    generals={cityGens}
                                    nationLevel={nation?.level}
                                    myNationId={myOfficer?.nationId}
                                />
                            )}
                        </div>
                    </div>
                );
            })}

            {sortedCities.length === 0 && <div className="text-center text-gray-500 py-8">소속 행성가 없습니다.</div>}
        </div>
    );
}

export default function CityPage() {
    return (
        <Suspense
            fallback={
                <div className="p-4">
                    <LoadingState message="행성 정보를 불러오는 중..." />
                </div>
            }
        >
            <CityPageContent />
        </Suspense>
    );
}

// --- Garrison Table ---

function GarrisonTable({
    generals,
    nationLevel,
    myNationId,
}: {
    generals: General[];
    nationLevel?: number;
    myNationId?: number;
}) {
    return (
        <div className="overflow-x-auto border-t border-gray-600">
            <table className="w-full text-xs border-collapse">
                <thead>
                    <tr className="legacy-bg1 text-center">
                        <Th>이름</Th>
                        <Th>관직</Th>
                        <Th>함종</Th>
                        <Th>함선</Th>
                        <Th>훈련</Th>
                        <Th>사기</Th>
                        <Th>수비</Th>
                        <Th>부상</Th>
                        <Th>통솔</Th>
                        <Th>지휘</Th>
                        <Th>정보</Th>
                        <Th>정치</Th>
                        <Th>운영</Th>
                        <Th>턴시간</Th>
                        <Th>명령</Th>
                        <Th>자금</Th>
                        <Th>물자</Th>
                    </tr>
                </thead>
                <tbody>
                    {generals.map((gen) => {
                        const npcColor = getNPCColor(gen.npcState);
                        const injury = formatInjury(gen.injury);
                        const officerText = formatOfficerLevelText(
                            gen.officerLevel,
                            nationLevel,
                            gen.nationId > 0,
                            undefined,
                            gen.npcState
                        );
                        const defText = formatDefenceTrain(gen.defenceTrain);
                        const isOwnNation = myNationId != null && gen.nationId === myNationId;
                        const turnTimeText =
                            isOwnNation && gen.turnTime
                                ? new Date(gen.turnTime).toLocaleString('ko-KR', {
                                      hour: '2-digit',
                                      minute: '2-digit',
                                  })
                                : '-';
                        const commandText = isOwnNation ? (gen.lastTurn?.command ?? '-') : '-';
                        return (
                            <tr key={gen.id} className="text-center border-t border-gray-700 hover:bg-white/5">
                                <td
                                    className="border-l border-gray-600 px-1 py-0.5 whitespace-nowrap"
                                    style={{ color: npcColor }}
                                >
                                    {gen.name}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 whitespace-nowrap">
                                    {officerText}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 whitespace-nowrap">
                                    {CREW_TYPE_NAMES[gen.crewType] ?? '보병'}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">
                                    {gen.crew.toLocaleString()}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.train}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.atmos}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5">{defText}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5" style={{ color: injury.color }}>
                                    {injury.text}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.leadership}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.strength}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.intel}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.politics}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">{gen.charm}</td>
                                <td className="border-l border-gray-600 px-1 py-0.5 whitespace-nowrap">
                                    {turnTimeText}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 whitespace-nowrap max-w-40 truncate">
                                    {commandText}
                                </td>
                                <td className="border-l border-gray-600 px-1 py-0.5 tabular-nums">
                                    {gen.gold.toLocaleString()}
                                </td>
                                <td className="border-l border-r border-gray-600 px-1 py-0.5 tabular-nums">
                                    {gen.rice.toLocaleString()}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}

// --- Shared cell components ---

function Th({ children }: { children: React.ReactNode }) {
    return <th className="border-l border-gray-600 px-1 py-0.5 font-normal whitespace-nowrap">{children}</th>;
}

function LabelCell({ children }: { children: React.ReactNode }) {
    return (
        <div className="border-t border-l border-gray-600 legacy-bg1 flex items-center justify-center text-center text-xs py-0.5">
            {children}
        </div>
    );
}

function ValueCell({ children }: { children: React.ReactNode }) {
    return <div className="border-t border-l border-gray-600 text-center text-xs py-0.5 px-0.5">{children}</div>;
}

/** Thresholds: high=green, mid=yellow, low=red. Agri/comm/secu use 80%/40%, def/wall use 60%/30% */
function statColor(val: number, max: number, kind?: 'def' | 'wall'): string {
    if (max <= 0) return 'inherit';
    const ratio = val / max;
    if (kind === 'def' || kind === 'wall') {
        if (ratio > 0.6) return 'lightgreen';
        if (ratio > 0.3) return 'yellow';
        return 'orangered';
    }
    if (ratio > 0.8) return 'lightgreen';
    if (ratio > 0.4) return 'yellow';
    return 'orangered';
}

/** Show remaining capacity warning when near max (legacy: yellow [remain] annotation) */
function remainWarning(val: number, max: number, perTurn: number): string | null {
    const remain = val - max;
    if (remain > -10 * perTurn) return `[${remain > 0 ? '+' : ''}${remain}]`;
    return null;
}

function StatValueCell({
    val,
    max,
    hidden,
    kind,
    perTurn,
}: {
    val: number;
    max: number;
    hidden?: boolean;
    kind?: 'pop' | 'agri' | 'comm' | 'secu' | 'def' | 'wall';
    perTurn?: number;
}) {
    if (hidden) {
        return <div className="border-t border-l border-gray-600 text-center text-xs py-0.5 px-0.5">?</div>;
    }
    const color =
        kind === 'pop'
            ? max > 0 && val / max > 0.9
                ? 'lightgreen'
                : max > 0 && val / max > 0.7
                  ? 'yellow'
                  : 'orangered'
            : kind === 'def' || kind === 'wall'
              ? statColor(val, max, kind)
              : statColor(val, max);
    const warn = perTurn != null ? remainWarning(val, max, perTurn) : null;
    return (
        <div className="border-t border-l border-gray-600 text-center text-xs py-0.5 px-0.5">
            <SammoBar height={7} percent={max > 0 ? (val / max) * 100 : 0} />
            <span style={{ color }}>
                {val.toLocaleString()}/{max.toLocaleString()}
            </span>
            {warn && (
                <span style={{ color: 'yellow' }} className="ml-0.5">
                    {warn}
                </span>
            )}
        </div>
    );
}

function OfficerValue({ gen, hidden }: { gen?: General; hidden?: boolean }) {
    if (hidden) {
        return <div className="border-t border-l border-gray-600 text-center text-xs py-0.5 text-gray-500">?</div>;
    }
    if (!gen) {
        return <div className="border-t border-l border-gray-600 text-center text-xs py-0.5 text-gray-500">-</div>;
    }
    const color = getNPCColor(gen.npcState);
    return (
        <div className="border-t border-l border-gray-600 text-center text-xs py-0.5" style={{ color }}>
            {gen.name}
        </div>
    );
}

function SupplyCell({ state, hidden }: { state: number; hidden?: boolean }) {
    if (hidden) {
        return <div className="border-t border-l border-gray-600 text-center text-xs py-0.5">?</div>;
    }
    const color = state === 0 ? 'limegreen' : state === 1 ? 'yellow' : 'red';
    const text = state === 0 ? '정상' : state === 1 ? '부족' : '고립';
    return (
        <div className="border-t border-l border-gray-600 text-center text-xs py-0.5">
            <span style={{ color }}>{text}</span>
        </div>
    );
}

function FrontCell({ state, hidden }: { state: number; hidden?: boolean }) {
    if (hidden) {
        return <div className="border-t border-l border-gray-600 text-center text-xs py-0.5">?</div>;
    }
    const color = state === 0 ? 'limegreen' : state === 1 ? 'yellow' : 'red';
    const text = state === 0 ? '후방' : state === 1 ? '전선' : '최전선';
    return (
        <div className="border-t border-l border-gray-600 text-center text-xs py-0.5">
            <span style={{ color }}>{text}</span>
        </div>
    );
}
