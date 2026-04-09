// FE-05 fog-of-war reducer tests (Phase 14 Plan 14-11).
// Covers D-17 (last-seen storage), D-18 (hierarchy-shared vision),
// D-19 (sensorRange-driven visibility), D-20 (TTL + opacity ramp).

import { describe, it, expect } from 'vitest';
import {
    computeVisibleEnemies,
    updateLastSeenEnemyPositions,
    ghostOpacity,
    GHOST_TTL_TICKS,
    GHOST_OPACITY_MAX,
    GHOST_OPACITY_MIN,
    GHOST_OPACITY_RAMP_START,
    type GhostEntry,
} from '@/lib/fogOfWar';
import { makeFixtureUnit } from '@/test/fixtures/tacticalBattleFixture';
import type { CommandHierarchyDto } from '@/types/tactical';

const emptyHierarchy = (fleetCommander: number): CommandHierarchyDto => ({
    fleetCommander,
    subFleets: [],
    successionQueue: [fleetCommander],
    vacancyStartTick: -1,
    commJammed: false,
    jammingTicksRemaining: 0,
});

describe('fog-of-war reducer (FE-05, D-17..D-20)', () => {
    it('enemy inside ally sensor range is visible (D-19)', () => {
        const ally = makeFixtureUnit({
            fleetId: 1,
            officerId: 1000,
            side: 'ATTACKER',
            posX: 0,
            posY: 0,
            sensorRange: 150,
        });
        const enemy = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'DEFENDER',
            posX: 50,
            posY: 50,
        });
        const visible = computeVisibleEnemies(
            [ally, enemy],
            'ATTACKER',
            1000,
            emptyHierarchy(1000)
        );
        expect(visible.has(2)).toBe(true);
    });

    it('enemy outside all ally sensor ranges is not visible', () => {
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
            posX: 500,
            posY: 500,
        });
        const visible = computeVisibleEnemies(
            [ally, enemy],
            'ATTACKER',
            1000,
            emptyHierarchy(1000)
        );
        expect(visible.has(2)).toBe(false);
    });

    it('ally with no sensorRange does not see anyone (D-19 strict)', () => {
        const ally = makeFixtureUnit({
            fleetId: 1,
            officerId: 1000,
            side: 'ATTACKER',
            posX: 0,
            posY: 0,
            // sensorRange undefined
        });
        const enemy = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'DEFENDER',
            posX: 10,
            posY: 10,
        });
        const visible = computeVisibleEnemies(
            [ally, enemy],
            'ATTACKER',
            1000,
            emptyHierarchy(1000)
        );
        expect(visible.has(2)).toBe(false);
    });

    it('D-18 hierarchy-shared vision — my sub-fleet member sees an enemy → I see it', () => {
        const meAsCommander = makeFixtureUnit({
            fleetId: 1,
            officerId: 1000,
            side: 'ATTACKER',
            posX: 0,
            posY: 0,
            sensorRange: 50,
        });
        const subMember = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'ATTACKER',
            posX: 400,
            posY: 400,
            sensorRange: 100,
        });
        const enemy = makeFixtureUnit({
            fleetId: 3,
            officerId: 3000,
            side: 'DEFENDER',
            posX: 420,
            posY: 420,
        });
        const hierarchy: CommandHierarchyDto = {
            fleetCommander: 1000,
            subFleets: [
                {
                    commanderOfficerId: 2000,
                    commanderName: 'Yang',
                    memberFleetIds: [2],
                    commanderRank: 8,
                },
            ],
            successionQueue: [1000, 2000],
            vacancyStartTick: -1,
            commJammed: false,
            jammingTicksRemaining: 0,
        };
        const visible = computeVisibleEnemies(
            [meAsCommander, subMember, enemy],
            'ATTACKER',
            1000,
            hierarchy
        );
        expect(visible.has(3)).toBe(true);
    });

    it('D-18: ally NOT in my chain cannot grant me vision', () => {
        // I am a sub-fleet commander of subA. subB has its own commander who
        // sees the enemy. I must NOT inherit that sighting.
        const me = makeFixtureUnit({
            fleetId: 1,
            officerId: 1000,
            side: 'ATTACKER',
            posX: 0,
            posY: 0,
            sensorRange: 10,
        });
        const otherSubCommander = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'ATTACKER',
            posX: 500,
            posY: 500,
            sensorRange: 200,
        });
        const enemy = makeFixtureUnit({
            fleetId: 3,
            officerId: 3000,
            side: 'DEFENDER',
            posX: 510,
            posY: 510,
        });
        const hierarchy: CommandHierarchyDto = {
            fleetCommander: 9999, // neither of us
            subFleets: [
                {
                    commanderOfficerId: 1000,
                    commanderName: 'Me',
                    memberFleetIds: [1],
                    commanderRank: 7,
                },
                {
                    commanderOfficerId: 2000,
                    commanderName: 'Them',
                    memberFleetIds: [2],
                    commanderRank: 7,
                },
            ],
            successionQueue: [9999, 1000, 2000],
            vacancyStartTick: -1,
            commJammed: false,
            jammingTicksRemaining: 0,
        };
        const visible = computeVisibleEnemies(
            [me, otherSubCommander, enemy],
            'ATTACKER',
            1000,
            hierarchy
        );
        expect(visible.has(3)).toBe(false);
    });

    it('updateLastSeenEnemyPositions preserves stale entries for not-visible enemies (D-17)', () => {
        const ally = makeFixtureUnit({
            fleetId: 1,
            officerId: 1000,
            side: 'ATTACKER',
            sensorRange: 50,
        });
        const enemy = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'DEFENDER',
            posX: 9999,
            posY: 9999,
        });
        const prev: Record<number, GhostEntry> = {
            2: {
                x: 100,
                y: 100,
                tick: 5,
                ships: 300,
                unitType: 'cruiser',
                side: 'DEFENDER',
            },
        };
        const visible = new Set<number>();
        const next = updateLastSeenEnemyPositions(
            prev,
            [ally, enemy],
            visible,
            10,
            'ATTACKER'
        );
        expect(next[2]).toEqual(prev[2]); // stale preserved
    });

    it('updateLastSeenEnemyPositions upserts visible enemies with current tick', () => {
        const enemy = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'DEFENDER',
            posX: 250,
            posY: 350,
            ships: 280,
        });
        const prev: Record<number, GhostEntry> = {
            2: {
                x: 100,
                y: 100,
                tick: 3,
                ships: 300,
                unitType: 'cruiser',
                side: 'DEFENDER',
            },
        };
        const visible = new Set<number>([2]);
        const next = updateLastSeenEnemyPositions(
            prev,
            [enemy],
            visible,
            17,
            'ATTACKER'
        );
        expect(next[2]).toEqual({
            x: 250,
            y: 350,
            tick: 17,
            ships: 280,
            unitType: 'cruiser',
            side: 'DEFENDER',
        });
    });

    it('removes ghosts when enemy dies', () => {
        const enemy = makeFixtureUnit({
            fleetId: 2,
            officerId: 2000,
            side: 'DEFENDER',
            isAlive: false,
        });
        const prev: Record<number, GhostEntry> = {
            2: {
                x: 100,
                y: 100,
                tick: 5,
                ships: 300,
                unitType: 'cruiser',
                side: 'DEFENDER',
            },
        };
        const next = updateLastSeenEnemyPositions(
            prev,
            [enemy],
            new Set(),
            10,
            'ATTACKER'
        );
        expect(next[2]).toBeUndefined();
    });

    it('prunes ghosts older than GHOST_TTL_TICKS (D-20)', () => {
        const prev: Record<number, GhostEntry> = {
            2: {
                x: 100,
                y: 100,
                tick: 5,
                ships: 300,
                unitType: 'cruiser',
                side: 'DEFENDER',
            },
        };
        const next = updateLastSeenEnemyPositions(
            prev,
            [],
            new Set(),
            5 + GHOST_TTL_TICKS + 1,
            'ATTACKER'
        );
        expect(next[2]).toBeUndefined();
    });

    it('ghostOpacity = GHOST_OPACITY_MAX (0.4) for fresh ghosts (age 0..30)', () => {
        expect(ghostOpacity(10, 10)).toBe(GHOST_OPACITY_MAX);
        expect(ghostOpacity(25, 0)).toBe(GHOST_OPACITY_MAX);
        expect(ghostOpacity(30, 0)).toBe(GHOST_OPACITY_MAX);
    });

    it('ghostOpacity ramps to GHOST_OPACITY_MIN (0.15) by tick age GHOST_TTL_TICKS', () => {
        expect(ghostOpacity(GHOST_TTL_TICKS, 0)).toBeCloseTo(GHOST_OPACITY_MIN);
    });

    it('ghostOpacity is monotonic decreasing across the ramp zone (30..60)', () => {
        const o30 = ghostOpacity(30, 0);
        const o45 = ghostOpacity(45, 0);
        const o60 = ghostOpacity(60, 0);
        expect(o30).toBeGreaterThan(o45);
        expect(o45).toBeGreaterThan(o60);
        expect(o30).toBe(GHOST_OPACITY_MAX);
        expect(o60).toBeCloseTo(GHOST_OPACITY_MIN);
        // Mid-ramp should be halfway between max and min (0.275)
        expect(o45).toBeCloseTo(
            (GHOST_OPACITY_MAX + GHOST_OPACITY_MIN) / 2,
            5
        );
    });

    it('exposes GHOST_OPACITY_RAMP_START as named export', () => {
        expect(GHOST_OPACITY_RAMP_START).toBe(30);
    });
});
