// Phase 14 Plan 14-17 — Galaxy map operations overlay tests.
//
// Covers D-28..D-31 + galaxyStore.activeOperations / handleOperationEvent
// + F1 hotkey toggle acceptance criteria.
//
// Vitest env is `node` (see vitest.config.ts) without a DOM polyfill, so
// these tests follow the Phase 14 pattern of asserting store behavior,
// pure helpers, and component source text rather than mounting React.
// See 14-09 CommandRangeCircle / 14-11 FogLayer for prior art.

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect, beforeEach } from 'vitest';
import { useGalaxyStore } from '@/stores/galaxyStore';
import {
    objectiveIcon,
} from '@/components/game/OperationsOverlay';
import { OBJECTIVE_LABEL_KO } from '@/components/game/OperationsSidePanel';
import type { OperationEventDto } from '@/types/tactical';

const SRC_ROOT = resolve(__dirname, '..', '..');

function readSource(relPath: string): string {
    return readFileSync(resolve(SRC_ROOT, relPath), 'utf8');
}

function resetStore() {
    useGalaxyStore.setState({
        systems: [],
        routes: [],
        factionTerritories: {},
        selectedSystemId: null,
        hoveredSystemId: null,
        isLoading: false,
        error: null,
        systemsById: {},
        fleetPositions: {},
        selectedFleetId: null,
        currentSystemId: null,
        battleSystemIds: new Set<number>(),
        activeOperations: [],
        requestToken: 0,
    });
}

function makeEvent(
    overrides: Partial<OperationEventDto> = {},
): OperationEventDto {
    return {
        type: 'OPERATION_PLANNED',
        operationId: 1,
        sessionId: 1,
        factionId: 1,
        objective: 'CONQUEST',
        targetStarSystemId: 42,
        participantFleetIds: [100, 101],
        status: 'PENDING',
        timestamp: 1_000,
        ...overrides,
    };
}

describe('galaxyStore.activeOperations (D-30, D-31)', () => {
    beforeEach(() => {
        resetStore();
    });

    it('starts with an empty activeOperations array', () => {
        expect(useGalaxyStore.getState().activeOperations).toEqual([]);
    });

    it('upsertOperation appends a new entry', () => {
        const evt = makeEvent({ operationId: 1 });
        useGalaxyStore.getState().upsertOperation(evt);
        expect(useGalaxyStore.getState().activeOperations).toHaveLength(1);
        expect(useGalaxyStore.getState().activeOperations[0].operationId).toBe(1);
    });

    it('upsertOperation replaces an existing entry by operationId', () => {
        const initial = makeEvent({ operationId: 1, status: 'PENDING' });
        const updated = makeEvent({
            operationId: 1,
            status: 'ACTIVE',
            type: 'OPERATION_STARTED',
        });
        useGalaxyStore.getState().upsertOperation(initial);
        useGalaxyStore.getState().upsertOperation(updated);
        const ops = useGalaxyStore.getState().activeOperations;
        expect(ops).toHaveLength(1);
        expect(ops[0].status).toBe('ACTIVE');
        expect(ops[0].type).toBe('OPERATION_STARTED');
    });

    it('removeOperation filters out the matching operationId', () => {
        useGalaxyStore.getState().upsertOperation(makeEvent({ operationId: 1 }));
        useGalaxyStore.getState().upsertOperation(makeEvent({ operationId: 2 }));
        useGalaxyStore.getState().removeOperation(1);
        const ops = useGalaxyStore.getState().activeOperations;
        expect(ops).toHaveLength(1);
        expect(ops[0].operationId).toBe(2);
    });

    it('handleOperationEvent routes PLANNED → upsert', () => {
        const evt = makeEvent({ type: 'OPERATION_PLANNED', operationId: 5 });
        useGalaxyStore.getState().handleOperationEvent(evt);
        expect(useGalaxyStore.getState().activeOperations).toHaveLength(1);
    });

    it('handleOperationEvent routes STARTED → upsert', () => {
        useGalaxyStore
            .getState()
            .handleOperationEvent(
                makeEvent({ type: 'OPERATION_PLANNED', operationId: 5 }),
            );
        useGalaxyStore.getState().handleOperationEvent(
            makeEvent({
                type: 'OPERATION_STARTED',
                operationId: 5,
                status: 'ACTIVE',
            }),
        );
        const ops = useGalaxyStore.getState().activeOperations;
        expect(ops).toHaveLength(1);
        expect(ops[0].status).toBe('ACTIVE');
    });

    it('handleOperationEvent routes COMPLETED → remove', () => {
        useGalaxyStore
            .getState()
            .handleOperationEvent(makeEvent({ operationId: 7 }));
        useGalaxyStore.getState().handleOperationEvent(
            makeEvent({
                type: 'OPERATION_COMPLETED',
                operationId: 7,
                status: 'COMPLETED',
            }),
        );
        expect(useGalaxyStore.getState().activeOperations).toHaveLength(0);
    });

    it('handleOperationEvent routes CANCELLED → remove', () => {
        useGalaxyStore
            .getState()
            .handleOperationEvent(makeEvent({ operationId: 9 }));
        useGalaxyStore.getState().handleOperationEvent(
            makeEvent({
                type: 'OPERATION_CANCELLED',
                operationId: 9,
                status: 'CANCELLED',
            }),
        );
        expect(useGalaxyStore.getState().activeOperations).toHaveLength(0);
    });
});

describe('OperationsOverlay pure helpers + Korean labels (D-28, D-30)', () => {
    it('OBJECTIVE_LABEL_KO maps the 3 objectives to D-30 Korean labels', () => {
        expect(OBJECTIVE_LABEL_KO.CONQUEST).toBe('점령');
        expect(OBJECTIVE_LABEL_KO.DEFENSE).toBe('방어');
        expect(OBJECTIVE_LABEL_KO.SWEEP).toBe('소탕');
    });

    it('objectiveIcon returns a component function per objective', () => {
        expect(typeof objectiveIcon('CONQUEST')).not.toBe('undefined');
        expect(typeof objectiveIcon('DEFENSE')).not.toBe('undefined');
        expect(typeof objectiveIcon('SWEEP')).not.toBe('undefined');
        // Lucide icons are forwardRef components; asserting reference
        // equality is sufficient.
        expect(objectiveIcon('CONQUEST')).not.toBe(objectiveIcon('DEFENSE'));
        expect(objectiveIcon('DEFENSE')).not.toBe(objectiveIcon('SWEEP'));
    });
});

describe('OperationsOverlay.tsx source contract (D-28, D-29)', () => {
    const src = readSource('components/game/OperationsOverlay.tsx');

    it('is gated on `open` — returns null when closed', () => {
        expect(src).toMatch(/if\s*\(!open\)\s*return\s*null/);
    });

    it('renders the 3 Lucide objective icons (Crosshair/ShieldCheck/Swords)', () => {
        expect(src).toMatch(/Crosshair/);
        expect(src).toMatch(/ShieldCheck/);
        expect(src).toMatch(/Swords/);
    });

    it('mounts OperationsSidePanel for the active operations list (D-30)', () => {
        expect(src).toMatch(/OperationsSidePanel/);
    });

    it('carries the F1 / Esc header hint copy (UI-SPEC Section F)', () => {
        expect(src).toMatch(/F1/);
        expect(src).toMatch(/작전 오버레이/);
        expect(src).toMatch(/Esc/);
    });
});

describe('OperationsSidePanel.tsx source contract (D-30, copywriting)', () => {
    const src = readSource('components/game/OperationsSidePanel.tsx');

    it('renders the empty-state copy "발령된 작전 없음"', () => {
        expect(src).toMatch(/발령된 작전 없음/);
    });

    it('renders the full empty-state body copy', () => {
        expect(src).toMatch(/작전계획을 발령하면 이 곳에 표시됩니다/);
    });

    it('exposes onFocus to the parent for camera panning', () => {
        expect(src).toMatch(/onFocus/);
    });

    it('embeds the 3 Korean objective labels (점령/방어/소탕)', () => {
        expect(src).toMatch(/점령/);
        expect(src).toMatch(/방어/);
        expect(src).toMatch(/소탕/);
    });
});

describe('GalaxyMap.tsx WebSocket subscription + F1 binding (D-28, D-31)', () => {
    const src = readSource('components/galaxy/GalaxyMap.tsx');

    it('subscribes to /topic/world/{sessionId}/operations', () => {
        expect(src).toMatch(/topic\/world\/.*operations/);
    });

    it('routes incoming events through galaxyStore.handleOperationEvent', () => {
        expect(src).toMatch(/handleOperationEvent/);
    });

    it('binds the F1 hotkey for the overlay toggle', () => {
        expect(src).toMatch(/F1/);
    });

    it('mounts the OperationsOverlay above the Stage', () => {
        expect(src).toMatch(/OperationsOverlay/);
    });

    it('binds Esc to close the overlay when open', () => {
        expect(src).toMatch(/Escape/);
    });
});
