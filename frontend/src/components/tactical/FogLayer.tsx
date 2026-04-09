/**
 * Phase 14 FE-05 — Fog-of-war ghost layer (D-17, D-18, D-20).
 *
 * Renders one `EnemyGhostIcon` per entry in `tacticalStore.lastSeenEnemyPositions`,
 * EXCLUDING enemies that are currently visible through the hierarchy's
 * aggregated sensor cones (those already render as live units in Layer 4).
 *
 * Mounted inside BattleMap's `<Layer id="fog-ghosts">` slot (carved by 14-09's
 * BattleMap restructure). This component emits Konva nodes directly — it
 * does NOT create its own `<Layer>` wrapper, so BattleMap keeps the 5-layer
 * ordering stable.
 */
'use client';

import { useMemo } from 'react';
import { useTacticalStore } from '@/stores/tacticalStore';
import {
    ghostOpacity,
    computeVisibleEnemies,
    GHOST_OPACITY_RAMP_START,
} from '@/lib/fogOfWar';
import { EnemyGhostIcon } from './EnemyGhostIcon';

export interface FogLayerProps {
    /** Logged-in officer id (owning player); drives hierarchy-shared vision. */
    myOfficerId: number;
    /** Stage → game-coordinate scale factor (x). Caller: width / GAME_W. */
    scaleX: number;
    /** Stage → game-coordinate scale factor (y). Caller: height / GAME_H. */
    scaleY: number;
}

export function FogLayer({ myOfficerId, scaleX, scaleY }: FogLayerProps) {
    const currentBattle = useTacticalStore((s) => s.currentBattle);
    const lastSeen = useTacticalStore((s) => s.lastSeenEnemyPositions);

    const { currentTick, visibleIds } = useMemo(() => {
        if (!currentBattle) {
            return { currentTick: 0, visibleIds: new Set<number>() };
        }
        const myUnit = currentBattle.units.find((u) => u.officerId === myOfficerId);
        const mySide = myUnit?.side;
        if (!mySide) {
            return {
                currentTick: currentBattle.tickCount,
                visibleIds: new Set<number>(),
            };
        }
        const hierarchy =
            mySide === 'ATTACKER'
                ? currentBattle.attackerHierarchy
                : currentBattle.defenderHierarchy;
        return {
            currentTick: currentBattle.tickCount,
            visibleIds: computeVisibleEnemies(
                currentBattle.units,
                mySide,
                myOfficerId,
                hierarchy ?? null,
            ),
        };
    }, [currentBattle, myOfficerId]);

    if (!currentBattle) return null;

    const entries = Object.entries(lastSeen);
    if (entries.length === 0) return null;

    return (
        <>
            {entries.map(([idStr, entry]) => {
                const id = Number(idStr);
                // Skip entries whose fleetId is currently visible — those
                // render as a live TacticalUnitIcon in Layer 4 (units).
                if (visibleIds.has(id)) return null;
                const ticksAgo = currentTick - entry.tick;
                const opacity = ghostOpacity(currentTick, entry.tick);
                const isStale = ticksAgo > GHOST_OPACITY_RAMP_START;
                return (
                    <EnemyGhostIcon
                        key={id}
                        fleetId={id}
                        entry={entry}
                        cx={entry.x * scaleX}
                        cy={entry.y * scaleY}
                        opacity={opacity}
                        ticksAgo={ticksAgo}
                        isStale={isStale}
                    />
                );
            })}
        </>
    );
}

export default FogLayer;
