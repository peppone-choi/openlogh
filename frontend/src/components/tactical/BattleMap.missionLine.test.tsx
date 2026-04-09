// Phase 14 Plan 14-16 — BattleMap mission target line tests (D-37).
//
// Separate test file from BattleMap.test.tsx to avoid merge conflicts with
// sibling Wave 4/5 plans (14-14, 14-15) that also extend BattleMap.
//
// Tests the exported pure helper `clampMissionLineEnd` and a source-text
// regression guard that the NPC-colored dashed Line renders inside the
// units Layer.

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { clampMissionLineEnd } from './BattleMap';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const battleMapSource = readFileSync(join(__dirname, 'BattleMap.tsx'), 'utf-8');

describe('BattleMap mission target line (D-37)', () => {
    describe('clampMissionLineEnd — pure helper', () => {
        const viewport = { viewportWidth: 1000, viewportHeight: 600 };

        it('returns the raw target when it is already inside the viewport', () => {
            const result = clampMissionLineEnd({
                unitX: 100,
                unitY: 100,
                targetX: 500,
                targetY: 300,
                ...viewport,
            });
            expect(result.isClamped).toBe(false);
            expect(result.endX).toBe(500);
            expect(result.endY).toBe(300);
        });

        it('clamps the end-point to the right edge when target is far right', () => {
            const result = clampMissionLineEnd({
                unitX: 500,
                unitY: 300,
                targetX: 2000, // off-screen right
                targetY: 300,
                ...viewport,
            });
            expect(result.isClamped).toBe(true);
            expect(result.endX).toBe(1000); // clipped to viewport width
            expect(result.endY).toBe(300); // horizontal ray — Y unchanged
        });

        it('clamps to the bottom edge when target is far below', () => {
            const result = clampMissionLineEnd({
                unitX: 500,
                unitY: 100,
                targetX: 500,
                targetY: 2000, // off-screen bottom
                ...viewport,
            });
            expect(result.isClamped).toBe(true);
            expect(result.endY).toBe(600);
            expect(result.endX).toBe(500);
        });

        it('clamps diagonally when target is off the bottom-right corner', () => {
            const result = clampMissionLineEnd({
                unitX: 0,
                unitY: 0,
                targetX: 2000,
                targetY: 1200, // slope 0.6 — ray hits right edge first at y=600
                ...viewport,
            });
            expect(result.isClamped).toBe(true);
            // Ray from (0,0) to (2000, 1200) hits right edge (x=1000) at y=600.
            expect(result.endX).toBeCloseTo(1000, 1);
            expect(result.endY).toBeCloseTo(600, 1);
        });

        it('returns the unit position unchanged when target equals unit position', () => {
            const result = clampMissionLineEnd({
                unitX: 500,
                unitY: 300,
                targetX: 500,
                targetY: 300,
                ...viewport,
            });
            expect(result.isClamped).toBe(false);
            expect(result.endX).toBe(500);
            expect(result.endY).toBe(300);
        });

        it('handles off-screen left correctly', () => {
            const result = clampMissionLineEnd({
                unitX: 500,
                unitY: 300,
                targetX: -500,
                targetY: 300,
                ...viewport,
            });
            expect(result.isClamped).toBe(true);
            expect(result.endX).toBe(0);
            expect(result.endY).toBe(300);
        });
    });

    describe('BattleMap source regression guard (D-37 dashed NPC line)', () => {
        it('renders a dashed Line with NPC color #a78bfa in the units layer', () => {
            // NPC marker color re-used for the mission line to keep the visual thread.
            expect(battleMapSource).toMatch(/#a78bfa/);
            // Dashed stroke — [5, 5] per plan.
            expect(battleMapSource).toMatch(/dash=\{\[5,\s*5\]\}/);
        });

        it('gates the mission line on isNpc + missionObjective + targetStarSystemId', () => {
            expect(battleMapSource).toMatch(/isNpc/);
            expect(battleMapSource).toMatch(/missionObjective/);
            expect(battleMapSource).toMatch(/targetStarSystemId/);
        });

        it('exports clampMissionLineEnd for testability', () => {
            expect(battleMapSource).toMatch(/export function clampMissionLineEnd/);
        });

        it('mission line is non-listening (no click interception)', () => {
            // The Line block before the units.map call must set listening={false}.
            const missionLineBlockStart = battleMapSource.indexOf('missionLine &&');
            expect(missionLineBlockStart).toBeGreaterThan(0);
            // Find the closest listening={false} occurrence after the missionLine block.
            const afterBlock = battleMapSource.slice(missionLineBlockStart);
            const unitsMapIdx = afterBlock.indexOf('units.map');
            const listeningFalseIdx = afterBlock.indexOf('listening={false}');
            expect(listeningFalseIdx).toBeGreaterThan(-1);
            expect(listeningFalseIdx).toBeLessThan(unitsMapIdx);
        });
    });
});
