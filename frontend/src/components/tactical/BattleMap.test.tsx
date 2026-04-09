// Wave 0 scaffold for FE-01 multi-CRC rendering from hierarchy
// (implemented in 14-10). Asserts that BattleMap renders one CRC per visible
// commander derived from attacker/defender hierarchy (D-01, D-04).

import { describe, it, expect } from 'vitest';

describe('BattleMap hierarchy-driven CRC (FE-01, D-01, D-04)', () => {
    it.skip('renders 1 CRC for fleet commander + N CRCs for sub-fleet commanders', () => {
        // Implemented in 14-10 — seed TacticalBattle with 1 commander + 2 sub-fleet
        // commanders and assert 3 CommandRangeCircle instances in the Konva tree.
        expect(true).toBe(true);
    });

    it.skip('only renders CRCs the logged-in officer is allowed to see (D-01)', () => {
        // Implemented in 14-10 — logged-in officer who is a sub-fleet commander
        // should see only own CRC + their direct fleet commander, not siblings.
        expect(true).toBe(true);
    });

    it.skip('updates CRC set when hierarchy changes mid-battle via WebSocket tick', () => {
        // Implemented in 14-10 — after onBattleTick with new hierarchy DTO,
        // the rendered CRC count must match the new sub-fleet list.
        expect(true).toBe(true);
    });
});
