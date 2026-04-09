// Phase 14 — Plan 14-15 — FlagshipFlash + SuccessionCountdownOverlay tests.
// FE-04 / D-13 / D-14 / UI-SPEC Section D acceptance criteria.
//
// Strategy (mirrors 14-09 CommandRangeCircle.test.tsx + 14-11 FogLayer.test.tsx):
// The vitest config uses `environment: 'node'` so we cannot mount react-konva
// components directly. Instead we test:
//   1. The pure `computeFlashFrame(progress)` helper exported alongside
//      FlagshipFlash — all visual decisions (radius, opacity) are driven by it.
//   2. The pure `clampSuccessionTicks(n)` helper exported alongside
//      SuccessionCountdownOverlay — all display clamping is driven by it.
//   3. Source-text regression guards on FlagshipFlash.tsx (Konva Group/Circle,
//      no full-screen flash, requestAnimationFrame loop).
//   4. Source-text regression guards on SuccessionCountdownOverlay.tsx
//      (Korean copy, gold border #f59e0b, absolute positioning).
//   5. Source-text regression guards on BattleMap.tsx — confirms the two
//      components are mounted in the right places (FlagshipFlash inside the
//      `succession-fx` Konva Layer, SuccessionCountdownOverlay as an HTML
//      sibling of the Stage).
//   6. Compile-time `FlagshipFlashProps` and
//      `SuccessionCountdownOverlayProps` contract assertions.

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
    computeFlashFrame,
    type FlagshipFlashProps,
} from './FlagshipFlash';
import {
    clampSuccessionTicks,
    type SuccessionCountdownOverlayProps,
} from './SuccessionCountdownOverlay';

const flagshipFlashSource = readFileSync(
    join(__dirname, 'FlagshipFlash.tsx'),
    'utf-8',
);
const successionCountdownSource = readFileSync(
    join(__dirname, 'SuccessionCountdownOverlay.tsx'),
    'utf-8',
);
const battleMapSource = readFileSync(
    join(__dirname, 'BattleMap.tsx'),
    'utf-8',
);

describe('FlagshipFlash FX (FE-04, D-14)', () => {
    describe('computeFlashFrame pure helper', () => {
        it('returns the initial frame at progress=0 (radius=24, opacity=0.9)', () => {
            const frame = computeFlashFrame(0);
            expect(frame.radius).toBe(24);
            expect(frame.opacity).toBeCloseTo(0.9, 5);
        });

        it('returns the terminal frame at progress=1 (radius=96, opacity=0)', () => {
            const frame = computeFlashFrame(1);
            expect(frame.radius).toBe(96);
            expect(frame.opacity).toBeCloseTo(0, 5);
        });

        it('halfway through the animation the radius is 60 (24 + 36) and opacity is 0.45', () => {
            const frame = computeFlashFrame(0.5);
            expect(frame.radius).toBeCloseTo(60, 5);
            expect(frame.opacity).toBeCloseTo(0.45, 5);
        });

        it('clamps negative progress to the initial frame', () => {
            const frame = computeFlashFrame(-0.5);
            expect(frame.radius).toBe(24);
            expect(frame.opacity).toBeCloseTo(0.9, 5);
        });

        it('clamps progress > 1 to the terminal frame', () => {
            const frame = computeFlashFrame(2);
            expect(frame.radius).toBe(96);
            expect(frame.opacity).toBeCloseTo(0, 5);
        });

        it('radius grows monotonically from 24 to 96 across the animation', () => {
            const steps = [0, 0.25, 0.5, 0.75, 1].map(computeFlashFrame);
            for (let i = 1; i < steps.length; i++) {
                expect(steps[i].radius).toBeGreaterThanOrEqual(steps[i - 1].radius);
            }
            expect(steps[0].radius).toBe(24);
            expect(steps[steps.length - 1].radius).toBe(96);
        });

        it('opacity decreases monotonically from 0.9 to 0 across the animation', () => {
            const steps = [0, 0.25, 0.5, 0.75, 1].map(computeFlashFrame);
            for (let i = 1; i < steps.length; i++) {
                expect(steps[i].opacity).toBeLessThanOrEqual(steps[i - 1].opacity);
            }
        });
    });

    describe('FlagshipFlash source-text regression guards', () => {
        it('imports Group and Circle from react-konva (Konva-native, no framer-motion)', () => {
            expect(flagshipFlashSource).toContain("from 'react-konva'");
            expect(flagshipFlashSource).toMatch(/\bGroup\b/);
            expect(flagshipFlashSource).toMatch(/\bCircle\b/);
            // D-14: no framer-motion — pure Konva + RAF.
            expect(flagshipFlashSource).not.toMatch(/framer-motion/);
        });

        it('uses requestAnimationFrame for the 0.5s animation loop (D-14)', () => {
            expect(flagshipFlashSource).toContain('requestAnimationFrame');
            expect(flagshipFlashSource).toContain('cancelAnimationFrame');
            // 500ms default — UI-SPEC Section D Phase 1a.
            expect(flagshipFlashSource).toMatch(/durationMs\s*=\s*500/);
        });

        it('Group has listening={false} so the FX never steals click events', () => {
            expect(flagshipFlashSource).toMatch(/listening=\{false\}/);
        });

        it('the FX is scoped to (cx, cy) — NO full-screen Rect overlay (D-14)', () => {
            // D-14 regression guard: the plan explicitly forbids a
            // viewport-filling Rect. Only Circle primitives allowed.
            expect(flagshipFlashSource).not.toMatch(/<Rect\b/);
            // And the Group is positioned at (x={cx} y={cy}), not (0, 0).
            expect(flagshipFlashSource).toMatch(/x=\{cx\}\s+y=\{cy\}/);
        });

        it('exports FlagshipFlashProps type for the caller', () => {
            // Compile-time check — ensures the prop contract is stable.
            const probe: FlagshipFlashProps = { cx: 0, cy: 0 };
            expect(probe.cx).toBe(0);
            expect(probe.cy).toBe(0);
        });
    });
});

describe('SuccessionCountdownOverlay (FE-04, D-13)', () => {
    describe('clampSuccessionTicks pure helper', () => {
        it('returns the input value inside the [0, 30] range', () => {
            expect(clampSuccessionTicks(15)).toBe(15);
            expect(clampSuccessionTicks(0)).toBe(0);
            expect(clampSuccessionTicks(30)).toBe(30);
        });

        it('clamps values above 30 down to 30', () => {
            expect(clampSuccessionTicks(31)).toBe(30);
            expect(clampSuccessionTicks(100)).toBe(30);
        });

        it('clamps negative values up to 0', () => {
            expect(clampSuccessionTicks(-1)).toBe(0);
            expect(clampSuccessionTicks(-100)).toBe(0);
        });

        it('floors fractional values', () => {
            expect(clampSuccessionTicks(12.7)).toBe(12);
            expect(clampSuccessionTicks(0.9)).toBe(0);
        });

        it('returns 0 for non-finite inputs (NaN, Infinity)', () => {
            expect(clampSuccessionTicks(NaN)).toBe(0);
            expect(clampSuccessionTicks(Infinity)).toBe(0);
            expect(clampSuccessionTicks(-Infinity)).toBe(0);
        });
    });

    describe('SuccessionCountdownOverlay source-text regression guards', () => {
        it('contains the Korean UI copy from UI-SPEC Section D Phase 2', () => {
            // D-13: "지휘 승계 중 — {ticksRemaining}틱"
            expect(successionCountdownSource).toContain('지휘 승계 중');
            expect(successionCountdownSource).toMatch(/\{display\}틱/);
        });

        it('uses the --game-gold hex literal #f59e0b for the border (UI-SPEC A)', () => {
            expect(successionCountdownSource).toContain('#f59e0b');
        });

        it('is an HTML div (NOT a Konva primitive)', () => {
            // Must be pure DOM so the monospaced pill inherits native font.
            expect(successionCountdownSource).not.toMatch(/from 'react-konva'/);
            expect(successionCountdownSource).toMatch(/<div/);
        });

        it('absolute-positions itself above the unit using screenX/screenY props', () => {
            expect(successionCountdownSource).toMatch(/position:\s*'absolute'/);
            expect(successionCountdownSource).toMatch(/left:\s*screenX/);
            expect(successionCountdownSource).toMatch(/top:\s*screenY/);
        });

        it('is pointer-events:none so the pill does not block unit clicks', () => {
            expect(successionCountdownSource).toContain("pointerEvents: 'none'");
        });

        it('exports SuccessionCountdownOverlayProps with the three required fields', () => {
            const probe: SuccessionCountdownOverlayProps = {
                screenX: 100,
                screenY: 200,
                ticksRemaining: 15,
            };
            expect(probe.screenX).toBe(100);
            expect(probe.screenY).toBe(200);
            expect(probe.ticksRemaining).toBe(15);
        });
    });
});

describe('BattleMap succession-fx wiring (14-15 handoff contract)', () => {
    it('imports FlagshipFlash from ./FlagshipFlash', () => {
        expect(battleMapSource).toMatch(
            /import\s*\{\s*FlagshipFlash\s*\}\s*from\s*'\.\/FlagshipFlash'/,
        );
    });

    it('imports SuccessionCountdownOverlay from ./SuccessionCountdownOverlay', () => {
        expect(battleMapSource).toMatch(
            /import\s*\{\s*SuccessionCountdownOverlay\s*\}\s*from\s*'\.\/SuccessionCountdownOverlay'/,
        );
    });

    it('mounts <FlagshipFlash /> inside the succession-fx Layer', () => {
        // The Layer id="succession-fx" block must contain the FlagshipFlash mount.
        const layerIdx = battleMapSource.indexOf('id="succession-fx"');
        expect(layerIdx).toBeGreaterThan(0);
        // Search for FlagshipFlash usage after the layer declaration.
        const afterLayer = battleMapSource.slice(layerIdx);
        expect(afterLayer).toMatch(/<FlagshipFlash\b/);
    });

    it('reads tacticalStore.activeFlagshipDestroyedFleetIds to drive the flash layer', () => {
        expect(battleMapSource).toContain('activeFlagshipDestroyedFleetIds');
    });

    it('renders SuccessionCountdownOverlay as an HTML sibling outside the Stage', () => {
        // The overlay must be rendered AFTER the </Stage> closing tag so it
        // lives in the DOM, not the canvas.
        const stageCloseIdx = battleMapSource.indexOf('</Stage>');
        expect(stageCloseIdx).toBeGreaterThan(0);
        const afterStage = battleMapSource.slice(stageCloseIdx);
        expect(afterStage).toMatch(/<SuccessionCountdownOverlay\b/);
    });

    it('filters units by successionState === "PENDING_SUCCESSION" for the overlay render', () => {
        expect(battleMapSource).toContain("successionState === 'PENDING_SUCCESSION'");
    });

    it('Layer id="succession-fx" exists with listening=false (no click-steal)', () => {
        // Regression guard that 14-10's layer ordering is still intact.
        const successionLayer = battleMapSource.match(
            /<Layer[^>]*id="succession-fx"[^>]*>/,
        );
        expect(successionLayer).not.toBeNull();
        expect(successionLayer![0]).toContain('listening={false}');
    });
});
