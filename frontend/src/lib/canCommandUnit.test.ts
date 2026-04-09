// Phase 14 — Plan 14-14 — Pure gating function table tests (FE-03, D-09..D-12).
//
// 7 branches covered:
//   1. fleet commander   → any same-side unit allowed
//   2. sub-commander     → unit in own subFleet allowed
//   3. sub-commander     → unit NOT in own subFleet → OUT_OF_CHAIN
//   4. plain officer     → any command → OUT_OF_CHAIN
//   5. commJammed        → fleet commander blocked with JAMMED reason
//   6. activeCommander   → delegated commander acts as fleet commander
//   7. null hierarchy    → any command → OUT_OF_CHAIN
//
// The function is pure — no store access, no effects — so these tests are
// table-driven with inline hierarchy literals and the makeFixtureUnit factory.

import { describe, it, expect } from 'vitest';
import { canCommandUnit } from './canCommandUnit';
import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

// Compact factory — only the fields the gating function reads + the required
// TacticalUnit shape fields so vitest doesn't trip on `as unknown as`.
function makeFixtureUnit(overrides: Partial<TacticalUnit> = {}): TacticalUnit {
    return {
        fleetId: 10,
        officerId: 2000,
        officerName: 'Target',
        factionId: 1,
        side: 'ATTACKER',
        posX: 500,
        posY: 500,
        hp: 1000,
        maxHp: 1000,
        ships: 300,
        maxShips: 300,
        training: 50,
        morale: 80,
        energy: { beam: 20, gun: 20, shield: 20, engine: 20, warp: 10, sensor: 10 },
        formation: 'MIXED',
        commandRange: 100,
        isAlive: true,
        isRetreating: false,
        retreatProgress: 0,
        unitType: 'battleship',
        ...overrides,
    };
}

function hierarchy(overrides: Partial<CommandHierarchyDto> = {}): CommandHierarchyDto {
    return {
        fleetCommander: 1000,
        subFleets: [],
        successionQueue: [1000],
        designatedSuccessor: null,
        vacancyStartTick: -1,
        commJammed: false,
        jammingTicksRemaining: 0,
        activeCommander: null,
        ...overrides,
    };
}

describe('canCommandUnit (FE-03, D-09..D-12)', () => {
    it('Test 1 — fleet commander commands any unit on my side → allowed', () => {
        const myHierarchy = hierarchy({ fleetCommander: 1000 });
        const target = makeFixtureUnit({ fleetId: 42, officerId: 3000 });

        const result = canCommandUnit(1000, myHierarchy, target);

        expect(result.allowed).toBe(true);
        expect(result.reason).toBeNull();
    });

    it('Test 2 — sub-commander commands unit in my subFleet → allowed', () => {
        const myHierarchy = hierarchy({
            fleetCommander: 1000,
            subFleets: [
                {
                    commanderOfficerId: 2000,
                    commanderName: 'Sub1',
                    memberFleetIds: [10, 11, 12],
                    commanderRank: 7,
                },
            ],
        });
        const target = makeFixtureUnit({ fleetId: 11, officerId: 3000 });

        const result = canCommandUnit(2000, myHierarchy, target);

        expect(result.allowed).toBe(true);
        expect(result.reason).toBeNull();
    });

    it('Test 3 — sub-commander commands unit NOT in my subFleet → OUT_OF_CHAIN', () => {
        const myHierarchy = hierarchy({
            fleetCommander: 1000,
            subFleets: [
                {
                    commanderOfficerId: 2000,
                    commanderName: 'Sub1',
                    memberFleetIds: [10, 11, 12],
                    commanderRank: 7,
                },
                {
                    commanderOfficerId: 2001,
                    commanderName: 'Sub2',
                    memberFleetIds: [20, 21, 22],
                    commanderRank: 7,
                },
            ],
        });
        // Target belongs to Sub2's subFleet, I'm Sub1 → out of chain.
        const target = makeFixtureUnit({ fleetId: 21, officerId: 3001 });

        const result = canCommandUnit(2000, myHierarchy, target);

        expect(result.allowed).toBe(false);
        expect(result.reason).toBe('OUT_OF_CHAIN');
        expect(result.message).toContain('지휘권 없음');
    });

    it('Test 4 — plain officer not in hierarchy → OUT_OF_CHAIN', () => {
        const myHierarchy = hierarchy({
            fleetCommander: 1000,
            subFleets: [
                {
                    commanderOfficerId: 2000,
                    commanderName: 'Sub1',
                    memberFleetIds: [10],
                    commanderRank: 7,
                },
            ],
        });
        const target = makeFixtureUnit({ fleetId: 10, officerId: 3000 });

        // Plain officer 9999 is not the fleet commander nor a sub-commander.
        const result = canCommandUnit(9999, myHierarchy, target);

        expect(result.allowed).toBe(false);
        expect(result.reason).toBe('OUT_OF_CHAIN');
    });

    it('Test 5 — commJammed on fleet commander → JAMMED', () => {
        const myHierarchy = hierarchy({
            fleetCommander: 1000,
            commJammed: true,
            jammingTicksRemaining: 15,
        });
        const target = makeFixtureUnit({ fleetId: 42, officerId: 3000 });

        const result = canCommandUnit(1000, myHierarchy, target);

        expect(result.allowed).toBe(false);
        expect(result.reason).toBe('JAMMED');
        expect(result.message).toContain('통신 방해');
    });

    it('Test 6 — delegated activeCommander acts as fleet commander', () => {
        const myHierarchy = hierarchy({
            fleetCommander: 1000, // original commander
            activeCommander: 1500, // delegated to me (Kircheis-after-delegation scenario)
        });
        const target = makeFixtureUnit({ fleetId: 42, officerId: 3000 });

        const result = canCommandUnit(1500, myHierarchy, target);

        expect(result.allowed).toBe(true);
        expect(result.reason).toBeNull();
    });

    it('Test 7 — null hierarchy → OUT_OF_CHAIN', () => {
        const target = makeFixtureUnit();

        const resultNull = canCommandUnit(1000, null, target);
        const resultUndef = canCommandUnit(1000, undefined, target);

        expect(resultNull.allowed).toBe(false);
        expect(resultNull.reason).toBe('OUT_OF_CHAIN');
        expect(resultUndef.allowed).toBe(false);
        expect(resultUndef.reason).toBe('OUT_OF_CHAIN');
    });
});
