// Phase 14 — Plan 14-10 — Command chain source of truth (D-01, D-12, D-18)
//
// Two pure helpers that answer "who can the logged-in officer see / command":
//
//  - findVisibleCrcCommanders — returns the list of CRC rings to draw on the
//    tactical map (self + every commander below me in the chain that I can
//    actually issue orders to).
//  - findAlliesInMyChain — returns every allied unit that shares a command
//    chain with the logged-in officer (used by the fog-of-war layer in 14-11
//    to aggregate sensor coverage).
//
// Both functions are pure — no store access, no effects — so they're testable
// in isolation and callable from selectors, effects, and layer renderers.

import type {
    BattleSide,
    CommandHierarchyDto,
    TacticalUnit,
} from '@/types/tactical';

/**
 * One CRC to draw on the tactical map.
 *
 * `isMine`        — true when this is the logged-in officer's own ring.
 * `isCommandable` — true when the logged-in officer can issue orders to this
 *                   commander (self is always commandable; sub-fleet commanders
 *                   below the logged-in fleet commander are commandable; CRCs
 *                   for siblings or chain-unrelated officers are not).
 */
export interface VisibleCommander {
    officerId: number;
    officerName: string;
    isMine: boolean;
    isCommandable: boolean;
    side: BattleSide;
    /** Fleet the commander is riding (their flagship fleetId). */
    flagshipFleetId: number;
}

function findUnitForOfficer(
    officerId: number,
    side: BattleSide,
    units: TacticalUnit[],
): TacticalUnit | undefined {
    return units.find((u) => u.officerId === officerId && u.side === side);
}

/**
 * D-01 — Determine which CRCs the logged-in officer should see on the
 * tactical map.
 *
 *  - Fleet commander (or active commander after delegation)
 *      → own CRC + every sub-fleet commander's CRC (all commandable)
 *  - Sub-fleet commander
 *      → only their own CRC
 *  - Anyone else (plain officer, officer on the other side, unknown id)
 *      → empty list
 *
 * @param myOfficerId logged-in officer id (pass `-1` if not in a commander slot)
 * @param hierarchy   attackerHierarchy OR defenderHierarchy (my side's snapshot)
 * @param units       current units (to map officerId → flagship fleetId)
 * @param side        the battle side the logged-in officer is on
 */
export function findVisibleCrcCommanders(
    myOfficerId: number,
    hierarchy: CommandHierarchyDto | null | undefined,
    units: TacticalUnit[],
    side: BattleSide,
): VisibleCommander[] {
    if (!hierarchy || myOfficerId < 0) return [];

    const amFleetCommander =
        hierarchy.fleetCommander === myOfficerId ||
        hierarchy.activeCommander === myOfficerId;
    const mySubFleet = hierarchy.subFleets.find(
        (sf) => sf.commanderOfficerId === myOfficerId,
    );

    const results: VisibleCommander[] = [];

    if (amFleetCommander) {
        const myUnit = findUnitForOfficer(myOfficerId, side, units);
        if (myUnit) {
            results.push({
                officerId: myOfficerId,
                officerName: myUnit.officerName,
                isMine: true,
                isCommandable: true,
                side,
                flagshipFleetId: myUnit.fleetId,
            });
        }
        for (const sf of hierarchy.subFleets) {
            const sfUnit = findUnitForOfficer(sf.commanderOfficerId, side, units);
            if (sfUnit) {
                results.push({
                    officerId: sf.commanderOfficerId,
                    officerName: sf.commanderName,
                    isMine: false,
                    isCommandable: true,
                    side,
                    flagshipFleetId: sfUnit.fleetId,
                });
            }
        }
        return results;
    }

    if (mySubFleet) {
        const myUnit = findUnitForOfficer(myOfficerId, side, units);
        if (myUnit) {
            results.push({
                officerId: myOfficerId,
                officerName: myUnit.officerName,
                isMine: true,
                isCommandable: true,
                side,
                flagshipFleetId: myUnit.fleetId,
            });
        }
    }

    return results;
}

/**
 * D-18 — Hierarchy-shared vision. Returns every allied (same-side, alive)
 * unit that is inside the logged-in officer's command chain. 14-11 uses this
 * to aggregate sensor coverage so the fog-of-war layer can share detections
 * across a sub-fleet.
 *
 *  - Fleet commander / active commander → all alive allies on the side
 *  - Sub-fleet commander → own flagship + every `memberFleetIds` entry
 *  - Anyone else → empty list
 */
export function findAlliesInMyChain(
    myOfficerId: number,
    hierarchy: CommandHierarchyDto | null | undefined,
    units: TacticalUnit[],
    side: BattleSide,
): TacticalUnit[] {
    if (!hierarchy || myOfficerId < 0) return [];

    const allySideUnits = units.filter((u) => u.side === side && u.isAlive);

    if (
        hierarchy.fleetCommander === myOfficerId ||
        hierarchy.activeCommander === myOfficerId
    ) {
        return allySideUnits;
    }

    const mySubFleet = hierarchy.subFleets.find(
        (sf) => sf.commanderOfficerId === myOfficerId,
    );
    if (mySubFleet) {
        const memberIds = new Set(mySubFleet.memberFleetIds);
        return allySideUnits.filter(
            (u) => memberIds.has(u.fleetId) || u.officerId === myOfficerId,
        );
    }

    return [];
}
