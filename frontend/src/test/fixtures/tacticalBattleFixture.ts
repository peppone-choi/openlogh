import type { TacticalBattle, TacticalUnit, BattleSide } from '@/types/tactical';

// Optional — CommandHierarchyDto is added by plan 14-06. For Wave 0, use `any`
// placeholder locally so this fixture compiles before the type extension lands.
type CommandHierarchyDto = unknown;

export interface FixtureOverrides {
    battleId?: number;
    sessionId?: number;
    phase?: 'PREPARING' | 'ACTIVE' | 'ENDED';
    tickCount?: number;
    attackerHierarchy?: CommandHierarchyDto | null;
    defenderHierarchy?: CommandHierarchyDto | null;
    attackerUnits?: Partial<TacticalUnit>[];
    defenderUnits?: Partial<TacticalUnit>[];
}

/**
 * Build a single TacticalUnit for fixtures. Accepts overrides for any field;
 * sensible defaults supply ships/hp/energy so unit tests can focus on a single
 * concern (hierarchy, gating, fog, etc.) without populating the whole shape.
 */
export function makeFixtureUnit(overrides: Partial<TacticalUnit> = {}): TacticalUnit {
    return {
        fleetId: overrides.fleetId ?? 1,
        officerId: overrides.officerId ?? 100,
        officerName: overrides.officerName ?? 'Yang',
        factionId: overrides.factionId ?? 1,
        side: overrides.side ?? ('ATTACKER' as BattleSide),
        posX: overrides.posX ?? 500,
        posY: overrides.posY ?? 500,
        hp: overrides.hp ?? 100,
        maxHp: overrides.maxHp ?? 100,
        ships: overrides.ships ?? 300,
        maxShips: overrides.maxShips ?? 300,
        training: overrides.training ?? 1,
        morale: overrides.morale ?? 1,
        energy:
            overrides.energy ?? {
                beam: 17,
                gun: 17,
                shield: 17,
                engine: 16,
                warp: 16,
                sensor: 17,
            },
        formation: overrides.formation ?? 'WEDGE',
        commandRange: overrides.commandRange ?? 50,
        isAlive: overrides.isAlive ?? true,
        isRetreating: overrides.isRetreating ?? false,
        retreatProgress: overrides.retreatProgress ?? 0,
        unitType: overrides.unitType ?? 'cruiser',
        ...overrides,
    } as TacticalUnit;
}

/**
 * createFixtureBattle — primary factory for TacticalBattle unit tests.
 *
 * Supplies a fully populated TacticalBattle with one attacker and one defender
 * by default, merging any overrides provided. Hierarchy fields are passed
 * through loosely because `TacticalBattle` does not yet type them (plan 14-06
 * extends the type); fixture consumers should cast as needed until then.
 */
export function createFixtureBattle(overrides: FixtureOverrides = {}): TacticalBattle {
    const battleId = overrides.battleId ?? 1;
    const attackerUnits = (overrides.attackerUnits ?? [{}]).map((o, i) =>
        makeFixtureUnit({
            fleetId: 100 + i,
            officerId: 1000 + i,
            side: 'ATTACKER' as BattleSide,
            ...o,
        }),
    );
    const defenderUnits = (overrides.defenderUnits ?? [{}]).map((o, i) =>
        makeFixtureUnit({
            fleetId: 200 + i,
            officerId: 2000 + i,
            side: 'DEFENDER' as BattleSide,
            ...o,
        }),
    );
    const battle = {
        id: battleId,
        sessionId: overrides.sessionId ?? 1,
        starSystemId: 1,
        attackerFactionId: 1,
        defenderFactionId: 2,
        phase: overrides.phase ?? 'ACTIVE',
        startedAt: '2026-04-09T00:00:00',
        tickCount: overrides.tickCount ?? 0,
        attackerFleetIds: attackerUnits.map((u) => u.fleetId),
        defenderFleetIds: defenderUnits.map((u) => u.fleetId),
        units: [...attackerUnits, ...defenderUnits],
        // hierarchy fields are attached as loose extensions — plan 14-06 extends
        // the TacticalBattle type and these casts will disappear then.
        attackerHierarchy: overrides.attackerHierarchy ?? null,
        defenderHierarchy: overrides.defenderHierarchy ?? null,
    } as unknown as TacticalBattle;
    return battle;
}
