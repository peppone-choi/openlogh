import axios from 'axios';
import type { ChatMessage, ChatScope, MailCount, AddressBookEntry } from '@/types/chat';

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
});

export const chatApi = {
    /** Get chat history for a scope */
    getChatHistory: (sessionId: number, scope: ChatScope, scopeId: number, limit = 50) =>
        api.get<ChatMessage[]>(`/api/v1/world/${sessionId}/chat/history`, {
            params: { scope, scopeId, limit },
        }),

    /** Get mailbox counts */
    getMailCount: (sessionId: number, generalId: number) =>
        api.get<MailCount>(`/api/v1/world/${sessionId}/mail/count`, {
            params: { generalId },
        }),

    /** Get address book */
    getAddressBook: (sessionId: number, generalId: number) =>
        api.get<AddressBookEntry[]>(`/api/v1/world/${sessionId}/mail/addressbook`, {
            params: { generalId },
        }),

    /** Search officers by name */
    searchOfficers: (sessionId: number, query: string) =>
        api.get<AddressBookEntry[]>(`/api/v1/world/${sessionId}/mail/search`, {
            params: { q: query },
        }),
};
