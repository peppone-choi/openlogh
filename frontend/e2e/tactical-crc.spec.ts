// Wave 0 scaffold for FE-01 — Tactical Command Range Circle rendering (E2E).
// All cases are test.skip until plan 14-09 / 14-10 implement the CRC rewrite.
// Per D-01..D-04: hierarchy-driven multi-CRC using server commandRange values.

import { test } from '@playwright/test';

test.describe('FE-01 — Tactical CRC rendering', () => {
    test.skip('renders multi-CRC from hierarchy when logged in as fleet commander', async () => {
        // Implemented in 14-09 / 14-10 per D-01, D-04.
        // Steps:
        // 1. Authenticate via test JWT (see oauth-gate.spec.ts pattern)
        // 2. Stub /api/.../battles/{id} with hierarchy: 1 commander + 2 sub-commanders
        // 3. Navigate to /world/{id}/tactical/{battleId}
        // 4. Assert 3 Konva ring instances via stage.toJSON() snapshot
    });

    test.skip('CRC radius updates when backend commandRange changes', async () => {
        // Implemented in 14-09 per D-03.
        // Steps:
        // 1. Open tactical map with seeded battle
        // 2. Push mock tick broadcast lowering a commander's commandRange from 50 -> 20
        // 3. Assert the rendered Konva Circle.radius prop reflects 20 (no 3s delay)
    });

    test.skip('CRC uses faction color (empire=#4466ff / alliance=#ff4444)', async () => {
        // Implemented in 14-09 per D-02.
        // Steps:
        // 1. Seed ATTACKER hierarchy with empire faction, DEFENDER with alliance
        // 2. Read stroke attribute of each CRC Konva node
        // 3. Assert exact hex values (no per-officer palette)
    });
});
