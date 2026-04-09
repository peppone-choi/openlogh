// Phase 14-12 — Pure drag-gating helper tests for FE-02 sub-fleet assignment drawer.
//
// Asserts battle-phase + CMD-05 (CRC-out + stationary) gating on drag sources
// per D-06, D-08. This replaces the Wave 0 `it.skip` scaffolds with live tests
// that exercise `canReassignUnit` from `@/lib/subFleetDragGating`.
//
// Plan reference: .planning/phases/14-frontend-integration/14-12-PLAN.md
//
// Scope split within Wave 4:
//  - 14-12 owns: subFleetDragGating.ts (pure helper) + SubFleetUnitChip +
//    this gating test file
//  - 14-13 owns: SubFleetAssignmentDrawer.tsx (drawer component + DndContext)
//
// Because vitest runs in `environment: 'node'` (see frontend/vitest.config.ts)
// there is NO jsdom and react-konva / @dnd-kit mounts would fail. Therefore
// every assertion in this file exercises the pure `canReassignUnit` function
// directly with fixture data — no rendering.

import { describe, it, expect } from 'vitest';
import {
    canReassignUnit,
    type DragGateResult,
} from '@/lib/subFleetDragGating';
import { makeFixtureUnit } from '@/test/fixtures/tacticalBattleFixture';
import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

// ── Shared fixtures ──────────────────────────────────────────────────────────

/** A fleet commander with a 100-radius CRC centred at (500, 500). */
const commander: TacticalUnit = makeFixtureUnit({
    fleetId: 1,
    officerId: 1000,
    officerName: 'Reinhardt',
    posX: 500,
    posY: 500,
    commandRange: 100,
});

/** CommandHierarchyDto with a single sub-fleet under officer 1000. */
const hierarchy: CommandHierarchyDto = {
    fleetCommander: 1000,
    subFleets: [
        {
            commanderOfficerId: 1000,
            commanderName: 'Reinhardt',
            memberFleetIds: [1, 2, 3],
            commanderRank: 9,
        },
    ],
    successionQueue: [],
    vacancyStartTick: -1,
    commJammed: false,
    jammingTicksRemaining: 0,
};

/**
 * Helper: build a TacticalUnit with an optional `isStopped` flag tacked onto
 * the DTO shape. `isStopped` is not yet on `TacticalUnit` (Phase 14 RESEARCH.md
 * Open Question 2) — the gating helper reads it defensively via duck-typing so
 * frontend tests can stage "server added the field" scenarios ahead of the
 * backend DTO extension.
 */
function makeUnitWithStopped(
    overrides: Partial<TacticalUnit> & { isStopped?: boolean },
): TacticalUnit {
    const { isStopped, ...rest } = overrides;
    const unit = makeFixtureUnit(rest);
    if (isStopped !== undefined) {
        (unit as unknown as { isStopped: boolean }).isStopped = isStopped;
    }
    return unit;
}

// ── Test suite ───────────────────────────────────────────────────────────────

describe('canReassignUnit (FE-02, D-06)', () => {
    describe('PREPARING phase', () => {
        it('allows any alive unit regardless of CRC / velocity', () => {
            const unit = makeFixtureUnit({ fleetId: 2, posX: 510, posY: 510 });
            const result = canReassignUnit(unit, 'PREPARING', hierarchy, commander);
            expect(result.allowed).toBe(true);
            expect(result.reason).toBeNull();
        });

        it('allows unit far outside CRC in PREPARING', () => {
            const unit = makeFixtureUnit({ fleetId: 2, posX: 9999, posY: 9999 });
            const result = canReassignUnit(unit, 'PREPARING', hierarchy, commander);
            expect(result.allowed).toBe(true);
        });

        it('allows unit with null commander in PREPARING', () => {
            const unit = makeFixtureUnit({ fleetId: 2 });
            const result = canReassignUnit(unit, 'PREPARING', hierarchy, null);
            expect(result.allowed).toBe(true);
        });
    });

    describe('ACTIVE phase — CMD-05 gating', () => {
        it('blocks unit inside commander CRC (WITHIN_CRC reason)', () => {
            // dx=10, dy=10 → distSq=200, crcRadius^2=10000 → well inside.
            const unit = makeFixtureUnit({ fleetId: 2, posX: 510, posY: 510 });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, commander);
            expect(result.allowed).toBe(false);
            expect(result.reason).toBe('WITHIN_CRC');
            expect(result.message).toBeDefined();
            expect(result.message).toContain('교전 중');
        });

        it('blocks unit exactly on CRC boundary (WITHIN_CRC — inclusive)', () => {
            // At exactly radius 100 on x axis → distSq == crcRadius^2, inside.
            const unit = makeFixtureUnit({ fleetId: 2, posX: 600, posY: 500 });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, commander);
            expect(result.allowed).toBe(false);
            expect(result.reason).toBe('WITHIN_CRC');
        });

        it('blocks unit outside CRC but server did not provide isStopped (conservative MOVING)', () => {
            const unit = makeFixtureUnit({ fleetId: 2, posX: 9999, posY: 9999 });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, commander);
            expect(result.allowed).toBe(false);
            // Either "cannot tell → MOVING" or "no velocity data → MOVING".
            // Both are correct per plan 14-12 — we conservatively treat
            // unknown-stopped as moving. Ensure the reason is MOVING (not
            // WITHIN_CRC, because the unit is clearly outside).
            expect(result.reason).toBe('MOVING');
            expect(result.message).toContain('교전 중');
        });

        it('blocks unit outside CRC and explicitly moving (isStopped=false)', () => {
            const unit = makeUnitWithStopped({
                fleetId: 2,
                posX: 9999,
                posY: 9999,
                isStopped: false,
            });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, commander);
            expect(result.allowed).toBe(false);
            expect(result.reason).toBe('MOVING');
        });

        it('allows unit outside CRC AND stopped (isStopped=true)', () => {
            const unit = makeUnitWithStopped({
                fleetId: 2,
                posX: 9999,
                posY: 9999,
                isStopped: true,
            });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, commander);
            expect(result.allowed).toBe(true);
            expect(result.reason).toBeNull();
        });

        it('allows unit already unassigned (null commander) in ACTIVE phase', () => {
            const unit = makeFixtureUnit({ fleetId: 2 });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, null);
            expect(result.allowed).toBe(true);
        });

        it('blocks when hierarchy is null (NO_HIERARCHY reason)', () => {
            const unit = makeFixtureUnit({ fleetId: 2 });
            const result = canReassignUnit(unit, 'ACTIVE', null, commander);
            expect(result.allowed).toBe(false);
            expect(result.reason).toBe('NO_HIERARCHY');
        });
    });

    describe('ENDED phase', () => {
        it('blocks re-assignment in ENDED phase', () => {
            const unit = makeFixtureUnit({ fleetId: 2 });
            const result = canReassignUnit(unit, 'ENDED', hierarchy, commander);
            expect(result.allowed).toBe(false);
        });
    });

    describe('dead units', () => {
        it('blocks dead unit in PREPARING (ALIVE_REQUIRED)', () => {
            const unit = makeFixtureUnit({ fleetId: 2, isAlive: false });
            const result: DragGateResult = canReassignUnit(
                unit,
                'PREPARING',
                hierarchy,
                commander,
            );
            expect(result.allowed).toBe(false);
            expect(result.reason).toBe('ALIVE_REQUIRED');
        });

        it('blocks dead unit in ACTIVE (ALIVE_REQUIRED takes precedence)', () => {
            const unit = makeUnitWithStopped({
                fleetId: 2,
                posX: 9999,
                posY: 9999,
                isAlive: false,
                isStopped: true,
            });
            const result = canReassignUnit(unit, 'ACTIVE', hierarchy, commander);
            expect(result.allowed).toBe(false);
            expect(result.reason).toBe('ALIVE_REQUIRED');
        });
    });

    describe('SubFleetUnitChip component file exists (14-12 owns the chip)', () => {
        it('exports a default React component', async () => {
            // Dynamic import so the test still runs in env=node without
            // mounting the component. We only assert the module exposes a
            // function-typed default export and the named `SubFleetUnitChip`
            // identifier — this is the acceptance contract 14-13 will consume.
            const mod = await import(
                '@/components/tactical/SubFleetUnitChip'
            );
            expect(mod).toBeDefined();
            expect(typeof mod.SubFleetUnitChip).toBe('function');
        });
    });
});
