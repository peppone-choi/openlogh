import { create } from 'zustand';
import { commandApi, proposalApi } from '@/lib/gameApi';
import type { CommandTableEntry, Proposal, SubmitProposalRequest } from '@/types';

interface CommandState {
    commandTable: Record<string, CommandTableEntry[]>;
    nationCommandTable: Record<string, CommandTableEntry[]>;
    pendingProposals: Proposal[];
    myProposals: Proposal[];
    loading: boolean;

    fetchCommandTable: (generalId: number) => Promise<void>;
    fetchNationCommandTable: (generalId: number) => Promise<void>;
    fetchPendingProposals: (generalId: number) => Promise<void>;
    fetchMyProposals: (generalId: number) => Promise<void>;
    submitProposal: (generalId: number, data: SubmitProposalRequest) => Promise<void>;
    resolveProposal: (generalId: number, proposalId: number, approved: boolean, reason?: string) => Promise<void>;
}

export const useCommandStore = create<CommandState>((set) => ({
    commandTable: {},
    nationCommandTable: {},
    pendingProposals: [],
    myProposals: [],
    loading: false,

    fetchCommandTable: async (generalId: number) => {
        set({ loading: true });
        try {
            const { data } = await commandApi.getCommandTable(generalId);
            set({ commandTable: data });
        } catch (error) {
            console.error('Failed to fetch command table:', error);
        } finally {
            set({ loading: false });
        }
    },

    fetchNationCommandTable: async (generalId: number) => {
        set({ loading: true });
        try {
            const { data } = await commandApi.getNationCommandTable(generalId);
            set({ nationCommandTable: data });
        } catch (error) {
            console.error('Failed to fetch nation command table:', error);
        } finally {
            set({ loading: false });
        }
    },

    fetchPendingProposals: async (generalId: number) => {
        try {
            const { data } = await proposalApi.pending(generalId);
            set({ pendingProposals: data });
        } catch (error) {
            console.error('Failed to fetch pending proposals:', error);
        }
    },

    fetchMyProposals: async (generalId: number) => {
        try {
            const { data } = await proposalApi.my(generalId);
            set({ myProposals: data });
        } catch (error) {
            console.error('Failed to fetch my proposals:', error);
        }
    },

    submitProposal: async (generalId: number, data: SubmitProposalRequest) => {
        await proposalApi.submit(generalId, data);
        // Refetch my proposals after submission
        const { data: myData } = await proposalApi.my(generalId);
        set({ myProposals: myData });
    },

    resolveProposal: async (generalId: number, proposalId: number, approved: boolean, reason?: string) => {
        await proposalApi.resolve(generalId, proposalId, { approved, reason });
        // Refetch pending proposals after resolution
        const { data: pendingData } = await proposalApi.pending(generalId);
        set({ pendingProposals: pendingData });
    },
}));
