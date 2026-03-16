'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useGameStore } from '@/stores/gameStore';
import { useWorldStore } from '@/stores/worldStore';
import {
    GAME_CDN_ROOT,
    getNationBgGradient,
    getCityLevelIcon,
    getEventIcon,
    getSpecialEventIcon,
    getMapBgUrl,
    getMapRoadUrl,
} from '@/lib/image';
import { REGION_NAMES, CITY_LEVEL_NAMES, isBrightColor } from '@/lib/game-utils';
import { useGeneralStore } from '@/stores/generalStore';
import { FactionFlag } from '@/components/game/faction-flag';
import type { PublicCachedMapResponse } from '@/types';

// --- Normalized city shape used internally by the renderer ---
interface RenderCity {
    id: number;
    name: string;
    x: number;
    y: number;
    level: number;
    region?: number;
    nationColor: string | null;
    nationName: string | null;
    nationAbbr: string | null;
    isCapital: boolean;
    supplyState: number;
    state: number;
    isMyCity: boolean;
    isEmperorCity: boolean;
}

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

const detailMapCitySizes: Record<number, number[]> = {
    1: [48, 45, 16, 15, -8, -4],
    2: [60, 42, 20, 14, -8, -4],
    3: [42, 42, 14, 14, -8, -4],
    4: [60, 45, 20, 15, -6, -3],
    5: [72, 48, 24, 16, -6, -4],
    6: [78, 54, 26, 18, -6, -4],
    7: [84, 60, 28, 20, -6, -4],
    8: [96, 72, 32, 24, -6, -3],
};

const MAP_WIDTH = 700;
const MAP_HEIGHT = 500;

type MapSeason = 'spring' | 'summer' | 'fall' | 'winter';

function getSeason(month: number | null | undefined): MapSeason {
    if (!month) return 'spring';
    if (month <= 3) return 'spring';
    if (month <= 6) return 'summer';
    if (month <= 9) return 'fall';
    return 'winter';
}

const SEASON_LABELS: Record<string, string> = {
    spring: '春',
    summer: '夏',
    fall: '秋',
    winter: '冬',
};

const CITY_STATE_NAMES: Record<number, string> = {
    1: '풍작',
    2: '호황',
    3: '한파/폭설',
    4: '역병',
    5: '지진',
    6: '태풍',
    7: '홍수',
    8: '메뚜기/흉년',
    9: '황건적',
    31: '파괴',
    32: '파괴',
    33: '약탈',
    34: '약탈',
    41: '분쟁중',
    42: '분쟁중',
    43: '분쟁중',
};

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
    const { cities: storeCities, nations, generals, mapData, loadAll, loadMap } = useGameStore();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const containerRef = useRef<HTMLDivElement>(null);
    const [mapScale, setMapScale] = useState(1);
    const [showNames, setShowNames] = useState(!compact);
    const [tooltip, setTooltip] = useState<{
        cityText: string;
        nationText: string | null;
        nationAbbr: string | null;
        nationColor: string | null;
        stateText: string | null;
        stateCode: number;
        isEmperorCity: boolean;
        x: number;
        y: number;
    } | null>(null);

    const isPublicMode = !!publicData;
    const mapCode = useMemo(
        () => mapCodeProp ?? (isPublicMode ? (publicData.mapCode ?? 'che').trim() || 'che' : 'che'),
        [mapCodeProp, isPublicMode, publicData?.mapCode]
    );

    useEffect(() => {
        if (!isPublicMode && worldId != null) {
            loadAll(worldId);
            loadMap(mapCode);
        }
    }, [worldId, mapCode, loadAll, loadMap, isPublicMode]);

    useEffect(() => {
        if (!isPublicMode) return;
        const el = containerRef.current;
        if (!el) return;
        const ro = new ResizeObserver((entries) => {
            for (const entry of entries) {
                setMapScale(entry.contentRect.width / MAP_WIDTH);
            }
        });
        ro.observe(el);
        return () => ro.disconnect();
    }, [isPublicMode]);

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
                nationAbbr: nation?.abbreviation || null,
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

    const mapFolder = mapCode.includes('miniche') ? 'che' : mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode;
    const mapBgUrl = getMapBgUrl(mapFolder, season);
    const mapRoadUrl = getMapRoadUrl(mapCode);
    const coordScale = isPublicMode ? 1 : compact ? 500 / 700 : 1;

    const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const x = e.clientX - rect.left + e.currentTarget.scrollLeft;
        const y = e.clientY - rect.top + e.currentTarget.scrollTop;
        setTooltip((prev) => (prev ? { ...prev, x, y } : null));
    }, []);

    const handleCityMouseEnter = useCallback(
        (city: RenderCity) => {
            const regionName = city.region != null ? (REGION_NAMES[city.region] ?? '중원') : '';
            const levelName = CITY_LEVEL_NAMES[city.level] ?? '';
            const prefix = regionName ? `【${regionName}|${levelName}】` : `【${levelName}】`;
            const stateName = city.state > 0 ? (CITY_STATE_NAMES[city.state] ?? `상태${city.state}`) : null;
            setTooltip({
                cityText: `${prefix}${city.name}`,
                nationText: city.nationName,
                nationAbbr: city.nationAbbr,
                nationColor: city.nationColor,
                stateText: stateName ? `⚠ ${stateName}` : null,
                stateCode: city.state,
                isEmperorCity: city.isEmperorCity,
                x: city.x * coordScale,
                y: city.y * coordScale,
            });
        },
        [coordScale]
    );

    const handleCityMouseLeave = useCallback(() => {
        setTooltip(null);
    }, []);

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

    if (!isPublicMode && !mapData) {
        return (
            <div className="flex items-center justify-center h-32 text-xs text-muted-foreground">지도 로딩중...</div>
        );
    }

    const useResponsiveScale = isPublicMode;
    const smV = useResponsiveScale
        ? 1
        : compact
          ? (MAP_WIDTH / MAP_HEIGHT / (MAP_WIDTH / MAP_HEIGHT)) * (500 / 700)
          : 1;
    const innerW = useResponsiveScale ? MAP_WIDTH : compact ? 500 : MAP_WIDTH;
    const innerH = useResponsiveScale ? MAP_HEIGHT : compact ? 357.14 : MAP_HEIGHT;

    const outerStyle = useResponsiveScale
        ? { backgroundColor: '#111827', aspectRatio: '700 / 500' }
        : { maxWidth: innerW, height: innerH, margin: '0 auto' };

    const innerStyle = useResponsiveScale
        ? {
              width: MAP_WIDTH,
              height: MAP_HEIGHT,
              transform: `scale(${mapScale})`,
              transformOrigin: 'top left' as const,
              position: 'relative' as const,
          }
        : {
              width: innerW,
              height: innerH,
              position: 'relative' as const,
          };

    return (
        <div
            ref={containerRef}
            className={`relative text-[14px] text-white ${useResponsiveScale ? 'border border-gray-800 rounded-lg' : 'w-full bg-black'}`}
            style={outerStyle}
        >
            <div style={innerStyle}>
                {yearMonth && (
                    <div className="absolute top-0 left-0 right-0 z-[4] text-center py-1 pointer-events-none">
                        <span
                            className="text-white text-sm font-bold drop-shadow-lg"
                            style={{ textShadow: '1px 1px 2px rgba(0,0,0,0.8)' }}
                        >
                            西紀 {yearMonth.year}年 {yearMonth.month}月 {SEASON_LABELS[season] ?? ''}
                        </span>
                    </div>
                )}

                <div
                    className="absolute inset-0 z-0 bg-no-repeat bg-center"
                    style={{
                        backgroundImage: `url('${mapBgUrl}')`,
                        backgroundSize: `${innerW}px ${innerH}px`,
                    }}
                />
                <div
                    className="absolute inset-0 z-[1] bg-no-repeat bg-center"
                    style={{
                        backgroundImage: `url('${mapRoadUrl}')`,
                        backgroundSize: `${innerW}px ${innerH}px`,
                    }}
                />

                {/* Blotch layer */}
                <div className="absolute inset-0 z-[2] pointer-events-none">
                    {renderCities.map((city) => {
                        if (!city.nationColor) return null;
                        const sizes = detailMapCitySizes[city.level] || detailMapCitySizes[1];
                        const bgW = sizes[0] * coordScale;
                        const bgH = sizes[1] * coordScale;
                        const left = city.x * coordScale - 20 * coordScale;
                        const top = city.y * coordScale - 15 * coordScale;
                        const hitW = 40 * coordScale;
                        const hitH = 30 * coordScale;

                        return (
                            <div
                                key={city.id}
                                className="absolute overflow-visible"
                                style={{ left, top, width: hitW, height: hitH }}
                            >
                                <div
                                    className="absolute"
                                    style={{
                                        background: getNationBgGradient(city.nationColor),
                                        width: bgW,
                                        height: bgH,
                                        left: (hitW - bgW) / 2,
                                        top: (hitH - bgH) / 2,
                                    }}
                                />
                            </div>
                        );
                    })}
                </div>

                {/* City layer */}
                <div
                    className="absolute inset-0 z-[3]"
                    onMouseMove={handleMouseMove}
                    role="application"
                    aria-label="city map"
                >
                    {renderCities.map((city) => {
                        const sizes = detailMapCitySizes[city.level] || detailMapCitySizes[1];
                        const icnW = sizes[2] * coordScale;
                        const icnH = sizes[3] * coordScale;
                        const flagR = sizes[4];
                        const flagT = sizes[5];
                        const hitW = 40 * coordScale;
                        const hitH = 30 * coordScale;
                        const left = city.x * coordScale - 20 * coordScale;
                        const top = city.y * coordScale - 15 * coordScale;
                        const nearRight = city.x * coordScale > innerW - 60;

                        const Tag = 'button';
                        const interactiveProps = interactive
                            ? {
                                  type: 'button' as const,
                                  onClick: (e: React.MouseEvent) => handleCityClick(city.id, e),
                              }
                            : { type: 'button' as const };

                        return (
                            <Tag
                                key={city.id}
                                className={`absolute overflow-visible border-0 bg-transparent p-0 text-left ${interactive ? 'cursor-pointer appearance-none' : ''}`}
                                style={{ left, top, width: hitW, height: hitH }}
                                onMouseEnter={() => handleCityMouseEnter(city)}
                                onMouseLeave={handleCityMouseLeave}
                                {...interactiveProps}
                            >
                                <div className="absolute inset-0">
                                    <div
                                        className="absolute"
                                        style={{
                                            width: icnW,
                                            height: icnH,
                                            left: (hitW - icnW) / 2,
                                            top: (hitH - icnH) / 2,
                                        }}
                                    >
                                        <img
                                            src={getCityLevelIcon(city.level)}
                                            className="w-full h-full block"
                                            alt=""
                                        />

                                        {city.isMyCity && (
                                            <div className="absolute -inset-[2px] rounded-[33%] border-[4px] border-solid border-red-500 animate-pulse" />
                                        )}

                                        {city.nationColor && (
                                            <div
                                                className="absolute"
                                                style={{
                                                    right: flagR,
                                                    top: flagT,
                                                    width: 12 * coordScale,
                                                    height: 12 * coordScale,
                                                }}
                                            >
                                                <FactionFlag
                                                    color={city.nationColor}
                                                    supplied={city.supplyState > 0}
                                                    className="w-full h-full block"
                                                />
                                                {city.isCapital && (
                                                    <div
                                                        className="absolute"
                                                        style={{
                                                            right: -1,
                                                            top: 0,
                                                            width: 10 * coordScale,
                                                            height: 10 * coordScale,
                                                        }}
                                                    >
                                                        <img
                                                            src={getSpecialEventIcon(51)}
                                                            className="w-full h-full block"
                                                            alt="수도"
                                                        />
                                                    </div>
                                                )}
                                            </div>
                                        )}

                                        {showNames && (
                                            <span
                                                className="absolute whitespace-nowrap px-[2px] py-[1px] bg-black/55 text-[10px] text-[#ccc]"
                                                style={
                                                    nearRight
                                                        ? { right: '70%', bottom: -10 }
                                                        : { left: '70%', bottom: -10 }
                                                }
                                            >
                                                {city.name}
                                            </span>
                                        )}
                                    </div>

                                    {city.state > 0 && (
                                        <div className="absolute left-0" style={{ top: 5 * coordScale }}>
                                            <img
                                                src={getEventIcon(city.state)}
                                                className="object-contain"
                                                style={{ width: 10 * coordScale }}
                                                alt=""
                                            />
                                        </div>
                                    )}
                                </div>
                            </Tag>
                        );
                    })}
                </div>

                {tooltip &&
                    (() => {
                        const abbr = (tooltip.nationAbbr || tooltip.nationText || '').slice(0, 1);
                        const flagSize = 16;
                        const textColor = tooltip.nationColor && isBrightColor(tooltip.nationColor) ? 'black' : 'white';
                        return (
                            <div
                                className="absolute z-[16] pointer-events-none whitespace-nowrap text-[13px] rounded overflow-hidden shadow-lg"
                                style={{
                                    top: Math.min(tooltip.y + 30, (useResponsiveScale ? MAP_HEIGHT : innerH) - 50),
                                    left: Math.min(tooltip.x + 10, (useResponsiveScale ? MAP_WIDTH : innerW) - 140),
                                    border: '1px solid rgba(255,255,255,0.15)',
                                    minWidth: 120,
                                }}
                            >
                                <div
                                    className="px-1.5 font-medium text-white"
                                    style={{
                                        backgroundColor: 'rgb(30, 140, 230)',
                                        lineHeight: '18px',
                                        height: 18,
                                    }}
                                >
                                    {tooltip.cityText}
                                </div>
                                {tooltip.nationText && (
                                    <div
                                        className="px-1.5 flex items-center gap-1.5 text-white font-bold"
                                        style={{
                                            backgroundColor: 'rgba(20, 20, 30, 0.92)',
                                            lineHeight: '20px',
                                            height: 20,
                                            borderTop: '1px solid rgba(255,255,255,0.08)',
                                        }}
                                    >
                                        {tooltip.isEmperorCity ? (
                                            <img src="/icons/emperor.png" alt="황제" width={14} height={14} />
                                        ) : (
                                            <span
                                                className="inline-flex items-center justify-center font-bold shrink-0"
                                                style={{
                                                    width: flagSize,
                                                    height: flagSize,
                                                    backgroundColor: tooltip.nationColor ?? '#666',
                                                    color: textColor,
                                                    fontSize: 10,
                                                    lineHeight: 1,
                                                    borderRadius: 2,
                                                    textShadow:
                                                        textColor === 'black' ? 'none' : '0 1px 2px rgba(0,0,0,0.5)',
                                                }}
                                            >
                                                {abbr}
                                            </span>
                                        )}
                                        <span style={{ textShadow: '0 0 4px rgba(0,0,0,0.8)' }}>
                                            {tooltip.nationText}
                                        </span>
                                    </div>
                                )}
                                {tooltip.stateText && (
                                    <div
                                        className="px-1.5 text-right text-white"
                                        style={{
                                            backgroundColor:
                                                tooltip.stateCode > 0 && tooltip.stateCode <= 2
                                                    ? 'rgb(46, 143, 70)'
                                                    : 'rgb(180, 40, 40)',
                                            lineHeight: '17px',
                                            height: 17,
                                            borderTop: '1px solid rgba(255,255,255,0.08)',
                                        }}
                                    >
                                        {tooltip.stateText}
                                    </div>
                                )}
                            </div>
                        );
                    })()}

                <button
                    type="button"
                    className="absolute bottom-2 right-2 z-[5] px-2 py-1 text-[11px] rounded bg-blue-600 hover:bg-blue-700 text-white border-0 cursor-pointer shadow"
                    onClick={() => setShowNames((v) => !v)}
                >
                    {showNames ? '도시명 표기 끄기' : '도시명 표기 켜기'}
                </button>
            </div>
        </div>
    );
}
