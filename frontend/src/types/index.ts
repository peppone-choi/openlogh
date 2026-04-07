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
    pcp: number;
    mcp: number;
    pcpMax: number;
    mcpMax: number;
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
    posX: number;
    posY: number;
    destX?: number | null;
    destY?: number | null;
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
    picture: string | null;
}

/** @deprecated Use AccountDetailedInfo instead */
export type AccountDetailedInfoResponse = AccountDetailedInfo;

export interface RealtimeStatus {
    generalId: number;
    commandPoints: number;
    pcp: number;
    mcp: number;
    pcpMax: number;
    mcpMax: number;
    commandEndTime: string | null;
    remainingSeconds: number;
}

// Scenario
export interface Scenario {
    code: string;
    title: string;
    startYear: number;
    mapName?: string;
    factionCount?: number;
    description?: string;
    formableFleets?: Record<string, number[]>;
    battleLocation?: string;
}

export interface ScenarioFactionInfo {
    name: string;
    color: string;
    description: string;
    systemCount: number;
}

export interface ScenarioCharacterInfo {
    name: string;
    factionName: string;
    factionIndex: number;
}

export interface ScenarioDetailResponse {
    scenario: Scenario;
    factions: ScenarioFactionInfo[];
    originalCharacters: ScenarioCharacterInfo[];
}

// Character creation (LOGH 8-stat)
export interface CreateCharacterRequest {
    name: string;
    nationId: number;
    statMode: '8stat';
    leadership: number;
    command: number;
    intelligence: number;
    politics: number;
    administration: number;
    mobility: number;
    attack: number;
    defense: number;
    origin?: string;
    personality?: string;
    selectPoolId?: number;
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
    autorunUser?: number;
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
    pcp: number;
    mcp: number;
    pcpMax: number;
    mcpMax: number;
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

// Position Card types
export interface PositionCard {
    code: string;
    nameKo: string;
    nameEn: string;
}

// Command table
export interface CommandTableEntry {
    actionCode: string;
    name: string;
    category: string;
    commandGroup?: string; // OPERATIONS | PERSONAL | COMMAND | LOGISTICS | PERSONNEL | POLITICS | INTELLIGENCE
    enabled: boolean;
    reason?: string;
    durationSeconds: number;
    commandPointCost: number;
    poolType?: 'PCP' | 'MCP';
}

// Proposal types
export interface Proposal {
    id: number;
    requesterId: number;
    requesterName: string;
    approverId: number;
    approverName: string;
    actionCode: string;
    args: Record<string, unknown>;
    status: 'pending' | 'approved' | 'rejected' | 'expired';
    reason: string | null;
    createdAt: string;
    resolvedAt: string | null;
}

export interface EligibleApprover {
    officerId: number;
    officerName: string;
    rank: number;
    cards: string[];
}

export interface SubmitProposalRequest {
    approverId: number;
    actionCode: string;
    args?: Record<string, unknown>;
    reason?: string;
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

// 은하 지도 타입
export interface LocationConfig {
    type: 'spot' | 'city';
    model: string;
    scale: number;
}

// Simulator Export
export interface SimulatorExportResult {
    result: boolean;
    reason?: string;
    data?: JsonObject;
}

// ──────────────────────────────────────────────────────────────────────
// LOGH Domain Aliases
// New canonical type names for the LOGH universe.
// These alias the original interfaces so all existing code continues to compile.
// Field-level renames will follow when backend DTOs are confirmed.
// ──────────────────────────────────────────────────────────────────────

/** SessionState (was WorldState) */
export type SessionState = WorldState;

/** Faction (was Nation) */
export type Faction = Nation;

/** Planet (was City) */
export type Planet = City;

/** Officer (was General) */
export type Officer = General;

/** BestOfficer (was BestGeneral) */
export type BestOfficer = BestGeneral;

/** Fleet (was Troop) */
export type Fleet = Troop;

/** FleetWithMembers (was TroopWithMembers) */
export type FleetWithMembers = TroopWithMembers;

/** FleetMemberInfo (was TroopMemberInfo) */
export type FleetMemberInfo = TroopMemberInfo;

/** OfficerTurn (was GeneralTurn) */
export type OfficerTurn = GeneralTurn;

/** FactionTurn (was NationTurn) */
export type FactionTurn = NationTurn;

/** FactionStatistic (was NationStatistic) */
export type FactionStatistic = NationStatistic;

/** FactionPolicyInfo (was NationPolicyInfo) */
export type FactionPolicyInfo = NationPolicyInfo;

/** FactionTypeInfo (was NationTypeInfo) */
export type FactionTypeInfo = NationTypeInfo;

/** OnlineFactionInfo (was OnlineNationInfo) */
export type OnlineFactionInfo = OnlineNationInfo;

/** OfficerFrontInfo (was GeneralFrontInfo) */
export type OfficerFrontInfo = GeneralFrontInfo;

/** FactionFrontInfo (was NationFrontInfo) */
export type FactionFrontInfo = NationFrontInfo;

/** PlanetFrontInfo (was CityFrontInfo) */
export type PlanetFrontInfo = CityFrontInfo;

/** PlanetFactionInfo (was CityNationInfo) */
export type PlanetFactionInfo = CityNationInfo;

/** PlanetOfficerInfo (was CityOfficerInfo) */
export type PlanetOfficerInfo = CityOfficerInfo;

/** PlanetConst (was CityConst) */
export type PlanetConst = CityConst;

/** PublicCachedMapPlanet (was PublicCachedMapCity) */
export type PublicCachedMapPlanet = PublicCachedMapCity;

/** AdminOfficer (was AdminGeneral) */
export type AdminOfficer = AdminGeneral;

/** OfficerLogEntry (was GeneralLogEntry) */
export type OfficerLogEntry = GeneralLogEntry;

/** OfficerLogResult (was GeneralLogResult) */
export type OfficerLogResult = GeneralLogResult;

/** FleetInfo (was TroopInfo) */
export type FleetInfo = TroopInfo;

/** FactionShipsInfo (was NationCrewInfo) */
export type FactionShipsInfo = NationCrewInfo;

/** BattleSimPlanet (was BattleSimCity) */
export type BattleSimPlanet = BattleSimCity;

/** MarketBuySuppliesResponse (was MarketBuyRiceResponse) */
export type MarketBuySuppliesResponse = MarketBuyRiceResponse;

/** MarketSellSuppliesResponse (was MarketSellRiceResponse) */
export type MarketSellSuppliesResponse = MarketSellRiceResponse;

// ──────────────────────────────────────────────────────────────────────
// Unit System (Phase 5 — Organization & Fleet Structure)
// ──────────────────────────────────────────────────────────────────────

export type UnitType = 'FLEET' | 'PATROL' | 'TRANSPORT' | 'GROUND' | 'GARRISON' | 'SOLO';

export type CrewSlotRole = 'COMMANDER' | 'VICE_COMMANDER' | 'CHIEF_OF_STAFF' |
    'STAFF_OFFICER_1' | 'STAFF_OFFICER_2' | 'STAFF_OFFICER_3' |
    'STAFF_OFFICER_4' | 'STAFF_OFFICER_5' | 'STAFF_OFFICER_6' | 'ADJUTANT';

export interface MilitaryUnit {
    id: number;
    sessionId: number;
    leaderOfficerId: number;
    factionId: number;
    name: string;
    unitType: UnitType;
    maxUnits: number;
    currentUnits: number;
    maxCrew: number;
    planetId: number | null;
    createdAt: string;
    crew: CrewMember[];
}

export interface CrewMember {
    officerId: number;
    officerName: string;
    slotRole: CrewSlotRole;
}

export interface FormationCapEntry {
    current: number;
    max: number;
    available: number;
}

export interface FormationCaps {
    caps: Record<string, FormationCapEntry>;
}

export const UNIT_TYPE_INFO: Record<UnitType, { nameKo: string; nameEn: string; maxUnits: number; maxShips: number; maxCrew: number }> = {
    FLEET: { nameKo: '함대', nameEn: 'Fleet', maxUnits: 60, maxShips: 18000, maxCrew: 10 },
    PATROL: { nameKo: '순찰대', nameEn: 'Patrol', maxUnits: 3, maxShips: 900, maxCrew: 3 },
    TRANSPORT: { nameKo: '수송함대', nameEn: 'Transport Fleet', maxUnits: 23, maxShips: 6900, maxCrew: 3 },
    GROUND: { nameKo: '지상함대', nameEn: 'Ground Force', maxUnits: 6, maxShips: 1800, maxCrew: 1 },
    GARRISON: { nameKo: '행성수비대', nameEn: 'Garrison', maxUnits: 10, maxShips: 0, maxCrew: 1 },
    SOLO: { nameKo: '단독함', nameEn: 'Solo', maxUnits: 0, maxShips: 1, maxCrew: 0 },
};

export const CREW_SLOT_INFO: Record<CrewSlotRole, { nameKo: string; nameEn: string }> = {
    COMMANDER: { nameKo: '사령관', nameEn: 'Commander' },
    VICE_COMMANDER: { nameKo: '부사령관', nameEn: 'Vice Commander' },
    CHIEF_OF_STAFF: { nameKo: '참모장', nameEn: 'Chief of Staff' },
    STAFF_OFFICER_1: { nameKo: '참모1', nameEn: 'Staff Officer 1' },
    STAFF_OFFICER_2: { nameKo: '참모2', nameEn: 'Staff Officer 2' },
    STAFF_OFFICER_3: { nameKo: '참모3', nameEn: 'Staff Officer 3' },
    STAFF_OFFICER_4: { nameKo: '참모4', nameEn: 'Staff Officer 4' },
    STAFF_OFFICER_5: { nameKo: '참모5', nameEn: 'Staff Officer 5' },
    STAFF_OFFICER_6: { nameKo: '참모6', nameEn: 'Staff Officer 6' },
    ADJUTANT: { nameKo: '부관', nameEn: 'Adjutant' },
};

// ──────────────────────────────────────────────────────────────────────
// Logistics System (병참 시스템 — Warehouse & Supply Chain)
// ──────────────────────────────────────────────────────────────────────

export type ShipClassType = 'BATTLESHIP' | 'CRUISER' | 'DESTROYER' | 'CARRIER' | 'TRANSPORT' | 'HOSPITAL';

export type CrewProficiency = 'GREEN' | 'NORMAL' | 'VETERAN' | 'ELITE';

export interface PlanetWarehouse {
    id: number;
    sessionId: number;
    planetId: number;
    battleship: number;
    cruiser: number;
    destroyer: number;
    carrier: number;
    transport: number;
    hospital: number;
    crewGreen: number;
    crewNormal: number;
    crewVeteran: number;
    crewElite: number;
    supplies: number;
    missiles: number;
    hasShipyard: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface FleetWarehouse {
    id: number;
    sessionId: number;
    fleetId: number;
    battleship: number;
    cruiser: number;
    destroyer: number;
    carrier: number;
    transport: number;
    hospital: number;
    crewGreen: number;
    crewNormal: number;
    crewVeteran: number;
    crewElite: number;
    supplies: number;
    missiles: number;
    createdAt: string;
    updatedAt: string;
}

export interface AllocationRequest {
    fleetId: number;
    battleship?: number;
    cruiser?: number;
    destroyer?: number;
    carrier?: number;
    transport?: number;
    hospital?: number;
    crewGreen?: number;
    crewNormal?: number;
    crewVeteran?: number;
    crewElite?: number;
    supplies?: number;
    missiles?: number;
}

export interface ReorganizationRequest {
    toWarehouse: boolean;
    battleship?: number;
    cruiser?: number;
    destroyer?: number;
    carrier?: number;
    transport?: number;
    hospital?: number;
}

export interface ReplenishmentRequest {
    shipClass: ShipClassType;
    shipClassCode: number;
    amount: number;
}

export interface ProductionReport {
    planetId: number;
    planetName: string;
    shipsProduced: number;
    shipClass: ShipClassType;
    crewProduced: number;
    suppliesProduced: number;
}

export const SHIP_CLASS_INFO: Record<ShipClassType, { code: number; nameKo: string; nameEn: string; shipsPerUnit: number }> = {
    BATTLESHIP: { code: 0, nameKo: '전함', nameEn: 'Battleship', shipsPerUnit: 300 },
    CRUISER: { code: 1, nameKo: '순양함', nameEn: 'Cruiser', shipsPerUnit: 300 },
    DESTROYER: { code: 2, nameKo: '구축함', nameEn: 'Destroyer', shipsPerUnit: 300 },
    CARRIER: { code: 3, nameKo: '항공모함', nameEn: 'Carrier', shipsPerUnit: 300 },
    TRANSPORT: { code: 4, nameKo: '수송함', nameEn: 'Transport', shipsPerUnit: 300 },
    HOSPITAL: { code: 5, nameKo: '병원선', nameEn: 'Hospital Ship', shipsPerUnit: 300 },
};

export const CREW_PROFICIENCY_INFO: Record<CrewProficiency, { level: number; nameKo: string; nameEn: string; combatMultiplier: number }> = {
    GREEN: { level: 0, nameKo: '신병', nameEn: 'Green', combatMultiplier: 0.7 },
    NORMAL: { level: 1, nameKo: '일반', nameEn: 'Normal', combatMultiplier: 1.0 },
    VETERAN: { level: 2, nameKo: '숙련', nameEn: 'Veteran', combatMultiplier: 1.2 },
    ELITE: { level: 3, nameKo: '정예', nameEn: 'Elite', combatMultiplier: 1.5 },
};

// =============================================================
// Mail System - Position Card Mail Addresses (직무 메일주소)
// =============================================================

export interface MailAddress {
    officerId: number;
    officerName: string;
    addressType: 'PERSONAL' | 'POSITION_CARD';
    positionCard: string | null;
    displayName: string;
}

export interface AddressBookEntry {
    officerId: number;
    name: string;
    factionId: number;
    factionName: string;
    rankLevel: number;
    isContact: boolean;
    addressType: string;
    positionCard: string | null;
    positionCardName: string | null;
}

export interface NameCardExchangeResponse {
    success: boolean;
    newAddressesAdded: number;
}

export interface MailboxCounts {
    private: number;
    national: number;
    diplomacy: number;
    privateMax: number;
}

// =============================================================
// Messenger System (메신저 1:1 통화)
// =============================================================

export type MessengerConnectionStatus = 'PENDING' | 'ACTIVE' | 'CANCELLED' | 'DECLINED';

export interface MessengerCallResponse {
    connectionId: number;
    status: MessengerConnectionStatus;
}

export interface MessengerAcceptResponse {
    connectionId: number;
    status: MessengerConnectionStatus;
    callerId: number;
}

export interface MessengerPendingCall {
    connectionId: number;
    callerId: number;
    callerName: string;
    callerFactionId: number;
    createdAt: string;
}

export interface MessengerEvent {
    type: 'INCOMING_CALL' | 'CALL_ACCEPTED' | 'CALL_DECLINED' | 'CALL_CANCELLED' | 'CALL_DISCONNECTED' | 'MESSAGE';
    connectionId: number;
    callerId?: number;
    callerName?: string;
    callerFactionId?: number;
    calleeId?: number;
    calleeName?: string;
    senderId?: number;
    senderName?: string;
    content?: string;
    timestamp?: string;
    reason?: string;
    disconnectedBy?: number;
}

// =============================================================
// Offline Location-Based Processing (오프라인 처리)
// =============================================================

export type OfflineLocation = 'HOME' | 'FLAGSHIP' | 'OTHER';

export interface OfflineStatus {
    location: OfflineLocation;
    canDieInCombat: boolean;
    canBeArrested: boolean;
    subjectToPersonnel: boolean;
    canMove: boolean;
    aiControlled: boolean;
    anchored: boolean;
    description: string;
}

// =============================================================
// Chat Commands (챗 커맨드)
// =============================================================

export interface ChatSystemMessage {
    type: 'SYSTEM';
    content: string;
    timestamp: string;
}

export interface ChatCommandRequest {
    officerId: number;
    content: string;
    scope: string;
    targetOfficerId?: number;
}
