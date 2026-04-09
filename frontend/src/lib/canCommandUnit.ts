// Phase 14 — Plan 14-14 — Pure gating function for FE-03 (D-09..D-12).
//
// Answers the question: "Can the logged-in officer issue this command to the
// target unit right now?" The frontend uses the result for three jobs:
//
//   1. D-09 — disable command buttons + show a Radix tooltip with the Korean
//      reason copy. The tooltip body also hints at Shift+click → proposal.
//   2. D-10 — Shift+click on a disabled button dispatches the proposal flow
//      instead of trying to execute the command directly.
//   3. D-11 — gold border on `TacticalUnitIcon` for units currently under my
//      command + "내 지휘권 하 유닛" badge on `InfoPanel`.
//
// D-12 keeps the backend as the authoritative check — this function is
// advisory UI gating. The execute path still goes through the server, which
// re-validates against the current engine hierarchy.
//
// The logic below mirrors 14-RESEARCH Section 12 and the commandChain.ts
// visibility rules (14-10) to stay a single source of truth for
// "commander → target" chain membership.

import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

/** Why the command is gated. `null` means the command is allowed. */
export type GatingReason = 'OUT_OF_CHAIN' | 'OUT_OF_CRC' | 'JAMMED' | null;

/**
 * Result of the FE-03 gating check.
 *
 * `allowed`  — true iff the logged-in officer may issue the command.
 * `reason`   — machine-readable reason when `allowed === false`.
 * `message`  — Korean user-facing copy for the disabled tooltip (only set for
 *              non-null reasons; the consumer falls back to a generic string
 *              otherwise).
 */
export interface GatingResult {
    allowed: boolean;
    reason: GatingReason;
    message?: string;
}

/**
 * D-12 — Compute whether the logged-in officer can issue commands to the
 * target unit. Pure — no store access, no I/O, no memoisation — so the
 * BattleMap layer, InfoPanel badge renderer, and command-execution-panel
 * button handler can all call this on every render without racing.
 *
 * Rules (in priority order):
 *
 *   1. No hierarchy at all            → OUT_OF_CHAIN (spectator / between-battle).
 *   2. I'm the fleet commander (or
 *      active delegated commander)
 *      AND jamming is active           → JAMMED (can't issue any order).
 *   3. I'm the fleet commander (or
 *      active delegated commander)     → allowed (any same-side unit).
 *   4. I command a sub-fleet that
 *      contains targetUnit.fleetId     → allowed.
 *   5. Anything else                   → OUT_OF_CHAIN.
 *
 * Note: self-command bypass (officer commanding their own flagship) is
 * handled implicitly by rules 3 + 4 because the commander's own fleet is
 * either the fleet commander's top-level fleet or listed in
 * subFleet.memberFleetIds. Cross-faction gating is out of scope here — the
 * caller already filters by side before rendering command buttons.
 */
export function canCommandUnit(
    myOfficerId: number,
    myHierarchy: CommandHierarchyDto | null | undefined,
    targetUnit: TacticalUnit,
): GatingResult {
    // Rule 1 — no hierarchy means we're not inside any chain (spectator /
    // admin / between battles). Render the tooltip with the generic
    // "지휘권 없음" copy and offer the Shift+click proposal hint.
    if (!myHierarchy) {
        return {
            allowed: false,
            reason: 'OUT_OF_CHAIN',
            message: '지휘권 없음 — Shift+클릭으로 상위자에게 제안하기',
        };
    }

    const amFleetCommander =
        myHierarchy.fleetCommander === myOfficerId ||
        myHierarchy.activeCommander === myOfficerId;

    // Rule 2 — jamming blocks even the fleet commander. Only the top-of-chain
    // is affected by the JAMMED reason because the buffered command path for
    // sub-fleet commanders still flows through the jammer's own chain
    // regardless (see D-12 + 14-RESEARCH Section 12). Use a dedicated reason
    // so the tooltip can show the jamming-specific copy.
    if (amFleetCommander && myHierarchy.commJammed) {
        return {
            allowed: false,
            reason: 'JAMMED',
            message: '통신 방해 중 — 명령 발령 불가',
        };
    }

    // Rule 3 — fleet commander / delegated active commander can order any
    // same-side unit.
    if (amFleetCommander) {
        return { allowed: true, reason: null };
    }

    // Rule 4 — sub-fleet commander can order every unit listed in their
    // sub-fleet's memberFleetIds. activeCommander === myOfficerId already
    // handled above — if we reach here, we're *not* the active commander.
    const mySubFleet = myHierarchy.subFleets.find(
        (sf) => sf.commanderOfficerId === myOfficerId,
    );
    if (mySubFleet && mySubFleet.memberFleetIds.includes(targetUnit.fleetId)) {
        return { allowed: true, reason: null };
    }

    // Rule 5 — everything else is out of chain. The UI should disable the
    // button and open the proposal flow on Shift+click per D-10.
    return {
        allowed: false,
        reason: 'OUT_OF_CHAIN',
        message: '지휘권 없음 — Shift+클릭으로 상위자에게 제안하기',
    };
}
