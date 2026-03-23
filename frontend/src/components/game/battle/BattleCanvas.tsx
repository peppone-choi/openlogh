'use client';

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Stage, Layer, Rect, Circle, Line, Text, RegularPolygon, Group, Arrow } from 'react-konva';
import Konva from 'konva';
import { useBattleStore } from '@/stores/battleStore';
import type { TacticalUnit, TacticalFleet, ShipClass } from '@/types/tactical';
import type { FactionType } from '@/stores/battleStore';

// ─── Grid constants ───────────────────────────────────────────────────────────
const CELL = 32;
const GRID = 20;
const GRID_PX = CELL * GRID; // 640

// ─── Colors ───────────────────────────────────────────────────────────────────
const SPACE_BG = '#02020a';

interface FactionStyle {
    fill: string;
    stroke: string;
    glow: string;
}
const FACTION_STYLE: Record<FactionType, FactionStyle> = {
    empire: { fill: '#3a0000', stroke: '#FFD700', glow: '#FFD700' },
    alliance: { fill: '#001230', stroke: '#4488FF', glow: '#4488FF' },
    fezzan: { fill: '#1a082a', stroke: '#CC88FF', glow: '#CC88FF' },
    rebel: { fill: '#200800', stroke: '#FF8844', glow: '#FF8844' },
};

const HP_COLOR = (pct: number) => (pct > 0.6 ? '#00cc55' : pct > 0.3 ? '#ffaa00' : '#ff3333');

const TERRAIN_COLOR: Record<string, string> = {
    space: '#02020a',
    asteroid: '#1a1a10',
    nebula: '#0d050d',
    debris: '#0d0800',
};

// ─── Enriched unit (unit + fleet context) ────────────────────────────────────
interface EnrichedUnit {
    id: number;
    shipClass: ShipClass;
    hp: number;
    maxHp: number;
    gridX: number;
    gridY: number;
    factionType: FactionType;
    isMyUnit: boolean;
    isCommander: boolean; // first unit in fleet = commander
    morale: number;
}

function enrichUnits(fleets: TacticalFleet[], isMyUnit: boolean): EnrichedUnit[] {
    return fleets.flatMap((fleet) =>
        fleet.units.map((unit, idx) => ({
            id: unit.id,
            shipClass: unit.shipClass,
            hp: unit.hp,
            maxHp: unit.maxHp,
            gridX: unit.gridX ?? 0,
            gridY: unit.gridY ?? 0,
            factionType: fleet.factionType as FactionType,
            isMyUnit,
            isCommander: idx === 0,
            morale: fleet.morale,
        }))
    );
}

// ─── Stars (deterministic) ────────────────────────────────────────────────────
interface Star {
    x: number;
    y: number;
    r: number;
    a: number;
}
function buildStars(n: number): Star[] {
    const stars: Star[] = [];
    let s = 0xdeadbeef;
    for (let i = 0; i < n; i++) {
        s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
        const x = s % GRID_PX;
        s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
        const y = s % GRID_PX;
        s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
        const r = s % 10 < 2 ? 1.5 : 0.7;
        const a = 0.15 + (s % 8) * 0.07;
        stars.push({ x, y, r, a });
    }
    return stars;
}
const STARS = buildStars(200);

// ─── Movement range ───────────────────────────────────────────────────────────
function getMovementCells(gx: number, gy: number, range: number): Array<[number, number]> {
    const cells: Array<[number, number]> = [];
    for (let dx = -range; dx <= range; dx++) {
        for (let dy = -range; dy <= range; dy++) {
            if (Math.abs(dx) + Math.abs(dy) <= range && (dx !== 0 || dy !== 0)) {
                const nx = gx + dx,
                    ny = gy + dy;
                if (nx >= 0 && nx < GRID && ny >= 0 && ny < GRID) cells.push([nx, ny]);
            }
        }
    }
    return cells;
}

// ─── Local attack effect ──────────────────────────────────────────────────────
interface LocalEffect {
    id: string;
    fromX: number;
    fromY: number;
    toX: number;
    toY: number;
    kind: 'beam' | 'gun';
}

// ─── Unit shape ───────────────────────────────────────────────────────────────
function UnitShape({
    shipClass,
    fill,
    stroke,
    glow,
    selected,
    flash,
}: {
    shipClass: ShipClass;
    fill: string;
    stroke: string;
    glow: string;
    selected: boolean;
    flash: boolean;
}) {
    const glowBlur = selected ? 18 : flash ? 20 : 6;
    const glowOpacity = flash ? 0.9 : selected ? 0.7 : 0.4;
    const strokeW = selected ? 2 : 1.5;
    const effectFill = flash ? '#ff2222' : fill;

    switch (shipClass) {
        case 'battleship':
            return (
                <Rect
                    x={-10}
                    y={-7}
                    width={20}
                    height={14}
                    fill={effectFill}
                    stroke={stroke}
                    strokeWidth={strokeW}
                    shadowColor={glow}
                    shadowBlur={glowBlur}
                    shadowOpacity={glowOpacity}
                />
            );
        case 'cruiser':
            return (
                <RegularPolygon
                    sides={3}
                    radius={11}
                    fill={effectFill}
                    stroke={stroke}
                    strokeWidth={strokeW}
                    shadowColor={glow}
                    shadowBlur={glowBlur}
                    shadowOpacity={glowOpacity}
                />
            );
        case 'destroyer':
            return (
                <RegularPolygon
                    sides={4}
                    radius={9}
                    rotation={45}
                    fill={effectFill}
                    stroke={stroke}
                    strokeWidth={strokeW}
                    shadowColor={glow}
                    shadowBlur={glowBlur}
                    shadowOpacity={glowOpacity}
                />
            );
        case 'carrier':
            return (
                <Circle
                    radius={11}
                    fill={effectFill}
                    stroke={stroke}
                    strokeWidth={strokeW}
                    shadowColor={glow}
                    shadowBlur={glowBlur}
                    shadowOpacity={glowOpacity}
                />
            );
        case 'transport':
            return (
                <Rect
                    x={-11}
                    y={-6}
                    width={22}
                    height={12}
                    fill="transparent"
                    stroke={stroke}
                    strokeWidth={strokeW}
                    dash={[4, 2]}
                    shadowColor={glow}
                    shadowBlur={glowBlur}
                    shadowOpacity={glowOpacity}
                />
            );
        case 'hospital':
            return (
                <Circle
                    radius={9}
                    fill={effectFill}
                    stroke="#ff4488"
                    strokeWidth={strokeW}
                    dash={[3, 2]}
                    shadowColor="#ff4488"
                    shadowBlur={glowBlur}
                    shadowOpacity={glowOpacity}
                />
            );
        case 'fortress':
            return (
                <RegularPolygon
                    sides={6}
                    radius={12}
                    fill={effectFill}
                    stroke={stroke}
                    strokeWidth={strokeW + 0.5}
                    shadowColor={glow}
                    shadowBlur={glowBlur + 4}
                    shadowOpacity={glowOpacity}
                />
            );
        default:
            return <Circle radius={8} fill={effectFill} stroke={stroke} strokeWidth={strokeW} />;
    }
}

// ─── Tactical unit node ───────────────────────────────────────────────────────
function TacticalUnitNode({
    unit,
    selected,
    flash,
    onClick,
    onRightClick,
}: {
    unit: EnrichedUnit;
    selected: boolean;
    flash: boolean;
    onClick: (e: Konva.KonvaEventObject<MouseEvent>) => void;
    onRightClick: (e: Konva.KonvaEventObject<MouseEvent>) => void;
}) {
    const groupRef = useRef<Konva.Group>(null);
    const prevGrid = useRef({ x: unit.gridX, y: unit.gridY });

    const targetX = unit.gridX * CELL + CELL / 2;
    const targetY = unit.gridY * CELL + CELL / 2;

    useLayoutEffect(() => {
        const node = groupRef.current;
        if (!node) return;
        if (prevGrid.current.x !== unit.gridX || prevGrid.current.y !== unit.gridY) {
            node.to({ x: targetX, y: targetY, duration: 0.5, easing: Konva.Easings.EaseInOut });
            prevGrid.current = { x: unit.gridX, y: unit.gridY };
        }
    }, [unit.gridX, unit.gridY, targetX, targetY]);

    const style = FACTION_STYLE[unit.factionType] ?? FACTION_STYLE.empire;
    const hpPct = unit.maxHp > 0 ? unit.hp / unit.maxHp : 0;
    const barW = CELL - 4;

    return (
        <Group
            ref={groupRef}
            x={targetX}
            y={targetY}
            onClick={onClick}
            onContextMenu={onRightClick}
            style={{ cursor: 'pointer' }}
        >
            {selected && (
                <Rect
                    x={-CELL / 2 + 1}
                    y={-CELL / 2 + 1}
                    width={CELL - 2}
                    height={CELL - 2}
                    stroke={style.stroke}
                    strokeWidth={1.5}
                    dash={[4, 3]}
                    fill="transparent"
                    opacity={0.8}
                />
            )}
            <UnitShape
                shipClass={unit.shipClass}
                fill={style.fill}
                stroke={style.stroke}
                glow={style.glow}
                selected={selected}
                flash={flash}
            />
            {unit.isCommander && <Circle radius={2.5} x={8} y={-8} fill={style.stroke} opacity={0.9} />}
            <Rect x={-barW / 2} y={10} width={barW} height={2} fill="#111122" />
            <Rect x={-barW / 2} y={10} width={Math.max(0, barW * hpPct)} height={2} fill={HP_COLOR(hpPct)} />
        </Group>
    );
}

// ─── Attack effect ────────────────────────────────────────────────────────────
function AttackEffectLine({ effect }: { effect: LocalEffect }) {
    const [opacity, setOpacity] = useState(1);
    useEffect(() => {
        const t = setTimeout(() => setOpacity(0), 200);
        return () => clearTimeout(t);
    }, []);
    return effect.kind === 'beam' ? (
        <Line
            points={[effect.fromX, effect.fromY, effect.toX, effect.toY]}
            stroke="#FFE066"
            strokeWidth={2}
            opacity={opacity}
            shadowColor="#FFD700"
            shadowBlur={8}
            shadowOpacity={0.8}
            lineCap="round"
        />
    ) : (
        <Arrow
            points={[effect.fromX, effect.fromY, effect.toX, effect.toY]}
            stroke="#FF8822"
            strokeWidth={1.5}
            fill="#FF8822"
            pointerLength={6}
            pointerWidth={4}
            opacity={opacity}
            dash={[6, 4]}
        />
    );
}

// ─── Main BattleCanvas ────────────────────────────────────────────────────────
export interface BattleCanvasProps {
    myFleet?: { id: string } | null;
    enemyFleets?: { id: string }[];
    alliedFleets?: { id: string }[];
    selectedFleetId?: string | null;
    onFleetSelect?: (id: string) => void;
}

export function BattleCanvas(_props: BattleCanvasProps) {
    const [mounted, setMounted] = useState(false);
    const stageRef = useRef<Konva.Stage>(null);

    const { myFleets, enemyFleets, grid, selectedUnitIds, pendingOrders, selectUnit, deselectAll, issueOrder } =
        useBattleStore();

    useEffect(() => {
        setMounted(true);
    }, []);

    // ── Flatten all units with fleet context ───────────────────────────────────
    const myEnriched = enrichUnits(myFleets, true);
    const enemyEnriched = enrichUnits(enemyFleets, false);
    const allUnits = [...myEnriched, ...enemyEnriched];

    // ── Selected unit ──────────────────────────────────────────────────────────
    const selectedUnit = selectedUnitIds.length === 1 ? allUnits.find((u) => u.id === selectedUnitIds[0]) : undefined;

    const moveCells = selectedUnit ? getMovementCells(selectedUnit.gridX, selectedUnit.gridY, 3) : [];

    // ── Flash set ──────────────────────────────────────────────────────────────
    const flashSet = useRef<Set<number>>(new Set());
    const [, forceUpdate] = useState(0);

    const triggerFlash = useCallback((id: number) => {
        flashSet.current.add(id);
        forceUpdate((n) => n + 1);
        setTimeout(() => {
            flashSet.current.delete(id);
            forceUpdate((n) => n + 1);
        }, 300);
    }, []);

    // ── Local attack effects ───────────────────────────────────────────────────
    const [localEffects, setLocalEffects] = useState<LocalEffect[]>([]);

    const addEffect = useCallback(
        (fromUnit: EnrichedUnit, toUnit: EnrichedUnit) => {
            const id = `${Date.now()}-${fromUnit.id}-${toUnit.id}`;
            const fromX = fromUnit.gridX * CELL + CELL / 2;
            const fromY = fromUnit.gridY * CELL + CELL / 2;
            const toX = toUnit.gridX * CELL + CELL / 2;
            const toY = toUnit.gridY * CELL + CELL / 2;
            const kind = fromUnit.shipClass === 'battleship' ? 'beam' : 'gun';
            setLocalEffects((prev) => [...prev, { id, fromX, fromY, toX, toY, kind }]);
            triggerFlash(toUnit.id);
            setTimeout(() => setLocalEffects((prev) => prev.filter((e) => e.id !== id)), 600);
        },
        [triggerFlash]
    );

    // ── Zoom & pan ─────────────────────────────────────────────────────────────
    const handleWheel = useCallback((e: Konva.KonvaEventObject<WheelEvent>) => {
        e.evt.preventDefault();
        const stage = stageRef.current;
        if (!stage) return;
        const oldScale = stage.scaleX();
        const pointer = stage.getPointerPosition();
        if (!pointer) return;
        const scaleBy = 1.1;
        const newScale = e.evt.deltaY < 0 ? Math.min(oldScale * scaleBy, 3) : Math.max(oldScale / scaleBy, 0.4);
        const mousePointTo = { x: (pointer.x - stage.x()) / oldScale, y: (pointer.y - stage.y()) / oldScale };
        const newPos = { x: pointer.x - mousePointTo.x * newScale, y: pointer.y - mousePointTo.y * newScale };
        stage.scale({ x: newScale, y: newScale });
        stage.position(newPos);
    }, []);

    // ── Unit click ─────────────────────────────────────────────────────────────
    const handleUnitClick = useCallback(
        (id: number, e: Konva.KonvaEventObject<MouseEvent>) => {
            e.cancelBubble = true;
            selectUnit(id, e.evt.shiftKey);
        },
        [selectUnit]
    );

    // ── Unit right-click → attack order ───────────────────────────────────────
    const handleUnitRightClick = useCallback(
        (targetUnit: EnrichedUnit, e: Konva.KonvaEventObject<MouseEvent>) => {
            e.cancelBubble = true;
            e.evt.preventDefault();
            if (selectedUnitIds.length === 0) return;
            selectedUnitIds.forEach((uid) => {
                issueOrder({ unitId: uid, type: 'attack', targetX: targetUnit.gridX, targetY: targetUnit.gridY });
                const attacker = allUnits.find((u) => u.id === uid);
                if (attacker) addEffect(attacker, targetUnit);
            });
        },
        [selectedUnitIds, issueOrder, allUnits, addEffect]
    );

    // ── Stage right-click → move order ────────────────────────────────────────
    const handleStageContextMenu = useCallback(
        (e: Konva.KonvaEventObject<MouseEvent>) => {
            e.evt.preventDefault();
            const stage = stageRef.current;
            if (!stage) return;
            const pointer = stage.getPointerPosition();
            if (!pointer) return;
            const scale = stage.scaleX();
            const pos = stage.position();
            const gx = Math.floor((pointer.x - pos.x) / (CELL * scale));
            const gy = Math.floor((pointer.y - pos.y) / (CELL * scale));
            if (gx >= 0 && gx < GRID && gy >= 0 && gy < GRID && selectedUnitIds.length > 0) {
                selectedUnitIds.forEach((uid) => {
                    issueOrder({ unitId: uid, type: 'move', targetX: gx, targetY: gy });
                });
            }
        },
        [selectedUnitIds, issueOrder]
    );

    const handleStageClick = useCallback(
        (e: Konva.KonvaEventObject<MouseEvent>) => {
            if (e.target === stageRef.current || (e.target as Konva.Node).name() === 'bg') deselectAll();
        },
        [deselectAll]
    );

    if (!mounted) {
        return (
            <div
                className="bg-[#02020a] border border-amber-900/20 rounded flex items-center justify-center"
                style={{ width: '100%', height: 580 }}
            >
                <span className="text-amber-900/40 text-xs font-mono tracking-widest">TACTICAL GRID LOADING…</span>
            </div>
        );
    }

    const STAGE_W = 640;
    const STAGE_H = 580;

    return (
        <div className="relative" style={{ width: STAGE_W, height: STAGE_H }}>
            <Stage
                ref={stageRef}
                width={STAGE_W}
                height={STAGE_H}
                draggable
                onWheel={handleWheel}
                onClick={handleStageClick}
                onContextMenu={handleStageContextMenu}
                style={{ cursor: 'crosshair', display: 'block', background: SPACE_BG }}
            >
                {/* ── Layer 1: Background + Grid ──────────────────────────── */}
                <Layer>
                    <Rect x={0} y={0} width={GRID_PX} height={GRID_PX} fill={SPACE_BG} name="bg" />

                    {/* Stars */}
                    {STARS.map((s, i) => (
                        <Circle key={i} x={s.x} y={s.y} radius={s.r} fill="#ffffff" opacity={s.a} />
                    ))}

                    {/* Terrain from store grid */}
                    {grid.map((row) =>
                        row.map((cell) => {
                            if (cell.terrain === 'space') return null;
                            return (
                                <Rect
                                    key={`t-${cell.x}-${cell.y}`}
                                    x={cell.x * CELL}
                                    y={cell.y * CELL}
                                    width={CELL}
                                    height={CELL}
                                    fill={TERRAIN_COLOR[cell.terrain] ?? '#0d0d1a'}
                                    opacity={0.7}
                                />
                            );
                        })
                    )}

                    {/* Grid lines */}
                    {Array.from({ length: GRID + 1 }, (_, i) => [
                        <Line
                            key={`v${i}`}
                            points={[i * CELL, 0, i * CELL, GRID_PX]}
                            stroke="#0d0d2a"
                            strokeWidth={0.5}
                        />,
                        <Line
                            key={`h${i}`}
                            points={[0, i * CELL, GRID_PX, i * CELL]}
                            stroke="#0d0d2a"
                            strokeWidth={0.5}
                        />,
                    ])}

                    {/* Coord labels */}
                    {Array.from({ length: GRID / 2 }, (_, i) => i * 2).map((i) => [
                        <Text
                            key={`lx${i}`}
                            x={i * CELL + CELL / 2 - 5}
                            y={2}
                            text={`${i}`}
                            fontSize={7}
                            fill="#ffffff15"
                            fontFamily="monospace"
                        />,
                        <Text
                            key={`ly${i}`}
                            x={2}
                            y={i * CELL + CELL / 2 - 4}
                            text={`${i}`}
                            fontSize={7}
                            fill="#ffffff15"
                            fontFamily="monospace"
                        />,
                    ])}

                    {/* Center axis */}
                    <Line
                        points={[GRID_PX / 2, 0, GRID_PX / 2, GRID_PX]}
                        stroke="#ffffff04"
                        strokeWidth={2}
                        dash={[12, 10]}
                    />
                </Layer>

                {/* ── Layer 2: Highlights ─────────────────────────────────── */}
                <Layer listening={false}>
                    {/* Movement range */}
                    {moveCells.map(([gx, gy]) => (
                        <Rect
                            key={`mv-${gx}-${gy}`}
                            x={gx * CELL + 1}
                            y={gy * CELL + 1}
                            width={CELL - 2}
                            height={CELL - 2}
                            fill="#4488FF14"
                            stroke="#4488FF"
                            strokeWidth={0.5}
                            opacity={0.7}
                        />
                    ))}

                    {/* Pending order arrows */}
                    {pendingOrders.map((order, i) => {
                        const unit = allUnits.find((u) => u.id === order.unitId);
                        if (!unit || order.targetX == null || order.targetY == null) return null;
                        const fromX = unit.gridX * CELL + CELL / 2;
                        const fromY = unit.gridY * CELL + CELL / 2;
                        const toX = order.targetX * CELL + CELL / 2;
                        const toY = order.targetY * CELL + CELL / 2;
                        const color = order.type === 'attack' ? '#FF4444' : '#44AAFF';
                        return (
                            <Arrow
                                key={i}
                                points={[fromX, fromY, toX, toY]}
                                stroke={color}
                                strokeWidth={1.5}
                                fill={color}
                                pointerLength={6}
                                pointerWidth={4}
                                dash={[5, 3]}
                                opacity={0.7}
                            />
                        );
                    })}

                    {/* Selected cell highlight */}
                    {selectedUnit && (
                        <Rect
                            x={selectedUnit.gridX * CELL}
                            y={selectedUnit.gridY * CELL}
                            width={CELL}
                            height={CELL}
                            fill="transparent"
                            stroke={(FACTION_STYLE[selectedUnit.factionType] ?? FACTION_STYLE.empire).stroke}
                            strokeWidth={1}
                            opacity={0.5}
                            dash={[3, 2]}
                        />
                    )}
                </Layer>

                {/* ── Layer 3: Units ──────────────────────────────────────── */}
                <Layer>
                    {allUnits.map((unit) => (
                        <TacticalUnitNode
                            key={unit.id}
                            unit={unit}
                            selected={selectedUnitIds.includes(unit.id)}
                            flash={flashSet.current.has(unit.id)}
                            onClick={(e) => handleUnitClick(unit.id, e)}
                            onRightClick={(e) => handleUnitRightClick(unit, e)}
                        />
                    ))}
                </Layer>

                {/* ── Layer 4: Attack effects ─────────────────────────────── */}
                <Layer listening={false}>
                    {localEffects.map((ef) => (
                        <AttackEffectLine key={ef.id} effect={ef} />
                    ))}
                </Layer>

                {/* ── Layer 5: Labels ─────────────────────────────────────── */}
                <Layer listening={false}>
                    {allUnits.map((unit) => {
                        const x = unit.gridX * CELL + CELL / 2;
                        const y = unit.gridY * CELL + CELL / 2;
                        const style = FACTION_STYLE[unit.factionType] ?? FACTION_STYLE.empire;
                        const label = unit.hp >= 1000 ? `${Math.round(unit.hp / 100) / 10}k` : `${unit.hp}`;
                        return (
                            <Text
                                key={`lbl-${unit.id}`}
                                x={x - 16}
                                y={y - CELL / 2 - 11}
                                width={32}
                                text={label}
                                fontSize={7}
                                fill={style.stroke}
                                fontFamily="monospace"
                                align="center"
                                opacity={0.8}
                            />
                        );
                    })}

                    {/* Pending order target markers */}
                    {pendingOrders.map((order, i) => {
                        if (order.targetX == null || order.targetY == null) return null;
                        return (
                            <Text
                                key={`tgt-${i}`}
                                x={order.targetX * CELL + CELL / 2 - 6}
                                y={order.targetY * CELL + CELL / 2 - 6}
                                text={order.type === 'attack' ? '✕' : '▶'}
                                fontSize={10}
                                fill={order.type === 'attack' ? '#FF4444' : '#44AAFF'}
                                opacity={0.9}
                            />
                        );
                    })}
                </Layer>
            </Stage>

            {/* ── Minimap (outside stage) ─────────────────────────────────── */}
            <TacticalMiniMap units={allUnits} selectedIds={selectedUnitIds} stageRef={stageRef} />

            {/* ── HUD hints ────────────────────────────────────────────────── */}
            <div className="absolute top-2 left-2 text-[9px] font-mono text-amber-900/50 space-y-0.5 pointer-events-none select-none">
                <div>SCROLL: 확대/축소 DRAG: 이동</div>
                <div>CLICK: 유닛 선택 R-CLICK: 이동/공격 명령</div>
                <div>SHIFT+CLICK: 다중 선택</div>
            </div>
            <div className="absolute bottom-2 left-2 text-[8px] font-mono text-amber-900/25 pointer-events-none select-none">
                {GRID}×{GRID} 전술 그리드 // TACTICAL GRID
            </div>
        </div>
    );
}

// ─── Minimap ──────────────────────────────────────────────────────────────────
function TacticalMiniMap({
    units,
    selectedIds,
    stageRef,
}: {
    units: EnrichedUnit[];
    selectedIds: number[];
    stageRef: React.RefObject<Konva.Stage | null>;
}) {
    const SIZE = 120;
    const DOT = 4;

    const handleClick = useCallback(
        (e: React.MouseEvent<HTMLDivElement>) => {
            const rect = (e.target as HTMLElement).closest('div')?.getBoundingClientRect();
            if (!rect) return;
            const mx = e.clientX - rect.left;
            const my = e.clientY - rect.top;
            const worldX = (mx / SIZE) * GRID_PX;
            const worldY = (my / SIZE) * GRID_PX;
            const stage = stageRef.current;
            if (!stage) return;
            const stageScale = stage.scaleX();
            stage.position({ x: stage.width() / 2 - worldX * stageScale, y: stage.height() / 2 - worldY * stageScale });
            stage.batchDraw();
        },
        [stageRef]
    );

    return (
        <div
            className="absolute bottom-8 right-2 rounded border border-amber-900/30 bg-[#02020a]/90 overflow-hidden cursor-crosshair"
            style={{ width: SIZE, height: SIZE + 14 }}
            onClick={handleClick}
        >
            <div className="text-[7px] font-mono text-amber-900/50 px-1 pt-0.5 select-none">MINIMAP</div>
            <div className="relative" style={{ width: SIZE, height: SIZE }}>
                {[5, 10, 15].map((g) => (
                    <div
                        key={`mg${g}`}
                        className="absolute bg-amber-900/10"
                        style={{ left: (g / GRID) * SIZE, top: 0, width: 1, bottom: 0 }}
                    />
                ))}
                {units.map((u) => {
                    const isSelected = selectedIds.includes(u.id);
                    const color =
                        u.factionType === 'empire'
                            ? isSelected
                                ? '#FFD700'
                                : '#775500'
                            : u.factionType === 'alliance'
                              ? isSelected
                                  ? '#4488FF'
                                  : '#224488'
                              : '#888888';
                    return (
                        <div
                            key={u.id}
                            className="absolute rounded-full"
                            style={{
                                left: (u.gridX / GRID) * SIZE + SIZE / GRID / 2 - DOT / 2,
                                top: (u.gridY / GRID) * SIZE + SIZE / GRID / 2 - DOT / 2,
                                width: DOT,
                                height: DOT,
                                background: color,
                                boxShadow: isSelected ? `0 0 4px ${color}` : undefined,
                            }}
                        />
                    );
                })}
            </div>
        </div>
    );
}
