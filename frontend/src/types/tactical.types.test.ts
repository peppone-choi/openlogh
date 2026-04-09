/**
 * Phase 14 Plan 14-06 — Type parity contract test.
 *
 * This is a compile-time test: if the types drift from the backend DTOs in
 * `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt`,
 * this file will fail to typecheck (`pnpm typecheck`) and Vitest will also
 * refuse to compile it.
 *
 * It asserts:
 *   1. `CommandHierarchyDto` / `SubFleetDto` are exported from `@/types/tactical`.
 *   2. A bare `TacticalUnit` (pre-14-06 shape) still compiles — the new fields
 *      are all optional.
 *   3. `BattleTickEvent.type` accepts each of the four Phase 14 D-23 literals.
 *   4. `TacticalBattle.attackerHierarchy` accepts `CommandHierarchyDto | null | undefined`.
 *   5. `BattleSummaryDto` and `OperationEventDto` can be constructed.
 */

import { describe, it, expect } from 'vitest';
import type {
    TacticalUnit,
    TacticalBattle,
    BattleTickEvent,
    BattleTickBroadcast,
    CommandHierarchyDto,
    SubFleetDto,
    BattleSummaryDto,
    BattleSummaryRow,
    OperationEventDto,
} from './tactical';

describe('Phase 14 type parity — tactical DTOs', () => {
    it('constructs a SubFleetDto matching backend field names', () => {
        const sf: SubFleetDto = {
            commanderOfficerId: 101,
            commanderName: 'Reinhard',
            memberFleetIds: [1, 2, 3],
            commanderRank: 10,
        };
        expect(sf.commanderOfficerId).toBe(101);
        expect(sf.memberFleetIds).toHaveLength(3);
    });

    it('constructs a CommandHierarchyDto with all Phase 14 fields', () => {
        const h: CommandHierarchyDto = {
            fleetCommander: 101,
            subFleets: [],
            successionQueue: [102, 103],
            designatedSuccessor: 102,
            vacancyStartTick: -1,
            commJammed: false,
            jammingTicksRemaining: 0,
            activeCommander: null,
        };
        expect(h.vacancyStartTick).toBe(-1);
        expect(h.commJammed).toBe(false);
    });

    it('accepts a bare TacticalUnit without the new Phase 14 optional fields', () => {
        const u: TacticalUnit = {
            fleetId: 1,
            officerId: 100,
            officerName: 'Yang',
            factionId: 2,
            side: 'ATTACKER',
            posX: 0,
            posY: 0,
            hp: 100,
            maxHp: 100,
            ships: 300,
            maxShips: 300,
            training: 1,
            morale: 1,
            energy: { beam: 17, gun: 17, shield: 17, engine: 16, warp: 16, sensor: 17 },
            formation: 'WEDGE',
            commandRange: 50,
            isAlive: true,
            isRetreating: false,
            retreatProgress: 0,
            unitType: 'cruiser',
        };
        expect(u.fleetId).toBe(1);
    });

    it('accepts a TacticalUnit with all Phase 14 optional fields', () => {
        const u: TacticalUnit = {
            fleetId: 1,
            officerId: 100,
            officerName: 'Yang',
            factionId: 2,
            side: 'ATTACKER',
            posX: 0,
            posY: 0,
            hp: 100,
            maxHp: 100,
            ships: 300,
            maxShips: 300,
            training: 1,
            morale: 1,
            energy: { beam: 17, gun: 17, shield: 17, engine: 16, warp: 16, sensor: 17 },
            formation: 'WEDGE',
            commandRange: 50,
            isAlive: true,
            isRetreating: false,
            retreatProgress: 0,
            unitType: 'cruiser',
            sensorRange: 30,
            subFleetCommanderId: 101,
            successionState: 'PENDING_SUCCESSION',
            successionTicksRemaining: 20,
            isOnline: true,
            isNpc: false,
            missionObjective: 'CONQUEST',
            maxCommandRange: 80,
        };
        expect(u.sensorRange).toBe(30);
        expect(u.subFleetCommanderId).toBe(101);
        expect(u.successionState).toBe('PENDING_SUCCESSION');
    });

    it('accepts TacticalBattle with attackerHierarchy/defenderHierarchy hierarchies', () => {
        const hierarchy: CommandHierarchyDto = {
            fleetCommander: 101,
            subFleets: [],
            successionQueue: [],
            vacancyStartTick: -1,
            commJammed: false,
            jammingTicksRemaining: 0,
        };
        const battle: TacticalBattle = {
            id: 1,
            sessionId: 1,
            starSystemId: 1,
            attackerFactionId: 1,
            defenderFactionId: 2,
            phase: 'ACTIVE',
            startedAt: '2026-04-09T00:00:00',
            tickCount: 0,
            attackerFleetIds: [1],
            defenderFleetIds: [2],
            units: [],
            attackerHierarchy: hierarchy,
            defenderHierarchy: null,
        };
        expect(battle.attackerHierarchy?.fleetCommander).toBe(101);
        expect(battle.defenderHierarchy).toBeNull();
    });

    it('accepts BattleTickBroadcast with hierarchy propagation', () => {
        const broadcast: BattleTickBroadcast = {
            battleId: 1,
            tickCount: 42,
            phase: 'ACTIVE',
            units: [],
            events: [],
            attackerHierarchy: null,
            defenderHierarchy: null,
        };
        expect(broadcast.tickCount).toBe(42);
    });

    it('accepts all four Phase 14 D-23 BattleTickEvent.type literals', () => {
        const types: BattleTickEvent['type'][] = [
            'DAMAGE',
            'HEAL',
            'DESTROY',
            'RETREAT',
            'FLAGSHIP_DESTROYED',
            'SUCCESSION_STARTED',
            'SUCCESSION_COMPLETED',
            'JAMMING_ACTIVE',
        ];
        expect(types).toHaveLength(8);
        expect(types).toContain('FLAGSHIP_DESTROYED');
        expect(types).toContain('SUCCESSION_STARTED');
        expect(types).toContain('SUCCESSION_COMPLETED');
        expect(types).toContain('JAMMING_ACTIVE');
    });

    it('constructs a BattleSummaryDto with per-unit merit breakdown rows', () => {
        const row: BattleSummaryRow = {
            fleetId: 1,
            officerId: 100,
            officerName: 'Yang',
            side: 'ATTACKER',
            survivingShips: 280,
            maxShips: 300,
            baseMerit: 93,
            operationMultiplier: 1.5,
            totalMerit: 139,
            isOperationParticipant: true,
        };
        const summary: BattleSummaryDto = {
            battleId: 1,
            winner: 'attacker_win',
            durationTicks: 600,
            rows: [row],
        };
        expect(summary.rows[0].totalMerit).toBe(139);
        expect(summary.rows[0].operationMultiplier).toBe(1.5);
    });

    it('constructs an OperationEventDto for each of the 4 event types', () => {
        const events: OperationEventDto[] = (
            ['OPERATION_PLANNED', 'OPERATION_STARTED', 'OPERATION_COMPLETED', 'OPERATION_CANCELLED'] as const
        ).map((type, i) => ({
            type,
            operationId: i,
            sessionId: 1,
            factionId: 1,
            objective: 'CONQUEST',
            targetStarSystemId: 10,
            participantFleetIds: [1, 2],
            status: 'PENDING',
            timestamp: 1000 + i,
        }));
        expect(events).toHaveLength(4);
        expect(events[0].objective).toBe('CONQUEST');
    });
});
