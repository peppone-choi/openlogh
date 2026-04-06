import axios from 'axios';
import type { VictoryResult, SessionRanking } from '@/types/victory';

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
});

export const victoryApi = {
    /** Get victory result for a session */
    getVictoryResult: (sessionId: number) =>
        api.get<{ ended: boolean; result?: VictoryResult }>(`/api/v1/world/${sessionId}/victory`),

    /** Get final rankings */
    getRankings: (sessionId: number) =>
        api.get<SessionRanking[]>(`/api/v1/world/${sessionId}/rankings`),

    /** Restart session with new scenario */
    restartSession: (sessionId: number, scenarioCode: string) =>
        api.post(`/api/v1/world/${sessionId}/restart`, { scenarioCode }),

    /** Admin-triggered session end */
    endSession: (sessionId: number) =>
        api.post(`/api/v1/world/${sessionId}/end`),
};
