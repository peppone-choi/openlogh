import {
    ATTACKER_START_X,
    DEFENDER_START_X,
    ADVANCE_DURATION_RATIO,
    CLASH_DURATION_RATIO,
    CLASH_POINT_X,
} from '@/lib/battle3d-constants';
import { getVisibleUnitCount, calculateFormationPositions, lerpVec3, easeInOutQuad } from '@/lib/battle3d-utils';
import type { UnitConfig } from '@/types/battle3d';

export function getAttackerFormation(config: UnitConfig): [number, number, number][] {
    const count = getVisibleUnitCount(config.initialCrew);
    return calculateFormationPositions(count, ATTACKER_START_X, 'right');
}

export function getDefenderFormation(config: UnitConfig): [number, number, number][] {
    const count = getVisibleUnitCount(config.initialCrew);
    return calculateFormationPositions(count, DEFENDER_START_X, 'left');
}

// Phase timeline:
//   0.0 – ADVANCE_RATIO:                advance toward clash point
//   ADVANCE_RATIO – (ADVANCE+CLASH):    clash (shake/vibrate at clash point)
//   (ADVANCE+CLASH) – 1.0:             retreat back to base
const ADVANCE_END = ADVANCE_DURATION_RATIO;
const CLASH_END = ADVANCE_DURATION_RATIO + CLASH_DURATION_RATIO;

function clashOffset(side: 'attacker' | 'defender', clashT: number): number {
    // Vibrate left/right ±0.15 units during clash
    const dir = side === 'attacker' ? 1 : -1;
    return Math.sin(clashT * Math.PI * 8) * 0.15 * dir;
}

export function getPhasePositions(
    basePositions: [number, number, number][],
    side: 'attacker' | 'defender',
    phaseProgress: number
): [number, number, number][] {
    const startX = side === 'attacker' ? ATTACKER_START_X : DEFENDER_START_X;
    // Clash target: units move to clash point, preserving z/y offsets from base
    const clashPositions: [number, number, number][] = basePositions.map(([, y, z]) => [
        CLASH_POINT_X + (side === 'attacker' ? -1 : 1),
        y,
        z,
    ]);

    return basePositions.map((base, i) => {
        const clash = clashPositions[i];

        if (phaseProgress <= ADVANCE_END) {
            // Advance phase: base → clash
            const t = easeInOutQuad(phaseProgress / ADVANCE_END);
            return lerpVec3(base, clash, t);
        }

        if (phaseProgress <= CLASH_END) {
            // Clash phase: stay near clash point + shake
            const clashT = (phaseProgress - ADVANCE_END) / CLASH_DURATION_RATIO;
            const shakeX = clashOffset(side, clashT);
            return [clash[0] + shakeX, clash[1], clash[2]];
        }

        // Retreat phase: clash → base
        const retreatT = easeInOutQuad((phaseProgress - CLASH_END) / (1 - CLASH_END));
        // Retreat slightly past original position then spring back — simple lerp is fine for Phase 1
        const retreated = lerpVec3(clash, base, retreatT);
        // Nudge x so retreating units face away properly
        void startX; // referenced to satisfy the import; actual offset comes from base
        return retreated;
    });
}
