// Phase 14 Plan 14-09 — Real tests replacing Wave 0 scaffold stubs.
// FE-01 / D-01..D-04 / D-11 acceptance criteria.
//
// Strategy (per 14-09-PLAN.md executor hint):
// The vitest config uses `environment: 'node'` and the react-konva Stage/Layer mount path
// fails without jsdom + Canvas polyfills. We therefore DO NOT mount <CommandRangeCircle />.
// Instead we test:
//   1. The pure `computeRingStyle(side, state)` helper exported alongside the component
//      — all visual decisions (color, strokeWidth, opacity, shadow) are driven by this helper.
//   2. The FACTION_TACTICAL_COLORS constant shape.
//   3. The source-text D-03 regression guard (no Konva.Animation).
//   4. The component's props interface (import CommandRangeCircleProps and typecheck).
//   5. The defensive-clamp early-return semantics via computeRingStyle returning `visible: false`.

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
    FACTION_TACTICAL_COLORS,
    sideToDefaultColor,
    lightenHex,
} from '@/lib/tacticalColors';
import {
    computeRingStyle,
    type CommandRangeCircleProps,
} from './CommandRangeCircle';

describe('CommandRangeCircle (FE-01, D-01..D-04, D-11)', () => {
    describe('FACTION_TACTICAL_COLORS (D-02 single source of truth)', () => {
        it('exports the three D-02 hex literals verbatim', () => {
            expect(FACTION_TACTICAL_COLORS.empire).toBe('#4466ff');
            expect(FACTION_TACTICAL_COLORS.alliance).toBe('#ff4444');
            expect(FACTION_TACTICAL_COLORS.fezzan).toBe('#888888');
        });

        it('sideToDefaultColor maps ATTACKER → empire and DEFENDER → alliance', () => {
            expect(sideToDefaultColor('ATTACKER')).toBe('#4466ff');
            expect(sideToDefaultColor('DEFENDER')).toBe('#ff4444');
        });

        it('lightenHex derives a lighter variant without introducing new base literals', () => {
            const empireLight = lightenHex('#4466ff', 15);
            // Lighter variant must still be a 7-char hex (#RRGGBB), not one of the base colors.
            expect(empireLight).toMatch(/^#[0-9a-f]{6}$/i);
            expect(empireLight).not.toBe('#4466ff');
            // Lightening must not invert the hue — R should stay lower than B for the cobalt base.
            const r = parseInt(empireLight.slice(1, 3), 16);
            const b = parseInt(empireLight.slice(5, 7), 16);
            expect(b).toBeGreaterThan(r);
        });

        it('lightenHex is idempotent on pure white (cannot lighten further)', () => {
            // Edge case: max lightness — no overflow, returns white.
            const white = lightenHex('#ffffff', 15);
            expect(white).toBe('#ffffff');
        });
    });

    describe('D-03 regression guard — no Konva.Animation in source', () => {
        const sourcePath = join(__dirname, 'CommandRangeCircle.tsx');
        const source = readFileSync(sourcePath, 'utf8');

        it('does NOT contain "Konva.Animation"', () => {
            expect(source).not.toMatch(/Konva\.Animation/);
        });

        it('does NOT contain "new Animation("', () => {
            expect(source).not.toMatch(/new Animation\(/);
        });

        it('does NOT import Konva directly (no raw namespace import required after rewrite)', () => {
            // react-konva components are fine; bare `import Konva from 'konva'` was
            // only needed for the Animation instance which is now gone.
            expect(source).not.toMatch(/^\s*import\s+Konva\s+from\s+['"]konva['"]/m);
        });
    });

    describe('computeRingStyle — hierarchy-driven pure helper (FE-01)', () => {
        it('returns empire stroke for ATTACKER default state', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            expect(style.visible).toBe(true);
            expect(style.inner.stroke).toBe('#4466ff');
            expect(style.outer.stroke).toBe('#4466ff');
        });

        it('returns alliance stroke for DEFENDER default state', () => {
            const style = computeRingStyle('DEFENDER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            expect(style.inner.stroke).toBe('#ff4444');
            expect(style.outer.stroke).toBe('#ff4444');
        });

        it('applies UI-SPEC A base-state stroke widths + opacities', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            // UI-SPEC A.: inner default strokeWidth 1.5 opacity 0.5
            expect(style.inner.strokeWidth).toBe(1.5);
            expect(style.inner.opacity).toBe(0.5);
            // UI-SPEC A.: outer dashed strokeWidth 0.5 opacity 0.25, dash [4, 4]
            expect(style.outer.strokeWidth).toBe(0.5);
            expect(style.outer.opacity).toBe(0.25);
            expect(style.outer.dash).toEqual([4, 4]);
        });

        it('applies UI-SPEC A hover-state overrides', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: true,
                isSelected: false,
                isMine: false,
            });
            // UI-SPEC A.: hover → inner opacity 0.8, strokeWidth 2.0
            expect(style.inner.strokeWidth).toBe(2.0);
            expect(style.inner.opacity).toBe(0.8);
            // Hover lightens the stroke via HSL L+15 — must differ from base color
            expect(style.inner.stroke).not.toBe('#4466ff');
            expect(style.inner.stroke).toMatch(/^#[0-9a-f]{6}$/i);
        });

        it('applies UI-SPEC A selected-state overrides (2.5 stroke, 0.9 opacity, 6 shadowBlur)', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: false,
                isSelected: true,
                isMine: false,
            });
            expect(style.inner.strokeWidth).toBe(2.5);
            expect(style.inner.opacity).toBe(0.9);
            expect(style.inner.shadowBlur).toBe(6);
            // shadowColor tracks the (lightened) active color
            expect(style.inner.shadowColor).toBe(style.inner.stroke);
            // Lightened (selected lightens same as hover)
            expect(style.inner.stroke).not.toBe('#4466ff');
        });

        it('D-11 layer a: isMine=true emits a third gold hint ring descriptor', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: false,
                isSelected: false,
                isMine: true,
            });
            expect(style.goldHint).not.toBeNull();
            // D-11 gold color (amber 500 from tailwind / lucide conventions)
            expect(style.goldHint?.stroke).toBe('#f59e0b');
            // Slightly larger than currentRadius so it visually wraps the inner ring
            expect(style.goldHint?.radius).toBeGreaterThan(100);
        });

        it('D-11 layer a: isMine=false omits the gold hint ring', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 100,
                maxRadius: 150,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            expect(style.goldHint).toBeNull();
        });

        it('defensive clamp — currentRadius 0 sets visible=false (command-reset snapshot)', () => {
            // D-03: server may briefly emit commandRange=0 on command issue before interpolation resumes
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 0,
                maxRadius: 150,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            expect(style.visible).toBe(false);
        });

        it('defensive clamp — maxRadius 0 sets visible=false', () => {
            const style = computeRingStyle('ATTACKER', {
                currentRadius: 50,
                maxRadius: 0,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            expect(style.visible).toBe(false);
        });

        it('radii are exposed so BattleMap can position the rings', () => {
            const style = computeRingStyle('DEFENDER', {
                currentRadius: 42,
                maxRadius: 100,
                isHovered: false,
                isSelected: false,
                isMine: false,
            });
            expect(style.inner.radius).toBe(42);
            expect(style.outer.radius).toBe(100);
        });
    });

    describe('CommandRangeCircleProps — hierarchy-aware contract (D-04, D-11)', () => {
        it('accepts the full hierarchy-driven prop shape at compile time', () => {
            // This is a compile-time assertion: if the type loses any field,
            // this literal will stop typechecking and `pnpm typecheck` fails.
            const props: CommandRangeCircleProps = {
                cx: 500,
                cy: 500,
                currentRadius: 100,
                maxRadius: 150,
                side: 'ATTACKER',
                isMine: true,
                isCommandable: true,
                isHovered: false,
                isSelected: false,
            };
            expect(props.cx).toBe(500);
            expect(props.side).toBe('ATTACKER');
        });

        it('optional props (isMine, isCommandable, isHovered, isSelected) can be omitted', () => {
            const props: CommandRangeCircleProps = {
                cx: 0,
                cy: 0,
                currentRadius: 10,
                maxRadius: 20,
                side: 'DEFENDER',
            };
            expect(props.isMine).toBeUndefined();
            expect(props.isCommandable).toBeUndefined();
        });
    });
});
