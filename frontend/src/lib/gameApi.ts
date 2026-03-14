import api from './api';
import type {
    WorldState,
    WorldSnapshot,
    Nation,
    City,
    General,
    Troop,
    Diplomacy,
    Message,
    GeneralTurn,
    NationTurn,
    CommandResult,
    CommandTableEntry,
    Scenario,
    MapData,
    FrontInfoResponse,
    ContactInfo,
    InheritanceInfo,
    InheritanceActionResult,
    InheritanceLogResponse,
    TournamentInfo,
    BettingInfo,
    BettingEventSummary,
    BattleSimUnit,
    BattleSimCity,
    BattleSimResponse,
    NationPolicyInfo,
    NpcPolicyInfo,
    OfficerInfo,
    TroopWithMembers,
    NationStatistic,
    AdminDashboard,
    AdminUser,
    AdminGeneral,
    AdminWorldListEntry,
    RealtimeStatus,
    AuctionBidResponse,
    AccountSettings,
    MailboxType,
    NpcTokenResponse,
    SelectNpcResult,
    BestGeneral,
    YearbookSummary,
    BoardComment,
    VoteComment,
    PublicCachedMapResponse,
    CommandArg,
    OAuthLinkResponse,
    HallOfFameOptionsResponse,
    TrafficResponse,
    InheritanceOwnerCheckResponse,
    AuctionActionResponse,
    AuctionHistoryEntry,
    MarketPriceResponse,
    MarketBuyRiceResponse,
    MarketSellRiceResponse,
    ItemAuctionCreateResponse,
    CreateWorldResponse,
    SystemFlagsResponse,
    ScrubResponse,
    ResetPasswordResponse,
    AdminRaiseEventResponse,
    AccountDetailedInfoResponse,
    TimeControlRequest,
    UniqueItemOwnerInfo,
    GameVersionInfo,
    GeneralLogEntry,
    GeneralLogResult,
    SimulatorExportResult,
    JsonObject,
    JsonValue,
    TurnStatus,
    TurnRunResult,
    TurnStateResult,
    WorldSummary,
    SelectPoolEntry,
} from '@/types';

// World API
export const worldApi = {
    list: () => api.get<WorldState[]>('/worlds'),
    get: (id: number) => api.get<WorldState>(`/worlds/${id}`),
    create: (payload: {
        scenarioCode: string;
        name?: string;
        tickSeconds?: number;
        commitSha?: string;
        gameVersion?: string;
    }) => api.post<WorldState>('/worlds', payload),
    delete: (id: number) => api.delete<void>(`/worlds/${id}`),
    reset: (id: number, scenarioCode?: string) =>
        api.post<WorldState>(`/worlds/${id}/reset`, scenarioCode ? { scenarioCode } : {}),
    getSnapshots: (id: number) => api.get<WorldSnapshot[]>(`/worlds/${id}/snapshots`),
    captureSnapshot: (id: number) => api.post<WorldSnapshot>(`/worlds/${id}/snapshots/capture`),
    activate: (
        id: number,
        payload?: {
            commitSha?: string;
            gameVersion?: string;
            jarPath?: string;
            port?: number;
            javaCommand?: string;
        }
    ) => api.post<void>(`/worlds/${id}/activate`, payload ?? {}),
    deactivate: (id: number) => api.post<void>(`/worlds/${id}/deactivate`),
    getSummary: (id: number) => api.get<WorldSummary>(`/worlds/${id}/summary`),
};

// Nation API
export const nationApi = {
    listByWorld: (worldId: number) => api.get<Nation[]>(`/worlds/${worldId}/nations`),
    get: (id: number) => api.get<Nation>(`/nations/${id}`),
};

// City API
export const cityApi = {
    listByWorld: (worldId: number) => api.get<City[]>(`/worlds/${worldId}/cities`),
    listVisibleByWorld: (worldId: number) => api.get<City[]>(`/worlds/${worldId}/cities/visible`),
    get: (id: number) => api.get<City>(`/cities/${id}`),
    listByNation: (nationId: number) => api.get<City[]>(`/nations/${nationId}/cities`),
};

// General API
export const generalApi = {
    listByWorld: (worldId: number) => api.get<General[]>(`/worlds/${worldId}/generals`),
    get: (id: number) => api.get<General>(`/generals/${id}`),
    getMine: (worldId: number) => api.get<General>(`/worlds/${worldId}/generals/me`),
    listByNation: (nationId: number) => api.get<General[]>(`/nations/${nationId}/generals`),
    selectNpc: (worldId: number, generalId: number) =>
        api.post<General>(`/worlds/${worldId}/select-npc`, { generalId }),
    listPool: (worldId: number) => api.get<General[]>(`/worlds/${worldId}/pool`),
    selectFromPool: (worldId: number, generalId: number) =>
        api.post<General>(`/worlds/${worldId}/select-pool`, { generalId }),
    buildPoolGeneral: (
        worldId: number,
        payload: {
            name: string;
            leadership: number;
            strength: number;
            intel: number;
            politics: number;
            charm: number;
            ego?: string;
            personality?: string;
        }
    ) => api.post<General>(`/worlds/${worldId}/pool`, payload),
    updatePoolGeneral: (
        worldId: number,
        generalId: number,
        stats: {
            leadership: number;
            strength: number;
            intel: number;
            politics: number;
            charm: number;
        }
    ) => api.put<General>(`/worlds/${worldId}/pool/${generalId}`, stats),
    create: (worldId: number, payload: Record<string, unknown>) =>
        api.post<General>(`/worlds/${worldId}/generals`, payload),
    listAvailableNpcs: (worldId: number) => api.get<General[]>(`/worlds/${worldId}/available-npcs`),
    listByCity: (cityId: number) => api.get<General[]>(`/cities/${cityId}/generals`),
};

export const npcTokenApi = {
    generate: (worldId: number) => api.post<NpcTokenResponse>(`/worlds/${worldId}/npc-token`),
    refresh: (worldId: number, nonce: string, keepIds: number[]) =>
        api.post<NpcTokenResponse>(`/worlds/${worldId}/npc-token/refresh`, {
            nonce,
            keepIds,
        }),
    select: (worldId: number, nonce: string, generalId: number) =>
        api.post<SelectNpcResult>(`/worlds/${worldId}/npc-select`, {
            nonce,
            generalId,
        }),
};

// Command API
export const commandApi = {
    getReservedCommands: (generalId: number) => api.get<GeneralTurn[]>(`/generals/${generalId}/turns`),
    reserveCommand: (generalId: number, payload: { turn: number; command: string; arg?: CommandArg }) =>
        api.post<GeneralTurn[]>(`/generals/${generalId}/turns`, {
            turns: [
                {
                    turnIdx: payload.turn,
                    actionCode: payload.command,
                    arg: payload.arg,
                },
            ],
        }),
    deleteReservedCommand: (generalId: number, turn: number) =>
        api.post<GeneralTurn[]>(`/generals/${generalId}/turns`, {
            turns: [{ turnIdx: turn, actionCode: '휴식' }],
        }),
    reserve: (
        generalId: number,
        turns: {
            turnIdx: number;
            actionCode: string;
            arg?: CommandArg;
        }[]
    ) => api.post<GeneralTurn[]>(`/generals/${generalId}/turns`, { turns }),
    execute: (generalId: number, actionCode: string, arg?: CommandArg) =>
        api.post<CommandResult>(`/generals/${generalId}/execute`, {
            actionCode,
            arg,
        }),
    executeNation: (generalId: number, actionCode: string, arg?: CommandArg) =>
        api.post<CommandResult>(`/generals/${generalId}/execute-nation`, {
            actionCode,
            arg,
        }),
    getNationReserved: (nationId: number, officerLevel: number) =>
        api.get<NationTurn[]>(`/nations/${nationId}/turns`, {
            params: { officerLevel },
        }),
    getAllOfficerTurns: async (nationId: number, officerLevels: number[]): Promise<NationTurn[]> => {
        const results = await Promise.all(
            officerLevels.map((lv) =>
                api.get<NationTurn[]>(`/nations/${nationId}/turns`, {
                    params: { officerLevel: lv },
                })
            )
        );
        return results.flatMap((r) => r.data);
    },
    reserveNation: (
        nationId: number,
        generalId: number,
        turns: {
            turnIdx: number;
            actionCode: string;
            arg?: CommandArg;
        }[]
    ) => api.post<NationTurn[]>(`/nations/${nationId}/turns`, { turns }, { params: { generalId } }),
    getCommandTable: (generalId: number) =>
        api.get<Record<string, CommandTableEntry[]>>(`/generals/${generalId}/command-table`),
    getNationCommandTable: (generalId: number) =>
        api.get<Record<string, CommandTableEntry[]>>(`/generals/${generalId}/nation-command-table`),
    repeatTurns: (generalId: number, count?: number) =>
        api.post<GeneralTurn[]>(`/generals/${generalId}/turns/repeat`, { count: count ?? 1 }),
    pushTurns: (generalId: number, amount: number) =>
        api.post<GeneralTurn[]>(`/generals/${generalId}/turns/push`, { amount }),
    repeatNationTurns: (nationId: number, generalId: number, count?: number) =>
        api.post<NationTurn[]>(`/nations/${nationId}/turns/repeat`, { count: count ?? 1 }, { params: { generalId } }),
    pushNationTurns: (nationId: number, generalId: number, amount: number) =>
        api.post<NationTurn[]>(`/nations/${nationId}/turns/push`, { amount }, { params: { generalId } }),
};

export const realtimeApi = {
    execute: (generalId: number, actionCode: string, arg?: CommandArg) =>
        api.post<CommandResult>('/realtime/execute', {
            generalId,
            actionCode,
            arg,
        }),
    getStatus: (generalId: number) => api.get<RealtimeStatus>(`/realtime/status/${generalId}`),
};

// Diplomacy API
export const diplomacyApi = {
    listByWorld: (worldId: number) => api.get<Diplomacy[]>(`/worlds/${worldId}/diplomacy`),
    listByNation: (worldId: number, nationId: number) =>
        api.get<Diplomacy[]>(`/worlds/${worldId}/diplomacy/nation/${nationId}`),
    respond: (worldId: number, messageId: number, action: string, accept: boolean) =>
        api.post(`/worlds/${worldId}/diplomacy/respond`, {
            messageId,
            action,
            accept,
        }),
};

// Message API
export const messageApi = {
    getByType: (
        type: 'public' | 'national' | 'private' | 'diplomacy',
        params: {
            worldId?: number;
            nationId?: number;
            generalId?: number;
            officerLevel?: number;
            beforeId?: number;
            limit?: number;
        }
    ) => api.get<Message[]>('/messages', { params: { type, ...params } }),
    getMine: (generalId: number, sinceId?: number | null, limit?: number) =>
        api.get<Message[]>(`/messages`, {
            params: { generalId, ...(sinceId != null ? { sinceId } : {}), ...(limit != null ? { limit } : {}) },
        }),
    send: (
        worldId: number,
        srcId: number,
        destId: number | null,
        content: string,
        options?: {
            mailboxCode?: string;
            mailboxType?: MailboxType;
            messageType?: string;
            officerLevel?: number;
        }
    ) =>
        api.post<Message>('/messages', {
            worldId,
            mailboxCode: options?.mailboxCode ?? 'personal',
            mailboxType: options?.mailboxType ?? 'PRIVATE',
            messageType: options?.messageType ?? 'personal',
            srcId,
            destId,
            officerLevel: options?.officerLevel,
            payload: { content },
        }),
    getBoard: (worldId: number, nationId: number) =>
        api.get<Message[]>('/messages/board', { params: { worldId, nationId } }),
    postBoard: (worldId: number, srcId: number, nationId: number, content: string, title?: string) =>
        api.post<Message>('/messages', {
            worldId,
            mailboxCode: 'board',
            mailboxType: 'PUBLIC',
            messageType: 'board',
            srcId,
            destId: nationId,
            payload: { content, ...(title ? { title } : {}) },
        }),
    getSecretBoard: (worldId: number, nationId: number) =>
        api.get<Message[]>('/messages/secret-board', {
            params: { worldId, nationId },
        }),
    postSecretBoard: (worldId: number, srcId: number, nationId: number, content: string, title?: string) =>
        api.post<Message>('/messages', {
            worldId,
            mailboxCode: 'secret',
            mailboxType: 'NATIONAL',
            messageType: 'secret',
            srcId,
            destId: nationId,
            payload: { content, ...(title ? { title } : {}) },
        }),
    getContacts: (worldId: number) => api.get<ContactInfo[]>(`/worlds/${worldId}/contacts`),
    respondDiplomacy: (messageId: number, accept: boolean) =>
        api.post<void>(`/messages/${messageId}/diplomacy-respond`, { accept }),
    delete: (id: number) => api.delete<void>(`/messages/${id}`),
    markAsRead: (id: number) => api.patch<void>(`/messages/${id}/read`),
    getRecent: (sequence: number) => api.get<Message[]>('/messages/recent', { params: { sequence } }),
};

// FrontInfo API
export const frontApi = {
    getInfo: (worldId: number, lastRecordId?: number, lastHistoryId?: number) => {
        const params: Record<string, number> = {};
        if (lastRecordId != null) params.lastRecordId = lastRecordId;
        if (lastHistoryId != null) params.lastHistoryId = lastHistoryId;
        return api.get<FrontInfoResponse>(`/worlds/${worldId}/front-info`, {
            params,
        });
    },
};

// History API
export const historyApi = {
    getWorldHistory: (worldId: number) => api.get<Message[]>(`/worlds/${worldId}/history`),
    getWorldHistoryByYearMonth: (worldId: number, year: number, month: number) =>
        api.get<Message[]>(`/worlds/${worldId}/history`, {
            params: { year, month },
        }),
    getYearbook: (worldId: number, year: number) =>
        api.get<YearbookSummary>(`/worlds/${worldId}/history/yearbook`, {
            params: { year },
        }),
    getWorldRecords: (worldId: number) => api.get<Message[]>(`/worlds/${worldId}/records`),
    getGeneralRecords: (generalId: number) => api.get<Message[]>(`/generals/${generalId}/records`),
};

export const generalLogApi = {
    getOldLogs: (
        generalId: number,
        targetId: number,
        type: 'generalHistory' | 'generalAction' | 'battleResult' | 'battleDetail',
        to?: number
    ) =>
        api.get<GeneralLogResult>(`/generals/${generalId}/logs/old`, {
            params: { targetId, type, ...(to ? { to } : {}) },
        }),
};

export const simulatorExportApi = {
    exportGeneral: (generalId: number, targetId: number) =>
        api.get<SimulatorExportResult>(`/generals/${generalId}/simulator-export`, {
            params: { targetId },
        }),
};

// Map Recent API (cached map with history)
export const mapRecentApi = {
    getMapRecent: (worldId: number) => api.get<PublicCachedMapResponse>(`/worlds/${worldId}/map-recent`),
};

export const boardApi = {
    getComments: (postId: number) => api.get<BoardComment[]>(`/boards/${postId}/comments`),
    createComment: (postId: number, authorGeneralId: number, content: string) =>
        api.post<BoardComment>(`/boards/${postId}/comments`, {
            authorGeneralId,
            content,
        }),
    deleteComment: (postId: number, commentId: number, generalId: number) =>
        api.delete<void>(`/boards/${postId}/comments/${commentId}`, {
            params: { generalId },
        }),
};

// Account API
export const accountApi = {
    changePassword: (currentPassword: string, newPassword: string) =>
        api.patch<void>('/account/password', { currentPassword, newPassword }),
    deleteAccount: (password?: string) => api.delete<void>('/account', { data: password ? { password } : undefined }),
    updateSettings: (settings: AccountSettings) => api.patch<void>('/account/settings', settings),
    toggleVacation: () => api.post<void>('/account/vacation'),
    buildNationCandidate: () => api.post<void>('/account/buildNationCandidate'),
    instantRetreat: () => api.post<void>('/account/instantRetreat'),
    dieOnPrestart: () => api.post<void>('/account/dieOnPrestart'),
    getOAuthProviders: () => api.get<import('@/types').OAuthProviderInfo[]>('/account/oauth'),
    linkOAuth: (provider: string) => api.post<OAuthLinkResponse>(`/account/oauth/${provider}/link`),
    completeOAuthLink: async (provider: string, code: string, redirectUri: string) => {
        // Primary API (added parity path)
        try {
            return await api.post<void>(`/account/oauth/${provider}/callback`, {
                code,
                redirectUri,
            });
        } catch {
            // Backward-compatible fallback for alternate route shape
            return api.post<void>(`/account/oauth/${provider}/link/callback`, {
                code,
                redirectUri,
            });
        }
    },
    unlinkOAuth: (provider: string) => api.delete<void>(`/account/oauth/${provider}`),
    uploadIcon: (formData: FormData) =>
        api.post<void>('/account/icon', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        }),
    deleteIcon: () => api.delete<void>('/account/icon'),
    syncIcon: () => api.post<void>('/account/icon/sync'),
    getDetailedInfo: () => api.get<AccountDetailedInfoResponse>('/account/detailed-info'),
};

// Nation Management API
export const nationManagementApi = {
    getOfficers: (nationId: number) => api.get<OfficerInfo[]>(`/nations/${nationId}/officers`),
    appointOfficer: (nationId: number, data: { generalId: number; officerLevel: number; officerCity?: number }) =>
        api.post<void>(`/nations/${nationId}/officers`, data),
    expel: (nationId: number, generalId: number) => api.post<void>(`/nations/${nationId}/expel`, { generalId }),
    setPermission: (nationId: number, data: { requesterId: number; isAmbassador: boolean; generalIds: number[] }) =>
        api.post<void>(`/nations/${nationId}/permissions`, data),
};

// Nation Policy API
export const nationPolicyApi = {
    getPolicy: (nationId: number) => api.get<NationPolicyInfo>(`/nations/${nationId}/policy`),
    updatePolicy: (nationId: number, data: JsonObject) => api.patch<void>(`/nations/${nationId}/policy`, data),
    updateNotice: (nationId: number, notice: string) => api.patch<void>(`/nations/${nationId}/notice`, { notice }),
    updateScoutMsg: (nationId: number, scoutMsg: string) =>
        api.patch<void>(`/nations/${nationId}/scout-msg`, { scoutMsg }),
    setBlockWar: (nationId: number, value: boolean) =>
        api.post<{ result: boolean; reason?: string; availableCnt?: number }>(`/nations/${nationId}/block-war`, {
            value,
        }),
    setBlockScout: (nationId: number, value: boolean) =>
        api.post<{ result: boolean; reason?: string }>(`/nations/${nationId}/block-scout`, { value }),
};

// NPC Policy API (dynamic policy maps - intentionally loose)
export const npcPolicyApi = {
    getPolicy: (nationId: number) => api.get<NpcPolicyInfo>(`/nations/${nationId}/npc-policy`),
    updatePolicy: (nationId: number, policy: Partial<NpcPolicyInfo> & JsonObject) =>
        api.put<void>(`/nations/${nationId}/npc-policy`, policy),
    updatePriority: (nationId: number, priority: Partial<NpcPolicyInfo> & JsonObject) =>
        api.put<void>(`/nations/${nationId}/npc-priority`, priority),
};

// Troop API
export const troopApi = {
    listByNation: (nationId: number) => api.get<TroopWithMembers[]>(`/nations/${nationId}/troops`),
    create: (data: { worldId: number; leaderGeneralId: number; nationId: number; name: string }) =>
        api.post<Troop>('/troops', data),
    join: (troopId: number, generalId: number) => api.post<void>(`/troops/${troopId}/join`, { generalId }),
    exit: (troopId: number, generalId: number) => api.post<void>(`/troops/${troopId}/exit`, { generalId }),
    kick: (troopId: number, generalId: number) => api.post<void>(`/troops/${troopId}/kick`, { generalId }),
    rename: (troopId: number, name: string) => api.patch<Troop>(`/troops/${troopId}`, { name }),
    disband: (troopId: number) => api.delete<void>(`/troops/${troopId}`),
};

// Diplomacy Letter API
export const diplomacyLetterApi = {
    list: (nationId: number) => api.get<Message[]>(`/nations/${nationId}/diplomacy-letters`),
    send: (
        nationId: number,
        data: {
            worldId: number;
            destNationId: number;
            type: string;
            content?: string;
            diplomaticContent?: string;
        }
    ) => api.post<Message>(`/nations/${nationId}/diplomacy-letters`, data),
    respond: (letterId: number, accept: boolean, reason?: string) =>
        api.post<void>(`/diplomacy-letters/${letterId}/respond`, {
            accept,
            reason,
        }),
    execute: (letterId: number) => api.post<void>(`/diplomacy-letters/${letterId}/execute`),
    rollback: (letterId: number) => api.post<void>(`/diplomacy-letters/${letterId}/rollback`),
    destroy: (letterId: number) => api.post<void>(`/diplomacy-letters/${letterId}/destroy`),
};

// Ranking API
export const rankingApi = {
    bestGenerals: (worldId: number, sortBy?: string, limit?: number) => {
        const params: Record<string, string | number> = {};
        if (sortBy) params.sortBy = sortBy;
        if (limit) params.limit = limit;
        return api.get<BestGeneral[]>(`/worlds/${worldId}/best-generals`, {
            params,
        });
    },
    hallOfFame: (worldId: number, params?: { season?: number; scenario?: string }) =>
        api.get<Message[]>(`/worlds/${worldId}/hall-of-fame`, { params }),
    hallOfFameOptions: (worldId: number) =>
        api.get<HallOfFameOptionsResponse>(`/worlds/${worldId}/hall-of-fame/options`),
    uniqueItemOwners: (worldId: number) => api.get<UniqueItemOwnerInfo[]>(`/worlds/${worldId}/unique-item-owners`),
};

// Traffic API
export const trafficApi = {
    getTraffic: (worldId: number) => api.get<TrafficResponse>(`/worlds/${worldId}/traffic`),
};

// Scenario API
export const scenarioApi = {
    list: () => api.get<Scenario[]>('/scenarios'),
};

// Map API
export const mapApi = {
    get: (mapName: string) => api.get<MapData>(`/maps/${mapName}`),
};

// Inheritance API
export const inheritanceApi = {
    getInfo: (worldId: number) => api.get<InheritanceInfo>(`/worlds/${worldId}/inheritance`),
    setSpecial: (worldId: number, specialCode: string) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/special`, { specialCode }),
    setCity: (worldId: number, cityId: number) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/city`, {
            cityId,
        }),
    resetTurn: (worldId: number) => api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/reset-turn`),
    buyRandomUnique: (worldId: number) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/random-unique`),
    resetSpecialWar: (worldId: number) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/reset-special-war`),
    resetStats: (
        worldId: number,
        stats: {
            leadership: number;
            strength: number;
            intel: number;
            inheritBonusStat?: [number, number, number];
        }
    ) => api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/reset-stats`, stats),
    checkOwner: (worldId: number, generalIdOrName: string | number) =>
        api.post<InheritanceOwnerCheckResponse>(
            `/worlds/${worldId}/inheritance/check-owner`,
            typeof generalIdOrName === 'number' ? { destGeneralID: generalIdOrName } : { generalName: generalIdOrName }
        ),
    buyInheritBuff: (worldId: number, data: { type: string; level: number }) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/buy-buff`, data),
    getMoreLog: (worldId: number, lastID: number) =>
        api.get<InheritanceLogResponse>(`/worlds/${worldId}/inheritance/log`, {
            params: { lastID },
        }),
    auctionUnique: (worldId: number, data: { uniqueCode: string; bidAmount: number }) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/auction-unique`, data),
    buy: (worldId: number, payload: { buffCode: string }) =>
        api.post<InheritanceActionResult>(`/worlds/${worldId}/inheritance/buy`, payload),
};

// Auction API
export const auctionApi = {
    list: (worldId: number) => api.get<Message[]>(`/worlds/${worldId}/auctions`),
    create: (
        worldId: number,
        data: {
            type: string;
            sellerId: number;
            item: string;
            amount: number;
            minPrice: number;
            finishBidAmount?: number;
            closeTurnCnt?: number;
        }
    ) => api.post<Message>(`/worlds/${worldId}/auctions`, data),
    bid: (auctionId: number, bidderId: number, amount: number) =>
        api.post<AuctionBidResponse>(`/auctions/${auctionId}/bid`, {
            bidderId,
            amount,
        }),
    cancel: (auctionId: number, generalId: number) =>
        api.post<AuctionActionResponse>(`/auctions/${auctionId}/cancel`, {
            generalId,
        }),
    finalize: (auctionId: number) => api.post<AuctionActionResponse>(`/auctions/${auctionId}/finalize`),
    getHistory: (worldId: number) => api.get<AuctionHistoryEntry[]>(`/worlds/${worldId}/auction-history`),
    getMarketPrice: (worldId: number) => api.get<MarketPriceResponse>(`/worlds/${worldId}/market-price`),
    buyRice: (worldId: number, generalId: number, amount: number) =>
        api.post<MarketBuyRiceResponse>(`/worlds/${worldId}/market/buy-rice`, {
            generalId,
            amount,
        }),
    sellRice: (worldId: number, generalId: number, amount: number) =>
        api.post<MarketSellRiceResponse>(`/worlds/${worldId}/market/sell-rice`, {
            generalId,
            amount,
        }),
    createItemAuction: (worldId: number, generalId: number, itemType: string, startPrice: number) =>
        api.post<ItemAuctionCreateResponse>(`/worlds/${worldId}/item-auctions`, {
            generalId,
            itemType,
            startPrice,
        }),
};

// Item API
export const itemApi = {
    discard: (generalId: number, itemType: string) =>
        api.post<CommandResult>(`/generals/${generalId}/items/discard`, {
            itemType,
        }),
    unequip: (generalId: number, itemType: string) =>
        api.post<CommandResult>(`/generals/${generalId}/items/unequip`, {
            itemType,
        }),
    use: (generalId: number, itemType: string, itemCode: string) =>
        api.post<CommandResult>(`/generals/${generalId}/items/use`, {
            itemType,
            itemCode,
        }),
    equip: (generalId: number, payload: { itemCode: string; itemType?: string }) =>
        api.post<CommandResult>(`/generals/${generalId}/items/equip`, payload),
    give: (generalId: number, payload: { itemType: string; targetGeneralId: number }) =>
        api.post<CommandResult>(`/generals/${generalId}/items/give`, payload),
};

// Tournament API
export const tournamentApi = {
    getInfo: (worldId: number) => api.get<TournamentInfo>(`/worlds/${worldId}/tournament`),
    register: (worldId: number, generalId: number) =>
        api.post<void>(`/worlds/${worldId}/tournament/register`, { generalId }),
    advancePhase: (worldId: number) => api.post<void>(`/worlds/${worldId}/tournament/advance`),
    sendMessage: (worldId: number, message: string) =>
        api.post<void>(`/worlds/${worldId}/tournament/message`, { message }),
    create: (worldId: number, type: number) =>
        api.post<Record<string, unknown>>(`/worlds/${worldId}/tournament`, { type }),
    start: (worldId: number) => api.post<Record<string, unknown>>(`/worlds/${worldId}/tournament/start`),
    finalize: (worldId: number) => api.post<Record<string, unknown>>(`/worlds/${worldId}/tournament/finalize`),
};

// Betting API
export const bettingApi = {
    getInfo: (worldId: number) => api.get<BettingInfo>(`/worlds/${worldId}/betting`),
    getHistory: (worldId: number) => api.get<BettingEventSummary[]>(`/worlds/${worldId}/betting/history`),
    getEvent: (worldId: number, yearMonth: string) => api.get<BettingInfo>(`/worlds/${worldId}/betting/${yearMonth}`),
    placeBet: (worldId: number, generalId: number, targetId: number, amount: number) =>
        api.post<void>(`/worlds/${worldId}/betting`, {
            generalId,
            targetId,
            amount,
        }),
    toggleGate: (worldId: number, open: boolean) => api.post<void>(`/worlds/${worldId}/betting/gate`, { open }),
};

// Vote API
export const voteApi = {
    list: (worldId: number) => api.get<Message[]>(`/worlds/${worldId}/votes`),
    create: (worldId: number, data: { title: string; options: string[]; creatorId: number }) =>
        api.post<Message>(`/worlds/${worldId}/votes`, data),
    cast: (voteId: number, voterId: number, optionIndex: number) =>
        api.post<void>(`/votes/${voteId}/cast`, { voterId, optionIndex }),
    close: (voteId: number) => api.post<void>(`/votes/${voteId}/close`),
    listComments: (voteId: number) => api.get<VoteComment[]>(`/votes/${voteId}/comments`),
    createComment: (voteId: number, authorGeneralId: number, content: string) =>
        api.post<VoteComment>(`/votes/${voteId}/comments`, {
            authorGeneralId,
            content,
        }),
    deleteComment: (voteId: number, commentId: number, generalId: number) =>
        api.delete<void>(`/votes/${voteId}/comments/${commentId}`, {
            params: { generalId },
        }),
};

// Battle Simulator API
export const battleSimApi = {
    simulate: (
        attacker: BattleSimUnit,
        defender: BattleSimUnit,
        defenderCity: BattleSimCity,
        options?: {
            year?: number;
            month?: number;
            seed?: string;
            repeatCount?: number;
        }
    ) =>
        api.post<BattleSimResponse>('/battle/simulate', {
            attacker,
            defender,
            defenderCity,
            ...options,
        }),
};

// Game Version API (Admin)
export const gameVersionApi = {
    list: () => api.get<GameVersionInfo[]>('/admin/game-versions'),
    available: () => api.get<string[]>('/admin/game-versions/available'),
    deploy: (data: { gameVersion: string; imageTag?: string; commitSha?: string }) =>
        api.post<GameVersionInfo>('/admin/game-versions', data),
    stop: (version: string) => api.delete<void>(`/admin/game-versions/${encodeURIComponent(version)}`),
};

// Public API (unauthenticated endpoints)
export const publicApi = {
    getCachedMap: (worldId?: number) =>
        api.get<PublicCachedMapResponse>('/public/cached-map', worldId != null ? { params: { worldId } } : {}),
};

// Helper: attach worldId as query param when provided
const wq = (worldId?: number) => (worldId != null ? { params: { worldId } } : {});

// Admin API
export const adminApi = {
    getDashboard: (worldId?: number) => api.get<AdminDashboard>('/admin/dashboard', wq(worldId)),
    updateSettings: (settings: JsonObject, worldId?: number) =>
        api.patch<void>('/admin/settings', settings, wq(worldId)),
    listGenerals: (worldId?: number) => api.get<AdminGeneral[]>('/admin/generals', wq(worldId)),
    generalAction: (id: number, type: string, worldId?: number) =>
        api.post<void>(`/admin/generals/${id}/action`, { type }, wq(worldId)),
    getStatistics: (worldId?: number) => api.get<NationStatistic[]>('/admin/statistics', wq(worldId)),
    getGeneralLogs: (id: number, worldId?: number) => api.get<Message[]>(`/admin/generals/${id}/logs`, wq(worldId)),
    getDiplomacy: (worldId?: number) => api.get<Diplomacy[]>('/admin/diplomacy', wq(worldId)),
    timeControl: (data: TimeControlRequest, worldId?: number) =>
        api.post<void>('/admin/time-control', data, wq(worldId)),
    listUsers: () => api.get<AdminUser[]>('/admin/users'),
    userAction: (
        id: number,
        action: {
            type:
                | 'setAdmin'
                | 'removeAdmin'
                | 'delete'
                | 'setGrade'
                | 'block'
                | 'unblock'
                | 'extendOauth'
                | 'banPermanent';
            grade?: number;
            days?: number;
            oauthDays?: number;
        }
    ) => api.post<void>(`/admin/users/${id}/action`, action),
    createWorld: (data: {
        scenarioCode: string;
        name?: string;
        tickSeconds?: number;
        notice?: string;
        gameVersion?: string;
    }) => api.post<CreateWorldResponse>('/worlds', data),
    deleteWorld: (worldId: number) => api.delete<void>(`/worlds/${worldId}`),
    listWorlds: () => api.get<AdminWorldListEntry[]>('/admin/worlds'),
    bulkGeneralAction: (ids: number[], type: string, worldId?: number) =>
        api.post<void>('/admin/generals/bulk-action', { ids, type }, wq(worldId)),
    activateWorld: (worldId: number, data?: { gameVersion?: string }) =>
        api.post<void>(`/worlds/${worldId}/activate`, data ?? {}),
    deactivateWorld: (worldId: number) => api.post<void>(`/worlds/${worldId}/deactivate`, {}),
    resetWorld: (worldId: number, scenarioCode?: string, gameVersion?: string) =>
        api.post<void>(`/worlds/${worldId}/reset`, {
            ...(scenarioCode ? { scenarioCode } : {}),
            ...(gameVersion ? { gameVersion } : {}),
        }),
    forceRehall: (worldId?: number) =>
        api.post<{ processedGenerals: number; updatedUsers: number }>('/admin/force-rehall', {}, wq(worldId)),
    writeLog: (message: string, worldId?: number) => api.post<void>('/admin/write-log', { message }, wq(worldId)),

    // Gateway-local admin ops (entrance/system)
    getSystemFlags: () => api.get<SystemFlagsResponse>('/admin/system-flags'),
    patchSystemFlags: (payload: { allowLogin?: boolean; allowJoin?: boolean }) =>
        api.patch<SystemFlagsResponse>('/admin/system-flags', payload),
    scrub: (type: 'scrub_old_user' | 'scrub_blocked_user' | 'scrub_deleted' | 'scrub_icon') =>
        api.post<ScrubResponse>('/admin/scrub', { type }),
    resetPassword: (userId: number) => api.post<ResetPasswordResponse>(`/admin/users/${userId}/reset-password`, {}),

    listSelectPools: (worldId?: number) => api.get<SelectPoolEntry[]>('/admin/select-pool', wq(worldId)),
    createSelectPool: (data: Partial<SelectPoolEntry>, worldId?: number) =>
        api.post<SelectPoolEntry>('/admin/select-pool', data, wq(worldId)),
    bulkCreateSelectPool: (entries: Partial<SelectPoolEntry>[], worldId?: number) =>
        api.post<SelectPoolEntry[]>('/admin/select-pool/bulk', entries, wq(worldId)),
    updateSelectPool: (id: number, data: Partial<SelectPoolEntry>, worldId?: number) =>
        api.put<SelectPoolEntry>(`/admin/select-pool/${id}`, data, wq(worldId)),
    deleteSelectPool: (id: number, worldId?: number) => api.delete<void>(`/admin/select-pool/${id}`, wq(worldId)),
};

// Turn Daemon API (game-app direct — proxied through gateway)
export const turnApi = {
    getStatus: () => api.get<TurnStatus>('/turns/status'),
    run: () => api.post<TurnRunResult>('/turns/run'),
    pause: () => api.post<TurnStateResult>('/turns/pause'),
    resume: () => api.post<TurnStateResult>('/turns/resume'),
};

// Admin Event API (legacy parity: j_raise_event.php)
export const adminEventApi = {
    raise: (event: string, args?: JsonValue[], worldId?: number) =>
        api.post<AdminRaiseEventResponse>('/admin/raise-event', {
            event,
            ...(args ? { args } : {}),
            ...(worldId != null ? { worldId } : {}),
        }),
};
