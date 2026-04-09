/**
 * Phase 14 FE-05 — Fog-of-war pure helpers.
 *
 * Implements D-17 (last-seen ghost storage), D-18 (hierarchy-shared vision),
 * D-19 (sensorRange-driven visibility), D-20 (ghost TTL + opacity ramp).
 *
 * All functions are pure so they can be unit-tested without a store / React.
 * The tacticalStore onBattleTick reducer calls these on every tick broadcast.
 *
 * Hierarchy resolution delegates to 14-10's `findAlliesInMyChain` in
 * `@/lib/commandChain` — single source of truth for "who shares my command
 * chain".
 */

import type {
    TacticalUnit,
    CommandHierarchyDto,
    BattleSide,
} from '@/types/tactical';
import { findAlliesInMyChain } from './commandChain';

export interface GhostEntry {
    x: number;
    y: number;
    tick: number;
    ships: number;
    unitType: string;
    side: BattleSide;
}

export const GHOST_TTL_TICKS = 60;
export const GHOST_OPACITY_MAX = 0.4;
export const GHOST_OPACITY_MIN = 0.15;
/** Age (in ticks) at which the opacity ramp begins. Fresh ghosts stay at MAX. */
export const GHOST_OPACITY_RAMP_START = 30;

/**
 * D-18 hierarchy-shared vision: compute which enemy fleetIds are currently
 * visible given the sensor cones of all allies in my command chain.
 *
 * Iterates enemies × allies and checks distance <= sensorRange. Returns the
 * set of visible enemy fleetIds (the D-17 upsert key).
 *
 *   - Fleet commander → every alive ally on my side contributes sensor
 *   - Sub-fleet commander → every unit in my sub-fleet + me
 *   - Plain officer / unrecognised id → empty (cannot see anything through chain)
 *
 * The actual chain resolution lives in `commandChain.findAlliesInMyChain`;
 * this function only layers the distance check on top.
 */
export function computeVisibleEnemies(
    units: TacticalUnit[],
    mySide: BattleSide,
    myOfficerId: number,
    hierarchy: CommandHierarchyDto | null | undefined,
): Set<number> {
    const allies = findAlliesInMyChain(myOfficerId, hierarchy, units, mySide);
    const visible = new Set<number>();

    for (const enemy of units) {
        if (enemy.side === mySide) continue;
        if (!enemy.isAlive) continue;
        for (const ally of allies) {
            const range = ally.sensorRange ?? 0;
            if (range <= 0) continue;
            const dx = enemy.posX - ally.posX;
            const dy = enemy.posY - ally.posY;
            const distSq = dx * dx + dy * dy;
            if (distSq <= range * range) {
                visible.add(enemy.fleetId);
                break;
            }
        }
    }
    return visible;
}

/**
 * D-20 fog update rules — applied on every onBattleTick:
 *   1. For each currently-visible enemy → upsert lastSeen with current tick.
 *   2. For each ghost NOT in visible set → retain (stale preserved).
 *   3. For each dead enemy → remove (stop ghosting corpses).
 *   4. Prune entries older than GHOST_TTL_TICKS.
 *
 * Pure / no mutation — returns a new record.
 */
export function updateLastSeenEnemyPositions(
    prev: Record<number, GhostEntry>,
    units: TacticalUnit[],
    visibleEnemyIds: Set<number>,
    currentTick: number,
    mySide: BattleSide,
): Record<number, GhostEntry> {
    const next: Record<number, GhostEntry> = { ...prev };

    // Pass 1: dead enemy cleanup + live upsert.
    for (const u of units) {
        if (u.side === mySide) continue;
        if (!u.isAlive) {
            delete next[u.fleetId];
            continue;
        }
        if (visibleEnemyIds.has(u.fleetId)) {
            next[u.fleetId] = {
                x: u.posX,
                y: u.posY,
                tick: currentTick,
                ships: u.ships,
                unitType: u.unitType,
                side: u.side,
            };
        }
    }

    // Pass 2: prune old ghosts.
    for (const fleetIdStr of Object.keys(next)) {
        const id = Number(fleetIdStr);
        const entry = next[id];
        if (currentTick - entry.tick > GHOST_TTL_TICKS) {
            delete next[id];
        }
    }

    return next;
}

/**
 * D-20 opacity ramp:
 *   - Age 0..GHOST_OPACITY_RAMP_START → GHOST_OPACITY_MAX (0.4)
 *   - Age GHOST_OPACITY_RAMP_START..GHOST_TTL_TICKS → linear ramp 0.4 → 0.15
 *   - Age > GHOST_TTL_TICKS → GHOST_OPACITY_MIN (0.15) — should never render
 *     because the entry would have been pruned, but the clamp is defensive.
 */
export function ghostOpacity(currentTick: number, entryTick: number): number {
    const age = currentTick - entryTick;
    if (age < 0) return GHOST_OPACITY_MAX;
    if (age <= GHOST_OPACITY_RAMP_START) return GHOST_OPACITY_MAX;
    if (age >= GHOST_TTL_TICKS) return GHOST_OPACITY_MIN;
    const progress =
        (age - GHOST_OPACITY_RAMP_START) /
        (GHOST_TTL_TICKS - GHOST_OPACITY_RAMP_START);
    return GHOST_OPACITY_MAX + (GHOST_OPACITY_MIN - GHOST_OPACITY_MAX) * progress;
}
