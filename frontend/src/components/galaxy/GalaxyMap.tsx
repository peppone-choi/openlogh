'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Stage, Layer, Rect } from 'react-konva';
import type Konva from 'konva';
import { useGalaxyStore } from '@/stores/galaxyStore';
import { StarSystemNode } from './StarSystemNode';
import { StarRouteEdge } from './StarRouteEdge';
import { StarSystemDetailPanel } from './StarSystemDetailPanel';
import { StarField } from './StarField';

interface GalaxyMapProps {
    sessionId: number;
}

const PADDING = 80;
const MIN_SCALE = 0.3;
const MAX_SCALE = 4.0;
/** gin7-style dark navy space background */
const BG_COLOR = '#0a0e17';

export function GalaxyMap({ sessionId }: GalaxyMapProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const stageRef = useRef<Konva.Stage>(null);

    const [containerSize, setContainerSize] = useState({ width: 900, height: 600 });
    const [stageScale, setStageScale] = useState(1);
    const [stagePos, setStagePos] = useState({ x: 0, y: 0 });

    const systems = useGalaxyStore((s) => s.systems);
    const routes = useGalaxyStore((s) => s.routes);
    const systemsById = useGalaxyStore((s) => s.systemsById);
    const selectedSystemId = useGalaxyStore((s) => s.selectedSystemId);
    const hoveredSystemId = useGalaxyStore((s) => s.hoveredSystemId);
    const isLoading = useGalaxyStore((s) => s.isLoading);
    const error = useGalaxyStore((s) => s.error);
    const fetchMap = useGalaxyStore((s) => s.fetchGalaxyMap);
    const selectSystem = useGalaxyStore((s) => s.selectSystem);
    const hoverSystem = useGalaxyStore((s) => s.hoverSystem);
    const getSystem = useGalaxyStore((s) => s.getSystem);

    // Fetch data on mount
    useEffect(() => {
        fetchMap(sessionId);
    }, [sessionId, fetchMap]);

    // ResizeObserver for container
    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;

        const observer = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const { width, height } = entry.contentRect;
                if (width > 0 && height > 0) {
                    setContainerSize({ width, height });
                }
            }
        });
        observer.observe(el);
        return () => observer.disconnect();
    }, []);

    // Compute coordinate mapping: scale star_systems coords to fit container
    const { offsetX, offsetY, uniformScale } = useMemo(() => {
        if (systems.length === 0) {
            return { offsetX: 0, offsetY: 0, uniformScale: 1 };
        }

        const xs = systems.map((s) => s.x);
        const ys = systems.map((s) => s.y);
        const minX = Math.min(...xs);
        const maxX = Math.max(...xs);
        const minY = Math.min(...ys);
        const maxY = Math.max(...ys);

        const dataWidth = maxX - minX || 1;
        const dataHeight = maxY - minY || 1;

        const availWidth = containerSize.width - PADDING * 2;
        const availHeight = containerSize.height - PADDING * 2;

        const sx = availWidth / dataWidth;
        const sy = availHeight / dataHeight;
        const us = Math.min(sx, sy);

        const ox = PADDING + (availWidth - dataWidth * us) / 2 - minX * us;
        const oy = PADDING + (availHeight - dataHeight * us) / 2 - minY * us;

        return { offsetX: ox, offsetY: oy, uniformScale: us };
    }, [systems, containerSize]);

    // Map star coordinates to canvas space
    const toCanvasX = useCallback(
        (x: number) => x * uniformScale + offsetX,
        [uniformScale, offsetX]
    );
    const toCanvasY = useCallback(
        (y: number) => y * uniformScale + offsetY,
        [uniformScale, offsetY]
    );

    // Determine highlighted connections (when a system is selected)
    const highlightedRoutes = useMemo(() => {
        if (selectedSystemId == null) return new Set<string>();
        const selected = systemsById[selectedSystemId];
        if (!selected) return new Set<string>();

        const set = new Set<string>();
        for (const connId of selected.connections) {
            const key1 = `${selectedSystemId}-${connId}`;
            const key2 = `${connId}-${selectedSystemId}`;
            set.add(key1);
            set.add(key2);
        }
        return set;
    }, [selectedSystemId, systemsById]);

    // Wheel zoom with pointer tracking
    const handleWheel = useCallback(
        (e: Konva.KonvaEventObject<WheelEvent>) => {
            e.evt.preventDefault();
            const stage = stageRef.current;
            if (!stage) return;

            const oldScale = stageScale;
            const pointer = stage.getPointerPosition();
            if (!pointer) return;

            const scaleBy = 1.08;
            const direction = e.evt.deltaY > 0 ? -1 : 1;
            const newScale = Math.min(
                MAX_SCALE,
                Math.max(MIN_SCALE, direction > 0 ? oldScale * scaleBy : oldScale / scaleBy)
            );

            const mousePointTo = {
                x: (pointer.x - stagePos.x) / oldScale,
                y: (pointer.y - stagePos.y) / oldScale,
            };

            setStageScale(newScale);
            setStagePos({
                x: pointer.x - mousePointTo.x * newScale,
                y: pointer.y - mousePointTo.y * newScale,
            });
        },
        [stageScale, stagePos]
    );

    const handleDragEnd = useCallback((e: Konva.KonvaEventObject<DragEvent>) => {
        setStagePos({ x: e.target.x(), y: e.target.y() });
    }, []);

    const selectedSystem = selectedSystemId != null ? getSystem(selectedSystemId) : null;

    if (isLoading) {
        return (
            <div className="flex h-full min-h-[400px] items-center justify-center text-gray-400">
                은하 지도를 불러오는 중...
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex h-full min-h-[400px] items-center justify-center text-red-400">
                오류: {error}
            </div>
        );
    }

    if (systems.length === 0) {
        return (
            <div className="flex h-full min-h-[400px] items-center justify-center text-gray-500">
                표시할 항성계가 없습니다.
            </div>
        );
    }

    return (
        <div ref={containerRef} className="relative h-full w-full min-h-[500px]">
            <Stage
                ref={stageRef}
                width={containerSize.width}
                height={containerSize.height}
                scaleX={stageScale}
                scaleY={stageScale}
                x={stagePos.x}
                y={stagePos.y}
                draggable
                onWheel={handleWheel}
                onDragEnd={handleDragEnd}
            >
                {/* Background layer: dark space + star field */}
                <Layer listening={false}>
                    <Rect
                        x={-5000}
                        y={-5000}
                        width={10000}
                        height={10000}
                        fill={BG_COLOR}
                    />
                    <StarField
                        width={containerSize.width}
                        height={containerSize.height}
                        count={400}
                    />
                </Layer>

                {/* Routes layer */}
                <Layer listening={false}>
                    {routes.map((route) => {
                        const from = systemsById[route.fromStarId];
                        const to = systemsById[route.toStarId];
                        if (!from || !to) return null;

                        const routeKey = `${route.fromStarId}-${route.toStarId}`;
                        const isHighlighted =
                            highlightedRoutes.has(routeKey) ||
                            highlightedRoutes.has(
                                `${route.toStarId}-${route.fromStarId}`
                            );

                        return (
                            <StarRouteEdge
                                key={routeKey}
                                fromX={toCanvasX(from.x)}
                                fromY={toCanvasY(from.y)}
                                toX={toCanvasX(to.x)}
                                toY={toCanvasY(to.y)}
                                isHighlighted={isHighlighted}
                            />
                        );
                    })}
                </Layer>

                {/* Star systems layer */}
                <Layer>
                    {systems.map((system) => (
                        <StarSystemNode
                            key={system.mapStarId}
                            system={{
                                ...system,
                                x: toCanvasX(system.x),
                                y: toCanvasY(system.y),
                            }}
                            isSelected={selectedSystemId === system.mapStarId}
                            isHovered={hoveredSystemId === system.mapStarId}
                            onSelect={() => selectSystem(system.mapStarId)}
                            onHover={(hovering) =>
                                hoverSystem(hovering ? system.mapStarId : null)
                            }
                        />
                    ))}
                </Layer>
            </Stage>

            {/* Detail panel overlay (HTML, not Konva) */}
            <StarSystemDetailPanel
                system={selectedSystem ?? null}
                onClose={() => selectSystem(null)}
            />
        </div>
    );
}
