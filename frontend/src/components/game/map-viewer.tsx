'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useGameStore } from '@/stores/gameStore';
import { GAME_CDN_ROOT, getNationBgGradient } from '@/lib/image';
import { REGION_NAMES, CITY_LEVEL_NAMES } from '@/lib/game-utils';
import { useGeneralStore } from '@/stores/generalStore';
import { FactionFlag } from '@/components/game/faction-flag';

interface MapViewerProps {
    worldId: number;
    mapCode?: string;
    compact?: boolean;
    /** Override runtime cities (e.g. historical snapshot with modified nationId) */
    overrideCities?: {
        id: number;
        name: string;
        nationId: number;
        supplyState?: number;
        state?: number;
        level?: number;
    }[];
}

// [bgW, bgH, icnW, icnH, flagR, flagT]
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

export function MapViewer({ worldId, mapCode = 'che', compact = false, overrideCities }: MapViewerProps) {
    const router = useRouter();
    const { cities: storeCities, nations, mapData, loadAll, loadMap } = useGameStore();
    const cities = overrideCities ?? storeCities;
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const [showNames, setShowNames] = useState(!compact);
    const [tooltip, setTooltip] = useState<{
        cityText: string;
        nationText: string | null;
        x: number;
        y: number;
    } | null>(null);
    const mapBodyRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        loadAll(worldId);
        loadMap(mapCode);
    }, [worldId, mapCode, loadAll, loadMap]);

    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);

    const cityMap = useMemo(() => new Map(cities.map((c) => [c.name, c])), [cities]);

    const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const x = e.clientX - rect.left + e.currentTarget.scrollLeft;
        const y = e.clientY - rect.top + e.currentTarget.scrollTop;
        setTooltip((prev) => (prev ? { ...prev, x, y } : null));
    }, []);

    const handleCityMouseEnter = useCallback(
        (cc: { id: number; name: string; level: number; region: number }, nationName: string | null) => {
            const regionName = REGION_NAMES[cc.region] ?? '중원';
            const levelName = CITY_LEVEL_NAMES[cc.level] ?? '';
            const cityText = `【${regionName}|${levelName}】${cc.name}`;
            setTooltip({ cityText, nationText: nationName, x: 0, y: 0 });
        },
        []
    );

    const handleCityMouseLeave = useCallback(() => {
        setTooltip(null);
    }, []);

    if (!mapData) {
        return (
            <div className="flex items-center justify-center h-32 text-xs text-muted-foreground">지도 로딩중...</div>
        );
    }

    const smV = compact ? 500 / 700 : 1;
    const containerWidth = compact ? 500 : 700;
    const containerHeight = compact ? 357.14 : 500;

    // Determine Map Season from server config
    const serverMonth =
        (typeof window !== 'undefined'
            ? (() => {
                  try {
                      const raw = localStorage.getItem('opensam:world:month');
                      return raw ? Number(raw) : null;
                  } catch {
                      return null;
                  }
              })()
            : null) ?? 1;
    const month = serverMonth;
    let season = 'spring';
    if (month >= 4 && month <= 6) season = 'summer';
    else if (month >= 7 && month <= 9) season = 'fall';
    else if (month >= 10 && month <= 12) season = 'winter';

    const mapFolder = mapCode.includes('miniche') ? 'che' : mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode;
    const mapRoadImage = mapCode.includes('miniche')
        ? 'miniche_road.png'
        : mapCode === 'ludo_rathowm'
          ? 'road.png'
          : `${mapCode}_road.png`;

    const mapLayerUrl = `${GAME_CDN_ROOT}/map/${mapFolder}/bg_${season}.jpg`;
    const mapRoadUrl = `${GAME_CDN_ROOT}/map/${mapFolder}/${mapRoadImage}`;

    const handleCityClick = (cityId: number, e: React.MouseEvent) => {
        e.stopPropagation();
        router.push(`/city?id=${cityId}`);
    };

    return (
        <div
            className="relative w-full overflow-hidden bg-black text-[14px] text-white"
            style={{
                maxWidth: containerWidth,
                height: containerHeight,
                margin: '0 auto',
            }}
        >
            {!compact && (
                <button
                    type="button"
                    onClick={() => setShowNames(!showNames)}
                    className="absolute right-1 bottom-1 z-10 border border-gray-600 bg-[#111] px-1.5 py-0.5 text-[10px] text-gray-300"
                >
                    {showNames ? '이름 숨김' : '이름 표시'}
                </button>
            )}

            {/* Map Background Layers */}
            <div
                className="absolute inset-0 z-0 bg-no-repeat bg-center"
                style={{
                    backgroundImage: `url('${mapLayerUrl}')`,
                    backgroundSize: `${containerWidth}px ${containerHeight}px`,
                }}
            />
            <div
                className="absolute inset-0 z-[1] bg-no-repeat bg-center"
                style={{
                    backgroundImage: `url('${mapRoadUrl}')`,
                    backgroundSize: `${containerWidth}px ${containerHeight}px`,
                }}
            />

            {/* Map Cities */}
            <div className="absolute inset-0 z-[2]" ref={mapBodyRef} onMouseMove={handleMouseMove}>
                {mapData.cities.map((cc) => {
                    const rtCity = cityMap.get(cc.name);
                    const nation = rtCity?.nationId ? nationMap.get(rtCity.nationId) : null;
                    const myCity = myGeneral?.cityId != null && rtCity?.id === myGeneral.cityId;

                    const sizes = detailMapCitySizes[cc.level] || detailMapCitySizes[1];
                    const bgW = sizes[0] * smV;
                    const bgH = sizes[1] * smV;
                    const icnW = sizes[2] * smV;
                    const icnH = sizes[3] * smV;
                    const flagR = sizes[4];
                    const flagT = sizes[5];

                    const left = cc.x * smV - 20;
                    const top = cc.y * smV - (compact ? 18 : 15);

                    return (
                        <button
                            key={cc.id}
                            type="button"
                            className="absolute h-[30px] w-[40px] cursor-pointer appearance-none border-0 bg-transparent p-0 text-left overflow-visible"
                            style={{ left, top }}
                            onClick={(e) => handleCityClick(cc.id, e)}
                            onMouseEnter={() => handleCityMouseEnter(cc, nation?.name ?? null)}
                            onMouseLeave={handleCityMouseLeave}
                        >
                            {/* Nation Color Blotch Base */}
                            {nation?.color && (
                                <div
                                    className="absolute z-[1] rounded-full"
                                    style={{
                                        background: getNationBgGradient(nation.color),
                                        width: bgW,
                                        height: bgH,
                                        left: (40 - bgW) / 2,
                                        top: (30 - bgH) / 2,
                                    }}
                                />
                            )}

                            <div className="absolute inset-0 z-[2]">
                                {/* City Icon Container */}
                                <div
                                    className="absolute"
                                    style={{
                                        width: icnW,
                                        height: icnH,
                                        left: (40 - icnW) / 2,
                                        top: (30 - icnH) / 2,
                                    }}
                                >
                                    <img
                                        src={`${GAME_CDN_ROOT}/cast_${cc.level}.gif`}
                                        className="w-full h-full block"
                                        alt=""
                                    />

                                    {/* My City Highlight */}
                                    {myCity && (
                                        <div className="absolute -inset-[2px] rounded-[33%] border-[4px] border-solid border-red-500 animate-pulse" />
                                    )}

                                    {/* Nation Flag and Capital Icon */}
                                    {nation && (
                                        <div
                                            className="absolute"
                                            style={{
                                                right: flagR,
                                                top: flagT,
                                                width: 12 * smV,
                                                height: 12 * smV,
                                            }}
                                        >
                                            <FactionFlag
                                                color={nation.color}
                                                supplied={(rtCity?.supplyState ?? 0) > 0}
                                                className="w-full h-full block"
                                            />
                                            {rtCity && nation.capitalCityId === rtCity.id && (
                                                <div
                                                    className="absolute bg-yellow-400"
                                                    style={{
                                                        right: -1,
                                                        top: 0,
                                                        width: 10 * smV,
                                                        height: 10 * smV,
                                                    }}
                                                >
                                                    <img
                                                        src={`${GAME_CDN_ROOT}/event51.gif`}
                                                        className="w-full h-full block"
                                                        alt="capital"
                                                    />
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>

                                {/* City State Icon */}
                                {rtCity && (rtCity.state ?? 0) > 0 && (
                                    <div className="absolute left-0" style={{ top: 5 * smV }}>
                                        <img
                                            src={`${GAME_CDN_ROOT}/event${rtCity.state}.gif`}
                                            className="object-contain"
                                            style={{ width: 10 * smV }}
                                            alt=""
                                        />
                                    </div>
                                )}

                                {/* City Name */}
                                {showNames && (
                                    <span
                                        className={`absolute whitespace-nowrap text-white px-[2px] py-[1px] bg-black/50 ${compact ? 'text-[10px]' : 'text-[10px]'}`}
                                        style={{
                                            left: '70%',
                                            bottom: compact ? -12 : -10,
                                        }}
                                    >
                                        {cc.name}
                                    </span>
                                )}
                            </div>
                        </button>
                    );
                })}
            </div>

            {/* City Tooltip */}
            {tooltip && (
                <div
                    className="absolute z-[16] pointer-events-none whitespace-nowrap text-[14px]"
                    style={{
                        top: tooltip.y + 30,
                        left: tooltip.x + 10,
                        border: '1px solid gray',
                        minWidth: 120,
                    }}
                >
                    <div
                        className="px-1"
                        style={{
                            backgroundColor: 'rgb(30, 164, 255)',
                            lineHeight: '15px',
                            height: 15,
                        }}
                    >
                        {tooltip.cityText}
                    </div>
                    {tooltip.nationText && (
                        <div
                            className="px-1 text-right"
                            style={{
                                backgroundColor: 'rgb(30, 164, 255)',
                                lineHeight: '15px',
                                height: 15,
                                borderTop: '1px solid gray',
                            }}
                        >
                            {tooltip.nationText}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
