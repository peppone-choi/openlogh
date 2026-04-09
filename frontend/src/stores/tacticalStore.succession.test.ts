// Phase 14 — Plan 14-15 — Succession feedback reducer tests (FE-04, D-13..D-16).
//
// Replaces the Wave 0 `it.skip` scaffold. Exercises the onBattleTick branch
// added in 14-15:
//   * FLAGSHIP_DESTROYED → activeFlagshipDestroyedFleetIds entry + flash SFX.
//   * SUCCESSION_STARTED → activeSuccessionFleetIds add + warning toast
//                          (`id: 'succ-{fleetId}'` for de-dup) + SFX.
//   * SUCCESSION_COMPLETED → remove from activeSuccessionFleetIds + success toast.
//   * Multiple events within a single tick accumulate + expired flashes prune.
//
// The test lives in vitest environment:'node' (per vitest.config.ts) so no
// react-konva / jsdom mount — we assert pure store state + mocked toast/sound
// call counts. Same pattern as tacticalStore.hierarchy.test.ts (14-10).

import { describe, it, expect, beforeEach, vi } from 'vitest';

// Mock sonner BEFORE importing the store so the store's `import { toast }`
// resolves to the mock. Returning a fresh object per-invocation is fine
// because the store imports the singleton once at load time.
vi.mock('sonner', () => ({
    toast: {
        warning: vi.fn(),
        success: vi.fn(),
        error: vi.fn(),
        info: vi.fn(),
    },
}));

// Mock useSoundEffects so we can assert which sounds fire without touching
// the browser AudioContext (not available in node).
vi.mock('@/hooks/useSoundEffects', () => ({
    playSoundEffect: vi.fn(),
    useSoundEffects: () => ({
        soundEnabled: true,
        toggleSound: vi.fn(),
        playSound: vi.fn(),
    }),
}));

import { toast } from 'sonner';
import { playSoundEffect } from '@/hooks/useSoundEffects';
import { useTacticalStore } from './tacticalStore';
import { createFixtureBattle } from '@/test/fixtures/tacticalBattleFixture';
import type {
    BattleTickBroadcast,
    BattleTickEvent,
} from '@/types/tactical';

function resetStore() {
    useTacticalStore.setState({
        currentBattle: null,
        units: [],
        recentEvents: [],
        activeBattles: [],
        loading: false,
        error: null,
        myOfficerId: null,
        lastSeenEnemyPositions: {},
        activeSuccessionFleetIds: [],
        activeFlagshipDestroyedFleetIds: [],
    });
}

function makeBroadcast(
    events: BattleTickEvent[],
    overrides: Partial<BattleTickBroadcast> = {},
): BattleTickBroadcast {
    return {
        battleId: 1,
        tickCount: 1,
        phase: 'ACTIVE',
        units: [],
        events,
        ...overrides,
    };
}

describe('tacticalStore succession reducer (FE-04, D-13..D-16)', () => {
    beforeEach(() => {
        resetStore();
        useTacticalStore.setState({ currentBattle: createFixtureBattle() });
        vi.mocked(toast.warning).mockClear();
        vi.mocked(toast.success).mockClear();
        vi.mocked(toast.error).mockClear();
        vi.mocked(playSoundEffect).mockClear();
    });

    // ── Test 1: FLAGSHIP_DESTROYED adds to activeFlagshipDestroyedFleetIds ──
    it('records FLAGSHIP_DESTROYED flash with expiresAt ≈ now + 500ms (D-14)', () => {
        const t0 = Date.now();
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                {
                    type: 'FLAGSHIP_DESTROYED',
                    sourceUnitId: 100,
                    targetUnitId: 0,
                    value: 0,
                    detail: '',
                },
            ]),
        );
        const flashes = useTacticalStore.getState().activeFlagshipDestroyedFleetIds;
        expect(flashes).toHaveLength(1);
        expect(flashes[0].fleetId).toBe(100);
        // expiresAt should be at least 500ms after `t0` minus a small jitter window.
        expect(flashes[0].expiresAt).toBeGreaterThanOrEqual(t0 + 490);
        expect(flashes[0].expiresAt).toBeLessThanOrEqual(t0 + 600);
    });

    // ── Test 2: SUCCESSION_STARTED adds to activeSuccessionFleetIds ──
    it('adds fleetId to activeSuccessionFleetIds on SUCCESSION_STARTED (D-13)', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                {
                    type: 'SUCCESSION_STARTED',
                    sourceUnitId: 100,
                    targetUnitId: 0,
                    value: 30,
                    detail: '라인하르트 사령관 전사',
                },
            ]),
        );
        expect(useTacticalStore.getState().activeSuccessionFleetIds).toEqual([100]);
    });

    // ── Test 3: SUCCESSION_COMPLETED removes from activeSuccessionFleetIds ──
    it('removes fleetId from activeSuccessionFleetIds on SUCCESSION_COMPLETED (D-16)', () => {
        useTacticalStore.setState({ activeSuccessionFleetIds: [100, 200] });
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                {
                    // sourceUnitId = new commander fleetId (200)
                    // targetUnitId = old commander fleetId (100) ← removed
                    type: 'SUCCESSION_COMPLETED',
                    sourceUnitId: 200,
                    targetUnitId: 100,
                    value: 0,
                    detail: '키르히아이스 대장',
                },
            ]),
        );
        expect(useTacticalStore.getState().activeSuccessionFleetIds).toEqual([200]);
    });

    // ── Test 4: Multiple FLAGSHIP_DESTROYED events accumulate in one tick ──
    it('accumulates multiple FLAGSHIP_DESTROYED events within a single tick', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                { type: 'FLAGSHIP_DESTROYED', sourceUnitId: 100, targetUnitId: 0, value: 0, detail: '' },
                { type: 'FLAGSHIP_DESTROYED', sourceUnitId: 200, targetUnitId: 0, value: 0, detail: '' },
                { type: 'FLAGSHIP_DESTROYED', sourceUnitId: 300, targetUnitId: 0, value: 0, detail: '' },
            ]),
        );
        const flashes = useTacticalStore.getState().activeFlagshipDestroyedFleetIds;
        expect(flashes).toHaveLength(3);
        expect(flashes.map((f) => f.fleetId).sort()).toEqual([100, 200, 300]);
    });

    // ── Test 4b: Expired entries are pruned on the NEXT tick ──
    it('prunes expired activeFlagshipDestroyedFleetIds entries on the next tick', () => {
        // Seed a stale entry (expired one second ago)
        useTacticalStore.setState({
            activeFlagshipDestroyedFleetIds: [
                { fleetId: 999, expiresAt: Date.now() - 1000 },
            ],
        });
        useTacticalStore.getState().onBattleTick(makeBroadcast([]));
        const flashes = useTacticalStore.getState().activeFlagshipDestroyedFleetIds;
        expect(flashes.find((f) => f.fleetId === 999)).toBeUndefined();
    });

    // ── Test 5: Sonner warning toast fires once per SUCCESSION_STARTED with id de-dup ──
    it('fires a destructive toast once per SUCCESSION_STARTED using id=succ-{fleetId}', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                {
                    type: 'SUCCESSION_STARTED',
                    sourceUnitId: 100,
                    targetUnitId: 0,
                    value: 30,
                    detail: '라인하르트 사령관 전사',
                },
            ]),
        );
        expect(toast.warning).toHaveBeenCalledTimes(1);
        const [message, options] = vi.mocked(toast.warning).mock.calls[0];
        expect(String(message)).toContain('기함 격침');
        expect(String(message)).toContain('라인하르트 사령관 전사');
        expect(options).toMatchObject({ id: 'succ-100' });
    });

    // ── Test 5b: SUCCESSION_COMPLETED fires a success toast ──
    it('fires a success toast on SUCCESSION_COMPLETED with new commander detail', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                {
                    type: 'SUCCESSION_COMPLETED',
                    sourceUnitId: 200,
                    targetUnitId: 100,
                    value: 0,
                    detail: '키르히아이스 대장',
                },
            ]),
        );
        expect(toast.success).toHaveBeenCalledTimes(1);
        const [message] = vi.mocked(toast.success).mock.calls[0];
        expect(String(message)).toContain('지휘 인수');
        expect(String(message)).toContain('키르히아이스 대장');
    });

    // ── Test 6: playSoundEffect called with 'flagshipDestroyed' on FLAGSHIP_DESTROYED ──
    it('plays flagshipDestroyed sound on FLAGSHIP_DESTROYED (D-15)', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                { type: 'FLAGSHIP_DESTROYED', sourceUnitId: 100, targetUnitId: 0, value: 0, detail: '' },
            ]),
        );
        expect(playSoundEffect).toHaveBeenCalledWith('flagshipDestroyed');
    });

    // ── Test 7: playSoundEffect called with 'successionStart' on SUCCESSION_STARTED ──
    it('plays successionStart sound on SUCCESSION_STARTED (D-15)', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                {
                    type: 'SUCCESSION_STARTED',
                    sourceUnitId: 100,
                    targetUnitId: 0,
                    value: 30,
                    detail: '사령관 전사',
                },
            ]),
        );
        expect(playSoundEffect).toHaveBeenCalledWith('successionStart');
    });

    // ── Test 8: De-dup flagship flash when two events reference the same fleetId ──
    it('de-duplicates FLAGSHIP_DESTROYED entries by fleetId (no double ring)', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                { type: 'FLAGSHIP_DESTROYED', sourceUnitId: 100, targetUnitId: 0, value: 0, detail: '' },
                { type: 'FLAGSHIP_DESTROYED', sourceUnitId: 100, targetUnitId: 0, value: 0, detail: '' },
            ]),
        );
        const flashes = useTacticalStore.getState().activeFlagshipDestroyedFleetIds;
        expect(flashes).toHaveLength(1);
        expect(flashes[0].fleetId).toBe(100);
    });

    // ── Test 9: Unrelated events are ignored (JAMMING_ACTIVE is handled elsewhere) ──
    it('does not touch succession state on unrelated events (JAMMING_ACTIVE, DAMAGE)', () => {
        useTacticalStore.getState().onBattleTick(
            makeBroadcast([
                { type: 'JAMMING_ACTIVE', sourceUnitId: 500, targetUnitId: 0, value: 10, detail: '' },
                { type: 'DAMAGE', sourceUnitId: 1, targetUnitId: 2, value: 100, detail: '' },
            ]),
        );
        expect(useTacticalStore.getState().activeSuccessionFleetIds).toEqual([]);
        expect(useTacticalStore.getState().activeFlagshipDestroyedFleetIds).toEqual([]);
        expect(toast.warning).not.toHaveBeenCalled();
        expect(toast.success).not.toHaveBeenCalled();
    });
});
