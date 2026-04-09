// Wave 0 scaffold for FE-03 command-execution-panel gating UI
// (implemented in 14-13). Asserts disabled state + tooltip per D-09, D-11.

import { describe, it, expect } from 'vitest';

describe('command-execution-panel gating UI (FE-03, D-09, D-11)', () => {
    it.skip('disables command button when target unit is outside hierarchy (D-09)', () => {
        // Implemented in 14-13 — render the panel with a non-commanded target
        // and assert the primary action button has `disabled` attribute set.
        expect(true).toBe(true);
    });

    it.skip('shows tooltip "지휘권 없음" on disabled button hover (D-09)', () => {
        // Implemented in 14-13 — Korean-only tooltip text per CLAUDE.md rule.
        expect(true).toBe(true);
    });

    it.skip('renders gold-border indicator on units under own command (D-11)', () => {
        // Implemented in 14-13 — visual affordance that targets are under
        // logged-in officer's command.
        expect(true).toBe(true);
    });

    it.skip('renders "본인의 지휘권 하 유닛입니다" badge on InfoPanel for commanded unit (D-11)', () => {
        // Implemented in 14-13 — badge appears in info panel when selected
        // unit is in the logged-in officer's hierarchy chain.
        expect(true).toBe(true);
    });
});
