// Wave 0 scaffold for FE-02 — Sub-fleet Assignment Drawer (E2E).
// All cases are test.skip until plan 14-12 implements the drawer.
// Per D-05, D-07, D-08: @dnd-kit drag-and-drop + responsive-sheet host.

import { test } from '@playwright/test';

test.describe('FE-02 — Sub-fleet assignment drawer', () => {
    test.skip('opens as side sheet and renders one drop zone per sub-fleet commander', async () => {
        // Implemented in 14-12 per D-05, D-07.
        // Steps:
        // 1. Authenticate + enter tactical battle
        // 2. Click "분함대 배정" button
        // 3. Assert drawer mounts as right-side sheet (responsive-sheet) on desktop
        // 4. Assert 6 staff drop zones + 1 reserve bucket visible
    });

    test.skip('drag-and-drops a unit card onto a sub-fleet commander slot', async () => {
        // Implemented in 14-12 per D-05, D-08.
        // Steps:
        // 1. Open drawer with 60 pre-assigned units
        // 2. page.dragAndDrop(unitCard, staffSlot)
        // 3. Assert WebSocket frame contained AssignSubFleet TacticalCommand
        // 4. Assert unit card is now under the new staff slot in re-rendered UI
    });

    test.skip('blocks drag on ACTIVE-phase units inside CRC (D-06)', async () => {
        // Implemented in 14-12 per D-06.
        // Steps:
        // 1. Force battle.phase = 'ACTIVE'
        // 2. Attempt to drag a unit that is inside its commander's CRC
        // 3. Assert drag is refused (card stays, visual = gray/block)
    });

    test.skip('drawer renders as bottom sheet on mobile viewport (D-07)', async () => {
        // Implemented in 14-12 per D-07.
        // Steps:
        // 1. Resize viewport to <768px
        // 2. Open drawer
        // 3. Assert responsive-sheet switches to bottom-sheet variant
    });
});
