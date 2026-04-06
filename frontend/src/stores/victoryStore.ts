import { create } from 'zustand';
import type { VictoryResult, SessionRanking } from '@/types/victory';
import { victoryApi } from '@/lib/victoryApi';

interface VictoryState {
    victoryResult: VictoryResult | null;
    rankings: SessionRanking[];
    isSessionEnded: boolean;
    loading: boolean;
    error: string | null;

    fetchVictoryResult: (sessionId: number) => Promise<void>;
    fetchRankings: (sessionId: number) => Promise<void>;
    restartSession: (sessionId: number, scenarioCode: string) => Promise<void>;
    onVictoryEvent: (data: { condition: string; tier: string; winnerName: string; loserName: string }) => void;
    reset: () => void;
}

export const useVictoryStore = create<VictoryState>((set) => ({
    victoryResult: null,
    rankings: [],
    isSessionEnded: false,
    loading: false,
    error: null,

    fetchVictoryResult: async (sessionId: number) => {
        set({ loading: true, error: null });
        try {
            const { data } = await victoryApi.getVictoryResult(sessionId);
            set({
                isSessionEnded: data.ended,
                victoryResult: data.result ?? null,
                loading: false,
            });
        } catch (err) {
            set({ error: 'Failed to fetch victory result', loading: false });
        }
    },

    fetchRankings: async (sessionId: number) => {
        set({ loading: true, error: null });
        try {
            const { data } = await victoryApi.getRankings(sessionId);
            set({ rankings: data, loading: false });
        } catch (err) {
            set({ error: 'Failed to fetch rankings', loading: false });
        }
    },

    restartSession: async (sessionId: number, scenarioCode: string) => {
        set({ loading: true, error: null });
        try {
            await victoryApi.restartSession(sessionId, scenarioCode);
            set({ isSessionEnded: false, victoryResult: null, rankings: [], loading: false });
        } catch (err) {
            set({ error: 'Failed to restart session', loading: false });
        }
    },

    onVictoryEvent: (data) => {
        set({
            isSessionEnded: true,
            victoryResult: {
                condition: data.condition as VictoryResult['condition'],
                conditionKorean: '',
                tier: data.tier as VictoryResult['tier'],
                tierKorean: '',
                winnerFactionId: 0,
                loserFactionId: 0,
                winnerName: data.winnerName,
                loserName: data.loserName,
                stats: {},
            },
        });
    },

    reset: () => set({
        victoryResult: null,
        rankings: [],
        isSessionEnded: false,
        loading: false,
        error: null,
    }),
}));
