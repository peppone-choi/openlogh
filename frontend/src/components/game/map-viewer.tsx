'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import dynamic from 'next/dynamic';
import { useGameStore } from '@/stores/gameStore';
import { useWorldStore } from '@/stores/worldStore';
import { REGION_NAMES, CITY_LEVEL_NAMES, isBrightColor } from '@/lib/game-utils';
import { useGeneralStore } from '@/stores/generalStore';
import { getSeason, CITY_STATE_NAMES, MAP_WIDTH, MAP_HEIGHT } from '@/lib/map-constants';
import type { MapSeason } from '@/lib/map-constants';
import { MapCanvas } from '@/components/game/map-canvas';
import type { RenderCity } from '@/components/game/map-canvas';
import { CompactTooltip } from '@/components/game/map-tooltips';
import type { PublicCachedMapResponse } from '@/types';
import { isWebGLSupported } from '@/lib/battle3d-utils';
import { MapHUD } from '@/components/map-3d/MapHUD';
import { MapTransition } from '@/components/map-3d/MapTransition';

const Map3DScene = dynamic(() => import('@/components/map-3d/Map3DScene').then((m) => ({ default: m.Map3DScene })), {
    ssr: false,
});

interface MapViewerProps {
    /** Mode 1: auto-load from gameStore */
    worldId?: number;
    /** Mode 2: pre-joined public data (lobby) */
    publicData?: PublicCachedMapResponse;

    mapCode?: string;
    compact?: boolean;
    interactive?: boolean;
    overrideCities?: {
        id: number;
        name: string;
        nationId: number;
        supplyState?: number;
        state?: number;
        level?: number;
    }[];

    /** Callback when city clicked (for command arg selection) - overrides default navigation */
    onCitySelect?: (cityId: number) => void;
    /** Callback when nation clicked (for command arg selection) */
    onNationSelect?: (nationId: number) => void;
}

export function MapViewer({
    worldId,
    publicData,
    mapCode: mapCodeProp,
    compact = false,
    interactive = true,
    overrideCities,
    onCitySelect,
    onNationSelect,
}: MapViewerProps) {
    const router = useRouter();
    const { cities: storeCities, nations, generals, mapData, loadMap } = useGameStore();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const [showNames, setShowNames] = useState(!compact);
    const [viewMode, setViewMode] = useState<'2d' | '3d'>('2d');
    const [webglOk] = useState(() => (typeof window !== 'undefined' ? isWebGLSupported() : false));

    const isPublicMode = !!publicData;
    const mapCode = useMemo(() => {
        if (mapCodeProp) return mapCodeProp;
        if (isPublicMode) return (publicData.mapCode ?? 'che').trim() || 'che';
        const worldMapCode = (currentWorld?.config as Record<string, string>)?.mapCode;
        return worldMapCode?.trim() || 'che';
    }, [mapCodeProp, isPublicMode, publicData?.mapCode, currentWorld?.config]);

    useEffect(() => {
        if (!isPublicMode && worldId != null) {
            loadMap(mapCode);
        }
    }, [mapCode, loadMap, isPublicMode, worldId]);

    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);
    const emperorCityId = useMemo(() => generals.find((g) => g.npcState === 10)?.cityId ?? -1, [generals]);

    const renderCities = useMemo<RenderCity[]>(() => {
        if (isPublicMode) {
            return publicData.cities.map((c) => ({
                id: c.id,
                name: c.name,
                x: c.x,
                y: c.y,
                level: c.level,
                region: c.region,
                nationColor: c.nationColor && c.nationColor !== '#4b5563' ? c.nationColor : null,
                nationName: c.nationName || null,
                nationAbbr: (c as { nationAbbr?: string }).nationAbbr || null,
                isCapital: c.isCapital ?? false,
                supplyState: c.supplyState ?? 1,
                state: c.state ?? 0,
                isMyCity: false,
                isEmperorCity: false,
            }));
        }

        const runtimeCities = overrideCities ?? storeCities;
        const rtMap = new Map(runtimeCities.map((c) => [c.name, c]));

        if (!mapData) return [];

        return mapData.cities.map((cc) => {
            const rt = rtMap.get(cc.name);
            const nation = rt?.nationId ? nationMap.get(rt.nationId) : null;
            return {
                id: rt?.id ?? cc.id,
                name: cc.name,
                x: cc.x,
                y: cc.y,
                level: cc.level,
                region: cc.region,
                nationColor: nation?.color ?? null,
                nationName: nation?.name ?? null,
                nationAbbr: nation?.abbreviation || nation?.name?.slice(0, 1) || null,
                isCapital: !!(rt && nation?.capitalCityId === rt.id),
                supplyState: rt?.supplyState ?? 0,
                state: (rt as { state?: number })?.state ?? 0,
                isMyCity: myGeneral?.cityId != null && rt?.id === myGeneral.cityId,
                isEmperorCity: rt?.id === emperorCityId,
            };
        });
    }, [isPublicMode, publicData, storeCities, overrideCities, mapData, nationMap, myGeneral?.cityId, emperorCityId]);

    const season = useMemo<MapSeason>(() => {
        if (isPublicMode) return getSeason(publicData.currentMonth);
        return getSeason(currentWorld?.currentMonth ?? null);
    }, [isPublicMode, publicData?.currentMonth, currentWorld?.currentMonth]);

    const yearMonth = useMemo(() => {
        if (isPublicMode) {
            return publicData.currentYear != null && publicData.currentMonth != null
                ? { year: publicData.currentYear, month: publicData.currentMonth }
                : null;
        }
        const y = currentWorld?.currentYear;
        const m = currentWorld?.currentMonth;
        return y && m ? { year: y, month: m } : null;
    }, [
        isPublicMode,
        publicData?.currentYear,
        publicData?.currentMonth,
        currentWorld?.currentYear,
        currentWorld?.currentMonth,
    ]);

    const handleCityClick = useCallback(
        (cityId: number, e: React.MouseEvent) => {
            if (!interactive) return;
            e.stopPropagation();
            if (onCitySelect) {
                onCitySelect(cityId);
            } else {
                router.push(`/city?id=${cityId}`);
            }
        },
        [interactive, router, onCitySelect]
    );

    const renderTooltipFn = useCallback(
        (city: RenderCity, pos: { x: number; y: number }) => {
            const regionName = city.region != null ? (REGION_NAMES[city.region] ?? '중원') : '';
            const levelName = CITY_LEVEL_NAMES[city.level] ?? '';
            const prefix = regionName ? `【${regionName}|${levelName}】` : `【${levelName}】`;
            const stateName = city.state > 0 ? (CITY_STATE_NAMES[city.state] ?? `상태${city.state}`) : null;
            const textColor = city.nationColor && isBrightColor(city.nationColor) ? 'black' : 'white';
            const boundsW = isPublicMode ? MAP_WIDTH : compact ? 500 : MAP_WIDTH;
            const boundsH = isPublicMode ? MAP_HEIGHT : compact ? 357.14 : MAP_HEIGHT;

            return (
                <CompactTooltip
                    cityText={`${prefix}${city.name}`}
                    nationAbbr={city.nationAbbr}
                    nationColor={city.nationColor}
                    nationText={city.nationName}
                    isEmperorCity={city.isEmperorCity}
                    stateText={stateName ? `⚠ ${stateName}` : null}
                    stateCode={city.state}
                    position={pos}
                    abbrTextColor={textColor}
                    bounds={{ width: boundsW, height: boundsH }}
                />
            );
        },
        [isPublicMode, compact]
    );

    if (!isPublicMode && !mapData) {
        return (
            <div className="flex items-center justify-center h-32 text-xs text-muted-foreground">지도 로딩중...</div>
        );
    }

    if (webglOk) {
        return (
            <div className={`relative ${compact ? 'h-48' : 'h-[500px]'}`}>
                <MapTransition
                    viewMode={viewMode}
                    children2D={
                        <MapCanvas
                            cities={renderCities}
                            mapCode={mapCode}
                            season={season}
                            yearMonth={yearMonth}
                            showNames={showNames}
                            onShowNamesChange={setShowNames}
                            interactive={interactive}
                            compact={compact}
                            renderTooltip={renderTooltipFn}
                            onCityClick={handleCityClick}
                            useResponsiveScale={isPublicMode}
                        />
                    }
                    children3D={
                        <Map3DScene
                            mapCode={mapCode}
                            season={season}
                            cities={renderCities}
                            generals={isPublicMode ? undefined : generals}
                            nations={isPublicMode ? undefined : nations}
                            mapData={isPublicMode ? undefined : (mapData ?? undefined)}
                            onCityClick={(cityId) => {
                                if (onCitySelect) {
                                    onCitySelect(cityId);
                                } else {
                                    router.push(`/city?id=${cityId}`);
                                }
                            }}
                            className="w-full h-full"
                        />
                    }
                />
                <MapHUD viewMode={viewMode} onViewModeChange={setViewMode} season={season} mapCode={mapCode} />
            </div>
        );
    }

    return (
        <div className="relative">
            <MapCanvas
                cities={renderCities}
                mapCode={mapCode}
                season={season}
                yearMonth={yearMonth}
                showNames={showNames}
                onShowNamesChange={setShowNames}
                interactive={interactive}
                compact={compact}
                renderTooltip={renderTooltipFn}
                onCityClick={handleCityClick}
                useResponsiveScale={isPublicMode}
            />
        </div>
    );
}
