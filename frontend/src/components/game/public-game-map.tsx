"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { PublicCachedMapResponse } from "@/types";
import {
  getCityLevelIcon,
  getEventIcon,
  getMapBgUrl,
  getMapRoadUrl,
  getNationBgGradient,
  getSpecialEventIcon,
} from "@/lib/image";
import { FactionFlag } from "@/components/game/faction-flag";

interface PublicGameMapProps {
  data: PublicCachedMapResponse;
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

const MAP_WIDTH = 700;
const MAP_HEIGHT = 500;
const CITY_HIT_WIDTH = 40;
const CITY_HIT_HEIGHT = 30;

type MapSeason = "spring" | "summer" | "fall" | "winter";

function getSeason(month: number | null | undefined): MapSeason {
  if (!month) return "spring";
  if (month <= 3) return "spring";
  if (month <= 6) return "summer";
  if (month <= 9) return "fall";
  return "winter";
}

const SEASON_LABELS: Record<string, string> = {
  spring: "春",
  summer: "夏",
  fall: "秋",
  winter: "冬",
};

export function PublicGameMap({ data }: PublicGameMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const [mapScale, setMapScale] = useState(1);
  const [showCityNames, setShowCityNames] = useState(true);

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

  const mapCode = useMemo(
    () => (data.mapCode ?? "che").trim() || "che",
    [data.mapCode],
  );
  const mapFolder = mapCode.includes("miniche")
    ? "che"
    : mapCode === "ludo_rathowm"
      ? "ludo_rathowm"
      : mapCode;

  const season = useMemo<MapSeason>(
    () => getSeason(data.currentMonth),
    [data.currentMonth],
  );

  const mapBgUrl = getMapBgUrl(mapFolder, season);
  const mapRoadUrl = getMapRoadUrl(mapCode);

  return (
    <div
      ref={mapContainerRef}
      className="relative border border-gray-800 rounded-lg overflow-hidden"
      style={{
        backgroundColor: "#111827",
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
        {/* Year/Season header */}
        {data.currentYear != null && data.currentMonth != null && (
          <div className="absolute top-0 left-0 right-0 z-[4] text-center py-1">
            <span
              className="text-white text-sm font-bold drop-shadow-lg"
              style={{ textShadow: "1px 1px 2px rgba(0,0,0,0.8)" }}
            >
              西紀 {data.currentYear}年 {data.currentMonth}月{" "}
              {SEASON_LABELS[season] ?? ""}
            </span>
          </div>
        )}

        {/* Season background */}
        <div
          className="absolute inset-0 z-0 bg-no-repeat bg-center"
          style={{
            backgroundImage: `url('${mapBgUrl}')`,
            backgroundSize: `${MAP_WIDTH}px ${MAP_HEIGHT}px`,
          }}
        />

        {/* Road overlay */}
        <div
          className="absolute inset-0 z-[1] bg-no-repeat bg-center"
          style={{
            backgroundImage: `url('${mapRoadUrl}')`,
            backgroundSize: `${MAP_WIDTH}px ${MAP_HEIGHT}px`,
          }}
        />

        {/* Cities */}
        <div className="absolute inset-0 z-[3]">
          {data.cities.map((city) => {
            const sizes =
              detailMapCitySizes[city.level] ?? detailMapCitySizes[1];
            const [bgW, bgH, icnW, icnH, flagR, flagT] = sizes;
            const left = city.x - 20;
            const top = city.y - 15;
            const hasNation =
              city.nationColor && city.nationColor !== "#4b5563";
            const isSupplied = (city.supplyState ?? 1) > 0;

            return (
              <div
                key={city.id}
                className="absolute h-[30px] w-[40px] overflow-visible"
                style={{ left, top }}
              >
                {/* Nation Color Blotch */}
                {hasNation && (
                  <div
                    className="absolute z-[1]"
                    style={{
                      background: getNationBgGradient(city.nationColor),
                      width: bgW,
                      height: bgH,
                      left: (CITY_HIT_WIDTH - bgW) / 2,
                      top: (CITY_HIT_HEIGHT - bgH) / 2,
                    }}
                  />
                )}

                <div className="absolute z-[2] w-full h-full">
                  {/* City Icon */}
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
                      src={getCityLevelIcon(city.level)}
                      className="w-full h-full block"
                      alt=""
                    />

                    {/* Nation Flag */}
                    {hasNation && (
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
                          color={city.nationColor}
                          supplied={isSupplied}
                          className="w-full h-full block"
                        />
                        {/* Capital marker */}
                        {city.isCapital && (
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

                  {/* City state icon */}
                  {(city.state ?? 0) > 0 && (
                    <div className="absolute left-0" style={{ top: 5 }}>
                      <img
                        src={getEventIcon(city.state!)}
                        className="object-contain"
                        style={{ width: 10 }}
                        alt=""
                      />
                    </div>
                  )}

                  {/* City name */}
                  {showCityNames && (
                    <span
                      className="absolute whitespace-nowrap px-[2px] py-[1px] bg-black/55 text-[10px] text-[#ccc]"
                      style={{
                        left: "70%",
                        bottom: -10,
                      }}
                    >
                      {city.name}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* City name toggle */}
        <button
          type="button"
          className="absolute bottom-2 right-2 z-[5] px-2 py-1 text-[11px] rounded bg-blue-600 hover:bg-blue-700 text-white border-0 cursor-pointer shadow"
          onClick={() => setShowCityNames((v) => !v)}
        >
          {showCityNames ? "도시명 표기 끄기" : "도시명 표기 켜기"}
        </button>
      </div>
    </div>
  );
}
