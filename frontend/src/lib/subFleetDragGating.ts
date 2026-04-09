// Phase 14-12 — Pure drag-gating helper for the sub-fleet assignment drawer
// (FE-02). Exposes `canReassignUnit(unit, phase, hierarchy, commanderUnit)` as
// a single source of truth for both the draggable chip's `disabled` flag and
// the drop handler's defensive re-check.
//
// Binding decisions (see .planning/phases/14-frontend-integration/14-CONTEXT.md):
//   - D-05: drag/drop library is @dnd-kit/core (not react-dnd)
//   - D-06: gating timing — PREPARING is free, ACTIVE enforces CMD-05
//   - D-07: drawer is a responsive-sheet side drawer
//   - D-08: drop handler dispatches AssignSubFleet / ReassignUnit via the
//           existing WebSocket command buffer
//   - CMD-05: the administrative re-assignment command requires the target
//     unit to be OUTSIDE its current commander's CRC AND stopped
//
// The helper is a pure function: no store access, no network effects, no React
// dependency. It runs under vitest `environment: 'node'` without mocking.

import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

/**
 * Reason code for a disallowed drag, or `null` when the drag is allowed.
 *
 *  - `WITHIN_CRC`     — unit is still inside its commander's CRC (CMD-05)
 *  - `MOVING`         — unit is (or may be) moving and cannot be reassigned
 *  - `NO_HIERARCHY`   — no CommandHierarchyDto available for the side
 *  - `ALIVE_REQUIRED` — unit is dead; dead units can never be reassigned
 */
export type DragGateReason =
    | 'WITHIN_CRC'
    | 'MOVING'
    | 'NO_HIERARCHY'
    | 'ALIVE_REQUIRED'
    | null;

/** Structured result returned by `canReassignUnit`. */
export interface DragGateResult {
    allowed: boolean;
    reason: DragGateReason;
    /** Korean tooltip copy suitable for a disabled drag handle. */
    message?: string;
}

/**
 * The Phase 14 `TacticalUnit` DTO does not yet expose velocity fields or a
 * derived `isStopped` flag — see `.planning/phases/14-frontend-integration/
 * 14-RESEARCH.md` Section 13 Open Question 2. The backend engine owns an
 * `isStopped: Boolean` derived property (TacticalBattleEngine.kt:135) and the
 * DTO extension is tracked as follow-up work.
 *
 * Until the field ships, this helper reads it defensively via duck-typing: if
 * the server has populated `isStopped`, we honour it; if not, we treat the
 * unit as MOVING (the conservative choice — blocking a drag is preferable to
 * letting the server reject a mid-battle reassignment at execute time).
 */
function readIsStopped(unit: TacticalUnit): boolean | undefined {
    const maybe = (unit as unknown as { isStopped?: unknown }).isStopped;
    return typeof maybe === 'boolean' ? maybe : undefined;
}

/** Tooltip copy sourced from 14-UI-SPEC Copywriting Contract FE-02. */
const GATED_MESSAGE_KR = '이 유닛은 교전 중이라 재배정할 수 없습니다.';

/**
 * Determine whether a unit may be re-assigned via drag in the current battle
 * phase.
 *
 * Rules:
 *   1. **Dead unit** → always blocked (`ALIVE_REQUIRED`).
 *   2. **PREPARING** → any alive unit is draggable.
 *   3. **ENDED**      → nothing is draggable (battle is over).
 *   4. **ACTIVE**     → CMD-05 gating:
 *        - `hierarchy == null`         → `NO_HIERARCHY`
 *        - `commanderUnit == null`     → allowed (already unassigned; the
 *          drop handler will route it to a sub-fleet bucket or the 미배정 pool)
 *        - unit inside commander CRC  → `WITHIN_CRC`
 *        - unit unknown/explicit moving → `MOVING`
 *        - unit stopped and outside   → allowed
 *
 * @param unit             the draggable TacticalUnit the user is trying to move
 * @param phase            current battle phase from `TacticalBattle.phase`
 * @param hierarchy        CommandHierarchyDto for the unit's side (nullable)
 * @param commanderUnit    the unit's current sub-fleet commander on the
 *                         tactical map, or `null` if the unit is unassigned
 */
export function canReassignUnit(
    unit: TacticalUnit,
    phase: 'PREPARING' | 'ACTIVE' | 'ENDED',
    hierarchy: CommandHierarchyDto | null | undefined,
    commanderUnit: TacticalUnit | null,
): DragGateResult {
    // Rule 1 — dead units can never be reassigned, regardless of phase.
    if (!unit.isAlive) {
        return { allowed: false, reason: 'ALIVE_REQUIRED' };
    }

    // Rule 2 — PREPARING phase: free assignment.
    if (phase === 'PREPARING') {
        return { allowed: true, reason: null };
    }

    // Rule 3 — ENDED phase: battle is over, assignment controls are locked.
    if (phase === 'ENDED') {
        return { allowed: false, reason: 'WITHIN_CRC' };
    }

    // Rule 4 — ACTIVE phase: CMD-05 gating.
    if (!hierarchy) {
        return { allowed: false, reason: 'NO_HIERARCHY' };
    }

    // Unassigned units (no current commander) are always reassignable in
    // ACTIVE — the drop handler will put them into whichever sub-fleet bucket
    // the player drops them on.
    if (!commanderUnit) {
        return { allowed: true, reason: null };
    }

    // CRC check — compare squared distance to squared radius to avoid a sqrt.
    const dx = unit.posX - commanderUnit.posX;
    const dy = unit.posY - commanderUnit.posY;
    const distSq = dx * dx + dy * dy;
    const crcRadius = commanderUnit.commandRange ?? 0;
    const crcRadiusSq = crcRadius * crcRadius;
    const isInsideCrc = distSq <= crcRadiusSq;
    if (isInsideCrc) {
        return {
            allowed: false,
            reason: 'WITHIN_CRC',
            message: GATED_MESSAGE_KR,
        };
    }

    // Stopped check — conservative: if the server hasn't told us, assume the
    // unit is moving and block the drag. Dragging a unit that turns out to
    // have velocity would surface as a server-side rejection at execute time
    // which is a worse UX than a blocked chip on the client.
    const stopped = readIsStopped(unit);
    if (stopped !== true) {
        return {
            allowed: false,
            reason: 'MOVING',
            message: GATED_MESSAGE_KR,
        };
    }

    return { allowed: true, reason: null };
}
