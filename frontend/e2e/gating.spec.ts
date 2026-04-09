// Wave 0 scaffold for FE-03 — Command gating UI (E2E).
// All cases are test.skip until plan 14-13 implements canCommandUnit gating.
// Per D-09, D-10, D-11: disabled + tooltip + Shift+click proposal path.

import { test } from '@playwright/test';

test.describe('FE-03 — Command gating UI', () => {
    test.skip('disables command button when selected unit is outside hierarchy', async () => {
        // Implemented in 14-13 per D-09.
        // Steps:
        // 1. Authenticate as a non-commander officer
        // 2. Click a unit belonging to another sub-fleet
        // 3. Assert the primary command button has [disabled]
    });

    test.skip('shows "지휘권 없음" tooltip on hover of disabled button', async () => {
        // Implemented in 14-13 per D-09.
        // Steps:
        // 1. Same as above; hover disabled button
        // 2. Assert tooltip text contains "지휘권 없음" (Korean-only per CLAUDE.md)
    });

    test.skip('Shift+click on disabled button opens proposal flow (D-10)', async () => {
        // Implemented in 14-13 per D-10.
        // Steps:
        // 1. Select out-of-hierarchy unit
        // 2. page.keyboard.down('Shift') + click command button
        // 3. Assert proposal-panel becomes visible with target fleetId pre-filled
    });

    test.skip('renders gold border on units under own command (D-11)', async () => {
        // Implemented in 14-13 per D-11.
        // Steps:
        // 1. Log in as fleet commander
        // 2. Assert every unit in own hierarchy has a gold outline stroke
    });
});
