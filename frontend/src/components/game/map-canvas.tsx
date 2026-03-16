'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
    getCityLevelIcon,
    getEventIcon,
    getMapBgUrl,
    getMapRoadUrl,
    getNationBgGradient,
    getSpecialEventIcon,
} from '@/lib/image';
import { FactionFlag } from '@/components/game/faction-flag';
import { detailMapCitySizes, MAP_WIDTH, MAP_HEIGHT, SEASON_LABELS, type MapSeason } from '@/lib/map-constants';

// --- RenderCity: normalized shape consumed by the canvas ---
export interface RenderCity {
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

export interface CityOverlay {
    generalCount?: number;
    hasForeignTroops?: boolean;
    isSupplyBroken?: boolean;
    terrainLevel?: number;
}

export interface MapCanvasProps {
    cities: RenderCity[];
    mapCode: string;
    season: MapSeason;
    yearMonth: { year: number; month: number } | null;
    showNames?: boolean;
    onShowNamesChange?: (v: boolean) => void;
    interactive?: boolean;
    compact?: boolean;
    showNationBlotch?: boolean;
    showSeasonBackground?: boolean;
    cityOverlays?: Map<number, CityOverlay>;
    renderTooltip?: (city: RenderCity, pos: { x: number; y: number }) => React.ReactNode;
    tooltipTrigger?: 'hover' | 'hover-delayed';
    tooltipHideDelay?: number;
    onCityClick?: (cityId: number, e: React.MouseEvent) => void;
    onCityTouch?: (cityId: number) => void;
    /** Custom city-name positioning function (used by full map page) */
    cityNamePosition?: (city: RenderCity, markerLeft: number, markerTop: number) => { left: number; top: number };
    /** Theme text color for city names (default: #ccc) */
    cityNameColor?: string;
    className?: string;
    themeColors?: { bg: string };
    /** Whether to use responsive scaling (ResizeObserver). Default: true for non-compact. */
    useResponsiveScale?: boolean;
    /** Extra elements to render on the dismiss layer (z-[2]) */
    dismissOverlay?: React.ReactNode;
}

// Constants for full map page overlays
const CITY_HIT_WIDTH = 40;
const CITY_HIT_HEIGHT = 30;
const CITY_RING_SIZE = 20;

export function MapCanvas({
    cities,
    mapCode,
    season,
    yearMonth,
    showNames = true,
    onShowNamesChange,
    interactive = true,
    compact = false,
    showNationBlotch = true,
    showSeasonBackground = true,
    cityOverlays,
    renderTooltip,
    tooltipTrigger = 'hover',
    tooltipHideDelay = 120,
    onCityClick,
    onCityTouch,
    cityNamePosition,
    cityNameColor = '#ccc',
    className,
    themeColors,
    useResponsiveScale: useResponsiveScaleProp,
    dismissOverlay,
}: MapCanvasProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const [mapScale, setMapScale] = useState(1);
    const [hoveredCity, setHoveredCity] = useState<{ city: RenderCity; x: number; y: number } | null>(null);
    const tooltipHideTimerRef = useRef<number | null>(null);

    // Determine scaling mode
    const useResponsive = useResponsiveScaleProp ?? !compact;

    const coordScale = useResponsive ? 1 : compact ? 500 / 700 : 1;
    const innerW = useResponsive ? MAP_WIDTH : compact ? 500 : MAP_WIDTH;
    const innerH = useResponsive ? MAP_HEIGHT : compact ? 357.14 : MAP_HEIGHT;

    const mapFolder = mapCode.includes('miniche') ? 'che' : mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode;
    const mapBgUrl = showSeasonBackground ? getMapBgUrl(mapFolder, season) : null;
    const mapRoadUrl = getMapRoadUrl(mapCode);

    // ResizeObserver for responsive scaling
    useEffect(() => {
        if (!useResponsive) return;
        const el = containerRef.current;
        if (!el) return;
        const ro = new ResizeObserver((entries) => {
            for (const entry of entries) {
                setMapScale(entry.contentRect.width / MAP_WIDTH);
            }
        });
        ro.observe(el);
        return () => ro.disconnect();
    }, [useResponsive]);

    // Cleanup tooltip hide timer
    useEffect(() => {
        return () => {
            if (tooltipHideTimerRef.current !== null) {
                window.clearTimeout(tooltipHideTimerRef.current);
            }
        };
    }, []);

    const clearTooltipHideTimer = useCallback(() => {
        if (tooltipHideTimerRef.current !== null) {
            window.clearTimeout(tooltipHideTimerRef.current);
            tooltipHideTimerRef.current = null;
        }
    }, []);

    const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const x = e.clientX - rect.left + e.currentTarget.scrollLeft;
        const y = e.clientY - rect.top + e.currentTarget.scrollTop;
        setHoveredCity((prev) => (prev ? { ...prev, x, y } : null));
    }, []);

    const handleCityMouseEnter = useCallback(
        (city: RenderCity, e?: React.MouseEvent) => {
            clearTooltipHideTimer();
            if (e) {
                // For hover-delayed (DetailTooltip) uses clientX/Y; for hover (CompactTooltip) uses map coords
                if (tooltipTrigger === 'hover-delayed') {
                    setHoveredCity({ city, x: e.clientX, y: e.clientY });
                } else {
                    setHoveredCity({ city, x: city.x * coordScale, y: city.y * coordScale });
                }
            } else {
                setHoveredCity({ city, x: city.x * coordScale, y: city.y * coordScale });
            }
        },
        [clearTooltipHideTimer, coordScale, tooltipTrigger]
    );

    const handleCityMouseMove = useCallback(
        (_city: RenderCity, e: React.MouseEvent) => {
            if (tooltipTrigger === 'hover-delayed') {
                setHoveredCity((prev) => {
                    if (!prev || prev.city.id !== _city.id) return prev;
                    return { ...prev, x: e.clientX, y: e.clientY };
                });
            }
        },
        [tooltipTrigger]
    );

    const handleCityMouseLeave = useCallback(() => {
        if (tooltipTrigger === 'hover-delayed' && tooltipHideDelay > 0) {
            clearTooltipHideTimer();
            tooltipHideTimerRef.current = window.setTimeout(() => {
                setHoveredCity(null);
            }, tooltipHideDelay);
        } else {
            setHoveredCity(null);
        }
    }, [tooltipTrigger, tooltipHideDelay, clearTooltipHideTimer]);

    const outerStyle: React.CSSProperties = useResponsive
        ? { backgroundColor: themeColors?.bg ?? '#111827', aspectRatio: '700 / 500' }
        : { maxWidth: innerW, height: innerH, margin: '0 auto' };

    const innerStyle: React.CSSProperties = useResponsive
        ? {
              width: MAP_WIDTH,
              height: MAP_HEIGHT,
              transform: `scale(${mapScale})`,
              transformOrigin: 'top left',
              position: 'relative',
          }
        : {
              width: innerW,
              height: innerH,
              position: 'relative',
          };

    return (
        <div
            ref={containerRef}
            className={`relative text-[14px] text-white ${useResponsive ? 'border border-gray-800 rounded-lg' : 'w-full bg-black'} ${className ?? ''}`}
            style={outerStyle}
        >
            <div style={innerStyle}>
                {/* Year/Season header */}
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

                {/* Background */}
                {mapBgUrl && (
                    <div
                        className="absolute inset-0 z-0 bg-no-repeat bg-center"
                        style={{
                            backgroundImage: `url('${mapBgUrl}')`,
                            backgroundSize: `${innerW}px ${innerH}px`,
                        }}
                    />
                )}
                {/* Road overlay */}
                <div
                    className="absolute inset-0 z-[1] bg-no-repeat bg-center"
                    style={{
                        backgroundImage: `url('${mapRoadUrl}')`,
                        backgroundSize: `${innerW}px ${innerH}px`,
                    }}
                />

                {/* Dismiss overlay (for touch dismiss on full map page) */}
                {dismissOverlay && <div className="absolute inset-0 z-[2]">{dismissOverlay}</div>}

                {/* Blotch layer */}
                {showNationBlotch && (
                    <div className="absolute inset-0 z-[2] pointer-events-none">
                        {cities.map((city) => {
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
                )}

                {/* City layer */}
                <div
                    className="absolute inset-0 z-[3]"
                    onMouseMove={tooltipTrigger === 'hover' ? handleMouseMove : undefined}
                    role="application"
                    aria-label="city map"
                >
                    {cities.map((city) => {
                        const sizes = detailMapCitySizes[city.level] || detailMapCitySizes[1];
                        const icnW = sizes[2] * coordScale;
                        const icnH = sizes[3] * coordScale;
                        const flagR = sizes[4];
                        const flagT = sizes[5];
                        const hitW = CITY_HIT_WIDTH * coordScale;
                        const hitH = CITY_HIT_HEIGHT * coordScale;
                        const left = city.x * coordScale - 20 * coordScale;
                        const top = city.y * coordScale - 15 * coordScale;
                        const nearRight = city.x * coordScale > innerW - 60;

                        const overlay = cityOverlays?.get(city.id);
                        const showNationFlag = city.nationColor != null;

                        const Tag = 'button';
                        const interactiveProps = interactive
                            ? {
                                  type: 'button' as const,
                                  onClick: (e: React.MouseEvent) => {
                                      e.stopPropagation();
                                      onCityClick?.(city.id, e);
                                  },
                              }
                            : { type: 'button' as const };

                        return (
                            <Tag
                                key={city.id}
                                className={`absolute overflow-visible border-0 bg-transparent p-0 text-left ${interactive ? 'cursor-pointer appearance-none' : ''}`}
                                style={{ left, top, width: hitW, height: hitH }}
                                onMouseEnter={(e) => handleCityMouseEnter(city, e)}
                                onMouseMove={
                                    tooltipTrigger === 'hover-delayed' ? (e) => handleCityMouseMove(city, e) : undefined
                                }
                                onMouseLeave={handleCityMouseLeave}
                                onTouchEnd={
                                    onCityTouch
                                        ? (e) => {
                                              e.preventDefault();
                                              e.stopPropagation();
                                              onCityTouch(city.id);
                                          }
                                        : undefined
                                }
                                {...interactiveProps}
                            >
                                {/* Foreign troops ring */}
                                {overlay?.hasForeignTroops && (
                                    <span
                                        className="absolute z-[3] animate-spin rounded-full border-2 border-dashed border-red-500"
                                        style={{
                                            width: CITY_RING_SIZE + 8,
                                            height: CITY_RING_SIZE + 8,
                                            left: (hitW - (CITY_RING_SIZE + 8)) / 2,
                                            top: (hitH - (CITY_RING_SIZE + 8)) / 2,
                                        }}
                                    />
                                )}

                                {/* Supply broken ring */}
                                {overlay?.isSupplyBroken && (
                                    <span
                                        className="absolute z-[3] rounded-full border border-dashed border-amber-500"
                                        style={{
                                            width: CITY_RING_SIZE + 12,
                                            height: CITY_RING_SIZE + 12,
                                            left: (hitW - (CITY_RING_SIZE + 12)) / 2,
                                            top: (hitH - (CITY_RING_SIZE + 12)) / 2,
                                        }}
                                    />
                                )}

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

                                        {/* My city pulse ring */}
                                        {city.isMyCity && (
                                            <div className="absolute -inset-[2px] rounded-[33%] border-[4px] border-solid border-red-500 animate-pulse" />
                                        )}

                                        {/* Nation flag */}
                                        {showNationFlag && (
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
                                                    color={city.nationColor!}
                                                    supplied={city.supplyState > 0}
                                                    className="w-full h-full block"
                                                />
                                                {/* Capital icon */}
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

                                        {/* City name (default positioning) */}
                                        {showNames && !cityNamePosition && (
                                            <span
                                                className="absolute whitespace-nowrap px-1 py-[1px] bg-black/60 backdrop-blur-[2px] text-[10px] rounded-sm"
                                                style={
                                                    nearRight
                                                        ? { right: '70%', bottom: -10, color: cityNameColor }
                                                        : { left: '70%', bottom: -10, color: cityNameColor }
                                                }
                                            >
                                                {city.name}
                                            </span>
                                        )}
                                    </div>

                                    {/* Event icon */}
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

                                    {/* General count badge */}
                                    {overlay?.generalCount != null && overlay.generalCount > 0 && (
                                        <span className="absolute -right-1 -top-1 z-[5] min-w-4 h-4 rounded-full bg-black/90 border border-gray-500 px-1 text-[9px] leading-4 text-white text-center font-bold">
                                            {overlay.generalCount}
                                        </span>
                                    )}

                                    {/* Terrain level badge */}
                                    {overlay?.terrainLevel != null && overlay.terrainLevel > 0 && (
                                        <span className="absolute -left-1 -top-1 z-[5] rounded bg-purple-900/80 border border-purple-400 px-1 text-[8px] leading-3 text-purple-100">
                                            Lv{overlay.terrainLevel}
                                        </span>
                                    )}

                                    {/* City name (custom positioning for full map page) */}
                                    {showNames && cityNamePosition && (
                                        <span
                                            className="absolute whitespace-nowrap px-1 py-[1px] bg-black/60 backdrop-blur-[2px] text-[10px] rounded-sm"
                                            style={{
                                                ...cityNamePosition(city, left, top),
                                                color: cityNameColor,
                                            }}
                                        >
                                            {city.name}
                                        </span>
                                    )}
                                </div>
                            </Tag>
                        );
                    })}
                </div>

                {/* Tooltip */}
                {hoveredCity && renderTooltip?.(hoveredCity.city, { x: hoveredCity.x, y: hoveredCity.y })}

                {/* City name toggle button */}
                {onShowNamesChange && (
                    <button
                        type="button"
                        className="absolute bottom-2 right-2 z-[5] px-2 py-1 text-[11px] rounded bg-blue-600 hover:bg-blue-700 text-white border-0 cursor-pointer shadow"
                        onClick={(e) => {
                            e.stopPropagation();
                            onShowNamesChange(!showNames);
                        }}
                    >
                        {showNames ? '도시명 표기 끄기' : '도시명 표기 켜기'}
                    </button>
                )}
            </div>
        </div>
    );
}
