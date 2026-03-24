'use client';

import { useMemo } from 'react';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';

interface NationTerritoryProps {
    cities: RenderCity[];
    cityPositions: Map<number, [number, number, number]>;
}

// Graham scan convex hull for 2D points
export function convexHull2D(points: [number, number][]): [number, number][] {
    if (points.length < 2) return points;

    // Sort by x then y
    const sorted = [...points].sort((a, b) => a[0] - b[0] || a[1] - b[1]);

    const cross = (o: [number, number], a: [number, number], b: [number, number]): number =>
        (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);

    // Lower hull
    const lower: [number, number][] = [];
    for (const p of sorted) {
        while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) {
            lower.pop();
        }
        lower.push(p);
    }

    // Upper hull
    const upper: [number, number][] = [];
    for (let i = sorted.length - 1; i >= 0; i--) {
        const p = sorted[i];
        while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) {
            upper.pop();
        }
        upper.push(p);
    }

    // Remove last point of each half (duplicate of first point of the other)
    lower.pop();
    upper.pop();
    return [...lower, ...upper];
}

export function expandHull(hull: [number, number][], amount: number): [number, number][] {
    if (hull.length === 0) return hull;

    // Compute centroid
    const cx = hull.reduce((s, p) => s + p[0], 0) / hull.length;
    const cy = hull.reduce((s, p) => s + p[1], 0) / hull.length;

    // Move each point away from centroid by `amount`
    return hull.map((p) => {
        const dx = p[0] - cx;
        const dy = p[1] - cy;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.0001) return p;
        const scale = (dist + amount) / dist;
        return [cx + dx * scale, cy + dy * scale] as [number, number];
    });
}

function hullToShape(hull: [number, number][]): THREE.Shape {
    const shape = new THREE.Shape();
    shape.moveTo(hull[0][0], hull[0][1]);
    for (let i = 1; i < hull.length; i++) {
        shape.lineTo(hull[i][0], hull[i][1]);
    }
    shape.closePath();
    return shape;
}

function parseColor(hex: string): THREE.Color {
    try {
        return new THREE.Color(hex);
    } catch {
        return new THREE.Color(0xffffff);
    }
}

interface TerritoryData {
    nationColor: string;
    // hull: for 2+ cities; circle: for exactly 1 city
    type: 'hull' | 'circle';
    // hull type
    hullPoints?: [number, number][];
    // circle type
    circleX?: number;
    circleZ?: number;
}

export function NationTerritory({ cities, cityPositions }: NationTerritoryProps) {
    const territories = useMemo((): TerritoryData[] => {
        // Group by nationColor (skip null / empty)
        const groups = new Map<string, RenderCity[]>();
        for (const city of cities) {
            if (!city.nationColor) continue;
            const key = city.nationColor;
            const arr = groups.get(key);
            if (arr) {
                arr.push(city);
            } else {
                groups.set(key, [city]);
            }
        }

        const result: TerritoryData[] = [];

        for (const [nationColor, groupCities] of groups) {
            // Collect XZ positions for each city
            const xzPoints: [number, number][] = [];
            for (const city of groupCities) {
                const pos = cityPositions.get(city.id);
                if (pos) {
                    xzPoints.push([pos[0], pos[2]]);
                }
            }

            if (xzPoints.length === 0) continue;

            if (xzPoints.length === 1) {
                result.push({
                    nationColor,
                    type: 'circle',
                    circleX: xzPoints[0][0],
                    circleZ: xzPoints[0][1],
                });
            } else {
                const hull = convexHull2D(xzPoints);
                const expanded = expandHull(hull, 2);
                result.push({
                    nationColor,
                    type: 'hull',
                    hullPoints: expanded,
                });
            }
        }

        return result;
    }, [cities, cityPositions]);

    return (
        <>
            {territories.map((t, i) => {
                const color = parseColor(t.nationColor);

                if (t.type === 'circle' && t.circleX !== undefined && t.circleZ !== undefined) {
                    return (
                        <group key={i}>
                            {/* Fill */}
                            <mesh position={[t.circleX, 0.15, t.circleZ]} rotation={[-Math.PI / 2, 0, 0]}>
                                <circleGeometry args={[3, 32]} />
                                <meshBasicMaterial
                                    color={color}
                                    transparent
                                    opacity={0.15}
                                    depthWrite={false}
                                    side={THREE.DoubleSide}
                                />
                            </mesh>
                            {/* Border */}
                            <mesh position={[t.circleX, 0.16, t.circleZ]} rotation={[-Math.PI / 2, 0, 0]}>
                                <ringGeometry args={[2.9, 3, 32]} />
                                <meshBasicMaterial
                                    color={color}
                                    transparent
                                    opacity={0.3}
                                    depthWrite={false}
                                    side={THREE.DoubleSide}
                                />
                            </mesh>
                        </group>
                    );
                }

                if (t.type === 'hull' && t.hullPoints && t.hullPoints.length >= 3) {
                    const shape = hullToShape(t.hullPoints);
                    const shapeGeometry = new THREE.ShapeGeometry(shape);

                    // Border: BufferGeometry from hull points
                    const borderPoints: THREE.Vector3[] = t.hullPoints.map((p) => new THREE.Vector3(p[0], p[1], 0));
                    borderPoints.push(borderPoints[0]); // close loop
                    const borderGeometry = new THREE.BufferGeometry().setFromPoints(borderPoints);

                    return (
                        <group key={i}>
                            {/* Fill: ShapeGeometry is in XY plane, rotate to XZ */}
                            <mesh position={[0, 0.15, 0]} rotation={[-Math.PI / 2, 0, 0]} geometry={shapeGeometry}>
                                <meshBasicMaterial
                                    color={color}
                                    transparent
                                    opacity={0.15}
                                    depthWrite={false}
                                    side={THREE.DoubleSide}
                                />
                            </mesh>
                            {/* Border line */}
                            <lineLoop position={[0, 0.16, 0]} rotation={[-Math.PI / 2, 0, 0]} geometry={borderGeometry}>
                                <lineBasicMaterial color={color} transparent opacity={0.3} />
                            </lineLoop>
                        </group>
                    );
                }

                return null;
            })}
        </>
    );
}
