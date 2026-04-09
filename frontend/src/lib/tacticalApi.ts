import axios from 'axios';
import type {
    TacticalBattle,
    BattleCommand,
    BattleSummaryDto,
} from '@/types/tactical';

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
});

export const tacticalApi = {
    /** Get all active tactical battles in a session */
    getActiveBattles: (sessionId: number) =>
        api.get<{ battles: TacticalBattle[] }>(`/api/v1/battle/${sessionId}/active`),

    /** Get a specific battle's current state */
    getBattleState: (sessionId: number, battleId: number) =>
        api.get<TacticalBattle>(`/api/v1/battle/${sessionId}/${battleId}`),

    /**
     * Phase 14 Plan 14-18 (D-32..D-34) — fetch the per-unit merit breakdown
     * for an ENDED tactical battle. Backing endpoint from plan 14-02:
     *   GET /api/v1/battle/{sessionId}/{battleId}/summary
     *
     * Returns the parsed `BattleSummaryDto` directly (not wrapped in an
     * axios response). The end-of-battle modal calls this on the
     * ACTIVE → ENDED phase transition.
     */
    fetchBattleSummary: async (
        sessionId: number,
        battleId: number,
    ): Promise<BattleSummaryDto> => {
        const { data } = await api.get<BattleSummaryDto>(
            `/api/v1/battle/${sessionId}/${battleId}/summary`,
        );
        return data;
    },
};

/**
 * Standalone export of {@link tacticalApi.fetchBattleSummary} so callers
 * can import the fetcher directly without going through the `tacticalApi`
 * namespace object. The BattleEndModal uses this form to keep its imports
 * flat.
 */
export const fetchBattleSummary = tacticalApi.fetchBattleSummary;

/**
 * Send a battle command via WebSocket STOMP.
 * The caller should use the stompClient from websocket.ts.
 */
export function buildBattleCommandPayload(command: BattleCommand): string {
    return JSON.stringify({
        battleId: command.battleId,
        officerId: command.officerId,
        commandType: command.commandType,
        energy: command.energy,
        formation: command.formation,
        stance: command.stance,
        targetFleetId: command.targetFleetId,
        unitCommand: command.unitCommand,
        dirX: command.dirX,
        dirY: command.dirY,
        speed: command.speed,
    });
}
