import type { City } from '@/types';

const BASE_CITY: Omit<
    City,
    | 'id'
    | 'name'
    | 'nationId'
    | 'region'
    | 'pop'
    | 'popMax'
    | 'agri'
    | 'agriMax'
    | 'comm'
    | 'commMax'
    | 'secu'
    | 'secuMax'
    | 'def'
    | 'defMax'
    | 'wall'
    | 'wallMax'
> = {
    worldId: -1,
    level: 5,
    supplyState: 1,
    frontState: 0,
    trust: 80,
    trade: 0,
    dead: 0,
    officerSet: 0,
    state: 0,
    term: 0,
    planetType: 'normal',
    conflict: {},
    meta: {},
};

/** 성도 — 촉 수도 */
export const MOCK_CITY_CHENGDU: City = {
    ...BASE_CITY,
    id: -1,
    name: '성도',
    nationId: -1,
    region: 10,
    pop: 8000,
    popMax: 10000,
    agri: 5000,
    agriMax: 8000,
    comm: 4000,
    commMax: 7000,
    secu: 700,
    secuMax: 1000,
    def: 2000,
    defMax: 3000,
    wall: 2500,
    wallMax: 3000,
};

/** 면죽 — 촉 행성 */
export const MOCK_CITY_MIANZHU: City = {
    ...BASE_CITY,
    id: -2,
    name: '면죽',
    nationId: -1,
    region: 10,
    pop: 4000,
    popMax: 6000,
    agri: 3000,
    agriMax: 5000,
    comm: 2000,
    commMax: 4000,
    secu: 500,
    secuMax: 1000,
    def: 1000,
    defMax: 2000,
    wall: 1500,
    wallMax: 2000,
};

/** 허창 — 위 수도 */
export const MOCK_CITY_XUCHANG: City = {
    ...BASE_CITY,
    id: -3,
    name: '허창',
    nationId: -2,
    region: 5,
    pop: 9000,
    popMax: 12000,
    agri: 6000,
    agriMax: 9000,
    comm: 5000,
    commMax: 8000,
    secu: 800,
    secuMax: 1000,
    def: 2500,
    defMax: 3500,
    wall: 3000,
    wallMax: 3500,
};

/** 한중 — 중립 행성 */
export const MOCK_CITY_HANZHONG: City = {
    ...BASE_CITY,
    id: -4,
    name: '한중',
    nationId: 0,
    region: 10,
    pop: 3000,
    popMax: 5000,
    agri: 2000,
    agriMax: 4000,
    comm: 1500,
    commMax: 3000,
    secu: 400,
    secuMax: 1000,
    def: 500,
    defMax: 2000,
    wall: 800,
    wallMax: 2000,
};

export const MOCK_CITIES: City[] = [MOCK_CITY_CHENGDU, MOCK_CITY_MIANZHU, MOCK_CITY_XUCHANG, MOCK_CITY_HANZHONG];
