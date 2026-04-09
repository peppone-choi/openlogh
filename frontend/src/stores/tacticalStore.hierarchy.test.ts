// Phase 14 — Plan 14-10 — Hierarchy SoT tests (FE-03, D-01, D-18, D-21)
//
// Covers:
//   * onBattleTick merges attackerHierarchy / defenderHierarchy from the
//     BattleTickBroadcast per D-21.
//   * Fog / succession bookkeeping slots are preserved across ticks so 14-11
//     and 14-14 can plug in their update logic without being stomped by 14-10.
//   * clearBattle resets hierarchy + bookkeeping slots.
//   * findVisibleCrcCommanders (D-01) — self + sub-fleet CRCs, sub-fleet only
//     sees own ring, plain officer sees nothing.
//   * findAlliesInMyChain (D-18) — chain-aware ally aggregation for 14-11's
//     shared sensor coverage.

import { describe, it, expect, beforeEach } from 'vitest';
import { useTacticalStore } from './tacticalStore';
import { createFixtureBattle } from '@/test/fixtures/tacticalBattleFixture';
import {
    findVisibleCrcCommanders,
    findAlliesInMyChain,
} from '@/lib/commandChain';
import type {
    BattleTickBroadcast,
    CommandHierarchyDto,
    BattleSide,
} from '@/types/tactical';

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

function resetStore() {
    useTacticalStore.setState({
        currentBattle: null,
        units: [],
        recentEvents: [],
        activeBattles: [],
        loading: false,
        error: null,
        myOfficerId: null,
        lastSeenEnemyPositions: {},
        activeSuccessionFleetIds: [],
        activeFlagshipDestroyedFleetIds: [],
    });
}

describe('tacticalStore hierarchy merge (FE-03, D-21)', () => {
    beforeEach(() => {
        resetStore();
    });

    it('onBattleTick merges attackerHierarchy from broadcast', () => {
        const battle = createFixtureBattle({
            attackerHierarchy: emptyHierarchy(1000),
        });
        useTacticalStore.setState({ currentBattle: battle });

        const broadcast: BattleTickBroadcast = {
            battleId: battle.id,
            tickCount: 1,
            phase: 'ACTIVE',
            units: battle.units,
            events: [],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Kircheis',
                        memberFleetIds: [101],
                        commanderRank: 8,
                    },
                ],
                successionQueue: [2000],
            }),
            defenderHierarchy: null,
        };
        useTacticalStore.getState().onBattleTick(broadcast);

        const merged = useTacticalStore.getState().currentBattle?.attackerHierarchy;
        expect(merged?.fleetCommander).toBe(1000);
        expect(merged?.subFleets).toHaveLength(1);
        expect(merged?.subFleets[0].commanderOfficerId).toBe(2000);
    });

    it('onBattleTick merges defenderHierarchy from broadcast', () => {
        const battle = createFixtureBattle();
        useTacticalStore.setState({ currentBattle: battle });

        useTacticalStore.getState().onBattleTick({
            battleId: battle.id,
            tickCount: 1,
            phase: 'ACTIVE',
            units: battle.units,
            events: [],
            attackerHierarchy: null,
            defenderHierarchy: emptyHierarchy(3000),
        });

        expect(
            useTacticalStore.getState().currentBattle?.defenderHierarchy?.fleetCommander,
        ).toBe(3000);
    });

    it('onBattleTick falls back to previous hierarchy when broadcast field is null', () => {
        const battle = createFixtureBattle({
            attackerHierarchy: emptyHierarchy(1000),
        });
        useTacticalStore.setState({ currentBattle: battle });

        // Broadcast with null hierarchy — previous hierarchy must survive.
        useTacticalStore.getState().onBattleTick({
            battleId: battle.id,
            tickCount: 2,
            phase: 'ACTIVE',
            units: battle.units,
            events: [],
            attackerHierarchy: null,
        });

        expect(
            useTacticalStore.getState().currentBattle?.attackerHierarchy?.fleetCommander,
        ).toBe(1000);
    });

    it('preserves lastSeenEnemyPositions across ticks so 14-11 can update them', () => {
        const battle = createFixtureBattle();
        useTacticalStore.setState({
            currentBattle: battle,
            lastSeenEnemyPositions: {
                999: {
                    x: 100,
                    y: 100,
                    tick: 5,
                    ships: 300,
                    unitType: 'cruiser',
                    side: 'DEFENDER' as BattleSide,
                },
            },
        });

        useTacticalStore.getState().onBattleTick({
            battleId: battle.id,
            tickCount: 6,
            phase: 'ACTIVE',
            units: battle.units,
            events: [],
        });

        expect(useTacticalStore.getState().lastSeenEnemyPositions[999]).toBeDefined();
        expect(useTacticalStore.getState().lastSeenEnemyPositions[999].ships).toBe(300);
    });

    it('preserves activeSuccessionFleetIds across ticks so 14-14 can maintain them', () => {
        const battle = createFixtureBattle();
        useTacticalStore.setState({
            currentBattle: battle,
            activeSuccessionFleetIds: [42, 43],
        });

        useTacticalStore.getState().onBattleTick({
            battleId: battle.id,
            tickCount: 7,
            phase: 'ACTIVE',
            units: battle.units,
            events: [],
        });

        expect(useTacticalStore.getState().activeSuccessionFleetIds).toEqual([42, 43]);
    });

    it('prunes expired activeFlagshipDestroyedFleetIds entries on tick', () => {
        const battle = createFixtureBattle();
        useTacticalStore.setState({
            currentBattle: battle,
            activeFlagshipDestroyedFleetIds: [
                { fleetId: 101, expiresAt: Date.now() - 1000 }, // expired
                { fleetId: 102, expiresAt: Date.now() + 60_000 }, // alive
            ],
        });

        useTacticalStore.getState().onBattleTick({
            battleId: battle.id,
            tickCount: 8,
            phase: 'ACTIVE',
            units: battle.units,
            events: [],
        });

        const flashes = useTacticalStore.getState().activeFlagshipDestroyedFleetIds;
        expect(flashes).toHaveLength(1);
        expect(flashes[0].fleetId).toBe(102);
    });

    it('initialises bookkeeping slots to empty when the store is created', () => {
        // Re-read the live store without resetting — initial creation defaults
        // must be empty map + empty arrays.
        const fresh = useTacticalStore.getState();
        expect(fresh.lastSeenEnemyPositions).toEqual({});
        expect(fresh.activeSuccessionFleetIds).toEqual([]);
        expect(fresh.activeFlagshipDestroyedFleetIds).toEqual([]);
    });

    it('clearBattle resets hierarchy and bookkeeping slots', () => {
        const battle = createFixtureBattle({
            attackerHierarchy: emptyHierarchy(1000),
        });
        useTacticalStore.setState({
            currentBattle: battle,
            lastSeenEnemyPositions: {
                5: {
                    x: 1,
                    y: 1,
                    tick: 1,
                    ships: 1,
                    unitType: 'cruiser',
                    side: 'ATTACKER',
                },
            },
            activeSuccessionFleetIds: [1],
            activeFlagshipDestroyedFleetIds: [
                { fleetId: 1, expiresAt: Date.now() + 1000 },
            ],
        });

        useTacticalStore.getState().clearBattle();

        const s = useTacticalStore.getState();
        expect(s.currentBattle).toBeNull();
        expect(s.lastSeenEnemyPositions).toEqual({});
        expect(s.activeSuccessionFleetIds).toEqual([]);
        expect(s.activeFlagshipDestroyedFleetIds).toEqual([]);
    });
});

describe('findVisibleCrcCommanders (D-01)', () => {
    it('returns empty for officer not in hierarchy', () => {
        const battle = createFixtureBattle({
            attackerUnits: [{ fleetId: 100, officerId: 1000 }],
            attackerHierarchy: emptyHierarchy(1000),
        });
        const result = findVisibleCrcCommanders(
            9999,
            battle.attackerHierarchy!,
            battle.units,
            'ATTACKER',
        );
        expect(result).toEqual([]);
    });

    it('returns empty for negative officer id (not in a commander slot)', () => {
        const battle = createFixtureBattle({
            attackerHierarchy: emptyHierarchy(1000),
        });
        expect(
            findVisibleCrcCommanders(-1, battle.attackerHierarchy!, battle.units, 'ATTACKER'),
        ).toEqual([]);
    });

    it('returns empty when hierarchy is null', () => {
        expect(findVisibleCrcCommanders(1000, null, [], 'ATTACKER')).toEqual([]);
        expect(findVisibleCrcCommanders(1000, undefined, [], 'ATTACKER')).toEqual([]);
    });

    it('fleet commander sees own CRC + all sub-commander CRCs', () => {
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000, officerName: 'Reinhardt' },
                { fleetId: 101, officerId: 2000, officerName: 'Kircheis' },
                { fleetId: 102, officerId: 3000, officerName: 'Mittermeyer' },
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Kircheis',
                        memberFleetIds: [101, 103],
                        commanderRank: 8,
                    },
                    {
                        commanderOfficerId: 3000,
                        commanderName: 'Mittermeyer',
                        memberFleetIds: [102],
                        commanderRank: 8,
                    },
                ],
                successionQueue: [2000, 3000],
            }),
        });
        const result = findVisibleCrcCommanders(
            1000,
            battle.attackerHierarchy!,
            battle.units,
            'ATTACKER',
        );
        expect(result).toHaveLength(3);
        expect(result[0]).toMatchObject({
            officerId: 1000,
            isMine: true,
            isCommandable: true,
            flagshipFleetId: 100,
        });
        expect(result[1]).toMatchObject({
            officerId: 2000,
            officerName: 'Kircheis',
            isMine: false,
            isCommandable: true,
            flagshipFleetId: 101,
        });
        expect(result[2]).toMatchObject({
            officerId: 3000,
            officerName: 'Mittermeyer',
            isMine: false,
            isCommandable: true,
            flagshipFleetId: 102,
        });
    });

    it('sub-fleet commander sees only their own CRC', () => {
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000 },
                { fleetId: 101, officerId: 2000 },
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Kircheis',
                        memberFleetIds: [101],
                        commanderRank: 8,
                    },
                ],
                successionQueue: [2000],
            }),
        });
        const result = findVisibleCrcCommanders(
            2000,
            battle.attackerHierarchy!,
            battle.units,
            'ATTACKER',
        );
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({
            officerId: 2000,
            isMine: true,
            isCommandable: true,
            flagshipFleetId: 101,
        });
    });

    it('active commander (post-delegation) is treated as fleet commander', () => {
        // After Reinhardt delegates to Kircheis, activeCommander=2000 but
        // fleetCommander still reads 1000. Kircheis should see every CRC.
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000 },
                { fleetId: 101, officerId: 2000 },
                { fleetId: 102, officerId: 3000 },
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                activeCommander: 2000,
                subFleets: [
                    {
                        commanderOfficerId: 3000,
                        commanderName: 'Mittermeyer',
                        memberFleetIds: [102],
                        commanderRank: 8,
                    },
                ],
            }),
        });
        const result = findVisibleCrcCommanders(
            2000,
            battle.attackerHierarchy!,
            battle.units,
            'ATTACKER',
        );
        // Active commander matches (amFleetCommander branch): own CRC + all
        // sub-commanders. Active commander 2000 has no own unit entry in the
        // subFleets list, so results are self + Mittermeyer.
        expect(result).toHaveLength(2);
        expect(result[0].officerId).toBe(2000);
        expect(result[0].isMine).toBe(true);
        expect(result[1].officerId).toBe(3000);
    });
});

describe('findAlliesInMyChain (D-18)', () => {
    it('returns all alive allies for the fleet commander', () => {
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000 },
                { fleetId: 101, officerId: 2000 },
                { fleetId: 102, officerId: 3000, isAlive: false },
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Kircheis',
                        memberFleetIds: [101],
                        commanderRank: 8,
                    },
                ],
            }),
        });
        const allies = findAlliesInMyChain(
            1000,
            battle.attackerHierarchy!,
            battle.units,
            'ATTACKER',
        );
        // Dead unit (102) is filtered out; 100 + 101 remain.
        expect(allies.map((u) => u.fleetId).sort()).toEqual([100, 101]);
    });

    it('sub-fleet commander sees own flagship + memberFleetIds only', () => {
        const battle = createFixtureBattle({
            attackerUnits: [
                { fleetId: 100, officerId: 1000 },
                { fleetId: 101, officerId: 2000 },
                { fleetId: 103, officerId: 2100 }, // under Kircheis
                { fleetId: 102, officerId: 3000 }, // under Mittermeyer
            ],
            attackerHierarchy: emptyHierarchy(1000, {
                subFleets: [
                    {
                        commanderOfficerId: 2000,
                        commanderName: 'Kircheis',
                        memberFleetIds: [101, 103],
                        commanderRank: 8,
                    },
                    {
                        commanderOfficerId: 3000,
                        commanderName: 'Mittermeyer',
                        memberFleetIds: [102],
                        commanderRank: 8,
                    },
                ],
            }),
        });
        const allies = findAlliesInMyChain(
            2000,
            battle.attackerHierarchy!,
            battle.units,
            'ATTACKER',
        );
        expect(allies.map((u) => u.fleetId).sort()).toEqual([101, 103]);
    });

    it('returns empty for a plain officer not in any chain', () => {
        const battle = createFixtureBattle({
            attackerHierarchy: emptyHierarchy(1000),
        });
        expect(
            findAlliesInMyChain(
                9999,
                battle.attackerHierarchy!,
                battle.units,
                'ATTACKER',
            ),
        ).toEqual([]);
    });
});
