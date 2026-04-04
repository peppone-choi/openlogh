import type { General } from '@/types';

const BASE_GENERAL: Omit<
    General,
    | 'id'
    | 'name'
    | 'nationId'
    | 'cityId'
    | 'userId'
    | 'npcState'
    | 'leadership'
    | 'strength'
    | 'intel'
    | 'politics'
    | 'charm'
    | 'crew'
    | 'crewType'
    | 'picture'
> = {
    worldId: -1,
    troopId: 0,
    npcOrg: null,
    affinity: 0,
    bornYear: 161,
    deadYear: 300,
    leadershipExp: 0,
    strengthExp: 0,
    intelExp: 0,
    politicsExp: 0,
    charmExp: 0,
    dex1: 50,
    dex2: 50,
    dex3: 50,
    dex4: 50,
    dex5: 50,
    injury: 0,
    experience: 100,
    dedication: 100,
    officerLevel: 12,
    officerCity: 0,
    gold: 1000,
    rice: 1000,
    train: 80,
    atmos: 80,
    weaponCode: 'None',
    bookCode: 'None',
    horseCode: 'None',
    itemCode: 'None',
    ownerName: '',
    newmsg: 0,
    turnTime: new Date().toISOString(),
    recentWarTime: null,
    makeLimit: 0,
    killTurn: null,
    age: 39,
    startAge: 20,
    belong: 1,
    betray: 0,
    personalCode: 'personality_001',
    specialCode: 'None',
    specAge: 0,
    special2Code: 'None',
    spec2Age: 0,
    commandPoints: 10,
    commandEndTime: null,
    lastTurn: { command: 'idle' },
    meta: {},
    penalty: {},
    defenceTrain: 80,
    tournamentState: 0,
    blockState: 0,
    permission: 'normal',
    imageServer: 0,
    dedLevel: 1,
    expLevel: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    posX: 0,
    posY: 0,
};

/** 유저 장수 — 유비 (촉 군주) */
export const MOCK_MY_GENERAL: General = {
    ...BASE_GENERAL,
    id: -1,
    name: '유비',
    userId: -1,
    nationId: -1,
    cityId: -1,
    npcState: 0,
    leadership: 75,
    strength: 70,
    intel: 65,
    politics: 80,
    charm: 95,
    crew: 1000,
    crewType: 1,
    officerLevel: 12,
    picture: 'default',
};

/** NPC 동료 — 관우 */
const MOCK_GUAN_YU: General = {
    ...BASE_GENERAL,
    id: -2,
    name: '관우',
    userId: null,
    nationId: -1,
    cityId: -1,
    npcState: 1,
    leadership: 85,
    strength: 97,
    intel: 75,
    politics: 50,
    charm: 80,
    crew: 2000,
    crewType: 1,
    officerLevel: 8,
    picture: 'default',
};

/** NPC 동료 — 제갈량 */
const MOCK_ZHUGE_LIANG: General = {
    ...BASE_GENERAL,
    id: -3,
    name: '제갈량',
    userId: null,
    nationId: -1,
    cityId: -1,
    npcState: 1,
    leadership: 90,
    strength: 30,
    intel: 99,
    politics: 95,
    charm: 85,
    crew: 1500,
    crewType: 3,
    officerLevel: 5,
    picture: 'default',
};

/** 적국 군주 — 조조 */
const MOCK_CAO_CAO: General = {
    ...BASE_GENERAL,
    id: -4,
    name: '조조',
    userId: null,
    nationId: -2,
    cityId: -3,
    npcState: 1,
    leadership: 95,
    strength: 80,
    intel: 90,
    politics: 85,
    charm: 70,
    crew: 3000,
    crewType: 2,
    officerLevel: 12,
    picture: 'default',
};

/** 적국 장수 — 하후돈 */
const MOCK_XIAHOU_DUN: General = {
    ...BASE_GENERAL,
    id: -5,
    name: '하후돈',
    userId: null,
    nationId: -2,
    cityId: -3,
    npcState: 1,
    leadership: 70,
    strength: 92,
    intel: 55,
    politics: 40,
    charm: 60,
    crew: 2500,
    crewType: 2,
    officerLevel: 8,
    picture: 'default',
};

export const MOCK_GENERALS: General[] = [
    MOCK_MY_GENERAL,
    MOCK_GUAN_YU,
    MOCK_ZHUGE_LIANG,
    MOCK_CAO_CAO,
    MOCK_XIAHOU_DUN,
];
