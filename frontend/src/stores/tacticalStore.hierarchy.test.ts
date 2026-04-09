// Wave 0 scaffold for FE-03 hierarchy source-of-truth (implemented in 14-10/14-13).
// Asserts tacticalStore exposes attacker/defender hierarchy DTOs and
// derives gating-ready helpers (D-09..D-12).

import { describe, it, expect } from 'vitest';

describe('tacticalStore hierarchy SoT (FE-03, D-09..D-12)', () => {
    it.skip('stores attackerHierarchy + defenderHierarchy from BattleTickBroadcast', () => {
        // Implemented in 14-10 — onBattleTick payload carries both hierarchies;
        // reducer must persist them verbatim into the store slice.
        expect(true).toBe(true);
    });

    it.skip('derives subFleetCommanderId lookup by fleetId', () => {
        // Implemented in 14-10/14-13 — selector must answer
        // "which sub-fleet commander owns this fleet?" from hierarchy DTO.
        expect(true).toBe(true);
    });

    it.skip('exposes current officer chain-of-command for gating (D-12)', () => {
        // Implemented in 14-13 — selector returns the list of ancestor officers
        // for a given fleet so canCommandUnit can gate on membership.
        expect(true).toBe(true);
    });

    it.skip('invalidates stale hierarchy when battleId changes', () => {
        // Implemented in 14-10 — switching battles must clear any cached
        // hierarchy from the previous battle.
        expect(true).toBe(true);
    });
});
