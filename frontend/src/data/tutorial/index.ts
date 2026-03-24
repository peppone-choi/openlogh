export { MOCK_WORLD } from './mock-world';
export { MOCK_MY_GENERAL, MOCK_GENERALS } from './mock-generals';
export { MOCK_NATION_SHU, MOCK_NATION_WEI, MOCK_NATIONS } from './mock-nations';
export {
    MOCK_CITY_CHENGDU,
    MOCK_CITY_MIANZHU,
    MOCK_CITY_XUCHANG,
    MOCK_CITY_HANZHONG,
    MOCK_CITIES,
} from './mock-cities';
export { MOCK_COMMAND_TABLE, getMockCommandResult } from './mock-commands';
export { MOCK_DIPLOMACY, MOCK_DIPLOMACY_AFTER_LETTER } from './mock-diplomacy';
export { TUTORIAL_STEPS } from './steps';
export type { TutorialPhase, TutorialStep } from './steps';

import { MOCK_CITIES } from './mock-cities';
import { MOCK_NATIONS } from './mock-nations';
import { MOCK_GENERALS, MOCK_MY_GENERAL } from './mock-generals';
import { MOCK_COMMAND_TABLE, getMockCommandResult } from './mock-commands';
import { MOCK_DIPLOMACY } from './mock-diplomacy';
import { MOCK_WORLD } from './mock-world';
import type { FrontInfoResponse } from '@/types';

/**
 * URL + method 패턴으로 mock 응답을 반환.
 * store seeding 이후에도 페이지에서 직접 API를 호출하는 경우 fallback으로 사용.
 */
const MOCK_FRONT_INFO: FrontInfoResponse = {
    global: {
        year: MOCK_WORLD.currentYear,
        month: MOCK_WORLD.currentMonth,
        turnTerm: 0,
        startyear: 200,
        genCount: [
            [3, -1],
            [2, -2],
        ],
        onlineNations: [
            { id: -1, name: '촉', color: '#DD0000', genCount: 3 },
            { id: -2, name: '위', color: '#0044DD', genCount: 2 },
        ],
        onlineUserCnt: 5,
        auctionCount: 0,
        tournamentState: 0,
        isTournamentActive: false,
        isTournamentApplicationOpen: false,
        isBettingActive: false,
        lastExecuted: null,
        isLocked: false,
        scenarioText: '튜토리얼',
        realtimeMode: false,
        extendedGeneral: 0,
        isFiction: 0,
        npcMode: 0,
        joinMode: 'normal',
        develCost: 100,
        noticeMsg: 0,
        apiLimit: 999,
        generalCntLimit: 50,
        serverCnt: 1,
        lastVoteID: 0,
        lastVote: null,
    },
    general: {
        no: -1,
        name: '유비',
        picture: 'default',
        imgsvr: 0,
        nation: -1,
        npc: 0,
        city: -1,
        troop: 0,
        officerLevel: 12,
        officerLevelText: '군주',
        officerCity: 0,
        permission: 0,
        lbonus: 5,
        leadership: 75,
        leadershipExp: 0,
        strength: 70,
        strengthExp: 0,
        intel: 65,
        intelExp: 0,
        politics: 80,
        politicsExp: 0,
        charm: 95,
        charmExp: 0,
        experience: 100,
        dedication: 100,
        explevel: 1,
        dedlevel: 1,
        honorText: '일반',
        dedLevelText: '일반',
        bill: 100,
        gold: 1000,
        rice: 1000,
        crew: 1000,
        crewtype: '창병',
        train: 80,
        atmos: 80,
        weapon: 'None',
        book: 'None',
        horse: 'None',
        item: 'None',
        personal: '인덕',
        specialDomestic: 'None',
        specialWar: 'None',
        specage: 0,
        specage2: 0,
        age: 39,
        injury: 0,
        killturn: null,
        belong: 1,
        betray: 0,
        blockState: 0,
        defenceTrain: 80,
        turntime: new Date().toISOString(),
        recentWar: null,
        commandPoints: 10,
        commandEndTime: null,
        ownerName: null,
        refreshScoreTotal: null,
        refreshScore: null,
        autorunLimit: 0,
        reservedCommand: null,
        troopInfo: null,
        dex1: 50,
        dex2: 50,
        dex3: 50,
        dex4: 50,
        dex5: 50,
        warnum: 0,
        killnum: 0,
        deathnum: 0,
        killcrew: 0,
        deathcrew: 0,
        firenum: 0,
    },
    nation: {
        id: -1,
        full: false,
        name: '촉',
        color: '#DD0000',
        level: 5,
        typeCode: 'che_ren',
        type: { raw: 'che_ren', name: '인덕', pros: '매력 보너스', cons: '무력 페널티' },
        gold: 10000,
        rice: 10000,
        tech: 1000,
        power: 500,
        gennum: 3,
        capital: -1,
        bill: 100,
        taxRate: 20,
        population: { cityCnt: 2, now: 12000, max: 16000 },
        crew: { generalCnt: 3, now: 4500, max: 10000 },
        onlineGen: '',
        notice: null,
        topChiefs: {},
        diplomaticLimit: 3,
        strategicCmdLimit: 3,
        impossibleStrategicCommand: [],
        prohibitScout: 0,
        prohibitWar: 0,
    },
    city: {
        id: -1,
        name: '성도',
        level: 5,
        region: 10,
        nationInfo: { id: -1, name: '촉', color: '#DD0000' },
        trust: 80,
        pop: [8000, 10000],
        agri: [5000, 8000],
        comm: [4000, 7000],
        secu: [700, 1000],
        def: [2000, 3000],
        wall: [2500, 3000],
        trade: 0,
        officerList: {},
    },
    recentRecord: {
        flushGeneral: false,
        flushGlobal: false,
        flushHistory: false,
        general: [],
        global: [{ id: -1, message: '[튜토리얼] 게임이 시작되었습니다.', date: new Date().toISOString() }],
        history: [],
    },
    aux: { myLastVote: null },
};

export function getTutorialMockResponse(method: string | undefined, url: string | undefined): unknown | undefined {
    if (!method || !url) return undefined;

    const m = method.toUpperCase();
    // Normalize world IDs to -1
    const normalizedUrl = url.replace(/\/worlds\/\d+/, '/worlds/-1');

    const key = `${m}:${normalizedUrl}`;

    const MOCK_RESPONSES: Record<string, unknown> = {
        'GET:/worlds/-1/cities': MOCK_CITIES,
        'GET:/worlds/-1/nations': MOCK_NATIONS,
        'GET:/worlds/-1/generals': MOCK_GENERALS,
        'GET:/worlds/-1/generals/me': MOCK_MY_GENERAL,
        'GET:/worlds/-1/diplomacy': MOCK_DIPLOMACY,
        'GET:/worlds/-1/front-info': MOCK_FRONT_INFO,
        'GET:/worlds/-1/commands/general/table': MOCK_COMMAND_TABLE,
    };

    // Direct match
    if (MOCK_RESPONSES[key] !== undefined) {
        return MOCK_RESPONSES[key];
    }

    // Pattern matches for POST commands
    if (m === 'POST' && normalizedUrl.includes('/commands/general/execute')) {
        return getMockCommandResult();
    }

    // Catch-all for any API call in tutorial mode — return empty success
    return { success: true, data: [] };
}
