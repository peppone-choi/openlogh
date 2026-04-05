import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Officer } from '@/types';
import { generalApi } from '@/lib/gameApi';

interface OfficerStore {
    myOfficer: Officer | null;
    officers: Officer[];
    loading: boolean;
    isHydrated: boolean;
    fetchMyOfficer: (worldId: number) => Promise<void>;
    fetchOfficers: (worldId: number) => Promise<void>;
    clearMyOfficer: () => void;
}

export const useOfficerStore = create<OfficerStore>()(
    persist(
        (set) => ({
            myOfficer: null,
            officers: [],
            loading: false,
            isHydrated: false,

            fetchMyOfficer: async (worldId) => {
                set({ loading: true });
                try {
                    const { data } = await generalApi.getMine(worldId);
                    set({ myOfficer: data });
                } catch {
                    set({ myOfficer: null });
                } finally {
                    set({ loading: false });
                }
            },

            clearMyOfficer: () => set({ myOfficer: null }),

            fetchOfficers: async (worldId) => {
                set({ loading: true });
                try {
                    const { data } = await generalApi.listByWorld(worldId);
                    set({ officers: data });
                } finally {
                    set({ loading: false });
                }
            },
        }),
        {
            name: 'officer-store',
            storage: createJSONStorage(() => sessionStorage),
            partialize: (state) => ({ myOfficer: state.myOfficer }),
        }
    )
);

// Reliably detect hydration completion (onRehydrateStorage can misfire in Next.js App Router)
if (typeof window !== 'undefined') {
    useOfficerStore.persist.onFinishHydration(() => {
        useOfficerStore.setState({ isHydrated: true });
    });
    if (useOfficerStore.persist.hasHydrated()) {
        useOfficerStore.setState({ isHydrated: true });
    }
}
