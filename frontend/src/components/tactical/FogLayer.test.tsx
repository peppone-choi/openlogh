// Phase 14 Plan 14-11 — FogLayer + EnemyGhostIcon tests.
// FE-05 / D-17 / D-18 / D-20 acceptance criteria.
//
// Strategy (mirrors 14-09 CommandRangeCircle.test.tsx pattern):
// The vitest config uses `environment: 'node'` and the react-konva Stage/Layer
// mount path fails without jsdom + Canvas polyfills. We therefore DO NOT mount
// <FogLayer /> / <EnemyGhostIcon /> directly. Instead we test:
//   1. The pure `ghostOpacity` / `computeVisibleEnemies` / `updateLastSeenEnemyPositions`
//      helpers (already covered end-to-end in tacticalStore.fog.test.ts —
//      reused here via `it` references to prove FogLayer's visual decisions
//      are driven by these same helpers).
//   2. Source-text regression guards on EnemyGhostIcon.tsx (dashed stroke,
//      Korean tick stamp, no fill, flagship-shape rule).
//   3. Source-text regression guards on FogLayer.tsx (uses store hooks,
//      imports from fogOfWar, skips visible enemies).
//   4. Compile-time `EnemyGhostIconProps` / `FogLayerProps` contract.
//   5. BattleMap.tsx wiring — the file mounts `<FogLayer ... />` inside the
//      `id="fog-ghosts"` Layer per UI-SPEC Section E layer ordering.

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
    ghostOpacity,
    computeVisibleEnemies,
    updateLastSeenEnemyPositions,
    GHOST_OPACITY_MAX,
    GHOST_OPACITY_MIN,
    GHOST_OPACITY_RAMP_START,
    GHOST_TTL_TICKS,
    type GhostEntry,
} from '@/lib/fogOfWar';
import { makeFixtureUnit } from '@/test/fixtures/tacticalBattleFixture';
import type { CommandHierarchyDto } from '@/types/tactical';
import type { EnemyGhostIconProps } from './EnemyGhostIcon';
import type { FogLayerProps } from './FogLayer';

const emptyHierarchy = (fleetCommander: number): CommandHierarchyDto => ({
    fleetCommander,
    subFleets: [],
    successionQueue: [fleetCommander],
    vacancyStartTick: -1,
    commJammed: false,
    jammingTicksRemaining: 0,
});

describe('FogLayer (FE-05, D-17, D-18, D-20)', () => {
    describe('ghostOpacity visual contract (D-20 opacity ramp)', () => {
        it('exports GHOST_OPACITY_MAX = 0.4 for fresh ghosts', () => {
            expect(GHOST_OPACITY_MAX).toBe(0.4);
        });

        it('exports GHOST_OPACITY_MIN = 0.15 for ghosts at TTL boundary', () => {
            expect(GHOST_OPACITY_MIN).toBe(0.15);
        });

        it('ghostOpacity(10, 10) === 0.4 — fresh ghost at max opacity', () => {
            expect(ghostOpacity(10, 10)).toBe(GHOST_OPACITY_MAX);
        });

        it('ghostOpacity at ramp start still returns MAX', () => {
            expect(ghostOpacity(GHOST_OPACITY_RAMP_START, 0)).toBe(
                GHOST_OPACITY_MAX,
            );
        });

        it('ghostOpacity ramps to MIN by age GHOST_TTL_TICKS', () => {
            expect(ghostOpacity(GHOST_TTL_TICKS, 0)).toBeCloseTo(
                GHOST_OPACITY_MIN,
            );
        });

        it('ghostOpacity at mid-ramp is exactly halfway between max and min', () => {
            const mid = (GHOST_OPACITY_RAMP_START + GHOST_TTL_TICKS) / 2;
            expect(ghostOpacity(mid, 0)).toBeCloseTo(
                (GHOST_OPACITY_MAX + GHOST_OPACITY_MIN) / 2,
                5,
            );
        });
    });

    describe('FogLayer skips currently-visible enemies (integration via pure helpers)', () => {
        it('computeVisibleEnemies membership drives the skip logic', () => {
            // This mirrors the exact predicate FogLayer uses at render time:
            // `if (visibleIds.has(id)) return null`.
            const ally = makeFixtureUnit({
                fleetId: 1,
                officerId: 1000,
                side: 'ATTACKER',
                posX: 0,
                posY: 0,
                sensorRange: 300,
            });
            const enemy = makeFixtureUnit({
                fleetId: 2,
                officerId: 2000,
                side: 'DEFENDER',
                posX: 100,
                posY: 100,
            });
            const visible = computeVisibleEnemies(
                [ally, enemy],
                'ATTACKER',
                1000,
                emptyHierarchy(1000),
            );
            expect(visible.has(2)).toBe(true);
            // FogLayer's Object.entries(lastSeen).map body would return null
            // for this fleetId because visibleIds.has(2) === true.
        });

        it('stale enemies outside visibility are rendered (ghost path exercised)', () => {
            const ally = makeFixtureUnit({
                fleetId: 1,
                officerId: 1000,
                side: 'ATTACKER',
                posX: 0,
                posY: 0,
                sensorRange: 50,
            });
            const enemy = makeFixtureUnit({
                fleetId: 2,
                officerId: 2000,
                side: 'DEFENDER',
                posX: 9000,
                posY: 9000,
            });
            const visible = computeVisibleEnemies(
                [ally, enemy],
                'ATTACKER',
                1000,
                emptyHierarchy(1000),
            );
            expect(visible.has(2)).toBe(false);
            // FogLayer would therefore render the stale ghost for fleetId 2.
            const prev: Record<number, GhostEntry> = {
                2: {
                    x: 500,
                    y: 500,
                    tick: 10,
                    ships: 280,
                    unitType: 'cruiser',
                    side: 'DEFENDER',
                },
            };
            const next = updateLastSeenEnemyPositions(
                prev,
                [ally, enemy],
                visible,
                15,
                'ATTACKER',
            );
            expect(next[2]).toEqual(prev[2]); // still in the map, will render as ghost
        });
    });
});

describe('EnemyGhostIcon visual contract (D-17, D-20)', () => {
    const sourcePath = join(__dirname, 'EnemyGhostIcon.tsx');
    const src = readFileSync(sourcePath, 'utf8');

    it('uses dashed stroke (dash=[3, 3]) per UI-SPEC Section E', () => {
        expect(src).toMatch(/dash=\{\[3,\s*3\]\}/);
    });

    it('renders Korean tick stamp "틱 전" per copywriting contract', () => {
        expect(src).toContain('틱 전');
    });

    it('appends " · 정보 노후" for stale ghosts (age > 30 ticks)', () => {
        expect(src).toContain('정보 노후');
    });

    it('disables fill so only the outline shows (no solid shape)', () => {
        expect(src).toMatch(/fillEnabled=\{false\}/);
    });

    it('chooses RegularPolygon (triangle) for flagship/battleship unitType', () => {
        // The shape rule: isFlagshipShape → RegularPolygon sides={3}, else Rect.
        expect(src).toMatch(/isFlagshipShape[\s\S]*RegularPolygon[\s\S]*sides=\{3\}/);
        expect(src).toMatch(/unitType === 'flagship'/);
        expect(src).toMatch(/unitType === 'battleship'/);
    });

    it('uses neutral gray stroke #888888 regardless of side (D-17)', () => {
        expect(src).toContain('#888888');
        // And does NOT use the faction-tinted colors reserved for live units.
        expect(src).not.toContain('#4466ff');
        expect(src).not.toContain('#ff4444');
    });

    it('marks the group non-listening so ghosts never steal clicks', () => {
        expect(src).toMatch(/listening=\{false\}/);
    });

    it('exposes EnemyGhostIconProps with the contract Phase 14 expects', () => {
        // Compile-time check — if any required prop is missing the
        // following object literal will fail tsc.
        const p: EnemyGhostIconProps = {
            fleetId: 1,
            entry: {
                x: 0,
                y: 0,
                tick: 0,
                ships: 100,
                unitType: 'cruiser',
                side: 'ATTACKER',
            },
            cx: 0,
            cy: 0,
            opacity: 0.4,
            ticksAgo: 0,
            isStale: false,
        };
        expect(p.fleetId).toBe(1);
        expect(p.opacity).toBe(0.4);
    });
});

describe('FogLayer component wiring (D-17, D-18, D-20)', () => {
    const sourcePath = join(__dirname, 'FogLayer.tsx');
    const src = readFileSync(sourcePath, 'utf8');

    it('subscribes to tacticalStore.currentBattle and lastSeenEnemyPositions', () => {
        expect(src).toContain('useTacticalStore');
        expect(src).toContain('currentBattle');
        expect(src).toContain('lastSeenEnemyPositions');
    });

    it('imports computeVisibleEnemies + ghostOpacity from fogOfWar (not inlined)', () => {
        expect(src).toMatch(
            /from\s+['"]@\/lib\/fogOfWar['"]/,
        );
        expect(src).toContain('computeVisibleEnemies');
        expect(src).toContain('ghostOpacity');
    });

    it('skips entries whose fleetId is currently visible (double-render guard)', () => {
        expect(src).toMatch(/visibleIds\.has\(id\)/);
    });

    it('selects the hierarchy matching my side (D-18)', () => {
        expect(src).toContain('attackerHierarchy');
        expect(src).toContain('defenderHierarchy');
        expect(src).toMatch(/mySide === 'ATTACKER'/);
    });

    it('renders null when there is no currentBattle (defensive guard)', () => {
        expect(src).toMatch(/if \(!currentBattle\) return null/);
    });

    it('renders EnemyGhostIcon for each non-visible stale entry', () => {
        expect(src).toContain('EnemyGhostIcon');
        expect(src).toMatch(/Object\.entries\(lastSeen\)/);
    });

    it('exposes FogLayerProps requiring myOfficerId + scaleX + scaleY', () => {
        // Compile-time contract check — failure here catches future prop
        // renames that would silently break BattleMap wiring.
        const p: FogLayerProps = {
            myOfficerId: 1000,
            scaleX: 1,
            scaleY: 1,
        };
        expect(p.myOfficerId).toBe(1000);
        expect(p.scaleX).toBe(1);
        expect(p.scaleY).toBe(1);
    });
});

describe('BattleMap fog-ghosts Layer wiring (UI-SPEC Section E layer ordering)', () => {
    const sourcePath = join(__dirname, 'BattleMap.tsx');
    const src = readFileSync(sourcePath, 'utf8');

    it('imports FogLayer', () => {
        expect(src).toMatch(/import\s*\{\s*FogLayer\s*\}\s*from\s*['"]\.\/FogLayer['"]/);
    });

    it('mounts <FogLayer /> inside the id="fog-ghosts" Layer slot', () => {
        // The slot must exist AND contain the FogLayer child. Regex spans
        // across whitespace so the body of the Layer can be multi-line.
        const layerMatch = src.match(
            /<Layer[^>]*id="fog-ghosts"[^>]*>[\s\S]*?<FogLayer[\s\S]*?\/>[\s\S]*?<\/Layer>/,
        );
        expect(layerMatch).toBeTruthy();
    });

    it('passes myOfficerId + scaleX + scaleY props into FogLayer', () => {
        expect(src).toMatch(/<FogLayer[\s\S]*myOfficerId=\{[\s\S]*?\}/);
        expect(src).toMatch(/<FogLayer[\s\S]*scaleX=\{scaleX\}/);
        expect(src).toMatch(/<FogLayer[\s\S]*scaleY=\{scaleY\}/);
    });

    it('places fog-ghosts Layer between background and command-range (UI-SPEC E)', () => {
        const bgIdx = src.indexOf('id="background"');
        const fogIdx = src.indexOf('id="fog-ghosts"');
        const crcIdx = src.indexOf('id="command-range"');
        expect(bgIdx).toBeGreaterThanOrEqual(0);
        expect(fogIdx).toBeGreaterThan(bgIdx);
        expect(crcIdx).toBeGreaterThan(fogIdx);
    });
});
