import { create } from 'zustand';
import type { ChatMessage, ChatScope, MailCount, AddressBookEntry } from '@/types/chat';
import { chatApi } from '@/lib/chatApi';

interface ChatState {
    messages: Record<string, ChatMessage[]>;
    currentScope: ChatScope;
    mailCount: MailCount | null;
    addressBook: AddressBookEntry[];
    loading: boolean;
    error: string | null;

    switchScope: (scope: ChatScope) => void;
    addMessage: (scopeKey: string, message: ChatMessage) => void;
    loadHistory: (sessionId: number, scope: ChatScope, scopeId: number) => Promise<void>;
    loadMailCount: (sessionId: number, generalId: number) => Promise<void>;
    loadAddressBook: (sessionId: number, generalId: number) => Promise<void>;
    searchOfficers: (sessionId: number, query: string) => Promise<AddressBookEntry[]>;
    reset: () => void;
}

function scopeKey(scope: ChatScope, scopeId: number): string {
    return `${scope}:${scopeId}`;
}

export const useChatStore = create<ChatState>((set, get) => ({
    messages: {},
    currentScope: 'GLOBAL',
    mailCount: null,
    addressBook: [],
    loading: false,
    error: null,

    switchScope: (scope: ChatScope) => set({ currentScope: scope }),

    addMessage: (key: string, message: ChatMessage) => {
        const current = get().messages[key] ?? [];
        set({
            messages: {
                ...get().messages,
                [key]: [...current, message].slice(-200), // Keep last 200 messages
            },
        });
    },

    loadHistory: async (sessionId: number, scope: ChatScope, scopeId: number) => {
        set({ loading: true, error: null });
        try {
            const { data } = await chatApi.getChatHistory(sessionId, scope, scopeId);
            const key = scopeKey(scope, scopeId);
            set({
                messages: { ...get().messages, [key]: data.reverse() },
                loading: false,
            });
        } catch (err) {
            set({ error: 'Failed to load chat history', loading: false });
        }
    },

    loadMailCount: async (sessionId: number, generalId: number) => {
        try {
            const { data } = await chatApi.getMailCount(sessionId, generalId);
            set({ mailCount: data });
        } catch (err) {
            console.error('Failed to load mail count', err);
        }
    },

    loadAddressBook: async (sessionId: number, generalId: number) => {
        try {
            const { data } = await chatApi.getAddressBook(sessionId, generalId);
            set({ addressBook: data });
        } catch (err) {
            console.error('Failed to load address book', err);
        }
    },

    searchOfficers: async (sessionId: number, query: string) => {
        try {
            const { data } = await chatApi.searchOfficers(sessionId, query);
            return data;
        } catch (err) {
            console.error('Failed to search officers', err);
            return [];
        }
    },

    reset: () => set({
        messages: {},
        currentScope: 'GLOBAL',
        mailCount: null,
        addressBook: [],
        loading: false,
        error: null,
    }),
}));

export { scopeKey };
