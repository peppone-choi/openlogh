"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useWorldStore } from "@/stores/worldStore";
import { useGameStore } from "@/stores/gameStore";
import { mapRecentApi } from "@/lib/gameApi";
import { subscribeWebSocket } from "@/lib/websocket";
import { formatLog } from "@/lib/formatLog";
import type { CityConst, PublicCachedMapHistory } from "@/types";
import { useRouter } from "next/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  getCityLevelIcon,
  getEventIcon,
  getMapBgUrl,
  getMapRoadUrl,
  getNationBgGradient,
  getSpecialEventIcon,
} from "@/lib/image";
import { FactionFlag } from "@/components/game/faction-flag";

type MapTheme = "default" | "spring" | "summer" | "autumn" | "winter";
const MAP_THEMES: {
  key: MapTheme;
  label: string;
  bg: string;
  line: string;
  text: string;
}[] = [
  { key: "default", label: "기본", bg: "#111827", line: "#333", text: "#ccc" },
  {
    key: "spring",
    label: "봄",
    bg: "#1a2e1a",
    line: "#4a7c4a",
    text: "#b0e0b0",
  },
  {
    key: "summer",
    label: "여름",
    bg: "#1a2e2e",
    line: "#2a6e6e",
    text: "#a0e0e0",
  },
  {
    key: "autumn",
    label: "가을",
    bg: "#2e1a0a",
    line: "#8e5e2e",
    text: "#e0c090",
  },
  {
    key: "winter",
    label: "겨울",
    bg: "#1e2030",
    line: "#6070a0",
    text: "#c0c8e0",
  },
];

type MapLayer = "nations" | "troops" | "supply" | "terrain";

interface CityTooltip {
  cityId: number;
  cityName: string;
  nationName: string;
  nationColor: string;
  level: number;
  pop: number;
  agri: string;
  comm: string;
  secu: string;
  def: string;
  wall: string;
  trust: number;
  generals: {
    name: string;
    nationColor: string;
    crew: number;
    crewType: string;
    isForeign: boolean;
  }[];
  screenX: number;
  screenY: number;
}

const CREW_TYPES: Record<number, string> = {
  0: "보병",
  1: "궁병",
  2: "기병",
  3: "귀병",
  4: "차병",
  5: "노병",
  6: "연노병",
  7: "근위기병",
  8: "무당병",
  9: "서량기병",
  10: "등갑병",
  11: "수군",
};

type MapSeason = "spring" | "summer" | "fall" | "winter";

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
const CITY_HIT_WIDTH = 40;
const CITY_HIT_HEIGHT = 30;
const CITY_RING_SIZE = 20;

export default function MapPage() {
  const router = useRouter();
  const { currentWorld } = useWorldStore();
  const { cities, nations, generals, mapData, loadAll, loadMap } =
    useGameStore();
  const [tooltip, setTooltip] = useState<CityTooltip | null>(null);
  const [history, setHistory] = useState<PublicCachedMapHistory[]>([]);
  const [touchTapId, setTouchTapId] = useState<number | null>(null);
  // Auto-detect season from world month (legacy: spring 1-3, summer 4-6, autumn 7-9, winter 10-12)
  const autoTheme = useMemo<MapTheme>(() => {
    let month: number | null = null;
    try {
      month = Number(localStorage.getItem("opensam:world:month"));
    } catch {}
    if (!month)
      month = (currentWorld?.config as Record<string, number>)?.month ?? null;
    if (!month) return "default";
    if (month <= 3) return "spring";
    if (month <= 6) return "summer";
    if (month <= 9) return "autumn";
    return "winter";
  }, [currentWorld]);
  const [theme, setTheme] = useState<MapTheme>("default");
  const [layers, setLayers] = useState<Set<MapLayer>>(
    new Set(["nations", "troops"]),
  );
  const [historyBrowseIdx, setHistoryBrowseIdx] = useState<number | null>(null);
  const [historyFilterYear, setHistoryFilterYear] = useState<number | null>(
    null,
  );
  const [historyFilterMonth, setHistoryFilterMonth] = useState<number | null>(
    null,
  );

  const [isAutoTheme, setIsAutoTheme] = useState(true);
  const [showCityNames, setShowCityNames] = useState(true);
  const tooltipHideTimerRef = useRef<number | null>(null);
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const [mapScale, setMapScale] = useState(1);
  const selectedTheme = isAutoTheme ? autoTheme : theme;
  const currentTheme =
    MAP_THEMES.find((t) => t.key === selectedTheme) ?? MAP_THEMES[0];
  const mapCode =
    (
      (currentWorld?.config as Record<string, string>)?.mapCode ?? "che"
    ).trim() || "che";
  const mapFolder = mapCode.includes("miniche")
    ? "che"
    : mapCode === "ludo_rathowm"
      ? "ludo_rathowm"
      : mapCode;
  const season = useMemo<MapSeason>(() => {
    const themeForSeason =
      selectedTheme === "default" ? autoTheme : selectedTheme;
    if (themeForSeason === "spring") return "spring";
    if (themeForSeason === "summer") return "summer";
    if (themeForSeason === "autumn") return "fall";
    if (themeForSeason === "winter") return "winter";
    return "spring";
  }, [selectedTheme, autoTheme]);
  const showSeasonBackground = isAutoTheme || selectedTheme !== "default";
  const mapBgUrl = showSeasonBackground ? getMapBgUrl(mapFolder, season) : null;
  const mapRoadUrl = getMapRoadUrl(mapCode);

  const clearTooltipHideTimer = useCallback(() => {
    if (tooltipHideTimerRef.current !== null) {
      window.clearTimeout(tooltipHideTimerRef.current);
      tooltipHideTimerRef.current = null;
    }
  }, []);

  const scheduleTooltipHide = useCallback(() => {
    clearTooltipHideTimer();
    tooltipHideTimerRef.current = window.setTimeout(() => {
      setTooltip((prev) => {
        if (prev && touchTapId === prev.cityId) return prev;
        return null;
      });
    }, 120);
  }, [clearTooltipHideTimer, touchTapId]);

  useEffect(() => {
    return () => {
      if (tooltipHideTimerRef.current !== null) {
        window.clearTimeout(tooltipHideTimerRef.current);
      }
    };
  }, []);

  // Measure map container width to scale the 700×500 coordinate space
  useEffect(() => {
    const el = mapContainerRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setMapScale(entry.contentRect.width / MAP_WIDTH);
      }
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
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

      mapRecentApi
        .getMapRecent(currentWorld.id)
        .then(({ data }) => {
          if (data.history) setHistory(data.history);
        })
        .catch(() => {});
    }
  }, [currentWorld, loadAll, loadMap, mapCode]);

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

  const nationMap = useMemo(
    () => new Map(nations.map((n) => [n.id, n])),
    [nations],
  );

  const cityMap = useMemo(
    () => new Map(cities.map((c) => [c.id, c])),
    [cities],
  );

  const cityByNameMap = useMemo(
    () => new Map(cities.map((c) => [c.name, c])),
    [cities],
  );

  const constMap = useMemo(
    () => new Map(mapData?.cities.map((c) => [c.id, c]) ?? []),
    [mapData],
  );

  // Build per-city general counts by nation (for troop indicators)
  const cityGeneralData = useMemo(() => {
    const map = new Map<
      number,
      { nationId: number; name: string; crew: number; crewType: number }[]
    >();
    for (const g of generals) {
      if (g.nationId <= 0 || g.crew <= 0) continue;
      if (!map.has(g.cityId)) map.set(g.cityId, []);
      map.get(g.cityId)!.push({
        nationId: g.nationId,
        name: g.name,
        crew: g.crew,
        crewType: g.crewType,
      });
    }
    return map;
  }, [generals]);

  // Identify cities with foreign troops (troops from a different nation than the city owner)
  const foreignTroopCities = useMemo(() => {
    const result = new Set<number>();
    for (const [cityId, gens] of cityGeneralData) {
      const city = cityMap.get(cityId);
      if (!city) continue;
      for (const g of gens) {
        if (g.nationId !== city.nationId && city.nationId > 0) {
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
      const nation = city.nationId ? nationMap.get(city.nationId) : null;
      try {
        localStorage.setItem(
          `opensam:cityInfo:${cityId}`,
          JSON.stringify({
            id: cityId,
            name: city.name ?? "",
            nationName: nation?.name ?? "공백지",
            nationColor: nation?.color ?? "#555",
            pop: city.pop,
            level: city.level,
            ts: Date.now(),
          }),
        );
      } catch {
        /* ignore quota */
      }
    },
    [cityMap, cityByNameMap, nationMap, constMap],
  );

  const buildTooltip = useCallback(
    (cc: CityConst, screenX: number, screenY: number): CityTooltip => {
      const city = cityByNameMap.get(cc.name);
      const nation = city?.nationId ? nationMap.get(city.nationId) : null;
      const cityGens = cityGeneralData.get(city?.id ?? -1) ?? [];
      const generalsInfo = cityGens.map((g) => ({
        name: g.name,
        nationColor: nationMap.get(g.nationId)?.color ?? "#555",
        crew: g.crew,
        crewType: CREW_TYPES[g.crewType] ?? `${g.crewType}`,
        isForeign: city ? g.nationId !== city.nationId : false,
      }));

      return {
        cityId: cc.id,
        cityName: cc.name,
        nationName: nation?.name ?? "공백지",
        nationColor: nation?.color ?? "#555",
        level: city?.level ?? cc.level,
        pop: city?.pop ?? 0,
        agri: city ? `${city.agri}/${city.agriMax}` : "-",
        comm: city ? `${city.comm}/${city.commMax}` : "-",
        secu: city ? `${city.secu}/${city.secuMax}` : "-",
        def: city ? `${city.def}/${city.defMax}` : "-",
        wall: city ? `${city.wall}/${city.wallMax}` : "-",
        trust: city?.trust ?? 0,
        generals: generalsInfo,
        screenX,
        screenY,
      };
    },
    [cityByNameMap, nationMap, cityGeneralData],
  );

  const handleCityMouseEnter = useCallback(
    (cc: CityConst, e: React.MouseEvent<HTMLButtonElement>) => {
      e.stopPropagation();
      clearTooltipHideTimer();
      setTouchTapId(null);
      const rtCity = cityByNameMap.get(cc.name);
      if (rtCity) saveCityInfo(rtCity.id);
      setTooltip(buildTooltip(cc, e.clientX, e.clientY));
    },
    [buildTooltip, clearTooltipHideTimer, saveCityInfo, cityByNameMap],
  );

  const handleCityMouseMove = useCallback(
    (cc: CityConst, e: React.MouseEvent<HTMLButtonElement>) => {
      setTooltip((prev) => {
        if (!prev || prev.cityId !== cc.id) return prev;
        return { ...prev, screenX: e.clientX, screenY: e.clientY };
      });
    },
    [],
  );

  const handleCityMouseLeave = useCallback(() => {
    scheduleTooltipHide();
  }, [scheduleTooltipHide]);

  const handleCityClick = useCallback(
    (e: React.MouseEvent<HTMLButtonElement>) => {
      e.stopPropagation();
    },
    [],
  );

  const handleCityTouch = useCallback(
    (cc: CityConst, e: React.TouchEvent<HTMLButtonElement>) => {
      clearTooltipHideTimer();
      e.preventDefault();
      e.stopPropagation();
      const rtCity = cityByNameMap.get(cc.name);
      if (touchTapId === cc.id && tooltip?.cityId === cc.id) {
        if (rtCity) saveCityInfo(rtCity.id);
        router.push(`/city?id=${rtCity?.id ?? cc.id}`);
        setTouchTapId(null);
      } else {
        const touch = e.touches[0] ?? e.changedTouches[0];
        if (rtCity) saveCityInfo(rtCity.id);
        setTooltip(buildTooltip(cc, touch?.clientX ?? 0, touch?.clientY ?? 0));
        setTouchTapId(cc.id);
      }
    },
    [
      buildTooltip,
      clearTooltipHideTimer,
      touchTapId,
      tooltip,
      router,
      saveCityInfo,
      cityByNameMap,
    ],
  );

  if (!mapData) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-500">
        지도를 불러오는 중...
      </div>
    );
  }

  const serverName = currentWorld?.name ?? "삼국지";
  const getHistoryBullet = (text: string) => {
    if (text.includes("【대회】") || text.includes("【안내】")) return "◆";
    return "●";
  };

  return (
    <Card className="w-full max-w-[750px] mx-auto">
      <CardHeader className="pb-2">
        <CardTitle className="text-lg">{serverName} 현황</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="w-full max-w-[700px] mx-auto">
          <div
            ref={mapContainerRef}
            className="relative border border-gray-800 rounded-lg overflow-hidden"
            style={{
              backgroundColor: currentTheme.bg,
              aspectRatio: "700 / 500",
            }}
          >
            {/* Scaled inner div: 700×500 coordinate space scaled to fit container */}
            <div
              style={{
                width: MAP_WIDTH,
                height: MAP_HEIGHT,
                transform: `scale(${mapScale})`,
                transformOrigin: "top left",
                position: "relative",
              }}
            >
              {/* Year/Season header like legacy "西紀 188年 4月 春" */}
              {(() => {
                let worldYear: number | null = null;
                let worldMonth: number | null = null;
                try {
                  worldYear =
                    Number(localStorage.getItem("opensam:world:year")) || null;
                } catch {}
                try {
                  worldMonth =
                    Number(localStorage.getItem("opensam:world:month")) || null;
                } catch {}
                const SEASON_LABELS: Record<string, string> = {
                  spring: "春",
                  summer: "夏",
                  fall: "秋",
                  winter: "冬",
                };
                const seasonLabel = SEASON_LABELS[season] ?? "";
                if (!worldYear && !worldMonth) return null;
                return (
                  <div className="absolute top-0 left-0 right-0 z-[4] text-center py-1">
                    <span
                      className="text-white text-sm font-bold drop-shadow-lg"
                      style={{ textShadow: "1px 1px 2px rgba(0,0,0,0.8)" }}
                    >
                      西紀 {worldYear ?? "?"}年 {worldMonth ?? "?"}月{" "}
                      {seasonLabel}
                    </span>
                  </div>
                );
              })()}

              {mapBgUrl && (
                <div
                  className="absolute inset-0 z-0 bg-no-repeat bg-center"
                  style={{
                    backgroundImage: `url('${mapBgUrl}')`,
                    backgroundSize: `${MAP_WIDTH}px ${MAP_HEIGHT}px`,
                  }}
                />
              )}
              <div
                className="absolute inset-0 z-[1] bg-no-repeat bg-center"
                style={{
                  backgroundImage: `url('${mapRoadUrl}')`,
                  backgroundSize: `${MAP_WIDTH}px ${MAP_HEIGHT}px`,
                }}
              />

              <button
                type="button"
                aria-label="지도 툴팁 닫기"
                className="absolute inset-0 z-[2] border-0 bg-transparent p-0"
                onClick={() => {
                  clearTooltipHideTimer();
                  setTooltip(null);
                  setTouchTapId(null);
                }}
              />

              {/* SVG connection lines removed — road overlay image already shows connections */}

              <div className="absolute inset-0 z-[3]">
                {mapData.cities.map((cc) => {
                  const rtCity = cityByNameMap.get(cc.name);
                  const nation = rtCity?.nationId
                    ? nationMap.get(rtCity.nationId)
                    : null;
                  const sizes =
                    detailMapCitySizes[cc.level] ?? detailMapCitySizes[1];
                  const [bgW, bgH, icnW, icnH, flagR, flagT] = sizes;
                  const left = cc.x - 20;
                  const top = cc.y - 15;
                  const hasForeignTroops =
                    layers.has("troops") &&
                    foreignTroopCities.has(rtCity?.id ?? -1);
                  const genCount = layers.has("troops")
                    ? (cityGeneralData.get(rtCity?.id ?? -1)?.length ?? 0)
                    : 0;
                  const isSupplyBroken =
                    layers.has("supply") &&
                    !!rtCity &&
                    rtCity.supplyState !== 1;
                  const terrainLevel =
                    layers.has("terrain") && rtCity ? rtCity.level : 0;
                  const showNationLayer = layers.has("nations") && !!nation;
                  const showCapital =
                    !!nation && !!rtCity && nation.capitalCityId === rtCity.id;

                  return (
                    <button
                      key={cc.id}
                      type="button"
                      className="absolute h-[30px] w-[40px] cursor-pointer appearance-none border-0 bg-transparent p-0 text-left"
                      style={{ left, top }}
                      onMouseEnter={(e) => handleCityMouseEnter(cc, e)}
                      onMouseMove={(e) => handleCityMouseMove(cc, e)}
                      onMouseLeave={handleCityMouseLeave}
                      onTouchEnd={(e) => handleCityTouch(cc, e)}
                      onClick={handleCityClick}
                    >
                      {showNationLayer && nation?.color && (
                        <div
                          className="absolute z-[1]"
                          style={{
                            background: getNationBgGradient(nation.color),
                            width: bgW,
                            height: bgH,
                            left: (CITY_HIT_WIDTH - bgW) / 2,
                            top: (CITY_HIT_HEIGHT - bgH) / 2,
                          }}
                        />
                      )}

                      {hasForeignTroops && (
                        <span
                          className="absolute z-[3] animate-spin rounded-full border-2 border-dashed border-red-500"
                          style={{
                            width: CITY_RING_SIZE + 8,
                            height: CITY_RING_SIZE + 8,
                            left: (CITY_HIT_WIDTH - (CITY_RING_SIZE + 8)) / 2,
                            top: (CITY_HIT_HEIGHT - (CITY_RING_SIZE + 8)) / 2,
                          }}
                        />
                      )}

                      {isSupplyBroken && (
                        <span
                          className="absolute z-[3] rounded-full border border-dashed border-amber-500"
                          style={{
                            width: CITY_RING_SIZE + 12,
                            height: CITY_RING_SIZE + 12,
                            left: (CITY_HIT_WIDTH - (CITY_RING_SIZE + 12)) / 2,
                            top: (CITY_HIT_HEIGHT - (CITY_RING_SIZE + 12)) / 2,
                          }}
                        />
                      )}

                      <div className="absolute z-[2] w-full h-full">
                        <div
                          className="absolute"
                          style={{
                            width: icnW,
                            height: icnH,
                            left: (CITY_HIT_WIDTH - icnW) / 2,
                            top: (CITY_HIT_HEIGHT - icnH) / 2,
                          }}
                        >
                          <img
                            src={getCityLevelIcon(cc.level)}
                            className="w-full h-full block"
                            alt={`${cc.name} 레벨 ${cc.level}`}
                          />

                          {showNationLayer && nation && (
                            <div
                              className="absolute"
                              style={{
                                right: flagR,
                                top: flagT,
                                width: 12,
                                height: 12,
                              }}
                            >
                               <FactionFlag
                                 color={nation.color}
                                 supplied={(rtCity?.supplyState ?? 0) > 0}
                                 className="w-full h-full block"
                               />
                              {showCapital && (
                                <div
                                  className="absolute"
                                  style={{
                                    right: -1,
                                    top: 0,
                                    width: 10,
                                    height: 10,
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
                        </div>

                        {rtCity && rtCity.state > 0 && (
                          <div className="absolute left-0" style={{ top: 5 }}>
                            <img
                              src={getEventIcon(rtCity.state)}
                              className="object-contain"
                              style={{ width: 10 }}
                              alt={`도시 상태 ${rtCity.state}`}
                            />
                          </div>
                        )}

                        {genCount > 0 && (
                          <span className="absolute -right-1 -top-1 z-[5] min-w-4 h-4 rounded-full bg-black/90 border border-gray-500 px-1 text-[9px] leading-4 text-white text-center font-bold">
                            {genCount}
                          </span>
                        )}

                        {terrainLevel > 0 && (
                          <span className="absolute -left-1 -top-1 z-[5] rounded bg-purple-900/80 border border-purple-400 px-1 text-[8px] leading-3 text-purple-100">
                            Lv{terrainLevel}
                          </span>
                        )}

                        {showCityNames && (
                          <span
                            className="absolute whitespace-nowrap px-[2px] py-[1px] bg-black/55 text-[10px]"
                            style={{
                              left: "70%",
                              bottom: -10,
                              color: currentTheme.text,
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

              {/* City name toggle button — legacy style */}
              <button
                type="button"
                className="absolute bottom-2 right-2 z-[5] px-2 py-1 text-[11px] rounded bg-blue-600 hover:bg-blue-700 text-white border-0 cursor-pointer shadow"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowCityNames((v) => !v);
                }}
              >
                {showCityNames ? "도시명 표기 끄기" : "도시명 표기 켜기"}
              </button>
            </div>

            {tooltip && (
              <div
                className="fixed z-50 bg-gray-800 border border-gray-700 rounded-lg p-3 shadow-lg text-sm space-y-1 max-w-xs"
                style={{
                  left: tooltip.screenX + 12,
                  top: tooltip.screenY - 10,
                }}
              >
                <div className="font-semibold flex items-center gap-2">
                  <span
                    className="w-3 h-3 rounded-full"
                    style={{ backgroundColor: tooltip.nationColor }}
                  />
                  {tooltip.cityName}
                </div>
                <div className="text-gray-400">소속: {tooltip.nationName}</div>
                <div className="text-gray-400">레벨: {tooltip.level}</div>
                <div className="text-gray-400">
                  인구: {tooltip.pop.toLocaleString()}
                </div>
                <div className="text-gray-400">농업: {tooltip.agri}</div>
                <div className="text-gray-400">상업: {tooltip.comm}</div>
                <div className="text-gray-400">치안: {tooltip.secu}</div>
                <div className="text-gray-400">수비: {tooltip.def}</div>
                <div className="text-gray-400">성벽: {tooltip.wall}</div>
                <div className="text-gray-400">민심: {tooltip.trust}</div>

                {/* Link to city detail */}
                <button
                  type="button"
                  className="w-full text-center text-xs text-cyan-400 hover:text-cyan-300 border border-gray-600 rounded px-2 py-1 mt-1"
                  onClick={(e) => {
                    e.stopPropagation();
                    router.push(`/city?id=${tooltip.cityId}`);
                  }}
                >
                  도시 상세 보기
                </button>

                {/* Generals in this city */}
                {tooltip.generals.length > 0 && (
                  <div className="border-t border-gray-700 pt-1 mt-1">
                    <div className="text-gray-300 font-medium text-xs mb-0.5">
                      주둔 장수 ({tooltip.generals.length}명)
                    </div>
                    <div className="max-h-32 overflow-y-auto space-y-0.5">
                      {tooltip.generals.map((g) => (
                        <div
                          key={`${g.name}:${g.crewType}:${g.crew}`}
                          className="flex items-center gap-1.5 text-xs"
                        >
                          <span
                            className="w-2 h-2 rounded-full shrink-0"
                            style={{ backgroundColor: g.nationColor }}
                          />
                          <span
                            className={
                              g.isForeign
                                ? "text-red-400 font-bold"
                                : "text-gray-300"
                            }
                          >
                            {g.name}
                          </span>
                          <span className="text-muted-foreground ml-auto">
                            {g.crewType} {g.crew.toLocaleString()}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="mx-auto flex w-full max-w-[700px] flex-wrap items-center gap-1.5 text-xs">
          <span className="text-muted-foreground">테마</span>
          <select
            className="h-7 rounded border border-border bg-background px-1.5 text-xs"
            value={selectedTheme}
            onChange={(e) => {
              const next = e.target.value as MapTheme;
              if (next === "default") {
                setIsAutoTheme(true);
                return;
              }
              setTheme(next);
              setIsAutoTheme(false);
            }}
          >
            <option value="default">자동</option>
            {MAP_THEMES.filter(
              (themeOption) => themeOption.key !== "default",
            ).map((themeOption) => (
              <option key={themeOption.key} value={themeOption.key}>
                {themeOption.label}
              </option>
            ))}
          </select>
          <span className="ml-1 text-muted-foreground">레이어</span>
          {[
            { key: "nations" as MapLayer, label: "국가색" },
            { key: "troops" as MapLayer, label: "병력" },
            { key: "supply" as MapLayer, label: "보급" },
            { key: "terrain" as MapLayer, label: "지형" },
          ].map((layer) => (
            <Button
              key={layer.key}
              size="sm"
              variant={layers.has(layer.key) ? "default" : "outline"}
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
            const yearMonthSet = new Map<
              string,
              { year: number; month: number }
            >();
            for (const h of history) {
              if (h.year != null && h.month != null) {
                const key = `${h.year}-${h.month}`;
                if (!yearMonthSet.has(key))
                  yearMonthSet.set(key, { year: h.year, month: h.month });
              }
            }
            const yearMonthList = Array.from(yearMonthSet.values()).sort(
              (a, b) => b.year - a.year || b.month - a.month,
            );
            const availableYears = [
              ...new Set(yearMonthList.map((ym) => ym.year)),
            ].sort((a, b) => b - a);

            // Filter logic
            const filteredHistory =
              historyFilterYear !== null
                ? history.filter(
                    (h) =>
                      h.year === historyFilterYear &&
                      (historyFilterMonth === null ||
                        h.month === historyFilterMonth),
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
                    variant={historyFilterYear === null ? "default" : "outline"}
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
                      variant={historyFilterYear === y ? "default" : "outline"}
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
                      <span className="text-xs text-muted-foreground ml-2">
                        월:
                      </span>
                      <Button
                        size="sm"
                        variant={
                          historyFilterMonth === null ? "default" : "outline"
                        }
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
                          variant={
                            historyFilterMonth === m ? "default" : "outline"
                          }
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
                    disabled={
                      historyBrowseIdx === null || historyBrowseIdx <= 0
                    }
                    onClick={() =>
                      setHistoryBrowseIdx((prev) =>
                        Math.max(0, (prev ?? filteredHistory.length) - 10),
                      )
                    }
                  >
                    ← 이전
                  </Button>
                  <span className="text-[10px] text-muted-foreground">
                    {historyBrowseIdx !== null
                      ? `${historyBrowseIdx + 1}~${Math.min(historyBrowseIdx + 10, filteredHistory.length)}`
                      : `1~${Math.min(10, filteredHistory.length)}`}{" "}
                    / {filteredHistory.length}
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="h-6 px-2 text-xs"
                    disabled={
                      (historyBrowseIdx === null &&
                        filteredHistory.length <= 10) ||
                      (historyBrowseIdx !== null &&
                        historyBrowseIdx + 10 >= filteredHistory.length)
                    }
                    onClick={() =>
                      setHistoryBrowseIdx((prev) =>
                        Math.min(filteredHistory.length - 10, (prev ?? 0) + 10),
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
                          getHistoryBullet(item.text) === "◆"
                            ? "text-sky-300"
                            : "text-cyan-300"
                        }
                      >
                        {item.year != null && item.month != null
                          ? `${getHistoryBullet(item.text)}${item.year}년 ${item.month}월:`
                          : `${getHistoryBullet(item.text)}`}
                      </span>{" "}
                      <span>{formatLog(item.text)}</span>
                    </div>
                  ))}
                  {displayItems.length === 0 && (
                    <div className="text-muted-foreground py-2">
                      해당 기간의 기록이 없습니다.
                    </div>
                  )}
                </div>
              </div>
            );
          })()}
      </CardContent>
    </Card>
  );
}
