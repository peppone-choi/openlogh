// Wave 0 scaffold for FE-03 Shift+click proposal path (implemented in 14-13).
// Asserts that a Shift-click on a disabled command button triggers the
// proposal flow instead of the primary command (D-10).

import { describe, it, expect } from 'vitest';

describe('command-execution-panel Shift+click proposal (FE-03, D-10)', () => {
    it.skip('Shift+click on disabled button opens proposal modal', () => {
        // Implemented in 14-13 — fire a pointer event with shiftKey=true on a
        // disabled command button; expect proposal-panel to become visible.
        expect(true).toBe(true);
    });

    it.skip('createProposal is called with target fleetId + command type', () => {
        // Implemented in 14-13 — assert proposalStore.createProposal spy was
        // called with the correct payload after Shift+click.
        expect(true).toBe(true);
    });

    it.skip('plain click on disabled button does NOT open proposal (Shift gate)', () => {
        // Implemented in 14-13 — regression guard that the proposal only
        // fires when Shift is held, to avoid accidental proposals.
        expect(true).toBe(true);
    });
});
