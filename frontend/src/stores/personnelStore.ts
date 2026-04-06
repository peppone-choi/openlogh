import { create } from 'zustand';
import type { RankLadderEntry, PersonnelInfo } from '@/types/personnel';
import {
    fetchRankLadder,
    fetchPersonnelInfo,
    promoteOfficer,
    demoteOfficer,
} from '@/lib/api/personnel';

interface PersonnelState {
    rankLadder: RankLadderEntry[];
    personnelInfo: PersonnelInfo | null;
    selectedFactionId: number | null;
    isLoading: boolean;
    error: string | null;
    actionMessage: string | null;
}

interface PersonnelActions {
    loadLadder: (sessionId: number, factionId: number) => Promise<void>;
    loadInfo: (sessionId: number, officerId: number) => Promise<void>;
    promote: (
        sessionId: number,
        promoterId: number,
        officerId: number
    ) => Promise<boolean>;
    demote: (
        sessionId: number,
        demoterId: number,
        officerId: number
    ) => Promise<boolean>;
    clearError: () => void;
    clearActionMessage: () => void;
}

export const usePersonnelStore = create<PersonnelState & PersonnelActions>()(
    (set, get) => ({
        rankLadder: [],
        personnelInfo: null,
        selectedFactionId: null,
        isLoading: false,
        error: null,
        actionMessage: null,

        loadLadder: async (sessionId: number, factionId: number) => {
            set({ isLoading: true, error: null, selectedFactionId: factionId });
            try {
                const data = await fetchRankLadder(sessionId, factionId);
                set({ rankLadder: data, isLoading: false });
            } catch (err) {
                const message =
                    err instanceof Error
                        ? err.message
                        : 'Failed to load rank ladder';
                set({ error: message, isLoading: false });
            }
        },

        loadInfo: async (sessionId: number, officerId: number) => {
            set({ isLoading: true, error: null });
            try {
                const data = await fetchPersonnelInfo(sessionId, officerId);
                set({ personnelInfo: data, isLoading: false });
            } catch (err) {
                const message =
                    err instanceof Error
                        ? err.message
                        : 'Failed to load personnel info';
                set({ error: message, isLoading: false });
            }
        },

        promote: async (
            sessionId: number,
            promoterId: number,
            officerId: number
        ) => {
            set({ isLoading: true, error: null, actionMessage: null });
            try {
                const response = await promoteOfficer(sessionId, promoterId, {
                    officerId,
                });
                if (response.success && response.updatedOfficer) {
                    set({
                        personnelInfo: response.updatedOfficer,
                        actionMessage: response.message,
                        isLoading: false,
                    });
                    // Reload ladder to reflect changes
                    const { selectedFactionId } = get();
                    if (selectedFactionId) {
                        const ladder = await fetchRankLadder(
                            sessionId,
                            selectedFactionId
                        );
                        set({ rankLadder: ladder });
                    }
                    return true;
                } else {
                    set({
                        error: response.message,
                        isLoading: false,
                    });
                    return false;
                }
            } catch (err) {
                const message =
                    err instanceof Error ? err.message : 'Promotion failed';
                set({ error: message, isLoading: false });
                return false;
            }
        },

        demote: async (
            sessionId: number,
            demoterId: number,
            officerId: number
        ) => {
            set({ isLoading: true, error: null, actionMessage: null });
            try {
                const response = await demoteOfficer(sessionId, demoterId, {
                    officerId,
                });
                if (response.success && response.updatedOfficer) {
                    set({
                        personnelInfo: response.updatedOfficer,
                        actionMessage: response.message,
                        isLoading: false,
                    });
                    // Reload ladder to reflect changes
                    const { selectedFactionId } = get();
                    if (selectedFactionId) {
                        const ladder = await fetchRankLadder(
                            sessionId,
                            selectedFactionId
                        );
                        set({ rankLadder: ladder });
                    }
                    return true;
                } else {
                    set({
                        error: response.message,
                        isLoading: false,
                    });
                    return false;
                }
            } catch (err) {
                const message =
                    err instanceof Error ? err.message : 'Demotion failed';
                set({ error: message, isLoading: false });
                return false;
            }
        },

        clearError: () => set({ error: null }),
        clearActionMessage: () => set({ actionMessage: null }),
    })
);
