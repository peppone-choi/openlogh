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

// World (SessionState)
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
    starSystemOwnership: { starSystemId: number; factionId: number }[];
    /** @deprecated use starSystemOwnership */
    cityOwnership: { cityId: number; nationId: number }[];
    events: string[];
}

// Faction (formerly Nation)
export interface Faction {
    id: number;
    worldId: number;
    name: string;
    abbreviation: string;
    color: string;
    capitalStarSystemId: number | null;
    /** @deprecated use capitalStarSystemId */
    capitalCityId: number | null;
    funds: number;
    supplies: number;
    tax_rate: number;
    conscription_rate: number;
    rateTmp: number;
    secretLimit: number;
    sovereignId: number;
    /** @deprecated use sovereignId */
    chiefGeneralId: number;
    scoutLevel: number;
    warState: number;
    strategicCmdLimit: number;
    surrenderLimit: number;
    tech_level: number;
    military_power: number;
    faction_rank: number;
    factionRank: number;
    faction_type: string;
    factionType: string;
    taxRate: number;
    conscriptionRate: number;
    techLevel: number;
    militaryPower: number;
    capitalPlanetId: number | null;
    supremeCommanderId: number;
    /** Spy intel map: star system ID (string) → spy level (number) */
    spy: Record<string, number>;
    meta: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use funds */ gold: number;
    /** @deprecated use supplies */ rice: number;
    /** @deprecated use conscription_rate */ rate: number;
    /** @deprecated use tax_rate */ bill: number;
    /** @deprecated use tech_level */ tech: number;
    /** @deprecated use military_power */ power: number;
    /** @deprecated use faction_rank */ level: number;
    /** @deprecated use faction_type */ typeCode: string;
}

/** @deprecated Use Faction */
export type Nation = Faction;

// StarSystem (formerly City)
export interface StarSystem {
    id: number;
    worldId: number;
    name: string;
    level: number;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    supplyState: number;
    frontState: number;
    population: number;
    populationMax: number;
    production: number;
    productionMax: number;
    commerce: number;
    commerceMax: number;
    security: number;
    securityMax: number;
    approval: number;
    trade_route: number;
    tradeRoute: number;
    dead: number;
    orbital_defense: number;
    orbitalDefense: number;
    orbitalDefenseMax: number;
    fortress: number;
    fortressMax: number;
    officerSet: number;
    state: number;
    region: number;
    term: number;
    /** Conflict score map: faction ID (string) → conflict score (number) */
    conflict: Record<string, number>;
    meta: Record<string, unknown>;
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use population */ pop: number;
    /** @deprecated use populationMax */ popMax: number;
    /** @deprecated use approval */ trust: number;
    /** @deprecated use production */ agri: number;
    /** @deprecated use commerce */ comm: number;
    /** @deprecated use security */ secu: number;
    /** @deprecated use orbital_defense */ def: number;
    /** @deprecated use orbitalDefenseMax */ defMax: number;
    /** @deprecated use fortress */ wall: number;
    /** @deprecated use fortressMax */ wallMax: number;
    /** @deprecated use trade_route */ trade: number;
    /** @deprecated use productionMax */ agriMax: number;
    /** @deprecated use commerceMax */ commMax: number;
    /** @deprecated use securityMax */ secuMax: number;
}

/** @deprecated Use StarSystem */
export type City = StarSystem;

export interface LastTurnInfo {
    command: string;
    arg?: Record<string, unknown>;
    term?: number;
}

// Officer (formerly General) — 8-stat system
export interface Officer {
    id: number;
    worldId: number;
    userId: number | null;
    name: string;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    starSystemId: number;
    planetId: number;
    /** @deprecated use starSystemId */
    cityId: number;
    fleetId: number;
    /** @deprecated use fleetId */
    troopId: number;
    npcState: number;
    npcOrg: number | null;
    affinity: number;
    bornYear: number;
    deadYear: number;
    leadership: number;
    leadershipExp: number;
    command: number;
    commandExp: number;
    intelligence: number;
    intelligenceExp: number;
    politics: number;
    politicsExp: number;
    administration: number;
    administrationExp: number;
    mobility: number;
    mobilityExp: number;
    attack: number;
    attackExp: number;
    defense: number;
    defenseExp: number;
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
    funds: number;
    supplies: number;
    ships: number;
    shipClass: number;
    training: number;
    morale: number;
    flagshipCode: string;
    equipCode: string;
    engineCode: string;
    accessoryCode: string;
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
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use command */ strength: number;
    /** @deprecated use intelligence */ intel: number;
    /** @deprecated use administration */ charm: number;
    /** @deprecated use funds */ gold: number;
    /** @deprecated use supplies */ rice: number;
    /** @deprecated use ships */ crew: number;
    /** @deprecated use training */ train: number;
    /** @deprecated use morale */ atmos: number;
    /** @deprecated use shipClass */ crewType: number;
    /** @deprecated use flagshipCode */ weaponCode: string;
    /** @deprecated use equipCode */ bookCode: string;
    /** @deprecated use engineCode */ horseCode: string;
    /** @deprecated use accessoryCode */ itemCode: string;
    /** @deprecated use commandExp */ strengthExp: number;
    /** @deprecated use intelligenceExp */ intelExp: number;
    /** @deprecated use administrationExp */ charmExp: number;
}

/** @deprecated Use Officer */
export type General = Officer;

export interface BestOfficer {
    id: number;
    worldId: number;
    name: string;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    npcState: number;
    picture: string;
    leadership: number;
    command: number;
    intelligence: number;
    politics: number;
    administration: number;
    experience: number;
    dedication: number;
    dex1: number;
    dex2: number;
    dex3: number;
    dex4: number;
    dex5: number;
    meta: Record<string, unknown>;
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use command */ strength: number;
    /** @deprecated use intelligence */ intel: number;
    /** @deprecated use administration */ charm: number;
}

/** @deprecated Use BestOfficer */
export type BestGeneral = BestOfficer;

export interface NpcCard {
    id: number;
    name: string;
    picture: string;
    imageServer: number;
    leadership: number;
    command: number;
    intelligence: number;
    politics: number;
    administration: number;
    factionId: number;
    factionName: string;
    factionColor: string;
    /** @deprecated use factionId */
    nationId: number;
    /** @deprecated use factionName */
    nationName: string;
    /** @deprecated use factionColor */
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
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use command */ strength: number;
    /** @deprecated use intelligence */ intel: number;
    /** @deprecated use administration */ charm: number;
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
    officer: Officer;
    /** @deprecated use officer */
    general: Officer;
}

// Fleet (formerly Troop)
export interface Fleet {
    id: number;
    worldId: number;
    leaderOfficerId: number;
    /** @deprecated use leaderOfficerId */
    leaderGeneralId: number;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    name: string;
    meta: Record<string, unknown>;
    createdAt: string;
    turnTime?: string;
    reservedCommandBrief?: string;
}

/** @deprecated Use Fleet */
export type Troop = Fleet;

// Diplomacy
export interface Diplomacy {
    id: number;
    worldId: number;
    srcFactionId: number;
    /** @deprecated use srcFactionId */
    srcNationId: number;
    destFactionId: number;
    /** @deprecated use destFactionId */
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

export interface YearbookFactionSummary {
    id: number;
    name: string;
    color: string;
    territoryCount: number;
    officerCount: number | null;
    starSystems: string[];
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use officerCount */ generalCount: number | null;
    /** @deprecated use starSystems */ cities: string[];
}

/** @deprecated Use YearbookFactionSummary */
export type YearbookNationSummary = YearbookFactionSummary;

export interface YearbookSummary {
    worldId: number;
    year: number;
    month: number;
    factions: YearbookFactionSummary[];
    /** @deprecated use factions */
    nations: YearbookFactionSummary[];
    globalHistory: string[];
    globalAction: string[];
    keyEvents: Message[];
}

export interface BoardComment {
    id: number;
    authorOfficerId: number;
    /** @deprecated use authorOfficerId */
    authorGeneralId: number;
    content: string;
    createdAt: string;
}

export interface VoteComment {
    id: number;
    authorOfficerId: number;
    /** @deprecated use authorOfficerId */
    authorGeneralId: number;
    content: string;
    createdAt: string;
}

// Command types
export interface OfficerTurn {
    id: number;
    worldId: number;
    officerId: number;
    /** @deprecated use officerId */
    generalId: number;
    turnIdx: number;
    actionCode: string;
    arg: CommandArg;
    brief: string | null;
    createdAt: string;
}

/** @deprecated Use OfficerTurn */
export type GeneralTurn = OfficerTurn;

export interface FactionTurn {
    id: number;
    worldId: number;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    officerLevel: number;
    turnIdx: number;
    actionCode: string;
    arg: CommandArg;
    brief: string | null;
    createdAt: string;
}

/** @deprecated Use FactionTurn */
export type NationTurn = FactionTurn;

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
    activeFactions: number;
    /** @deprecated use activeFactions */
    activeNations: number;
    activeOfficers: number;
    /** @deprecated use activeOfficers */
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
    sellerOfficerId: number;
    /** @deprecated use sellerOfficerId */
    sellerGeneralId: number;
    buyerOfficerId: number | null;
    /** @deprecated use buyerOfficerId */
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
    fundsPerSupplies: number;
    suppliesPerFunds: number;
    /** @deprecated use fundsPerSupplies */
    goldPerRice: number;
    /** @deprecated use suppliesPerFunds */
    ricePerGold: number;
    supply: number;
    demand: number;
}

export interface MarketBuySuppliesResponse {
    success: boolean;
    amount: number;
    costFunds: number;
    fundsPerSupplies: number;
    officerFunds: number;
    officerSupplies: number;
    /** @deprecated use costFunds */ costGold: number;
}

/** @deprecated Use MarketBuySuppliesResponse */
export type MarketBuyRiceResponse = MarketBuySuppliesResponse;

export interface MarketSellSuppliesResponse {
    success: boolean;
    amount: number;
    revenueFunds: number;
    fundsPerSupplies: number;
    officerFunds: number;
    officerSupplies: number;
    /** @deprecated use revenueFunds */ revenueGold: number;
}

/** @deprecated Use MarketSellSuppliesResponse */
export type MarketSellRiceResponse = MarketSellSuppliesResponse;

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
    officerId: number;
    /** @deprecated use officerId */
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
export interface StarSystemConst {
    id: number;
    name: string;
    level: number;
    region: number;
    x: number;
    y: number;
    connections: number[];
}

/** @deprecated Use StarSystemConst */
export type CityConst = StarSystemConst;

export interface MapData {
    starSystems: StarSystemConst[];
    /** @deprecated use starSystems */
    cities: StarSystemConst[];
}

export interface PublicCachedMapStarSystem {
    id: number;
    name: string;
    x: number;
    y: number;
    level: number;
    region?: number;
    factionName: string;
    factionColor: string;
    /** @deprecated use factionName */
    nationName: string;
    /** @deprecated use factionColor */
    nationColor: string;
    isCapital?: boolean;
    supplyState?: number;
    state?: number;
}

/** @deprecated Use PublicCachedMapStarSystem */
export type PublicCachedMapCity = PublicCachedMapStarSystem;

export interface PublicCachedMapHistory {
    id: number;
    sentAt: string;
    text: string;
    year?: number;
    month?: number;
    starSystemOwnership?: { starSystemId: number; factionId: number }[];
    /** @deprecated use starSystemOwnership */
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
    starSystems: PublicCachedMapStarSystem[];
    /** @deprecated use starSystems */
    cities: PublicCachedMapStarSystem[];
    history: PublicCachedMapHistory[];
    worlds?: PublicWorldSummary[];
}

// FrontInfo (main dashboard API response) — legacy parity
export interface FrontInfoResponse {
    global: GlobalInfo;
    officer: OfficerFrontInfo | null;
    /** @deprecated use officer */
    general: OfficerFrontInfo | null;
    faction: FactionFrontInfo | null;
    /** @deprecated use faction */
    nation: FactionFrontInfo | null;
    starSystem: StarSystemFrontInfo | null;
    /** @deprecated use starSystem */
    city: StarSystemFrontInfo | null;
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
    onlineNations: OnlineFactionInfo[];
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

export interface OnlineFactionInfo {
    id: number;
    name: string;
    color: string;
    genCount: number;
}

/** @deprecated Use OnlineFactionInfo */
export type OnlineNationInfo = OnlineFactionInfo;

export interface FactionTypeInfo {
    raw: string;
    name: string;
    pros: string;
    cons: string;
}

/** @deprecated Use FactionTypeInfo */
export type NationTypeInfo = FactionTypeInfo;

export interface TopChiefInfo {
    officerLevel: number;
    no: number;
    name: string;
    npc: number;
}

export interface FactionPopulationInfo {
    cityCnt: number;
    now: number;
    max: number;
}

/** @deprecated Use FactionPopulationInfo */
export type NationPopulationInfo = FactionPopulationInfo;

export interface FactionShipsInfo {
    generalCnt: number;
    now: number;
    max: number;
}

/** @deprecated Use FactionShipsInfo */
export type NationCrewInfo = FactionShipsInfo;

export interface FactionNoticeInfo {
    date: string;
    msg: string;
    author: string;
    authorID: number;
}

/** @deprecated Use FactionNoticeInfo */
export type NationNoticeInfo = FactionNoticeInfo;

export interface FleetInfo {
    leader: { starSystem: number; reservedCommand: CommandArg | null };
    name: string;
}

/** @deprecated Use FleetInfo */
export type TroopInfo = FleetInfo;

export interface OfficerFrontInfo {
    no: number;
    name: string;
    picture: string;
    imgsvr: number;
    faction: number;
    /** @deprecated use faction */
    nation: number;
    npc: number;
    starSystem: number;
    /** @deprecated use starSystem */
    city: number;
    fleet: number;
    /** @deprecated use fleet */
    troop: number;
    officerLevel: number;
    officerLevelText: string;
    officerCity: number;
    permission: number;
    lbonus: number;
    leadership: number;
    leadershipExp: number;
    command: number;
    commandExp: number;
    intelligence: number;
    intelligenceExp: number;
    politics: number;
    politicsExp: number;
    administration: number;
    administrationExp: number;
    experience: number;
    dedication: number;
    explevel: number;
    dedlevel: number;
    honorText: string;
    dedLevelText: string;
    salary: number;
    /** @deprecated use salary */
    bill: number;
    funds: number;
    /** @deprecated use funds */
    gold: number;
    supplies: number;
    /** @deprecated use supplies */
    rice: number;
    ships: number;
    /** @deprecated use ships */
    crew: number;
    shipClass: string;
    /** @deprecated use shipClass */
    crewtype: string;
    training: number;
    /** @deprecated use training */
    train: number;
    morale: number;
    /** @deprecated use morale */
    atmos: number;
    flagship: string;
    /** @deprecated use flagship */
    weapon: string;
    equipment: string;
    /** @deprecated use equipment */
    book: string;
    engine: string;
    /** @deprecated use engine */
    horse: string;
    accessory: string;
    /** @deprecated use accessory */
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
    fleetInfo: FleetInfo | null;
    /** @deprecated use fleetInfo */
    troopInfo: FleetInfo | null;
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
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use command */ strength: number;
    /** @deprecated use intelligence */ intel: number;
    /** @deprecated use administration */ charm: number;
}

/** @deprecated Use OfficerFrontInfo */
export type GeneralFrontInfo = OfficerFrontInfo;

export interface FactionFrontInfo {
    id: number;
    full: boolean;
    name: string;
    color: string;
    level: number;
    faction_type?: string;
    /** @deprecated use faction_type */
    typeCode?: string;
    type: FactionTypeInfo;
    funds: number;
    /** @deprecated use funds */
    gold: number;
    supplies: number;
    /** @deprecated use supplies */
    rice: number;
    tech_level: number;
    /** @deprecated use tech_level */
    tech: number;
    military_power: number;
    /** @deprecated use military_power */
    power: number;
    gennum: number;
    capital: number | null;
    salary_rate: number;
    /** @deprecated use salary_rate */
    bill: number;
    taxRate: number;
    population: FactionPopulationInfo;
    ships: FactionShipsInfo;
    /** @deprecated use ships */
    crew: FactionShipsInfo;
    onlineGen: string;
    notice: FactionNoticeInfo | null;
    topChiefs: Record<number, TopChiefInfo | null>;
    diplomaticLimit: number;
    strategicCmdLimit: number;
    impossibleStrategicCommand: string[];
    prohibitScout: number;
    prohibitWar: number;
}

/** @deprecated Use FactionFrontInfo */
export type NationFrontInfo = FactionFrontInfo;

export interface StarSystemNationInfo {
    id: number;
    name: string;
    color: string;
}

/** @deprecated Use StarSystemNationInfo */
export type CityNationInfo = StarSystemNationInfo;

export interface StarSystemFrontInfo {
    id: number;
    name: string;
    level: number;
    region: number;
    factionInfo: StarSystemNationInfo;
    /** @deprecated use factionInfo */
    nationInfo: StarSystemNationInfo;
    approval: number;
    /** @deprecated use approval */
    trust: number;
    population: number[];
    /** @deprecated use population */
    pop: number[];
    production: number[];
    /** @deprecated use production */
    agri: number[];
    commerce: number[];
    /** @deprecated use commerce */
    comm: number[];
    security: number[];
    /** @deprecated use security */
    secu: number[];
    orbital_defense: number[];
    /** @deprecated use orbital_defense */
    def: number[];
    fortress: number[];
    /** @deprecated use fortress */
    wall: number[];
    trade_route: number | null;
    /** @deprecated use trade_route */
    trade: number | null;
    officerList: Record<number, StarSystemOfficerInfo | null>;
}

/** @deprecated Use StarSystemFrontInfo */
export type CityFrontInfo = StarSystemFrontInfo;

export interface StarSystemOfficerInfo {
    officerLevel: number;
    name: string;
    npc: number;
}

/** @deprecated Use StarSystemOfficerInfo */
export type CityOfficerInfo = StarSystemOfficerInfo;

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
    officerId: number;
    /** @deprecated use officerId */
    generalId: number;
    name: string;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    factionName: string;
    /** @deprecated use factionName */
    nationName: string;
    factionColor?: string;
    /** @deprecated use factionColor */
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
    availableTargetOfficer?: Record<number, string>;
    /** @deprecated use availableTargetOfficer */
    availableTargetGeneral?: Record<number, string>;
    currentStat?: {
        leadership: number;
        command: number;
        intelligence: number;
        statMax: number;
        statMin: number;
        /** @deprecated use command */ strength: number;
        /** @deprecated use intelligence */ intel: number;
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
    /** Faction betting fields (legacy parity) */
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
    officerId: number;
    /** @deprecated use officerId */
    generalId: number;
    targetId: number;
    amount: number;
}

// Battle Simulator
export interface BattleSimUnit {
    name?: string;
    leadership?: number;
    command?: number;
    intelligence?: number;
    ships?: number;
    shipClass?: number;
    training?: number;
    morale?: number;
    flagshipCode?: string;
    equipCode?: string;
    engineCode?: string;
    accessoryCode?: string;
    specialCode?: string;
    personalCode?: string;
    injury?: number;
    supplies?: number;
    dex1?: number;
    dex2?: number;
    dex3?: number;
    dex4?: number;
    dex5?: number;
    defenceTrain?: number;
    officerLevel?: number;
    expLevel?: number;
    // Deprecated field aliases (backward compat with OpenSamguk)
    /** @deprecated use command */ strength?: number;
    /** @deprecated use intelligence */ intel?: number;
    /** @deprecated use ships */ crew?: number;
    /** @deprecated use shipClass */ crewType?: number;
    /** @deprecated use training */ train?: number;
    /** @deprecated use morale */ atmos?: number;
    /** @deprecated use flagshipCode */ weaponCode?: string;
    /** @deprecated use equipCode */ bookCode?: string;
    /** @deprecated use engineCode */ horseCode?: string;
    /** @deprecated use accessoryCode */ itemCode?: string;
    /** @deprecated use supplies */ rice?: number;
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

export interface BattleSimStarSystem {
    orbital_defense?: number;
    fortress?: number;
    level?: number;
    /** @deprecated use orbital_defense */
    def?: number;
    /** @deprecated use fortress */
    wall?: number;
}

/** @deprecated Use BattleSimStarSystem */
export type BattleSimCity = BattleSimStarSystem;

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

// Faction Policy
export interface FactionPolicyInfo {
    conscription_rate: number;
    salary_rate: number;
    /** @deprecated use conscription_rate */
    rate: number;
    /** @deprecated use salary_rate */
    bill: number;
    secretLimit: number;
    strategicCmdLimit: number;
    notice: string;
    scoutMsg: string;
    blockWar: boolean;
    blockScout: boolean;
}

/** @deprecated Use FactionPolicyInfo */
export type NationPolicyInfo = FactionPolicyInfo;

export interface NpcPolicyInfo {
    reqFactionFunds: number;
    reqFactionSupplies: number;
    /** @deprecated */ reqNationGold: number;
    /** @deprecated */ reqNationRice: number;
    reqHumanWarUrgentFunds: number;
    reqHumanWarUrgentSupplies: number;
    /** @deprecated */ reqHumanWarUrgentGold: number;
    /** @deprecated */ reqHumanWarUrgentRice: number;
    reqHumanWarRecommandFunds: number;
    reqHumanWarRecommandSupplies: number;
    /** @deprecated */ reqHumanWarRecommandGold: number;
    /** @deprecated */ reqHumanWarRecommandRice: number;
    reqHumanDevelFunds: number;
    reqHumanDevelSupplies: number;
    /** @deprecated */ reqHumanDevelGold: number;
    /** @deprecated */ reqHumanDevelRice: number;
    reqNPCWarFunds: number;
    reqNPCWarSupplies: number;
    /** @deprecated */ reqNPCWarGold: number;
    /** @deprecated */ reqNPCWarRice: number;
    reqNPCDevelFunds: number;
    reqNPCDevelSupplies: number;
    /** @deprecated */ reqNPCDevelGold: number;
    /** @deprecated */ reqNPCDevelRice: number;
    minimumResourceActionAmount: number;
    maximumResourceActionAmount: number;
    minNPCWarLeadership: number;
    minWarShips: number;
    /** @deprecated use minWarShips */
    minWarCrew: number;
    minNPCRecruitStarSystemPopulation: number;
    /** @deprecated use minNPCRecruitStarSystemPopulation */
    minNPCRecruitCityPopulation: number;
    safeRecruitCityPopulationRatio: number;
    properWarTrainingMorale: number;
    /** @deprecated use properWarTrainingMorale */
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
    factionPriority: string[];
    /** @deprecated use factionPriority */
    nationPriority: string[];
    officerPriority: string[];
    /** @deprecated use officerPriority */
    generalPriority: string[];
    currentFactionPriority: string[];
    /** @deprecated use currentFactionPriority */
    currentNationPriority: string[];
    currentOfficerActionPriority: string[];
    /** @deprecated use currentOfficerActionPriority */
    currentGeneralActionPriority: string[];
    defaultFactionPriority: string[];
    /** @deprecated use defaultFactionPriority */
    defaultNationPriority: string[];
    defaultOfficerActionPriority: string[];
    /** @deprecated use defaultOfficerActionPriority */
    defaultGeneralActionPriority: string[];
    availableFactionPriorityItems: string[];
    /** @deprecated use availableFactionPriorityItems */
    availableNationPriorityItems: string[];
    availableOfficerActionPriorityItems: string[];
    /** @deprecated use availableOfficerActionPriorityItems */
    availableGeneralActionPriorityItems: string[];
    /** NPC auto-play mode (0=off, 1+=active) */
    npcMode?: number;
    /** Last setter info per category */
    lastSetters?: {
        faction?: { setter: string; date: string };
        officer?: { setter: string; date: string };
        policy?: { setter: string; date: string };
        /** @deprecated */ nation?: { setter: string; date: string };
        /** @deprecated */ general?: { setter: string; date: string };
    };
    /** Recent settings change history */
    history?: { setter: string; date: string; action: string; details: string }[];
}

// Officer Info (in star system)
export interface OfficerInfo {
    id: number;
    name: string;
    picture: string;
    officerLevel: number;
    starSystemId: number;
    /** @deprecated use starSystemId */
    cityId: number;
}

/** @deprecated Use OfficerInfo */
export type OfficerInfoLegacy = OfficerInfo;

// Fleet (extended)
export interface FleetMemberInfo {
    id: number;
    name: string;
    picture: string;
}

export interface FleetWithMembers {
    fleet: Fleet;
    members: FleetMemberInfo[];
}

/** @deprecated Use FleetWithMembers */
export type TroopWithMembers = FleetWithMembers;

// Faction Statistic
export interface FactionStatistic {
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    name: string;
    color: string;
    faction_rank: number;
    /** @deprecated use faction_rank */
    level: number;
    funds: number;
    /** @deprecated use funds */
    gold: number;
    supplies: number;
    /** @deprecated use supplies */
    rice: number;
    tech_level: number;
    /** @deprecated use tech_level */
    tech: number;
    military_power: number;
    /** @deprecated use military_power */
    power: number;
    genCount: number;
    starSystemCount: number;
    /** @deprecated use starSystemCount */
    cityCount: number;
    totalShips: number;
    /** @deprecated use totalShips */
    totalCrew: number;
    totalPop: number;
}

/** @deprecated Use FactionStatistic */
export type NationStatistic = FactionStatistic;

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

export interface AdminOfficer {
    id: number;
    name: string;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    ships: number;
    /** @deprecated use ships */
    crew: number;
    experience: number;
    npcState: number;
    blockState: number;
    killTurn: number | null;
}

/** @deprecated Use AdminOfficer */
export type AdminGeneral = AdminOfficer;

export interface SelectPoolEntry {
    id: number;
    worldId: number;
    uniqueName: string;
    ownerId: number | null;
    officerId: number | null;
    /** @deprecated use officerId */
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
    distribute?: { funds: number; supplies: number; target: string };
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
    officerId: number;
    /** @deprecated use officerId */
    generalId: number;
    officerName: string;
    /** @deprecated use officerName */
    generalName: string;
    factionId: number;
    /** @deprecated use factionId */
    nationId: number;
    factionName: string;
    /** @deprecated use factionName */
    nationName: string;
    factionColor: string;
    /** @deprecated use factionColor */
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

// Officer Log (battle center)
export interface OfficerLogEntry {
    id: number;
    message: string;
    date: string;
}

/** @deprecated Use OfficerLogEntry */
export type GeneralLogEntry = OfficerLogEntry;

export interface OfficerLogResult {
    result: boolean;
    reason?: string;
    logs: OfficerLogEntry[];
}

/** @deprecated Use OfficerLogResult */
export type GeneralLogResult = OfficerLogResult;

// Simulator Export
export interface SimulatorExportResult {
    result: boolean;
    reason?: string;
    data?: JsonObject;
}
