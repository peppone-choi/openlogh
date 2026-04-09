// Wave 0 scaffold for FE-02 PREPARING vs ACTIVE gating (implemented in 14-12).
// Asserts battle-phase + CMD-05 (CRC-out + stationary) gating on drag sources
// per D-06, D-08.

import { describe, it, expect } from 'vitest';

describe('SubFleetAssignmentDrawer gating (FE-02, D-06)', () => {
    it.skip('PREPARING phase: ALL units are freely draggable', () => {
        // Implemented in 14-12 — render drawer with battle.phase='PREPARING'
        // and assert every unit card has draggable attribute true.
        expect(true).toBe(true);
    });

    it.skip('ACTIVE phase: units inside CRC cannot be dragged', () => {
        // Implemented in 14-12 — CMD-05 rule: cannot reassign a unit that is
        // still within a commander's CRC.
        expect(true).toBe(true);
    });

    it.skip('ACTIVE phase: only stationary units are draggable (moving=blocked)', () => {
        // Implemented in 14-12 — units with non-zero velocity must be blocked
        // from drag even if outside CRC.
        expect(true).toBe(true);
    });

    it.skip('blocked units show gray/block visual on drag start', () => {
        // Implemented in 14-12 — visual affordance consistent with command
        // gating D-09 pattern.
        expect(true).toBe(true);
    });
});
