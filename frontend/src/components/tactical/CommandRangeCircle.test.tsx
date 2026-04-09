// Wave 0 scaffold for FE-01 CRC rendering (implemented in 14-09).
// Tests are `it.skip` until plan 14-09 rewrites CommandRangeCircle.tsx to a
// hierarchy-driven renderer using server commandRange (D-01..D-04).

import { describe, it, expect } from 'vitest';

describe('CommandRangeCircle (FE-01, D-01..D-04)', () => {
    it.skip('renders with server commandRange radius (no local animation)', () => {
        // Implemented in 14-09 — will mount <CommandRangeCircle radius={42} ... />
        // and assert Konva Circle `radius` prop matches the server value exactly.
        expect(true).toBe(true);
    });

    it.skip('does NOT instantiate Konva.Animation (regression guard for D-03)', () => {
        // Implemented in 14-09 — read source file text and assert no
        // "new Konva.Animation" substring remains after the rewrite.
        expect(true).toBe(true);
    });

    it.skip('uses empire color for ATTACKER side + alliance color for DEFENDER side (D-02)', () => {
        // Implemented in 14-09 — asserts stroke prop is '#4466ff' for empire
        // side and '#ff4444' for alliance side.
        expect(true).toBe(true);
    });

    it.skip('highlights with hue variant when unit is selected/hovered (D-02)', () => {
        // Implemented in 14-09 — selected/hovered CRC must use the selection
        // hue variant of the faction color, not a per-officer palette.
        expect(true).toBe(true);
    });
});
