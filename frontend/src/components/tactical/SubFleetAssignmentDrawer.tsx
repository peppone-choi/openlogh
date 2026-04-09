// Phase 14 Plan 14-13 — Sub-fleet assignment drawer (FE-02, D-05..D-08, CMD-05).
//
// Drawer component that consumes the pure gating helper from 14-12
// (`canReassignUnit`) and hosts a @dnd-kit DndContext with 10 bucket drop-zones
// (부사령관, 참모장, 참모 1-6, 전계, 미배정). Drop events dispatch AssignSubFleet
// or ReassignUnit commands via the existing WebSocket command buffer.
//
// Binding decisions (see .planning/phases/14-frontend-integration/14-CONTEXT.md):
//   - D-05 — drag/drop library is @dnd-kit/core (NOT react-dnd)
//   - D-06 — gating timing: PREPARING is free, ACTIVE enforces CMD-05
//   - D-07 — drawer is a responsive-sheet side drawer (right edge desktop)
//   - D-08 — drop handler dispatches AssignSubFleet / ReassignUnit through the
//            existing publishWebSocket command pipeline
//   - CMD-05 — administrative reassignment requires CRC-outside + stopped

'use client';

import { useMemo } from 'react';
import {
    DndContext,
    DragEndEvent,
    DragOverlay,
    PointerSensor,
    KeyboardSensor,
    useSensor,
    useSensors,
    useDroppable,
    closestCenter,
} from '@dnd-kit/core';
import { toast } from 'sonner';
import { ResponsiveSheet } from '@/components/responsive-sheet';
import { publishWebSocket } from '@/lib/websocket';
import { canReassignUnit } from '@/lib/subFleetDragGating';
import { useTacticalStore } from '@/stores/tacticalStore';
import { useWorldStore } from '@/stores/worldStore';
import { SubFleetUnitChip } from './SubFleetUnitChip';
import type {
    CommandHierarchyDto,
    SubFleetDto,
    TacticalUnit,
} from '@/types/tactical';

interface SubFleetAssignmentDrawerProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}

// ── Korean slot labels (UI-SPEC Copywriting Contract FE-02) ─────────────────
const EMPTY_SLOT_LABELS = [
    '부사령관',
    '참모장',
    '참모 1',
    '참모 2',
    '참모 3',
    '참모 4',
    '참모 5',
    '참모 6',
] as const;

const SUBTITLE_PREPARING = '준비 단계 — 자유롭게 배정할 수 있습니다.';
const SUBTITLE_ACTIVE =
    '교전 중 — 정지 상태이며 지휘권 밖인 유닛만 재배정할 수 있습니다.';
const EMPTY_BUCKET_HINT = '유닛을 이곳으로 끌어다 놓아 배정합니다.';
const DRAWER_TITLE = '분함대 편성';

// ── Pure drop handler ────────────────────────────────────────────────────────
// Exported so it can be unit-tested with a mock DragEndEvent under the node
// vitest environment (D-08 + 14-10 test pattern).

/**
 * Build a pure `onDragEnd` handler bound to the current session + officer.
 *
 * Dispatches:
 *   - `AssignSubFleet` when the drop target is a `sub-{officerId}` bucket
 *   - `ReassignUnit` when the drop target is `unassigned` OR `direct` (returns
 *     the unit to the fleet commander's direct control → subFleetCommanderId=null)
 *
 * Empty placeholder slots (`slot-{N}`) and untyped drop targets are a no-op.
 * No "over" target (drop outside any bucket) or missing active.data is also a
 * no-op.
 */
export function createDragEndHandler(
    sessionId: number,
    myOfficerId: number,
): (event: DragEndEvent) => void {
    return (event: DragEndEvent) => {
        const { active, over } = event;
        if (!over || !active?.data?.current) return;

        const targetId = String(over.id);
        const activeData = active.data.current as {
            fleetId?: number;
            officerId?: number;
        };
        const targetFleetId = activeData.fleetId;
        if (typeof targetFleetId !== 'number') return;

        let commandCode: 'AssignSubFleet' | 'ReassignUnit';
        let subFleetCommanderId: number | null;

        if (targetId.startsWith('sub-')) {
            commandCode = 'AssignSubFleet';
            subFleetCommanderId = Number(targetId.slice(4));
            if (!Number.isFinite(subFleetCommanderId)) return;
        } else if (targetId === 'unassigned' || targetId === 'direct') {
            commandCode = 'ReassignUnit';
            subFleetCommanderId = null;
        } else {
            // Empty placeholder slots and unknown targets are a no-op.
            return;
        }

        publishWebSocket(`/app/command/${sessionId}/execute`, {
            officerId: myOfficerId,
            commandCode,
            args: {
                targetFleetId,
                subFleetCommanderId,
            },
        });

        // Korean acknowledgement toast (UI-SPEC FE-02).
        toast.success(
            commandCode === 'AssignSubFleet'
                ? '분함대에 배정했습니다.'
                : '유닛을 미배정으로 돌렸습니다.',
        );
    };
}

// ── Bucket (internal droppable card) ─────────────────────────────────────────

interface BucketProps {
    id: string;
    title: string;
    empty: boolean;
    children?: React.ReactNode;
}

function Bucket({ id, title, empty, children }: BucketProps) {
    const { isOver, setNodeRef } = useDroppable({ id });
    return (
        <div
            ref={setNodeRef}
            data-slot="card"
            data-testid={`bucket-${id}`}
            className="flex flex-col rounded-md border"
            style={{
                backgroundColor: isOver ? 'var(--accent)' : 'var(--card)',
                borderColor: isOver ? 'var(--primary)' : 'var(--border)',
                minHeight: 56,
                padding: 14,
                gap: 8,
            }}
        >
            <div className="text-sm font-semibold">{title}</div>
            {empty ? (
                <div className="text-xs text-muted-foreground">
                    {EMPTY_BUCKET_HINT}
                </div>
            ) : (
                <div
                    className="grid gap-2"
                    style={{
                        gridTemplateColumns:
                            'repeat(auto-fill, minmax(120px, 1fr))',
                    }}
                >
                    {children}
                </div>
            )}
        </div>
    );
}

// ── Drawer body ──────────────────────────────────────────────────────────────

interface ResolvedBucket {
    id: string;
    title: string;
    units: TacticalUnit[];
}

function resolveMySide(
    myOfficerId: number | null,
    units: TacticalUnit[],
): { mySide: 'ATTACKER' | 'DEFENDER' | null; myUnit: TacticalUnit | null } {
    if (myOfficerId == null) return { mySide: null, myUnit: null };
    const myUnit = units.find((u) => u.officerId === myOfficerId) ?? null;
    return { mySide: myUnit?.side ?? null, myUnit };
}

function buildBuckets(
    hierarchy: CommandHierarchyDto | null | undefined,
    sideUnits: TacticalUnit[],
): ResolvedBucket[] {
    const buckets: ResolvedBucket[] = [];
    const assignedFleetIds = new Set<number>();
    const subFleets: SubFleetDto[] = hierarchy?.subFleets ?? [];

    // 1. Sub-fleet commander buckets (up to 8 slots — 부사령관, 참모장, 참모 1-6).
    subFleets.slice(0, 8).forEach((sf) => {
        const memberUnits = sideUnits.filter((u) =>
            sf.memberFleetIds.includes(u.fleetId),
        );
        memberUnits.forEach((u) => assignedFleetIds.add(u.fleetId));
        buckets.push({
            id: `sub-${sf.commanderOfficerId}`,
            title: `${sf.commanderName} (${sf.commanderRank}) — ${memberUnits.length}/60`,
            units: memberUnits,
        });
    });

    // 2. Empty placeholder slots padding to 8 commander buckets total.
    for (let i = subFleets.length; i < 8; i++) {
        buckets.push({
            id: `slot-${i}`,
            title: EMPTY_SLOT_LABELS[i] ?? `슬롯 ${i + 1}`,
            units: [],
        });
    }

    // 3. Fleet-commander direct-control bucket ("전계").
    const fleetCommanderId =
        hierarchy?.activeCommander ?? hierarchy?.fleetCommander ?? null;
    const directUnits = sideUnits.filter((u) => {
        if (assignedFleetIds.has(u.fleetId)) return false;
        if (u.subFleetCommanderId == null) {
            // Belongs to the fleet commander's direct pool iff the unit is
            // not explicitly tagged with a sub-fleet AND the fleet commander
            // is known. We still include it when the officer itself is the
            // fleet commander so the player can see their own flagship.
            return (
                fleetCommanderId != null &&
                u.officerId !== null &&
                u.isAlive
            );
        }
        return u.subFleetCommanderId === fleetCommanderId;
    });
    directUnits.forEach((u) => assignedFleetIds.add(u.fleetId));
    buckets.push({
        id: 'direct',
        title: `전계 (사령관 직할) — ${directUnits.length}`,
        units: directUnits,
    });

    // 4. "미배정" pool — anything not yet placed.
    const unassignedUnits = sideUnits.filter(
        (u) => !assignedFleetIds.has(u.fleetId),
    );
    buckets.push({
        id: 'unassigned',
        title: `미배정 유닛 — ${unassignedUnits.length}`,
        units: unassignedUnits,
    });

    return buckets;
}

export function SubFleetAssignmentDrawer({
    open,
    onOpenChange,
}: SubFleetAssignmentDrawerProps) {
    const currentBattle = useTacticalStore((s) => s.currentBattle);
    const myOfficerId = useTacticalStore((s) => s.myOfficerId);
    const currentWorld = useWorldStore((s) => s.currentWorld);

    const sensors = useSensors(
        useSensor(PointerSensor, {
            activationConstraint: { distance: 4 },
        }),
        useSensor(KeyboardSensor),
    );

    const { mySide } = useMemo(
        () => resolveMySide(myOfficerId, currentBattle?.units ?? []),
        [myOfficerId, currentBattle?.units],
    );

    const hierarchy =
        mySide === 'ATTACKER'
            ? currentBattle?.attackerHierarchy
            : mySide === 'DEFENDER'
              ? currentBattle?.defenderHierarchy
              : null;

    const sideUnits = useMemo(
        () =>
            mySide
                ? (currentBattle?.units ?? []).filter((u) => u.side === mySide)
                : [],
        [currentBattle?.units, mySide],
    );

    const buckets = useMemo(
        () => buildBuckets(hierarchy, sideUnits),
        [hierarchy, sideUnits],
    );

    if (!currentBattle || !mySide || myOfficerId == null) {
        return null;
    }

    const sessionId = currentWorld?.id ?? currentBattle.sessionId;
    // `TacticalBattle.phase` includes 'PAUSED'; the gating helper only knows
    // about PREPARING/ACTIVE/ENDED so we collapse PAUSED → ACTIVE (same rules).
    const gatingPhase: 'PREPARING' | 'ACTIVE' | 'ENDED' =
        currentBattle.phase === 'PREPARING'
            ? 'PREPARING'
            : currentBattle.phase === 'ENDED'
              ? 'ENDED'
              : 'ACTIVE';

    const subtitle =
        gatingPhase === 'PREPARING' ? SUBTITLE_PREPARING : SUBTITLE_ACTIVE;

    const handleDragEnd = createDragEndHandler(sessionId, myOfficerId);

    // Look up a unit's current sub-fleet commander unit (for gating).
    const commanderUnitFor = (unit: TacticalUnit): TacticalUnit | null => {
        const cid = unit.subFleetCommanderId;
        if (cid == null) return null;
        return sideUnits.find((u) => u.officerId === cid) ?? null;
    };

    return (
        <ResponsiveSheet
            open={open}
            onOpenChange={onOpenChange}
            title={DRAWER_TITLE}
            description={subtitle}
        >
            <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={handleDragEnd}
            >
                <div className="flex flex-col gap-4 p-4">
                    {buckets.map((bucket) => (
                        <Bucket
                            key={bucket.id}
                            id={bucket.id}
                            title={bucket.title}
                            empty={bucket.units.length === 0}
                        >
                            {bucket.units.map((unit) => {
                                const gate = canReassignUnit(
                                    unit,
                                    gatingPhase,
                                    hierarchy,
                                    commanderUnitFor(unit),
                                );
                                return (
                                    <SubFleetUnitChip
                                        key={unit.fleetId}
                                        unit={unit}
                                        disabled={!gate.allowed}
                                        disabledReason={gate.message}
                                    />
                                );
                            })}
                        </Bucket>
                    ))}
                </div>
                <DragOverlay />
            </DndContext>
        </ResponsiveSheet>
    );
}
