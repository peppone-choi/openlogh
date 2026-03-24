import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Officer } from '@/types';
import { officerApi as generalApi } from '@/lib/gameApi';

interface OfficerStore {
    myOfficer: Officer | null;
    officers: Officer[];
    loading: boolean;
    isHydrated: boolean;
    fetchMyOfficer: (worldId: number) => Promise<void>;
    fetchOfficers: (worldId: number) => Promise<void>;
    clearMyOfficer: () => void;
    // Deprecated aliases
    /** @deprecated use myOfficer */ myGeneral: Officer | null;
    /** @deprecated use fetchMyOfficer */ fetchMyGeneral: (worldId: number) => Promise<void>;
    /** @deprecated use fetchOfficers */ fetchGenerals: (worldId: number) => Promise<void>;
    /** @deprecated use clearMyOfficer */ clearMyGeneral: () => void;
}

export const useOfficerStore = create<OfficerStore>()(
    persist(
        (set, get) => ({
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

            // Deprecated aliases
            get myGeneral() {
                return get().myOfficer;
            },
            fetchMyGeneral: async (worldId) => get().fetchMyOfficer(worldId),
            fetchGenerals: async (worldId) => get().fetchOfficers(worldId),
            clearMyGeneral: () => get().clearMyOfficer(),
        }),
        {
            name: 'openlogh:officer',
            storage: typeof window !== 'undefined' ? createJSONStorage(() => sessionStorage) : undefined,
            partialize: (state) => ({
                myOfficer: state.myOfficer,
            }),
            onRehydrateStorage: () => (state) => {
                if (state) state.isHydrated = true;
            },
        }
    )
);

// Trigger hydration flag after store initializes
if (typeof window !== 'undefined') {
    const unsub = useOfficerStore.persist.onFinishHydration((state) => {
        useOfficerStore.setState({ isHydrated: true });
        void state;
        unsub();
    });
    if (useOfficerStore.persist.hasHydrated()) {
        useOfficerStore.setState({ isHydrated: true });
    }
}

/** @deprecated Use useOfficerStore */
export { useOfficerStore as useGeneralStore };
