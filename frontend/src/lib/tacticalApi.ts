import axios from 'axios';
import type { TacticalBattle, BattleCommand } from '@/types/tactical';

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
};

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
