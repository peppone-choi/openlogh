// Wave 0 scaffold for FE-02 sub-fleet assignment drawer
// (implemented in 14-12). Covers dnd-kit headless structure per D-05, D-07.

import { describe, it, expect } from 'vitest';

describe('SubFleetAssignmentDrawer (FE-02, D-05, D-07)', () => {
    it.skip('renders as responsive sheet (re-uses responsive-sheet component) (D-07)', () => {
        // Implemented in 14-12 — drawer shell must use responsive-sheet so
        // mobile viewport displays bottom sheet.
        expect(true).toBe(true);
    });

    it.skip('renders one drop zone per sub-fleet commander from hierarchy', () => {
        // Implemented in 14-12 — seed hierarchy with 6 staff + 1 fleet commander
        // and assert 7 dnd-kit droppable zones exist.
        expect(true).toBe(true);
    });

    it.skip('calls AssignSubFleet command on drop (D-08)', () => {
        // Implemented in 14-12 — dnd-kit onDragEnd handler must dispatch the
        // AssignSubFleet TacticalCommand via command buffer.
        expect(true).toBe(true);
    });

    it.skip('uses @dnd-kit/core (NOT react-dnd) for React 19 compatibility (D-05)', () => {
        // Implemented in 14-12 — import source assertion that the drawer
        // module imports from @dnd-kit/core, never react-dnd.
        expect(true).toBe(true);
    });
});
