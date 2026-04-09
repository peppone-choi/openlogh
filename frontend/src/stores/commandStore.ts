import { create } from 'zustand';
import { commandApi, proposalApi } from '@/lib/gameApi';
import type { CommandTableEntry, Proposal, SubmitProposalRequest } from '@/types';

/**
 * Phase 14 — Plan 14-14 (FE-03, D-10) — payload for Shift+click proposal flow.
 *
 * `targetOfficerId`    — the unit the command would have targeted.
 * `superiorOfficerId`  — the approver we route the proposal to (normally the
 *                        requester's fleet commander or sub-fleet commander).
 * `targetFleetId`      — optional; passed through to the backend args so the
 *                        approver's preview shows which fleet is affected.
 */
export interface CreateProposalPayload {
    targetOfficerId: number;
    superiorOfficerId: number;
    targetFleetId?: number;
}

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
    /**
     * Phase 14 — Plan 14-14 (FE-03, D-10) — Shift+click proposal dispatch.
     *
     * Wraps `submitProposal` with the command-execution-panel's gating payload
     * so the panel's Shift+click handler doesn't have to know the
     * `SubmitProposalRequest` shape. On success, refreshes `myProposals`.
     */
    createProposal: (
        requesterOfficerId: number,
        commandCode: string,
        payload: CreateProposalPayload,
    ) => Promise<void>;
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

    /**
     * Phase 14 — Plan 14-14 (FE-03, D-10) — Shift+click proposal dispatch.
     *
     * Collapses the FE-03 gating payload into a `SubmitProposalRequest` so the
     * command-execution-panel can fire a proposal without knowing the proposal
     * DTO shape. The requester is the logged-in officer; the approver is the
     * `superiorOfficerId` passed in (normally the fleet commander from the
     * current CommandHierarchyDto). `targetOfficerId` + `targetFleetId` are
     * forwarded into the proposal's `args` map so the approver's preview can
     * render which unit the command would have targeted.
     *
     * The reason field is intentionally left blank — UI-SPEC C states the
     * proposal flow is "pre-selected" on Shift+click and the actual reason
     * copy is entered later in the proposal panel (future D-10 polish).
     */
    createProposal: async (
        requesterOfficerId: number,
        commandCode: string,
        payload: CreateProposalPayload,
    ) => {
        await proposalApi.submit(requesterOfficerId, {
            approverId: payload.superiorOfficerId,
            actionCode: commandCode,
            args: {
                targetOfficerId: payload.targetOfficerId,
                ...(payload.targetFleetId !== undefined && {
                    targetFleetId: payload.targetFleetId,
                }),
            },
        });
        // Refetch "my proposals" so the proposal-panel "보낸 제안" tab reflects
        // the new row immediately.
        try {
            const { data: myData } = await proposalApi.my(requesterOfficerId);
            set({ myProposals: myData });
        } catch (error) {
            console.error('Failed to refresh my proposals after createProposal:', error);
        }
    },

    resolveProposal: async (generalId: number, proposalId: number, approved: boolean, reason?: string) => {
        await proposalApi.resolve(generalId, proposalId, { approved, reason });
        // Refetch pending proposals after resolution
        const { data: pendingData } = await proposalApi.pending(generalId);
        set({ pendingProposals: pendingData });
    },
}));
