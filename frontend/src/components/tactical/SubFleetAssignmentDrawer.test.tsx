// Phase 14 Plan 14-13 — Real tests replacing Wave 0 scaffold stubs.
// FE-02 / D-05 / D-07 / D-08 / CMD-05 acceptance criteria.
//
// Strategy (per 14-13-PLAN.md executor hint + 14-10 precedent):
// The vitest config uses `environment: 'node'` and dnd-kit's DndContext depends on
// window/pointer APIs that are not available in the node env. We therefore DO NOT
// mount <SubFleetAssignmentDrawer />. Instead we test:
//   1. The pure `createDragEndHandler(sessionId, myOfficerId)` helper exported
//      alongside the drawer component — it returns a pure function that takes a
//      mock DragEndEvent and dispatches the correct command via publishWebSocket.
//   2. Source-text regression for Korean copy (drawer title, phase subtitles,
//      bucket labels) per UI-SPEC Section B.
//   3. Source-text regression for the dnd-kit-only rule (D-05): no react-dnd.
//   4. Source-text regression for publishWebSocket routing.
//
// This is the same approach 14-09 / 14-10 / 14-11 took for Konva components that
// cannot be mounted in the node env.

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Mock the websocket module BEFORE importing the drawer so the exported helper
// picks up the mock.
vi.mock('@/lib/websocket', () => ({
    publishWebSocket: vi.fn(),
}));

import { publishWebSocket } from '@/lib/websocket';
import { createDragEndHandler } from './SubFleetAssignmentDrawer';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const drawerSource = readFileSync(
    join(__dirname, 'SubFleetAssignmentDrawer.tsx'),
    'utf-8',
);

describe('SubFleetAssignmentDrawer — Korean copy & contract (FE-02, UI-SPEC Section B)', () => {
    it('uses "분함대 편성" as drawer title', () => {
        expect(drawerSource).toContain('분함대 편성');
    });

    it('renders PREPARING phase subtitle "준비 단계 — 자유롭게 배정할 수 있습니다."', () => {
        expect(drawerSource).toContain(
            '준비 단계 — 자유롭게 배정할 수 있습니다.',
        );
    });

    it('renders ACTIVE phase subtitle "교전 중 — 정지 상태이며 지휘권 밖인 유닛만 재배정할 수 있습니다."', () => {
        expect(drawerSource).toContain(
            '교전 중 — 정지 상태이며 지휘권 밖인 유닛만 재배정할 수 있습니다.',
        );
    });

    it('uses "부사령관" as the vice-commander slot label', () => {
        expect(drawerSource).toContain('부사령관');
    });

    it('uses "참모장" as the chief-of-staff slot label', () => {
        expect(drawerSource).toContain('참모장');
    });

    it('uses "참모 1", "참모 6" etc. as staff slot labels', () => {
        expect(drawerSource).toContain('참모 1');
        expect(drawerSource).toContain('참모 6');
    });

    it('uses "전계 (사령관 직할)" as the direct-command bucket label', () => {
        expect(drawerSource).toContain('전계');
        expect(drawerSource).toContain('사령관 직할');
    });

    it('uses "미배정 유닛" as the unassigned pool bucket label', () => {
        expect(drawerSource).toContain('미배정 유닛');
    });

    it('uses "유닛을 이곳으로 끌어다 놓아 배정합니다." as the empty-bucket hint', () => {
        expect(drawerSource).toContain(
            '유닛을 이곳으로 끌어다 놓아 배정합니다.',
        );
    });
});

describe('SubFleetAssignmentDrawer — D-05 dnd-kit-only regression guard', () => {
    it('does NOT import from react-dnd anywhere', () => {
        expect(drawerSource).not.toMatch(/from\s+['"]react-dnd['"]/);
    });

    it('imports DndContext from @dnd-kit/core', () => {
        expect(drawerSource).toContain('DndContext');
        expect(drawerSource).toMatch(/from\s+['"]@dnd-kit\/core['"]/);
    });

    it('uses useDroppable for bucket drop-zones', () => {
        expect(drawerSource).toContain('useDroppable');
    });

    it('uses createDragEndHandler as both export and internal use (2+ matches)', () => {
        const matches = drawerSource.match(/createDragEndHandler/g) ?? [];
        expect(matches.length).toBeGreaterThanOrEqual(2);
    });
});

describe('SubFleetAssignmentDrawer — command dispatch routing', () => {
    it('references publishWebSocket as the command dispatch sink', () => {
        expect(drawerSource).toContain('publishWebSocket');
    });

    it('dispatches AssignSubFleet commandCode', () => {
        expect(drawerSource).toContain('AssignSubFleet');
    });

    it('dispatches ReassignUnit commandCode', () => {
        expect(drawerSource).toContain('ReassignUnit');
    });

    it('targets /app/command/{sessionId}/execute STOMP destination', () => {
        expect(drawerSource).toMatch(/\/app\/command\/.*\/execute/);
    });
});

describe('createDragEndHandler (FE-02, D-08 — pure handler)', () => {
    beforeEach(() => {
        vi.mocked(publishWebSocket).mockClear();
    });

    it('returns a callable function', () => {
        const handler = createDragEndHandler(1, 1000);
        expect(typeof handler).toBe('function');
    });

    it('is a no-op when event has no "over" target (drop outside any bucket)', () => {
        const handler = createDragEndHandler(1, 1000);
        handler({
            active: { id: 'unit-100', data: { current: { fleetId: 100, officerId: 1000 } } },
            over: null,
        } as never);
        expect(publishWebSocket).not.toHaveBeenCalled();
    });

    it('is a no-op when active.data.current is missing', () => {
        const handler = createDragEndHandler(1, 1000);
        handler({
            active: { id: 'unit-100', data: { current: null } },
            over: { id: 'sub-2000' },
        } as never);
        expect(publishWebSocket).not.toHaveBeenCalled();
    });

    it('dispatches AssignSubFleet with subFleetCommanderId=2000 when dropped on sub-2000', () => {
        const handler = createDragEndHandler(1, 1000);
        handler({
            active: {
                id: 'unit-100',
                data: { current: { fleetId: 100, officerId: 999 } },
            },
            over: { id: 'sub-2000' },
        } as never);
        expect(publishWebSocket).toHaveBeenCalledTimes(1);
        expect(publishWebSocket).toHaveBeenCalledWith(
            '/app/command/1/execute',
            expect.objectContaining({
                officerId: 1000,
                commandCode: 'AssignSubFleet',
                args: expect.objectContaining({
                    targetFleetId: 100,
                    subFleetCommanderId: 2000,
                }),
            }),
        );
    });

    it('dispatches ReassignUnit with subFleetCommanderId=null when dropped on "unassigned" bucket', () => {
        const handler = createDragEndHandler(1, 1000);
        handler({
            active: {
                id: 'unit-100',
                data: { current: { fleetId: 100, officerId: 999 } },
            },
            over: { id: 'unassigned' },
        } as never);
        expect(publishWebSocket).toHaveBeenCalledTimes(1);
        expect(publishWebSocket).toHaveBeenCalledWith(
            '/app/command/1/execute',
            expect.objectContaining({
                officerId: 1000,
                commandCode: 'ReassignUnit',
                args: expect.objectContaining({
                    targetFleetId: 100,
                    subFleetCommanderId: null,
                }),
            }),
        );
    });

    it('dispatches AssignSubFleet with subFleetCommanderId=null when dropped on "direct" bucket (전계)', () => {
        // Dropping on the fleet commander's direct-control bucket is a
        // ReassignUnit to null sub-commander (backend: null = fleet commander direct).
        const handler = createDragEndHandler(1, 1000);
        handler({
            active: {
                id: 'unit-100',
                data: { current: { fleetId: 100, officerId: 999 } },
            },
            over: { id: 'direct' },
        } as never);
        expect(publishWebSocket).toHaveBeenCalledTimes(1);
        expect(publishWebSocket).toHaveBeenCalledWith(
            '/app/command/1/execute',
            expect.objectContaining({
                commandCode: 'ReassignUnit',
                args: expect.objectContaining({
                    targetFleetId: 100,
                    subFleetCommanderId: null,
                }),
            }),
        );
    });

    it('is a no-op when dropped on an empty placeholder slot (slot-N)', () => {
        // Empty slots have no commanderOfficerId yet — dragging onto them should
        // be a visual no-op (the player must first assign via a commander slot).
        const handler = createDragEndHandler(1, 1000);
        handler({
            active: {
                id: 'unit-100',
                data: { current: { fleetId: 100, officerId: 999 } },
            },
            over: { id: 'slot-3' },
        } as never);
        expect(publishWebSocket).not.toHaveBeenCalled();
    });
});
