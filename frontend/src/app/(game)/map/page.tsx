'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';
import { useOfficerStore } from '@/stores/officerStore';
import { mapRecentApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import { formatLog } from '@/lib/formatLog';
import type { PublicCachedMapHistory } from '@/types';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PLANET_LEVEL_NAMES, SHIP_CLASS_NAMES } from '@/lib/game-utils';
import { MAP_WIDTH, MAP_HEIGHT } from '@/lib/map-constants';
import type { MapSeason } from '@/lib/map-constants';
import { MapCanvas } from '@/components/game/map-canvas';
import type { RenderCity, CityOverlay } from '@/components/game/map-canvas';
import { DetailTooltip } from '@/components/game/map-tooltips';

type MapTheme = 'default' | 'spring' | 'summer' | 'autumn' | 'winter';
const MAP_THEMES: {
    key: MapTheme;
    label: string;
    bg: string;
    line: string;
    text: string;
}[] = [
    { key: 'default', label: '기본', bg: '#111827', line: '#333', text: '#ccc' },
    {
        key: 'spring',
        label: '봄',
        bg: '#1a2e1a',
        line: '#4a7c4a',
        text: '#b0e0b0',
    },
    {
        key: 'summer',
        label: '여름',
        bg: '#1a2e2e',
        line: '#2a6e6e',
        text: '#a0e0e0',
    },
    {
        key: 'autumn',
        label: '가을',
        bg: '#2e1a0a',
        line: '#8e5e2e',
        text: '#e0c090',
    },
    {
        key: 'winter',
        label: '겨울',
        bg: '#1e2030',
        line: '#6070a0',
        text: '#c0c8e0',
    },
];

type MapLayer = 'nations' | 'troops' | 'supply' | 'terrain';

const CITY_NAME_MARGIN = 6;
const CITY_NAME_HEIGHT = 14;
const CITY_NAME_CHAR_WIDTH = 10;
const CITY_NAME_PADDING = 6;

function clamp(value: number, min: number, max: number): number {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

function getCityNameOffsets(city: { name: string; x: number; y: number }, markerLeft: number, markerTop: number) {
    const estimatedWidth = city.name.length * CITY_NAME_CHAR_WIDTH + CITY_NAME_PADDING;
    const rightX = city.x + 8;
    const leftX = city.x - estimatedWidth - 8;
    const prefersLeft = rightX + estimatedWidth > MAP_WIDTH - CITY_NAME_MARGIN && leftX >= CITY_NAME_MARGIN;
    const preferredX = prefersLeft ? leftX : rightX;
    const clampedX = clamp(preferredX, CITY_NAME_MARGIN, MAP_WIDTH - CITY_NAME_MARGIN - estimatedWidth);

    const belowY = city.y + 16;
    const aboveY = city.y - CITY_NAME_HEIGHT - 8;
    const preferredY = belowY + CITY_NAME_HEIGHT <= MAP_HEIGHT - CITY_NAME_MARGIN ? belowY : aboveY;
    const clampedY = clamp(preferredY, CITY_NAME_MARGIN, MAP_HEIGHT - CITY_NAME_MARGIN - CITY_NAME_HEIGHT);

    return {
        left: clampedX - markerLeft,
        top: clampedY - markerTop,
    };
}

export default function MapPage() {
    const router = useRouter();
    const { currentWorld } = useWorldStore();
    const { cities, nations, generals, mapData, loadAll, loadMap } = useGameStore();
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const fetchMyGeneral = useOfficerStore((s) => s.fetchMyGeneral);
    const [history, setHistory] = useState<PublicCachedMapHistory[]>([]);
    const [touchTapId, setTouchTapId] = useState<number | null>(null);

    // Auto-detect season from world month
    const autoTheme = useMemo<MapTheme>(() => {
        const month = currentWorld?.currentMonth ?? null;
        if (!month) return 'default';
        if (month <= 3) return 'spring';
        if (month <= 6) return 'summer';
        if (month <= 9) return 'autumn';
        return 'winter';
    }, [currentWorld?.currentMonth]);
    const [theme, setTheme] = useState<MapTheme>('default');
    const [layers, setLayers] = useState<Set<MapLayer>>(new Set(['nations', 'troops']));
    const [historyBrowseIdx, setHistoryBrowseIdx] = useState<number | null>(null);
    const [historyFilterYear, setHistoryFilterYear] = useState<number | null>(null);
    const [historyFilterMonth, setHistoryFilterMonth] = useState<number | null>(null);

    const [isAutoTheme, setIsAutoTheme] = useState(true);
    const [showCityNames, setShowCityNames] = useState(true);

    const selectedTheme = isAutoTheme ? autoTheme : theme;
    const currentTheme = MAP_THEMES.find((t) => t.key === selectedTheme) ?? MAP_THEMES[0];
    const mapCode = ((currentWorld?.config as Record<string, string>)?.mapCode ?? 'che').trim() || 'che';

    const season = useMemo<MapSeason>(() => {
        const themeForSeason = selectedTheme === 'default' ? autoTheme : selectedTheme;
        if (themeForSeason === 'spring') return 'spring';
        if (themeForSeason === 'summer') return 'summer';
        if (themeForSeason === 'autumn') return 'fall';
        if (themeForSeason === 'winter') return 'winter';
        return 'spring';
    }, [selectedTheme, autoTheme]);
    const showSeasonBackground = isAutoTheme || selectedTheme !== 'default';

    const toggleLayer = (layer: MapLayer) => {
        setLayers((prev) => {
            const next = new Set(prev);
            if (next.has(layer)) next.delete(layer);
            else next.add(layer);
            return next;
        });
    };

    useEffect(() => {
        if (currentWorld) {
            loadAll(currentWorld.id);
            loadMap(mapCode);
            fetchMyGeneral(currentWorld.id);

            mapRecentApi
                .getMapRecent(currentWorld.id)
                .then(({ data }) => {
                    if (data.history) setHistory(data.history);
                })
                .catch(() => {});
        }
    }, [currentWorld, loadAll, loadMap, mapCode, fetchMyGeneral]);

    useEffect(() => {
        if (!currentWorld) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            loadAll(currentWorld.id);
            mapRecentApi
                .getMapRecent(currentWorld.id)
                .then(({ data }) => {
                    if (data.history) setHistory(data.history);
                })
                .catch(() => {});
        });
    }, [currentWorld, loadAll]);

    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);
    const cityMap = useMemo(() => new Map(cities.map((c) => [c.id, c])), [cities]);

    const myNation = useMemo(() => {
        if (!myOfficer || myOfficer.factionId <= 0) return null;
        return nationMap.get(myOfficer.factionId) ?? null;
    }, [myOfficer, nationMap]);

    const spyVisibleCityIds = useMemo(() => {
        const result = new Set<number>();
        const raw = myNation?.spy;
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
    }, [myNation?.spy]);

    const canViewCityInfo = useCallback(
        (cityId: number) => {
            const city = cityMap.get(cityId);
            if (!city || !myOfficer) return false;
            if (myOfficer.permission === 'spy') return true;
            if (myOfficer.factionId > 0 && city.factionId === myOfficer.factionId) {
                return true;
            }
            return spyVisibleCityIds.has(city.id);
        },
        [cityMap, myOfficer, spyVisibleCityIds]
    );

    const cityByNameMap = useMemo(() => new Map(cities.map((c) => [c.name, c])), [cities]);

    // Build per-city general counts by nation (for troop indicators)
    const cityGeneralData = useMemo(() => {
        const map = new Map<number, { factionId: number; name: string; ships: number; shipClass: number }[]>();
        for (const g of generals) {
            if (g.factionId <= 0 || g.ships <= 0) continue;
            if (!map.has(g.planetId)) map.set(g.planetId, []);
            map.get(g.planetId)!.push({
                factionId: g.factionId,
                name: g.name,
                ships: g.ships,
                shipClass: g.shipClass,
            });
        }
        return map;
    }, [generals]);

    // Identify cities with foreign troops
    const foreignTroopCities = useMemo(() => {
        const result = new Set<number>();
        for (const [cityId, gens] of cityGeneralData) {
            const city = cityMap.get(cityId);
            if (!city) continue;
            for (const g of gens) {
                if (g.factionId !== city.factionId && city.factionId > 0) {
                    result.add(cityId);
                    break;
                }
            }
        }
        return result;
    }, [cityGeneralData, cityMap]);

    // Save city info to localStorage for cross-page reference
    const saveCityInfo = useCallback(
        (cityId: number) => {
            const city = cityMap.get(cityId);
            if (!city) return;
            const nation = city.factionId ? nationMap.get(city.factionId) : null;
            const isVisible = canViewCityInfo(cityId);
            try {
                localStorage.setItem(
                    `openlogh:planetInfo:${cityId}`,
                    JSON.stringify({
                        id: cityId,
                        name: city.name ?? '',
                        nationName: nation?.name ?? '공백지',
                        nationColor: nation?.color ?? '#555',
                        pop: isVisible ? city.population : 0,
                        level: city.level,
                        ts: Date.now(),
                    })
                );
            } catch {
                /* ignore quota */
            }
        },
        [cityMap, nationMap, canViewCityInfo]
    );

    // Build RenderCity array from mapData + runtime cities
    const renderCities = useMemo<RenderCity[]>(() => {
        if (!mapData?.cities) return [];
        return mapData.cities.map((cc) => {
            const rtCity = cityByNameMap.get(cc.name);
            const nation = rtCity?.factionId ? nationMap.get(rtCity.factionId) : null;
            const showNationLayer = layers.has('nations') && !!nation;
            const isMyCity = myOfficer?.planetId != null && rtCity?.id === myOfficer.planetId;

            return {
                id: rtCity?.id ?? cc.id,
                name: cc.name,
                x: cc.x,
                y: cc.y,
                level: cc.level,
                region: cc.region,
                nationColor: showNationLayer ? (nation?.color ?? null) : null,
                nationName: showNationLayer ? (nation?.name ?? null) : null,
                nationAbbr: showNationLayer ? nation?.abbreviation || nation?.name?.slice(0, 1) || null : null,
                isCapital: showNationLayer && !!rtCity && nation!.capitalPlanetId === rtCity.id,
                supplyState: rtCity?.supplyState ?? 0,
                state: rtCity?.state ?? 0,
                isMyCity,
                isEmperorCity: false,
            };
        });
    }, [mapData, cityByNameMap, nationMap, layers, myOfficer?.cityId]);

    // Build cityOverlays for troops/supply/terrain layers
    const cityOverlays = useMemo(() => {
        const overlays = new Map<number, CityOverlay>();
        if (!mapData?.cities) return overlays;
        for (const cc of mapData.cities) {
            const rtCity = cityByNameMap.get(cc.name);
            if (!rtCity) continue;
            const overlay: CityOverlay = {};
            if (layers.has('troops')) {
                overlay.generalCount = cityGeneralData.get(rtCity.id)?.length ?? 0;
                overlay.hasForeignTroops = foreignTroopCities.has(rtCity.id);
            }
            if (layers.has('supply') && rtCity.supplyState !== 1) {
                overlay.isSupplyBroken = true;
            }
            if (layers.has('terrain')) {
                overlay.terrainLevel = rtCity.level;
            }
            if (overlay.generalCount || overlay.hasForeignTroops || overlay.isSupplyBroken || overlay.terrainLevel) {
                overlays.set(rtCity.id, overlay);
            }
        }
        return overlays;
    }, [mapData, cityByNameMap, layers, cityGeneralData, foreignTroopCities]);

    // Year/month for header
    const yearMonth = useMemo(() => {
        const y = currentWorld?.currentYear;
        const m = currentWorld?.currentMonth;
        return y && m ? { year: y, month: m } : null;
    }, [currentWorld?.currentYear, currentWorld?.currentMonth]);

    // Custom city name positioning for full map page
    const cityNamePositionFn = useCallback((city: RenderCity, markerLeft: number, markerTop: number) => {
        return getCityNameOffsets(city, markerLeft, markerTop);
    }, []);

    // Build tooltip using DetailTooltip
    const buildTooltip = useCallback(
        (city: RenderCity, screenX: number, screenY: number) => {
            const rtCity = cityByNameMap.get(city.name);
            const nation = rtCity?.factionId ? nationMap.get(rtCity.factionId) : null;
            const cityGens = cityGeneralData.get(rtCity?.id ?? -1) ?? [];
            const isVisible = rtCity ? canViewCityInfo(rtCity.id) : false;
            const generalsInfo = isVisible
                ? cityGens.map((g) => ({
                      name: g.name,
                      nationColor: nationMap.get(g.factionId)?.color ?? '#555',
                      crew: g.ships,
                      crewType: SHIP_CLASS_NAMES[g.shipClass] ?? `${g.shipClass}`,
                      isForeign: rtCity ? g.factionId !== rtCity.factionId : false,
                  }))
                : [];

            return {
                cityId: rtCity?.id ?? city.id,
                cityName: city.name,
                nationName: nation?.name ?? '공백지',
                nationColor: nation?.color ?? '#555',
                isVisible,
                level: rtCity?.level ?? city.level,
                pop: rtCity?.population ?? 0,
                agri: rtCity && isVisible ? `${rtCity.production}/${rtCity.productionMax}` : '?',
                comm: rtCity && isVisible ? `${rtCity.commerce}/${rtCity.commerceMax}` : '?',
                secu: rtCity && isVisible ? `${rtCity.security}/${rtCity.securityMax}` : '?',
                def: rtCity && isVisible ? `${rtCity.orbitalDefense}/${rtCity.orbitalDefenseMax}` : '?',
                wall: rtCity && isVisible ? `${rtCity.fortress}/${rtCity.fortressMax}` : '?',
                trust: rtCity?.approval ?? 0,
                generals: generalsInfo,
                screenX,
                screenY,
            };
        },
        [cityByNameMap, nationMap, cityGeneralData, canViewCityInfo]
    );

    const renderTooltipFn = useCallback(
        (city: RenderCity, pos: { x: number; y: number }) => {
            const data = buildTooltip(city, pos.x, pos.y);
            return (
                <DetailTooltip
                    cityId={data.cityId}
                    cityName={data.cityName}
                    nationName={data.nationName}
                    nationColor={data.nationColor}
                    isVisible={data.isVisible}
                    level={data.level}
                    pop={data.pop}
                    agri={data.agri}
                    comm={data.comm}
                    secu={data.secu}
                    def={data.def}
                    wall={data.wall}
                    trust={data.trust}
                    generals={data.generals}
                    position={pos}
                />
            );
        },
        [buildTooltip]
    );

    const handleCityClick = useCallback(
        (cityId: number, _e: React.MouseEvent) => {
            // Find the city by id from renderCities or cityByNameMap
            const rtCity = cities.find((c) => c.id === cityId);
            if (rtCity) saveCityInfo(rtCity.id);
            router.push(`/city?id=${cityId}`);
        },
        [cities, router, saveCityInfo]
    );

    const handleCityTouch = useCallback(
        (cityId: number) => {
            const rtCity = cities.find((c) => c.id === cityId);
            if (touchTapId === cityId) {
                if (rtCity) saveCityInfo(rtCity.id);
                router.push(`/city?id=${cityId}`);
                setTouchTapId(null);
            } else {
                if (rtCity) saveCityInfo(rtCity.id);
                setTouchTapId(cityId);
            }
        },
        [cities, touchTapId, router, saveCityInfo]
    );

    if (!mapData || !mapData.cities || !Array.isArray(mapData.cities)) {
        return <div className="flex items-center justify-center h-64 text-gray-500">지도를 불러오는 중...</div>;
    }

    const serverName = currentWorld?.name ?? '삼국지';
    const getHistoryBullet = (text: string | undefined) => {
        if (!text) return '●';
        if (text.includes('【대회】') || text.includes('【안내】')) return '◆';
        return '●';
    };

    return (
        <Card className="w-full max-w-[750px] mx-auto overflow-hidden">
            <CardHeader className="pb-2">
                <CardTitle className="text-lg">{serverName} 현황</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
                <div className="w-full max-w-[700px] mx-auto overflow-hidden">
                    <MapCanvas
                        cities={renderCities}
                        mapCode={mapCode}
                        season={season}
                        yearMonth={yearMonth}
                        showNames={showCityNames}
                        onShowNamesChange={setShowCityNames}
                        interactive
                        showNationBlotch={layers.has('nations')}
                        showSeasonBackground={showSeasonBackground}
                        cityOverlays={cityOverlays}
                        renderTooltip={renderTooltipFn}
                        tooltipTrigger="hover-delayed"
                        tooltipHideDelay={120}
                        onCityClick={handleCityClick}
                        onCityTouch={handleCityTouch}
                        cityNamePosition={cityNamePositionFn}
                        cityNameColor={currentTheme.text}
                        themeColors={{ bg: currentTheme.bg }}
                        dismissOverlay={
                            <button
                                type="button"
                                aria-label="지도 툴팁 닫기"
                                className="absolute inset-0 border-0 bg-transparent p-0"
                                onClick={() => {
                                    setTouchTapId(null);
                                }}
                            />
                        }
                    />
                </div>

                <div className="mx-auto flex w-full max-w-[700px] flex-wrap items-center gap-1.5 text-xs px-1">
                    <span className="text-muted-foreground">테마</span>
                    <select
                        className="h-7 rounded border border-border bg-background px-1.5 text-xs"
                        value={selectedTheme}
                        onChange={(e) => {
                            const next = e.target.value as MapTheme;
                            if (next === 'default') {
                                setIsAutoTheme(true);
                                return;
                            }
                            setTheme(next);
                            setIsAutoTheme(false);
                        }}
                    >
                        <option value="default">자동</option>
                        {MAP_THEMES.filter((themeOption) => themeOption.key !== 'default').map((themeOption) => (
                            <option key={themeOption.key} value={themeOption.key}>
                                {themeOption.label}
                            </option>
                        ))}
                    </select>
                    <span className="ml-1 text-muted-foreground">레이어</span>
                    {[
                        { key: 'nations' as MapLayer, label: '국가색' },
                        { key: 'troops' as MapLayer, label: '병력' },
                        { key: 'supply' as MapLayer, label: '보급' },
                        { key: 'terrain' as MapLayer, label: '지형' },
                    ].map((layer) => (
                        <Button
                            key={layer.key}
                            size="sm"
                            variant={layers.has(layer.key) ? 'default' : 'outline'}
                            className="h-6 px-2 text-[11px]"
                            onClick={() => toggleLayer(layer.key)}
                        >
                            {layer.label}
                        </Button>
                    ))}
                </div>

                {history.length > 0 &&
                    (() => {
                        // Extract unique year/month pairs from history
                        const yearMonthSet = new Map<string, { year: number; month: number }>();
                        for (const h of history) {
                            if (h.year != null && h.month != null) {
                                const key = `${h.year}-${h.month}`;
                                if (!yearMonthSet.has(key)) yearMonthSet.set(key, { year: h.year, month: h.month });
                            }
                        }
                        const yearMonthList = Array.from(yearMonthSet.values()).sort(
                            (a, b) => b.year - a.year || b.month - a.month
                        );
                        const availableYears = [...new Set(yearMonthList.map((ym) => ym.year))].sort((a, b) => b - a);

                        // Filter logic
                        const filteredHistory =
                            historyFilterYear !== null
                                ? history.filter(
                                      (h) =>
                                          h.year === historyFilterYear &&
                                          (historyFilterMonth === null || h.month === historyFilterMonth)
                                  )
                                : history;

                        const monthsForYear =
                            historyFilterYear !== null
                                ? yearMonthList
                                      .filter((ym) => ym.year === historyFilterYear)
                                      .map((ym) => ym.month)
                                      .sort((a, b) => a - b)
                                : [];

                        const displayItems =
                            historyBrowseIdx !== null
                                ? filteredHistory.slice(historyBrowseIdx, historyBrowseIdx + 10)
                                : filteredHistory.slice(0, 10);

                        return (
                            <div className="mt-3 border-t border-gray-800 pt-3 space-y-2">
                                <div className="text-sm font-semibold">최근 기록</div>
                                {/* Year/Month filter */}
                                <div className="flex flex-wrap items-center gap-2">
                                    <span className="text-xs text-muted-foreground">년도:</span>
                                    <Button
                                        size="sm"
                                        variant={historyFilterYear === null ? 'default' : 'outline'}
                                        className="h-6 px-2 text-xs"
                                        onClick={() => {
                                            setHistoryFilterYear(null);
                                            setHistoryFilterMonth(null);
                                            setHistoryBrowseIdx(null);
                                        }}
                                    >
                                        전체
                                    </Button>
                                    {availableYears.map((y) => (
                                        <Button
                                            key={y}
                                            size="sm"
                                            variant={historyFilterYear === y ? 'default' : 'outline'}
                                            className="h-6 px-2 text-xs"
                                            onClick={() => {
                                                setHistoryFilterYear(y);
                                                setHistoryFilterMonth(null);
                                                setHistoryBrowseIdx(null);
                                            }}
                                        >
                                            {y}년
                                        </Button>
                                    ))}
                                    {historyFilterYear !== null && monthsForYear.length > 0 && (
                                        <>
                                            <span className="text-xs text-muted-foreground ml-2">월:</span>
                                            <Button
                                                size="sm"
                                                variant={historyFilterMonth === null ? 'default' : 'outline'}
                                                className="h-6 px-2 text-xs"
                                                onClick={() => {
                                                    setHistoryFilterMonth(null);
                                                    setHistoryBrowseIdx(null);
                                                }}
                                            >
                                                전체
                                            </Button>
                                            {monthsForYear.map((m) => (
                                                <Button
                                                    key={m}
                                                    size="sm"
                                                    variant={historyFilterMonth === m ? 'default' : 'outline'}
                                                    className="h-6 px-2 text-xs"
                                                    onClick={() => {
                                                        setHistoryFilterMonth(m);
                                                        setHistoryBrowseIdx(null);
                                                    }}
                                                >
                                                    {m}월
                                                </Button>
                                            ))}
                                        </>
                                    )}
                                </div>

                                {/* Pagination controls */}
                                <div className="flex items-center gap-1">
                                    <Button
                                        size="sm"
                                        variant="ghost"
                                        className="h-6 px-2 text-xs"
                                        disabled={historyBrowseIdx === null || historyBrowseIdx <= 0}
                                        onClick={() =>
                                            setHistoryBrowseIdx((prev) =>
                                                Math.max(0, (prev ?? filteredHistory.length) - 10)
                                            )
                                        }
                                    >
                                        ← 이전
                                    </Button>
                                    <span className="text-[10px] text-muted-foreground">
                                        {historyBrowseIdx !== null
                                            ? `${historyBrowseIdx + 1}~${Math.min(historyBrowseIdx + 10, filteredHistory.length)}`
                                            : `1~${Math.min(10, filteredHistory.length)}`}{' '}
                                        / {filteredHistory.length}
                                    </span>
                                    <Button
                                        size="sm"
                                        variant="ghost"
                                        className="h-6 px-2 text-xs"
                                        disabled={
                                            (historyBrowseIdx === null && filteredHistory.length <= 10) ||
                                            (historyBrowseIdx !== null &&
                                                historyBrowseIdx + 10 >= filteredHistory.length)
                                        }
                                        onClick={() =>
                                            setHistoryBrowseIdx((prev) =>
                                                Math.min(filteredHistory.length - 10, (prev ?? 0) + 10)
                                            )
                                        }
                                    >
                                        다음 →
                                    </Button>
                                    {historyBrowseIdx !== null && (
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            className="h-6 px-2 text-xs"
                                            onClick={() => setHistoryBrowseIdx(null)}
                                        >
                                            최신으로
                                        </Button>
                                    )}
                                </div>

                                {/* History items */}
                                <div className="max-h-64 overflow-y-auto space-y-0.5 text-xs">
                                    {displayItems.map((item) => (
                                        <div key={item.id} className="py-0.5 text-gray-300">
                                            <span
                                                className={
                                                    getHistoryBullet(item.text) === '◆'
                                                        ? 'text-sky-300'
                                                        : 'text-cyan-300'
                                                }
                                            >
                                                {item.year != null && item.month != null
                                                    ? `${getHistoryBullet(item.text)}${item.year}년 ${item.month}월:`
                                                    : `${getHistoryBullet(item.text)}`}
                                            </span>{' '}
                                            <span>{formatLog(item.text)}</span>
                                        </div>
                                    ))}
                                    {displayItems.length === 0 && (
                                        <div className="text-muted-foreground py-2">해당 기간의 기록이 없습니다.</div>
                                    )}
                                </div>
                            </div>
                        );
                    })()}
            </CardContent>
        </Card>
    );
}
