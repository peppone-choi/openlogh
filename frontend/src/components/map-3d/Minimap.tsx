'use client';

import { useCallback } from 'react';
import type { MapSeason } from '@/lib/map-constants';
import type { RenderCity } from '@/components/game/map-canvas';
import { getMapBgUrl } from '@/lib/image';
import { WORLD_WIDTH, WORLD_DEPTH } from '@/lib/map3d-utils';
import { MAP_WIDTH, MAP_HEIGHT } from '@/lib/map-constants';

// Aspect ratio: 700:500 = 1.4, so height = size / 1.4 = size * 0.714
const MAP_ASPECT = MAP_HEIGHT / MAP_WIDTH; // 500/700 ≈ 0.714

// ─── Coordinate conversion utilities (exported for testing) ───

/**
 * Convert 2D map pixel coords (0..MAP_WIDTH, 0..MAP_HEIGHT) to minimap pixel coords.
 * @param mapX  city.x in the 700×500 coordinate system
 * @param mapY  city.y in the 700×500 coordinate system
 * @param size  minimap width in pixels
 */
export function mapToMinimap(mapX: number, mapY: number, size: number): { px: number; py: number } {
    const minimapH = size * MAP_ASPECT;
    return {
        px: (mapX / MAP_WIDTH) * size,
        py: (mapY / MAP_HEIGHT) * minimapH,
    };
}

/**
 * Convert minimap pixel coords back to 2D map coords (0..MAP_WIDTH, 0..MAP_HEIGHT).
 */
export function minimapToMap(px: number, py: number, size: number): { mapX: number; mapY: number } {
    const minimapH = size * MAP_ASPECT;
    return {
        mapX: (px / size) * MAP_WIDTH,
        mapY: (py / minimapH) * MAP_HEIGHT,
    };
}

/**
 * Convert world coords (Three.js) to minimap pixel coords.
 * World X range: [-WORLD_WIDTH/2 .. +WORLD_WIDTH/2]
 * World Z range: [-WORLD_DEPTH/2 .. +WORLD_DEPTH/2]
 */
export function worldToMinimap(wx: number, wz: number, size: number): { px: number; py: number } {
    const mapX = (wx / WORLD_WIDTH + 0.5) * MAP_WIDTH;
    const mapY = (wz / WORLD_DEPTH + 0.5) * MAP_HEIGHT;
    return mapToMinimap(mapX, mapY, size);
}

/**
 * Convert minimap pixel coords back to world coords.
 */
export function minimapToWorld(px: number, py: number, size: number): { worldX: number; worldZ: number } {
    const { mapX, mapY } = minimapToMap(px, py, size);
    return {
        worldX: (mapX / MAP_WIDTH - 0.5) * WORLD_WIDTH,
        worldZ: (mapY / MAP_HEIGHT - 0.5) * WORLD_DEPTH,
    };
}

// ─── Viewport rectangle calculation ───

interface ViewportRect {
    left: number;
    top: number;
    width: number;
    height: number;
}

/**
 * Given a camera position in world coords, estimate the viewport rectangle
 * on the minimap. Camera height (Y) determines zoom level — higher = wider.
 */
function calcViewportRect(cameraX: number, cameraY: number, cameraZ: number, size: number): ViewportRect {
    // Estimate half-width of visible area based on camera height and FOV (~50deg)
    const fovHalfTan = Math.tan((50 / 2) * (Math.PI / 180));
    const viewHalfWorld = cameraY * fovHalfTan;

    // Convert world half-extents to minimap pixels
    const minimapH = size * MAP_ASPECT;
    const halfW = (viewHalfWorld / WORLD_WIDTH) * size * 2;
    const halfH = (viewHalfWorld / WORLD_DEPTH) * minimapH * 2;

    const center = worldToMinimap(cameraX, cameraZ, size);

    return {
        left: center.px - halfW / 2,
        top: center.py - halfH / 2,
        width: halfW,
        height: halfH,
    };
}

// ─── Component ───

export interface MinimapProps {
    mapCode: string;
    season: MapSeason;
    cities: RenderCity[];
    /** Camera position in world coords — used to show viewport rect */
    cameraPosition?: [number, number, number];
    /** Callback when user clicks on minimap to reposition */
    onNavigate?: (worldX: number, worldZ: number) => void;
    size?: number;
}

export function Minimap({ mapCode, season, cities, cameraPosition, onNavigate, size = 180 }: MinimapProps) {
    const minimapH = Math.round(size * MAP_ASPECT);
    const mapFolder = mapCode.includes('miniche') ? 'che' : mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode;
    const bgUrl = getMapBgUrl(mapFolder, season);

    const handleClick = useCallback(
        (e: React.MouseEvent<HTMLDivElement>) => {
            if (!onNavigate) return;
            const rect = e.currentTarget.getBoundingClientRect();
            const px = e.clientX - rect.left;
            const py = e.clientY - rect.top;
            const { worldX, worldZ } = minimapToWorld(px, py, size);
            onNavigate(worldX, worldZ);
        },
        [onNavigate, size]
    );

    const viewport = cameraPosition
        ? calcViewportRect(cameraPosition[0], cameraPosition[1], cameraPosition[2], size)
        : null;

    return (
        <div
            style={{
                position: 'absolute',
                bottom: 12,
                right: 12,
                width: size,
                height: minimapH,
                borderRadius: 6,
                overflow: 'hidden',
                border: '1px solid rgba(255,255,255,0.25)',
                backgroundColor: 'rgba(0,0,0,0.55)',
                cursor: onNavigate ? 'crosshair' : 'default',
                pointerEvents: 'auto',
                zIndex: 10,
            }}
            onClick={handleClick}
            role="img"
            aria-label="minimap"
        >
            {/* Background map image */}
            <div
                style={{
                    position: 'absolute',
                    inset: 0,
                    backgroundImage: `url('${bgUrl}')`,
                    backgroundSize: `${size}px ${minimapH}px`,
                    backgroundRepeat: 'no-repeat',
                    backgroundPosition: 'center',
                    opacity: 0.75,
                }}
            />

            {/* City dots */}
            {cities.map((city) => {
                const { px, py } = mapToMinimap(city.x, city.y, size);
                const color = city.nationColor ?? '#888888';
                return (
                    <div
                        key={city.id}
                        style={{
                            position: 'absolute',
                            left: px - 3,
                            top: py - 3,
                            width: 6,
                            height: 6,
                            borderRadius: '50%',
                            backgroundColor: color,
                            border: '1px solid #ffffff',
                            pointerEvents: 'none',
                        }}
                    />
                );
            })}

            {/* Viewport rectangle */}
            {viewport && (
                <div
                    style={{
                        position: 'absolute',
                        left: viewport.left,
                        top: viewport.top,
                        width: viewport.width,
                        height: viewport.height,
                        border: '1.5px solid rgba(255,255,255,0.7)',
                        backgroundColor: 'rgba(255,255,255,0.08)',
                        pointerEvents: 'none',
                        boxSizing: 'border-box',
                    }}
                />
            )}
        </div>
    );
}
