export type ChatScope = 'PLANET' | 'FACTION' | 'GLOBAL';

export const CHAT_SCOPE_LABELS: Record<ChatScope, string> = {
    PLANET: '행성',
    FACTION: '진영',
    GLOBAL: '전체',
};

export interface ChatMessage {
    id: number;
    senderId: number;
    senderName: string;
    factionId: number;
    content: string;
    scope: ChatScope;
    timestamp: string;
}

export interface MailCount {
    private: number;
    national: number;
    diplomacy: number;
    privateMax: number;
}

export interface AddressBookEntry {
    officerId: number;
    name: string;
    factionId: number;
    factionName: string;
    rankLevel: number;
    isContact: boolean;
}
