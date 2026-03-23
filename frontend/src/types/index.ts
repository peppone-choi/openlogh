// Auth types
export type JsonValue = unknown;

export type JsonObject = Record<string, unknown>;

export interface User {
    id: number;
    loginId: string;
    displayName: string;
    role?: string;
    picture?: string;
    oauthProviders?: OAuthProviderInfo[];
}

export interface OAuthProviderInfo {
    provider: string;
    linkedAt: string;
    externalId: string;
}

export interface AuthResponse {
    token: string;
    user: User;
    nextToken?: [number, string];
    validUntil?: string;
}

// World
export interface WorldState {
    id: number;
    name: string;
    scenarioCode: string;
    commitSha: string;
    gameVersion: string;
    currentYear: number;
    currentMonth: number;
    tickSeconds: number;
    realtimeMode: boolean;
    commandPointRegenRate: number;
    config: Record<string, unknown>;
    meta: Record<string, unknown>;
    updatedAt: string;
}

export interface WorldSnapshot {
    id: number;
    worldId: number;
    year: number;
    month: number;
    createdAt: string;
    phase?: string;
    season?: string;
    cityOwnership: { cityId: number; nationId: number }[];
    events: string[];
}

// Nation
export interface Nation {
    id: number;
    worldId: number;
    name: string;
    abbreviation: string;
    color: string;
    capitalCityId: number | null;
    gold: number;
    rice: number;
    bill: number;
    rate: number;
    rateTmp: number;
    secretLimit: number;
    chiefGeneralId: number;
    scoutLevel: number;
    warState: number;
    strategicCmdLimit: number;
    surrenderLimit: number;
    tech: number;
    power: number;
    level: number;
    typeCode: string;
    /** Spy intel map: city ID (string) → spy level (number) */
    spy: Record<string, number>;
    meta: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
}

// City
export interface City {
    id: number;
    worldId: number;
    name: string;
    level: number;
    nationId: number;
    supplyState: number;
    frontState: number;
    pop: number;
    popMax: number;
    agri: number;
    agriMax: number;
    comm: number;
    commMax: number;
    secu: number;
    secuMax: number;
    trust: number;
    trade: number;
    dead: number;
    def: number;
    defMax: number;
    wall: number;
    wallMax: number;
    officerSet: number;
    state: number;
    region: number;
    term: number;
    /** Conflict score map: nation ID (string) → conflict score (number) */
    conflict: Record<string, number>;
    meta: Record<string, unknown>;
}

export interface LastTurnInfo {
    command: string;
    arg?: Record<string, unknown>;
    term?: number;
}

// General (5-stat system)
export interface General {
    id: number;
    worldId: number;
    userId: number | null;
    name: string;
    nationId: number;
    cityId: number;
    troopId: number;
    npcState: number;
    npcOrg: number | null;
    affinity: number;
    bornYear: number;
    deadYear: number;
    leadership: number;
    leadershipExp: number;
    strength: number;
    strengthExp: number;
    intel: number;
    intelExp: number;
    politics: number;
    politicsExp: number;
    charm: number;
    charmExp: number;
    dex1: number;
    dex2: number;
    dex3: number;
    dex4: number;
    dex5: number;
    injury: number;
    experience: number;
    dedication: number;
    officerLevel: number;
    officerCity: number;
    gold: number;
    rice: number;
    crew: number;
    crewType: number;
    train: number;
    atmos: number;
    weaponCode: string;
    bookCode: string;
    horseCode: string;
    itemCode: string;
    ownerName: string;
    newmsg: number;
    turnTime: string;
    recentWarTime: string | null;
    makeLimit: number;
    killTurn: number | null;
    age: number;
    startAge: number;
    belong: number;
    betray: number;
    personalCode: string;
    specialCode: string;
    specAge: number;
    special2Code: string;
    spec2Age: number;
    commandPoints: number;
    commandEndTime: string | null;
    lastTurn: LastTurnInfo;
    meta: Record<string, unknown>;
    penalty: Record<string, unknown>;
    picture: string;
    defenceTrain: number;
    tournamentState: number;
    blockState: number;
    permission: string;
    imageServer: number;
    dedLevel: number;
    expLevel: number;
    createdAt: string;
    updatedAt: string;
    warnum?: number;
    killnum?: number;
    deathnum?: number;
    killcrew?: number;
    deathcrew?: number;
    firenum?: number;
    refreshScore?: number;
}

export interface BestGeneral {
    id: number;
    worldId: number;
    name: string;
    nationId: number;
    npcState: number;
    picture: string;
    leadership: number;
    strength: number;
    intel: number;
    politics: number;
    charm: number;
    experience: number;
    dedication: number;
    dex1: number;
    dex2: number;
    dex3: number;
    dex4: number;
    dex5: number;
    meta: Record<string, unknown>;
}

export interface NpcCard {
    id: number;
    name: string;
    picture: string;
    imageServer: number;
    leadership: number;
    strength: number;
    intel: number;
    politics: number;
    charm: number;
    nationId: number;
    nationName: string;
    nationColor: string;
    personality: string;
    special: string;
    special2?: string;
    dex?: number[];
    experience?: number;
    dedication?: number;
    expLevel?: number;
    personalityInfo?: string;
    specialInfo?: string;
    special2Info?: string;
    keepCount?: number;
}

export interface NpcTokenResponse {
    nonce: string;
    npcs: NpcCard[];
    validUntil: string;
    pickMoreAfter: string;
    keepCount: number;
}

export interface SelectNpcResult {
    success: boolean;
    general: General;
}

// Troop
export interface Troop {
    id: number;
    worldId: number;
    leaderGeneralId: number;
    nationId: number;
    name: string;
    meta: Record<string, unknown>;
    createdAt: string;
    turnTime?: string;
    reservedCommandBrief?: string;
}

// Diplomacy
export interface Diplomacy {
    id: number;
    worldId: number;
    srcNationId: number;
    destNationId: number;
    stateCode: string;
    term: number;
    isDead: boolean;
    isShowing: boolean;
}

// Message
export type MailboxType = 'PUBLIC' | 'NATIONAL' | 'PRIVATE' | 'DIPLOMACY';

export interface Message {
    id: number;
    worldId: number;
    mailboxCode: string;
    mailboxType: MailboxType;
    messageType: string;
    srcId: number | null;
    destId: number | null;
    sentAt: string;
    validUntil: string | null;
    payload: Record<string, unknown>;
    meta: Record<string, unknown>;
}

export interface YearbookNationSummary {
    id: number;
    name: string;
    color: string;
    territoryCount: number;
    generalCount: number | null;
    cities: string[];
}

export interface YearbookSummary {
    worldId: number;
    year: number;
    month: number;
    nations: YearbookNationSummary[];
    globalHistory: string[];
    globalAction: string[];
    keyEvents: Message[];
}

export interface BoardComment {
    id: number;
    authorGeneralId: number;
    content: string;
    createdAt: string;
}

export interface VoteComment {
    id: number;
    authorGeneralId: number;
    content: string;
    createdAt: string;
}

// Command types
export interface GeneralTurn {
    id: number;
    worldId: number;
    generalId: number;
    turnIdx: number;
    actionCode: string;
    arg: CommandArg;
    brief: string | null;
    createdAt: string;
}

export interface NationTurn {
    id: number;
    worldId: number;
    nationId: number;
    officerLevel: number;
    turnIdx: number;
    actionCode: string;
    arg: CommandArg;
    brief: string | null;
    createdAt: string;
}

export interface CommandResult {
    success: boolean;
    logs: string[];
    message?: string;
}

export type CommandArg = JsonObject;

export interface WorldSummary {
    id: number;
    name: string;
    scenarioCode: string;
    currentYear: number;
    currentMonth: number;
    season: string;
    phase: string;
    tickSeconds: number;
    realtimeMode: boolean;
    totalPopulation: number;
    activeNations: number;
    activeGenerals: number;
    humanPlayers: number;
    atWar: boolean;
    allowedTechLevel: number;
    mapCode: string;
}

export interface OAuthLinkResponse {
    redirectUrl: string;
}

export interface HallOfFameOptionsResponse {
    seasons: {
        id: number;
        label: string;
        scenarios: { code: string; label: string }[];
    }[];
}

export interface TrafficResponse {
    recentTraffic: {
        year: number;
        month: number;
        refresh: number;
        online: number;
        date: string;
    }[];
    maxRefresh: number;
    maxOnline: number;
    topRefreshers: {
        name: string;
        refresh: number;
        refreshScoreTotal: number;
    }[];
    totalRefresh: number;
    totalRefreshScoreTotal: number;
}

export interface InheritanceOwnerCheckResponse {
    ownerName?: string;
    found: boolean;
}

export interface AuctionActionResponse {
    success: boolean;
    auctionId: number;
    status: string;
}

export interface AuctionHistoryEntry {
    id: number;
    sellerGeneralId: number;
    buyerGeneralId: number | null;
    itemCode: string;
    minPrice: number;
    currentPrice: number;
    status: string;
    createdAt: string;
    expiresAt: string;
}

export interface MarketPriceResponse {
    worldId: number;
    goldPerRice: number;
    ricePerGold: number;
    supply: number;
    demand: number;
}

export interface MarketBuyRiceResponse {
    success: boolean;
    amount: number;
    costGold: number;
    goldPerRice: number;
    generalGold: number;
    generalRice: number;
}

export interface MarketSellRiceResponse {
    success: boolean;
    amount: number;
    revenueGold: number;
    goldPerRice: number;
    generalGold: number;
    generalRice: number;
}

export interface ItemAuctionCreateResponse {
    id: number;
    status: string;
    expiresAt: string;
}

export interface CreateWorldResponse {
    id: number;
}

export interface SystemFlagsResponse {
    allowLogin: boolean;
    allowJoin: boolean;
}

export interface ScrubResponse {
    affected: number;
}

export interface ResetPasswordResponse {
    tempPassword: string;
}

export interface AdminRaiseEventResponse {
    result: boolean;
    reason: string;
    info?: JsonValue;
}

export interface AccountDetailedInfo {
    loginId: string;
    displayName: string;
    grade: number;
    role: string;
    joinDate: string;
    lastLoginAt: string | null;
    thirdUse: boolean;
    oauthType: string | null;
    tokenValidUntil: string | null;
    acl: string | null;
}

/** @deprecated Use AccountDetailedInfo instead */
export type AccountDetailedInfoResponse = AccountDetailedInfo;

export interface RealtimeStatus {
    generalId: number;
    commandPoints: number;
    commandEndTime: string | null;
    remainingSeconds: number;
}

// Scenario
export interface Scenario {
    code: string;
    title: string;
    startYear: number;
}

// Map
export interface CityConst {
    id: number;
    name: string;
    level: number;
    region: number;
    x: number;
    y: number;
    connections: number[];
}

export interface MapData {
    cities: CityConst[];
}

export interface PublicCachedMapCity {
    id: number;
    name: string;
    x: number;
    y: number;
    level: number;
    region?: number;
    nationName: string;
    nationColor: string;
    nationAbbr?: string;
    isCapital?: boolean;
    supplyState?: number;
    state?: number;
}

export interface PublicCachedMapHistory {
    id: number;
    sentAt: string;
    text: string;
    year?: number;
    month?: number;
    cityOwnership?: { cityId: number; nationId: number }[];
    events?: string[];
}

export interface PublicWorldSummary {
    id: number;
    name: string;
}

export interface PublicCachedMapResponse {
    available: boolean;
    worldId: number | null;
    worldName: string | null;
    mapCode: string | null;
    currentYear?: number | null;
    currentMonth?: number | null;
    cities: PublicCachedMapCity[];
    history: PublicCachedMapHistory[];
    worlds?: PublicWorldSummary[];
}

// FrontInfo (main dashboard API response) — legacy parity
export interface FrontInfoResponse {
    global: GlobalInfo;
    general: GeneralFrontInfo | null;
    nation: NationFrontInfo | null;
    city: CityFrontInfo | null;
    recentRecord: RecentRecordInfo;
    aux: AuxInfo;
}

export interface AuxInfo {
    myLastVote?: number | null;
}

export interface GlobalInfo {
    year: number;
    month: number;
    turnTerm: number;
    startyear: number;
    genCount: number[][];
    onlineNations: OnlineNationInfo[];
    onlineUserCnt: number;
    auctionCount: number;
    tournamentState: number;
    tournamentType?: number | null;
    tournamentTime?: string | null;
    isTournamentActive: boolean;
    isTournamentApplicationOpen: boolean;
    isBettingActive: boolean;
    lastExecuted: string | null;
    isLocked: boolean;
    scenarioText: string;
    realtimeMode: boolean;
    extendedGeneral: number;
    isFiction: number;
    npcMode: number;
    joinMode: string;
    develCost: number;
    noticeMsg: number;
    apiLimit: number;
    generalCntLimit: number;
    serverCnt: number;
    lastVoteID: number;
    lastVote: LastVoteInfo | null;
}

export interface LastVoteInfo {
    id: number;
    title: string;
    options: string[];
    votes: Record<string, number>;
}

export interface OnlineNationInfo {
    id: number;
    name: string;
    color: string;
    genCount: number;
}

export interface NationTypeInfo {
    raw: string;
    name: string;
    pros: string;
    cons: string;
}

export interface TopChiefInfo {
    officerLevel: number;
    no: number;
    name: string;
    npc: number;
}

export interface NationPopulationInfo {
    cityCnt: number;
    now: number;
    max: number;
}

export interface NationCrewInfo {
    generalCnt: number;
    now: number;
    max: number;
}

export interface NationNoticeInfo {
    date: string;
    msg: string;
    author: string;
    authorID: number;
}

export interface TroopInfo {
    leader: { city: number; reservedCommand: CommandArg | null };
    name: string;
}

export interface GeneralFrontInfo {
    no: number;
    name: string;
    picture: string;
    imgsvr: number;
    nation: number;
    npc: number;
    city: number;
    troop: number;
    officerLevel: number;
    officerLevelText: string;
    officerCity: number;
    permission: number;
    lbonus: number;
    leadership: number;
    leadershipExp: number;
    strength: number;
    strengthExp: number;
    intel: number;
    intelExp: number;
    politics: number;
    politicsExp: number;
    charm: number;
    charmExp: number;
    experience: number;
    dedication: number;
    explevel: number;
    dedlevel: number;
    honorText: string;
    dedLevelText: string;
    bill: number;
    gold: number;
    rice: number;
    crew: number;
    crewtype: string;
    train: number;
    atmos: number;
    weapon: string;
    book: string;
    horse: string;
    item: string;
    personal: string;
    specialDomestic: string;
    specialWar: string;
    specage: number;
    specage2: number;
    age: number;
    injury: number;
    killturn: number | null;
    belong: number;
    betray: number;
    blockState: number;
    defenceTrain: number;
    turntime: string;
    recentWar: string | null;
    commandPoints: number;
    commandEndTime: string | null;
    ownerName: string | null;
    refreshScoreTotal: number | null;
    refreshScore: number | null;
    autorunLimit: number;
    reservedCommand: CommandArg | null;
    troopInfo: TroopInfo | null;
    dex1: number;
    dex2: number;
    dex3: number;
    dex4: number;
    dex5: number;
    warnum: number;
    killnum: number;
    deathnum: number;
    killcrew: number;
    deathcrew: number;
    firenum: number;
}

export interface NationFrontInfo {
    id: number;
    full: boolean;
    name: string;
    color: string;
    level: number;
    typeCode?: string;
    type: NationTypeInfo;
    gold: number;
    rice: number;
    tech: number;
    power: number;
    gennum: number;
    capital: number | null;
    bill: number;
    taxRate: number;
    population: NationPopulationInfo;
    crew: NationCrewInfo;
    onlineGen: string;
    notice: NationNoticeInfo | null;
    topChiefs: Record<number, TopChiefInfo | null>;
    diplomaticLimit: number;
    strategicCmdLimit: number;
    impossibleStrategicCommand: string[];
    prohibitScout: number;
    prohibitWar: number;
}

export interface CityNationInfo {
    id: number;
    name: string;
    color: string;
}

export interface CityFrontInfo {
    id: number;
    name: string;
    level: number;
    region: number;
    nationInfo: CityNationInfo;
    trust: number;
    pop: number[];
    agri: number[];
    comm: number[];
    secu: number[];
    def: number[];
    wall: number[];
    trade: number | null;
    officerList: Record<number, CityOfficerInfo | null>;
}

export interface CityOfficerInfo {
    officerLevel: number;
    name: string;
    npc: number;
}

export interface RecentRecordInfo {
    flushGeneral: boolean;
    flushGlobal: boolean;
    flushHistory: boolean;
    general: RecordEntry[];
    global: RecordEntry[];
    history: RecordEntry[];
}

export interface RecordEntry {
    id: number;
    message: string;
    date: string;
}

// Command table
export interface CommandTableEntry {
    actionCode: string;
    name: string;
    category: string;
    enabled: boolean;
    reason?: string;
    durationSeconds: number;
    commandPointCost: number;
}

// Contact
export interface ContactInfo {
    generalId: number;
    name: string;
    nationId: number;
    nationName: string;
    nationColor?: string;
    picture: string;
}

// Inheritance
export type InheritBuffType =
    | 'warAvoidRatio'
    | 'warCriticalRatio'
    | 'warMagicTrialProb'
    | 'warAvoidRatioOppose'
    | 'warCriticalRatioOppose'
    | 'warMagicTrialProbOppose'
    | 'domesticSuccessProb'
    | 'domesticFailProb';

export type InheritancePointCategory =
    | 'previous'
    | 'lived_month'
    | 'max_belong'
    | 'max_domestic_critical'
    | 'active_action'
    | 'combat'
    | 'sabotage'
    | 'unifier'
    | 'dex'
    | 'tournament'
    | 'betting';

export interface InheritanceActionCost {
    buff: number[];
    resetTurnTime: number;
    resetSpecialWar: number;
    randomUnique: number;
    nextSpecial: number;
    minSpecificUnique: number;
    checkOwner: number;
    bornStatPoint: number;
}

export interface InheritanceInfo {
    points: number;
    previousPoints?: number;
    newPoints?: number;
    pointSources?: { label: string; amount: number }[];
    pointBreakdown?: Record<InheritancePointCategory, number>;
    buffs: Record<string, number>;
    inheritBuff?: Record<InheritBuffType, number>;
    maxInheritBuff?: number;
    log: InheritanceLogEntry[];
    turnResetCount?: number;
    specialWarResetCount?: number;
    inheritActionCost?: InheritanceActionCost;
    availableSpecialWar?: Record<string, { title: string; info: string }>;
    availableUnique?: Record<string, { title: string; rawName: string; info: string }>;
    availableTargetGeneral?: Record<number, string>;
    currentStat?: {
        leadership: number;
        strength: number;
        intel: number;
        statMax: number;
        statMin: number;
    };
}

export interface InheritanceLogEntry {
    id?: number;
    action: string;
    amount: number;
    date: string;
    text?: string;
}

export interface InheritanceActionResult {
    remainingPoints?: number;
    newLevel?: number;
    error?: string;
}

export interface InheritanceLogResponse {
    log: InheritanceLogEntry[];
}

// Tournament
export interface TournamentInfo {
    state: number;
    bracket: TournamentBracketMatch[];
    participants: number[];
}

export interface TournamentBracketMatch {
    round: number;
    match: number;
    p1: number;
    p2: number;
    winner?: number;
}

// Betting
export interface BettingInfo {
    bets: BetEntry[];
    odds: Record<string, number>;
    history?: BettingEventSummary[];
    /** Nation betting fields (legacy parity) */
    selectCnt?: number;
    isExclusive?: boolean;
    reqInheritancePoint?: boolean;
    candidates?: Record<string, { title: string; info?: string; isHtml?: boolean }>;
    finished?: boolean;
    winner?: number[];
    remainPoint?: number;
    closeYearMonth?: number;
    openYearMonth?: number;
    name?: string;
}

export interface BettingEventSummary {
    yearMonth: string;
    tournamentType: number;
    championId?: number;
    championName?: string;
    totalPool: number;
    participantCount: number;
}

export interface BetEntry {
    generalId: number;
    targetId: number;
    amount: number;
}

// Battle Simulator
export interface BattleSimUnit {
    name?: string;
    leadership?: number;
    strength?: number;
    intel?: number;
    crew?: number;
    crewType?: number;
    train?: number;
    atmos?: number;
    weaponCode?: string;
    bookCode?: string;
    horseCode?: string;
    itemCode?: string;
    specialCode?: string;
    personalCode?: string;
    injury?: number;
    rice?: number;
    dex1?: number;
    dex2?: number;
    dex3?: number;
    dex4?: number;
    dex5?: number;
    defenceTrain?: number;
    officerLevel?: number;
    expLevel?: number;
    inheritBuff?: {
        warAvoidRatio?: number;
        warCriticalRatio?: number;
        warMagicTrialProb?: number;
        warAvoidRatioOppose?: number;
        warCriticalRatioOppose?: number;
        warMagicTrialProbOppose?: number;
    };
}

export interface BattleSimRepeatResult {
    totalRuns: number;
    attackerWins: number;
    defenderWins: number;
    draws: number;
    attackerWinRate: number;
    avgAttackerRemaining: number;
    avgDefenderRemaining: number;
    avgRounds: number;
}

export interface BattleSimCity {
    def?: number;
    wall?: number;
    level?: number;
}

export interface BattleSimResult {
    winner: string;
    attackerRemaining: number;
    defenderRemaining: number;
    rounds: number;
    logs: string[];
    terrain?: string;
    weather?: string;
    defendersRemaining?: number[];
}

export interface BattleSimResponse extends BattleSimResult {
    repeatSummary?: BattleSimRepeatResult;
}

// Nation Policy
export interface NationPolicyInfo {
    rate: number;
    bill: number;
    secretLimit: number;
    strategicCmdLimit: number;
    notice: string;
    scoutMsg: string;
    blockWar: boolean;
    blockScout: boolean;
}

export interface NpcPolicyInfo {
    reqNationGold: number;
    reqNationRice: number;
    reqHumanWarUrgentGold: number;
    reqHumanWarUrgentRice: number;
    reqHumanWarRecommandGold: number;
    reqHumanWarRecommandRice: number;
    reqHumanDevelGold: number;
    reqHumanDevelRice: number;
    reqNPCWarGold: number;
    reqNPCWarRice: number;
    reqNPCDevelGold: number;
    reqNPCDevelRice: number;
    minimumResourceActionAmount: number;
    maximumResourceActionAmount: number;
    minNPCWarLeadership: number;
    minWarCrew: number;
    minNPCRecruitCityPopulation: number;
    safeRecruitCityPopulationRatio: number;
    properWarTrainAtmos: number;
    cureThreshold: number;
    combatForce: Record<number, [number, number]>;
    supportForce: number[];
    developForce: number[];
    CombatForce: Record<number, number[]>;
    SupportForce: number[];
    DevelopForce: number[];
    zeroPolicy: Partial<Record<keyof NpcPolicyInfo, number>>;
    defaultStatMax: number;
    defaultStatNPCMax: number;
    priority: string[];
    nationPriority: string[];
    generalPriority: string[];
    currentNationPriority: string[];
    currentGeneralActionPriority: string[];
    defaultNationPriority: string[];
    defaultGeneralActionPriority: string[];
    availableNationPriorityItems: string[];
    availableGeneralActionPriorityItems: string[];
    /** NPC auto-play mode (0=off, 1+=active) */
    npcMode?: number;
    /** Last setter info per category */
    lastSetters?: {
        nation?: { setter: string; date: string };
        general?: { setter: string; date: string };
        policy?: { setter: string; date: string };
    };
    /** Recent settings change history */
    history?: { setter: string; date: string; action: string; details: string }[];
}

// Officer
export interface OfficerInfo {
    id: number;
    name: string;
    picture: string;
    officerLevel: number;
    cityId: number;
}

// Troop (extended)
export interface TroopMemberInfo {
    id: number;
    name: string;
    picture: string;
}

export interface TroopWithMembers {
    troop: Troop;
    members: TroopMemberInfo[];
}

// Nation Statistic
export interface NationStatistic {
    nationId: number;
    name: string;
    color: string;
    level: number;
    gold: number;
    rice: number;
    tech: number;
    power: number;
    genCount: number;
    cityCount: number;
    totalCrew: number;
    totalPop: number;
}

export interface AuctionBidResponse {
    success: boolean;
    currentPrice?: number;
    message?: string;
}

export interface AccountSettings {
    defenceTrain?: number;
    tournamentState?: number;
    potionThreshold?: number;
    preRiseDelete?: boolean;
    preOpenDelete?: boolean;
    borderReturn?: boolean;
    customCss?: string;
    autoNationTurn?: boolean;
    thirdUse?: boolean;
    picture?: string;
}

// Admin
export interface AdminDashboard {
    worldCount: number;
    currentWorld: AdminWorldInfo | null;
}

export interface AdminWorldInfo {
    id: number;
    year: number;
    month: number;
    scenarioCode: string;
    realtimeMode: boolean;
    tickSeconds: number;
    commandPointRegenRate: number;
    config: Record<string, unknown>;
}

export interface AdminUser {
    id: number;
    loginId: string;
    displayName: string;
    role: string;
    grade: number;
    createdAt: string;
    lastLoginAt: string | null;
}

export interface AdminGeneral {
    id: number;
    name: string;
    nationId: number;
    crew: number;
    experience: number;
    npcState: number;
    blockState: number;
    killTurn: number | null;
}

export interface SelectPoolEntry {
    id: number;
    worldId: number;
    uniqueName: string;
    ownerId: number | null;
    generalId: number | null;
    reservedUntil: string | null;
    info: Record<string, unknown>;
    createdAt: string;
}

export interface TimeControlRequest {
    year?: number;
    month?: number;
    startYear?: number;
    locked?: boolean;
    turnTerm?: number;
    distribute?: { gold: number; rice: number; target: string };
    auctionSync?: boolean;
    auctionCloseMinutes?: number;
    opentime?: string;
    startTime?: string;
    reserveOpen?: string;
    preReserveOpen?: string;
}

// Admin World List Entry
export interface AdminWorldListEntry {
    id: number;
    scenarioCode: string;
    year: number;
    month: number;
    locked: boolean;
}

// Unique Item Owner
export interface UniqueItemOwnerInfo {
    slot: string;
    slotLabel: string;
    generalId: number;
    generalName: string;
    nationId: number;
    nationName: string;
    nationColor: string;
    itemName: string;
    itemGrade: string;
}

// Game Version (Admin)
export interface GameVersionInfo {
    commitSha: string;
    gameVersion: string;
    jarPath: string;
    port: number;
    worldIds: number[];
    alive: boolean;
    pid: number;
    baseUrl: string;
    containerId: string | null;
    imageTag: string | null;
}

// Turn Daemon
export interface TurnStatus {
    state: string;
    reason?: string;
    requestId?: string;
}

export interface TurnRunResult {
    result: string;
}

export interface TurnStateResult {
    state: string;
}

// Game Record (returned by /api/records/*)
export interface GameRecord {
    id: number;
    worldId: number;
    recordType: string;
    srcId: number | null;
    destId: number | null;
    year: number;
    month: number;
    payload: Record<string, unknown>;
    createdAt: string;
}

// General Log (battle center)
export interface GeneralLogEntry {
    id: number;
    message: string;
    date: string;
}

export interface GeneralLogResult {
    result: boolean;
    reason?: string;
    logs: GeneralLogEntry[];
}

// Simulator Export
export interface SimulatorExportResult {
    result: boolean;
    reason?: string;
    data?: JsonObject;
}
