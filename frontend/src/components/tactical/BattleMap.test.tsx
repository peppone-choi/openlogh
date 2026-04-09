// Phase 14 — Plan 14-10 — BattleMap layer + multi-CRC tests (FE-01, D-01, D-04).
//
// BattleMap uses react-konva which requires a DOM. Vitest runs in a `node`
// environment by default, so the brittle bits (mounting the Stage + asserting
// Konva node IDs) are covered via the exported pure helper
// `computeBattleMapVisibleCommanders`. The layer-ordering and component-wiring
// assertions use a source-text regression guard — the BattleMap source must
// contain all five layer IDs in the correct order. This is the same approach
// 14-09 took for its CommandRangeCircle source-text regression guard.

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { computeBattleMapVisibleCommanders } from './BattleMap';
import { createFixtureBattle, makeFixtureUnit } from '@/test/fixtures/tacticalBattleFixture';
import type { CommandHierarchyDto } from '@/types/tactical';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const battleMapSource = readFileSync(join(__dirname, 'BattleMap.tsx'), 'utf-8');

function emptyHierarchy(
    fleetCommander: number,
    overrides: Partial<CommandHierarchyDto> = {},
): CommandHierarchyDto {
    return {
        fleetCommander,
        subFleets: [],
        successionQueue: [],
        vacancyStartTick: -1,
        commJammed: false,
        jammingTicksRemaining: 0,
        ...overrides,
    };
}

describe('BattleMap 5-layer ordering (UI-SPEC Section A/E, D-04)', () => {
    it('contains all 5 Konva layer ids in BattleMap.tsx source', () => {
        expect(battleMapSource).toContain('id="background"');
        expect(battleMapSource).toContain('id="fog-ghosts"');
        expect(battleMapSource).toContain('id="command-range"');
        expect(battleMapSource).toContain('id="units"');
        expect(battleMapSource).toContain('id="succession-fx"');
    });

    it('orders layers correctly: background → fog-ghosts → command-range → units → succession-fx', () => {
        const posBg = battleMapSource.indexOf('id="background"');
        const posFog = battleMapSource.indexOf('id="fog-ghosts"');
        const posCrc = battleMapSource.indexOf('id="command-range"');
        const posUnits = battleMapSource.indexOf('id="units"');
        const posFx = battleMapSource.indexOf('id="succession-fx"');
        expect(posBg).toBeGreaterThan(0);
        expect(posFog).toBeGreaterThan(posBg);
        expect(posCrc).toBeGreaterThan(posFog);
        expect(posUnits).toBeGreaterThan(posCrc);
        expect(posFx).toBeGreaterThan(posUnits);
    });

    it('does NOT gate the CRC layer on selectedUnit.commandRange (old single-CRC contract removed)', () => {
        // Forbidden pattern from the pre-14-10 implementation.
        expect(battleMapSource).not.toMatch(/selectedUnit && selectedUnit\.commandRange > 0/);
    });

    it('imports findVisibleCrcCommanders from commandChain as the SoT', () => {
        expect(battleMapSource).toContain('findVisibleCrcCommanders');
        expect(battleMapSource).toContain("from '@/lib/commandChain'");
    });

    it('CRC layer multi-renders via visibleCommanders.map (not a single selectedUnit render)', () => {
        expect(battleMapSource).toContain('visibleCommanders.map');
    });
});

describe('computeBattleMapVisibleCommanders (pure helper, D-01)', () => {
    it('returns empty list when myOfficerId is missing / not in battle', () => {
        const battle = createFixtureBattle();
        expect(
            computeBattleMapVisibleCommanders(
                -1,
                battle.units,
                battle.attackerHierarchy,
                battle.defenderHierarchy,
            ),
        ).toEqual([]);
    });

    it('returns empty list when officer is not a commander in either hierarchy', () => {
        const battle = createFixtureBattle({
            attackerUnits: [{ fleetId: 100, officerId: 1000 }],
            attackerHierarchy: emptyHierarchy(1000),
        });
        const result = computeBattleMapVisibleCommanders(
            9999,
            battle.units,
            battle.attackerHierarchy,
            battle.defenderHierarchy,
        );
        expect(result).toEqual([]);
    });

    it('fleet commander sees own CRC + N sub-commander CRCs (3 when N=2)', () => {
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000, officerName: 'Yang' },
                { fleetId: 101, officerId: 2000, officerName: 'Fischer' },
                { fleetId: 102, officerId: 3000, officerName: 'Bagdash' },
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Fischer',
                        memberFleetIds: [101],
                        commanderRank: 8,
                    },
                    {
                        commanderOfficerId: 3000,
                        commanderName: 'Bagdash',
                        memberFleetIds: [102],
                        commanderRank: 8,
                    },
                ],
            }),
        });
        const result = computeBattleMapVisibleCommanders(
            1000,
            battle.units,
            battle.attackerHierarchy,
            battle.defenderHierarchy,
        );
        expect(result).toHaveLength(3);
        expect(result[0].isMine).toBe(true);
        expect(result[0].flagshipFleetId).toBe(100);
        expect(result[1].isMine).toBe(false);
        expect(result[1].flagshipFleetId).toBe(101);
        expect(result[2].flagshipFleetId).toBe(102);
    });

    it('resolves hierarchy by my side — defender officer reads defenderHierarchy, not attacker', () => {
        const battle = createFixtureBattle({
            attackerUnits: [{ fleetId: 100, officerId: 1000 }],
            defenderUnits: [
                { fleetId: 200, officerId: 2000 },
                { fleetId: 201, officerId: 2500 },
            ],
            attackerHierarchy: emptyHierarchy(1000),
            defenderHierarchy: emptyHierarchy(2000, {
                subFleets: [
                    {
                        commanderOfficerId: 2500,
                        commanderName: 'Bittenfeld',
                        memberFleetIds: [201],
                        commanderRank: 8,
                    },
                ],
            }),
        });
        const result = computeBattleMapVisibleCommanders(
            2000,
            battle.units,
            battle.attackerHierarchy,
            battle.defenderHierarchy,
        );
        expect(result).toHaveLength(2);
        expect(result[0].side).toBe('DEFENDER');
        expect(result[1].side).toBe('DEFENDER');
    });

    it('sub-fleet commander sees only own CRC', () => {
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000 },
                { fleetId: 101, officerId: 2000 },
                { fleetId: 102, officerId: 3000 },
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Mid1',
                        memberFleetIds: [101],
                        commanderRank: 8,
                    },
                    {
                        commanderOfficerId: 3000,
                        commanderName: 'Mid2',
                        memberFleetIds: [102],
                        commanderRank: 8,
                    },
                ],
            }),
        });
        const result = computeBattleMapVisibleCommanders(
            2000,
            battle.units,
            battle.attackerHierarchy,
            battle.defenderHierarchy,
        );
        expect(result).toHaveLength(1);
        expect(result[0].officerId).toBe(2000);
        expect(result[0].isMine).toBe(true);
    });

    it('returns empty when hierarchies are null on both sides', () => {
        const units = [
            makeFixtureUnit({ fleetId: 100, officerId: 1000, side: 'ATTACKER' }),
        ];
        expect(
            computeBattleMapVisibleCommanders(1000, units, null, null),
        ).toEqual([]);
    });
});
