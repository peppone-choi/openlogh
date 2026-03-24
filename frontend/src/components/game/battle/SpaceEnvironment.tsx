/// <reference types="@react-three/fiber" />
'use client';

import { useRef, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import { Stars } from '@react-three/drei';
import * as THREE from 'three';
import type { GridCell, TerrainType } from '@/stores/battleStore';

// ─── Starfield background ───────────────────────────────────────────────────
export function SpaceBackground() {
    return (
        <>
            {/* Ambient deep space lighting */}
            <ambientLight intensity={0.15} color="#334466" />
            <directionalLight position={[50, 80, 30]} intensity={0.4} color="#8899cc" />
            <directionalLight position={[-30, 60, -20]} intensity={0.2} color="#664433" />

            {/* Star particles (drei helper) */}
            <Stars radius={300} depth={100} count={3000} factor={3} saturation={0.1} fade speed={0.3} />

            {/* Distant nebula planes */}
            <NebulaPlane position={[80, -20, -120]} color="#1a0030" scale={120} rotation={0.3} />
            <NebulaPlane position={[-60, -30, -100]} color="#001a30" scale={80} rotation={-0.5} />
        </>
    );
}

// ─── Nebula decorative plane ────────────────────────────────────────────────
function NebulaPlane({
    position,
    color,
    scale,
    rotation,
}: {
    position: [number, number, number];
    color: string;
    scale: number;
    rotation: number;
}) {
    return (
        <mesh position={position} rotation={[0, rotation, 0]}>
            <planeGeometry args={[scale, scale * 0.6]} />
            <meshBasicMaterial color={color} transparent opacity={0.15} side={THREE.DoubleSide} />
        </mesh>
    );
}

// ─── Tactical grid (flat reference plane) ───────────────────────────────────
const GRID_SIZE = 20;
const CELL_SIZE = 3; // 3 world units per cell
const GRID_EXTENT = GRID_SIZE * CELL_SIZE; // 60

export const GRID_CONSTANTS = {
    GRID_SIZE,
    CELL_SIZE,
    GRID_EXTENT,
    /** Convert grid coordinate to world position */
    gridToWorld: (gx: number, gy: number): [number, number, number] => {
        const x = gx * CELL_SIZE - GRID_EXTENT / 2 + CELL_SIZE / 2;
        const z = gy * CELL_SIZE - GRID_EXTENT / 2 + CELL_SIZE / 2;
        return [x, 0, z];
    },
    /** Convert world position to grid coordinate */
    worldToGrid: (wx: number, wz: number): [number, number] => {
        const gx = Math.floor((wx + GRID_EXTENT / 2) / CELL_SIZE);
        const gz = Math.floor((wz + GRID_EXTENT / 2) / CELL_SIZE);
        return [Math.max(0, Math.min(GRID_SIZE - 1, gx)), Math.max(0, Math.min(GRID_SIZE - 1, gz))];
    },
};

export function TacticalGrid({ grid }: { grid: GridCell[][] }) {
    const lineGeometry = useMemo(() => {
        const points: THREE.Vector3[] = [];
        const half = GRID_EXTENT / 2;

        // Vertical lines
        for (let i = 0; i <= GRID_SIZE; i++) {
            const x = i * CELL_SIZE - half;
            points.push(new THREE.Vector3(x, -0.01, -half));
            points.push(new THREE.Vector3(x, -0.01, half));
        }
        // Horizontal lines
        for (let i = 0; i <= GRID_SIZE; i++) {
            const z = i * CELL_SIZE - half;
            points.push(new THREE.Vector3(-half, -0.01, z));
            points.push(new THREE.Vector3(half, -0.01, z));
        }
        const geo = new THREE.BufferGeometry().setFromPoints(points);
        return geo;
    }, []);

    // Center axis
    const centerGeometry = useMemo(() => {
        const half = GRID_EXTENT / 2;
        const points = [new THREE.Vector3(0, 0, -half), new THREE.Vector3(0, 0, half)];
        return new THREE.BufferGeometry().setFromPoints(points);
    }, []);

    return (
        <group>
            {/* Grid lines */}
            <lineSegments geometry={lineGeometry}>
                <lineBasicMaterial color="#0d0d2a" transparent opacity={0.4} />
            </lineSegments>

            {/* Center axis */}
            <line geometry={centerGeometry}>
                <lineBasicMaterial color="#ffffff" transparent opacity={0.04} />
            </line>

            {/* Terrain cells */}
            <TerrainOverlay grid={grid} />
        </group>
    );
}

// ─── Terrain overlay (asteroids, nebulae, debris) ───────────────────────────
const TERRAIN_COLORS: Record<string, string> = {
    asteroid: '#1a1a10',
    nebula: '#0d050d',
    debris: '#0d0800',
};

function TerrainOverlay({ grid }: { grid: GridCell[][] }) {
    const terrainCells = useMemo(() => {
        const cells: { x: number; y: number; terrain: TerrainType; color: string }[] = [];
        for (const row of grid) {
            for (const cell of row) {
                if (cell.terrain !== 'space') {
                    cells.push({
                        x: cell.x,
                        y: cell.y,
                        terrain: cell.terrain,
                        color: TERRAIN_COLORS[cell.terrain] ?? '#0d0d1a',
                    });
                }
            }
        }
        return cells;
    }, [grid]);

    if (terrainCells.length === 0) return null;

    return (
        <group>
            {terrainCells.map((cell) => {
                const [wx, , wz] = GRID_CONSTANTS.gridToWorld(cell.x, cell.y);
                return (
                    <mesh
                        key={`terrain-${cell.x}-${cell.y}`}
                        position={[wx, -0.005, wz]}
                        rotation={[-Math.PI / 2, 0, 0]}
                    >
                        <planeGeometry args={[CELL_SIZE * 0.95, CELL_SIZE * 0.95]} />
                        <meshBasicMaterial color={cell.color} transparent opacity={0.6} />
                    </mesh>
                );
            })}
        </group>
    );
}

// ─── Movement range highlight ───────────────────────────────────────────────
export function MovementHighlight({ cells }: { cells: Array<[number, number]> }) {
    if (cells.length === 0) return null;

    return (
        <group>
            {cells.map(([gx, gy]) => {
                const [wx, , wz] = GRID_CONSTANTS.gridToWorld(gx, gy);
                return (
                    <mesh key={`mv-${gx}-${gy}`} position={[wx, 0.01, wz]} rotation={[-Math.PI / 2, 0, 0]}>
                        <planeGeometry args={[CELL_SIZE * 0.9, CELL_SIZE * 0.9]} />
                        <meshBasicMaterial color="#4488FF" transparent opacity={0.12} />
                    </mesh>
                );
            })}
        </group>
    );
}

// ─── Selection highlight ────────────────────────────────────────────────────
export function SelectionHighlight({ gridX, gridY, color }: { gridX: number; gridY: number; color: string }) {
    const ref = useRef<THREE.Mesh>(null);
    const [wx, , wz] = GRID_CONSTANTS.gridToWorld(gridX, gridY);

    useFrame(() => {
        if (ref.current) {
            const mat = ref.current.material as THREE.MeshBasicMaterial;
            mat.opacity = 0.3 + Math.sin(Date.now() * 0.005) * 0.15;
        }
    });

    return (
        <mesh ref={ref as React.RefObject<THREE.Mesh>} position={[wx, 0.02, wz]} rotation={[-Math.PI / 2, 0, 0]}>
            <ringGeometry args={[CELL_SIZE * 0.35, CELL_SIZE * 0.45, 32]} />
            <meshBasicMaterial color={color} transparent opacity={0.4} side={THREE.DoubleSide} />
        </mesh>
    );
}
