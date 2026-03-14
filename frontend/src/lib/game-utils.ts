// ============================================================
// Game utility functions — ported from legacy hwe/ts/utilGame & util
// ============================================================

// --- Color utilities ---

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result
        ? {
              r: parseInt(result[1], 16),
              g: parseInt(result[2], 16),
              b: parseInt(result[3], 16),
          }
        : null;
}

export function isBrightColor(color: string): boolean {
    const cv = hexToRgb(color);
    if (!cv) return false;
    return cv.r * 0.299 + cv.g * 0.587 + cv.b * 0.114 > 140;
}

// --- Injury ---

export function calcInjury(baseStat: number, injury: number): number {
    return Math.round((baseStat * (100 - injury)) / 100);
}

export function formatInjury(injury: number): { text: string; color: string } {
    if (injury <= 0) return { text: '건강', color: 'white' };
    if (injury <= 20) return { text: '경상', color: 'yellow' };
    if (injury <= 40) return { text: '중상', color: 'orange' };
    if (injury <= 60) return { text: '심각', color: 'magenta' };
    return { text: '위독', color: 'red' };
}

// --- General type classification ---

// Default game constants (from legacy GameConstBase / default.json)
const DEFAULT_CHIEF_STAT_MIN = 65;
const DEFAULT_STAT_GRADE_LEVEL = 5;

export function formatGeneralTypeCall(
    leadership: number,
    strength: number,
    intel: number,
    chiefStatMin: number = DEFAULT_CHIEF_STAT_MIN,
    statGradeLevel: number = DEFAULT_STAT_GRADE_LEVEL
): string {
    if (leadership < 40) {
        if (strength + intel < 40) return '아둔';
        if (intel >= chiefStatMin && strength < intel * 0.8) return '학자';
        if (strength >= chiefStatMin && intel < strength * 0.8) return '장사(무투파)';
        return '명사';
    }

    const maxStat = Math.max(leadership, strength, intel);
    const sum2Stat = Math.min(leadership + strength, strength + intel, intel + leadership);
    if (maxStat >= chiefStatMin + statGradeLevel && sum2Stat >= maxStat * 1.7) return '만능(균형형)';
    if (strength >= chiefStatMin - statGradeLevel && intel < strength * 0.8) return '용장(무력형)';
    if (intel >= chiefStatMin - statGradeLevel && strength < intel * 0.8) return '명장(지력형)';
    if (leadership >= chiefStatMin - statGradeLevel && strength + intel < leadership) return '차장(통솔형)';
    return '평범';
}

// --- NPC color ---

export function getNPCColor(npcState: number): string | undefined {
    if (npcState === 6) return 'mediumaquamarine';
    if (npcState === 5) return 'darkcyan';
    if (npcState === 4) return 'deepskyblue';
    if (npcState >= 2) return 'cyan';
    if (npcState === 1) return 'skyblue';
    return undefined;
}

// --- Refresh score ---

const refreshScoreMap: [number, string][] = [
    [0, '안함'],
    [50, '무관심'],
    [100, '보통'],
    [200, '가끔'],
    [400, '자주'],
    [800, '열심'],
    [1600, '중독'],
    [3200, '폐인'],
    [6400, '경고'],
    [12800, '헐...'],
];

/** Binary-search a sorted [threshold, label][] array and return the label for the matching bucket. */
function searchThresholdMap(map: [number, string][], value: number): string {
    let lo = 0;
    let hi = map.length - 1;
    let result = 0;
    while (lo <= hi) {
        const mid = (lo + hi) >>> 1;
        if (map[mid][0] <= value) {
            result = mid;
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return map[result][1];
}

export function formatRefreshScore(score: number): string {
    if (!score) score = 0;
    return searchThresholdMap(refreshScoreMap, score);
}

// --- Experience / level ---

export function nextExpLevelRemain(experience: number, expLevel: number): [number, number] {
    if (experience < 1000) {
        return [experience - expLevel * 100, 100];
    }
    const expBase = 10 * expLevel ** 2;
    const expNext = 10 * (expLevel + 1) ** 2;
    return [experience - expBase, expNext - expBase];
}

// --- Officer level text ---

const OfficerLevelMapDefault: Record<number, string> = {
    20: '군주',
    19: '참모',
    18: '제1장군',
    17: '제1모사',
    16: '제2장군',
    15: '제2모사',
    14: '제3장군',
    13: '제3모사',
    12: '제4장군',
    11: '제4모사',
    10: '부장',
    9: '종사',
    8: '부종사',
    7: '서기',
    6: '부서기',
    5: '졸장',
    4: '태수',
    3: '군사',
    2: '종사',
    1: '일반',
    0: '재야',
};

const OfficerLevelMapByNationLevel: Record<number, Record<number, string>> = {
    9: {
        20: '황제',
        19: '승상',
        18: '태위',
        17: '사도',
        16: '사공',
        15: '광록훈',
        14: '위위',
        13: '대홍려',
        12: '대장군',
        11: '태상',
        10: '대사농',
        9: '소부',
        8: '종정',
        7: '표기장군',
        6: '거기장군',
        5: '위장군',
    },
    8: {
        20: '왕',
        19: '상',
        18: '대장군',
        17: '내사',
        16: '중위',
        15: '대부',
        14: '위사장',
        13: '알자',
        12: '교위',
        11: '태사령',
        10: '낭중',
        9: '종사중랑',
        8: '사마',
        7: '별부사마',
    },
    7: {
        20: '공',
        19: '어사대부',
        18: '중위',
        17: '장사',
        16: '사마',
        15: '종사중랑',
        14: '교위',
        13: '주부',
        12: '별부사마',
        11: '참군',
        10: '간의대부',
        9: '편장군',
    },
    6: {
        20: '대사마',
        19: '장사',
        18: '사마',
        17: '종사중랑',
        16: '교위',
        15: '참군사',
        14: '별부사마',
        13: '비장군',
    },
    5: {
        20: '대장군',
        19: '장사',
        18: '사마',
        17: '종사중랑',
        16: '교위',
        15: '주부',
        14: '참군',
        13: '별부사마',
    },
    4: {
        20: '중랑장',
        19: '장사',
        18: '사마',
        17: '주부',
        16: '군후',
        15: '기실참군',
        14: '둔장',
        13: '창조참군',
    },
    3: {
        20: '주목',
        19: '별가',
        18: '종사중랑',
        17: '치중',
        16: '사마',
        15: '종사',
        14: '부사마',
        13: '주부',
    },
    2: {
        20: '주자사',
        19: '별가종사',
        18: '병조종사',
        17: '치중종사',
        16: '무종사',
        15: '부종사',
        14: '도위',
        13: '전종사',
    },
    1: {
        20: '도위',
        19: '공조',
        18: '건무교위',
        17: '주부',
        16: '분무교위',
        15: '기실',
        14: '파적교위',
        13: '서좌',
    },
    0: {
        20: '방주',
        19: '부방주',
        18: '선봉',
        17: '군사',
        16: '호위',
        15: '척후',
        14: '보급',
        13: '전령',
    },
};

export function formatOfficerLevelText(officerLevel: number, nationLevel?: number, hasNation?: boolean): string {
    if (officerLevel <= 0 && hasNation) return '일반';

    if (officerLevel < 5) {
        return OfficerLevelMapDefault[officerLevel] ?? '???';
    }

    const nationMap =
        nationLevel === undefined
            ? OfficerLevelMapDefault
            : (OfficerLevelMapByNationLevel[nationLevel] ?? OfficerLevelMapDefault);

    return nationMap[officerLevel] ?? OfficerLevelMapDefault[officerLevel] ?? '???';
}

// --- Age color (legacy parity: 3-color based on retirementYear) ---

export function ageColor(age: number, retirementYear: number = 80): string {
    if (age < retirementYear * 0.75) return 'limegreen';
    if (age < retirementYear) return 'yellow';
    return 'red';
}

// --- Defence train ---

const defenceMap: [number, string][] = [
    [0, '△'],
    [60, '○'],
    [80, '◎'],
    [90, '☆'],
    [999, '×'],
];

export function formatDefenceTrain(defenceTrain: number): string {
    return searchThresholdMap(defenceMap, defenceTrain);
}

// --- Dexterity / dedication level ---

const DexLevelMap: [number, string, string][] = [
    [0, 'navy', 'F-'],
    [350, 'navy', 'F'],
    [1375, 'navy', 'F+'],
    [3500, 'skyblue', 'E-'],
    [7125, 'skyblue', 'E'],
    [12650, 'skyblue', 'E+'],
    [20475, 'seagreen', 'D-'],
    [31000, 'seagreen', 'D'],
    [44625, 'seagreen', 'D+'],
    [61750, 'teal', 'C-'],
    [82775, 'teal', 'C'],
    [108100, 'teal', 'C+'],
    [138125, 'limegreen', 'B-'],
    [173250, 'limegreen', 'B'],
    [213875, 'limegreen', 'B+'],
    [260400, 'darkorange', 'A-'],
    [313225, 'darkorange', 'A'],
    [372750, 'darkorange', 'A+'],
    [439375, 'tomato', 'S-'],
    [513500, 'tomato', 'S'],
    [595525, 'tomato', 'S+'],
    [685850, 'darkviolet', 'Z-'],
    [784875, 'darkviolet', 'Z'],
    [893000, 'darkviolet', 'Z+'],
    [1010625, 'gold', 'EX-'],
    [1138150, 'gold', 'EX'],
    [1275975, 'white', 'EX+'],
];

export interface DexInfo {
    level: number;
    name: string;
    color: string;
}

export function formatDexLevel(dex: number): DexInfo {
    let lo = 0;
    let hi = DexLevelMap.length - 1;
    let result = 0;
    while (lo <= hi) {
        const mid = (lo + hi) >>> 1;
        if (DexLevelMap[mid][0] <= dex) {
            result = mid;
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    const [, color, name] = DexLevelMap[result];
    return { level: result, name, color };
}

// --- Honor (experience label) ---

const honorMap: [number, string][] = [
    [0, '전무'],
    [640, '무명'],
    [2560, '신동'],
    [5760, '약간'],
    [10240, '평범'],
    [16000, '지역적'],
    [23040, '전국적'],
    [31360, '세계적'],
    [40960, '유명'],
    [45000, '명사'],
    [51840, '호걸(영웅호걸)'],
    [55000, '효웅(패자)'],
    [64000, '영웅'],
    [77440, '구세주'],
];

export function formatHonor(experience: number): string {
    return searchThresholdMap(honorMap, experience);
}

// --- Tech level ---

export const TECH_LEVEL_STEP = 1000;

export function convTechLevel(tech: number, maxTechLevel: number): number {
    return Math.min(Math.max(Math.floor(tech / TECH_LEVEL_STEP), 0), maxTechLevel);
}

export function getMaxRelativeTechLevel(
    startYear: number,
    year: number,
    maxTechLevel: number,
    initialAllowedTechLevel: number,
    techLevelIncYear: number
): number {
    const relYear = year - startYear;
    return Math.min(Math.max(Math.floor(relYear / techLevelIncYear) + initialAllowedTechLevel, 1), maxTechLevel);
}

export function isTechLimited(
    startYear: number,
    year: number,
    tech: number,
    maxTechLevel: number,
    initialAllowedTechLevel: number,
    techLevelIncYear: number
): boolean {
    const relMaxTech = getMaxRelativeTechLevel(
        startYear,
        year,
        maxTechLevel,
        initialAllowedTechLevel,
        techLevelIncYear
    );
    const techLevel = convTechLevel(tech, maxTechLevel);
    return techLevel >= relMaxTech;
}

// --- Number formatting ---

export function numberWithCommas(x: number): string {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

// --- Valid object key check ---

export function isValidObjKey<T>(key: T | 'None' | undefined | null): boolean {
    if (key === 'None' || key === undefined || key === null) return false;
    return true;
}

// --- Crew type names ---

export const CREW_TYPE_NAMES: Record<number, string> = {
    0: '보병',
    1: '궁병',
    2: '기병',
    3: '귀병',
    4: '차병',
    5: '노병',
    6: '연노병',
    7: '근위기병',
    8: '무당병',
    9: '서량기병',
    10: '등갑병',
    11: '수군',
    1100: '보병',
    1101: '청주병',
    1102: '수병',
    1103: '자객병',
    1104: '근위병',
    1105: '등갑병',
    1106: '백이병',
    1200: '궁병',
    1201: '궁기병',
    1202: '연노병',
    1203: '강궁병',
    1204: '석궁병',
    1300: '기병',
    1301: '백마병',
    1302: '중장기병',
    1303: '돌격기병',
    1304: '철기병',
    1305: '수렵기병',
    1306: '맹수병',
    1307: '호표기병',
    1400: '귀병',
    1401: '신귀병',
    1402: '백귀병',
    1403: '흑귀병',
    1404: '악귀병',
    1405: '남귀병',
    1406: '황귀병',
    1407: '천귀병',
    1408: '마귀병',
    1500: '정란',
    1501: '충차',
    1502: '벽력거',
    1503: '목우',
};

/** Base crew type subset (0-11) for dropdowns / UI selectors */
export const BASE_CREW_TYPES: Record<number, string> = Object.fromEntries(
    Object.entries(CREW_TYPE_NAMES)
        .filter(([k]) => Number(k) <= 11)
        .map(([k, v]) => [Number(k), v])
);

export function parseCrewTypeCode(crewTypeRaw: string | number | null | undefined): number {
    if (typeof crewTypeRaw === 'number') return crewTypeRaw;
    if (!crewTypeRaw) return 0;
    const matched = String(crewTypeRaw).match(/(\d+)$/);
    return matched ? Number(matched[1]) : 0;
}

export function getCrewTypeName(crewTypeStr: string): string {
    const code = parseCrewTypeCode(crewTypeStr);
    return CREW_TYPE_NAMES[code] ?? crewTypeStr;
}

// --- Display name utilities (strip legacy che_* prefixes) ---

/** Strip `che_` prefix from raw code for UI display (e.g. `che_유가` → `유가`). */
export function stripCodePrefix(code: string): string {
    return code.replace(/^che_/, '');
}

// --- Nation type labels (제자백가 + descriptions) ---

const NATION_TYPE_LABELS: Record<string, string> = {
    che_도적: '도적(약탈형)',
    che_명가: '명가(논리학파)',
    che_음양가: '음양가(자연철학)',
    che_종횡가: '종횡가(외교학파)',
    che_불가: '불가(불교)',
    che_오두미도: '오두미도(도교종파)',
    che_태평도: '태평도(도교종파)',
    che_도가: '도가(노자·장자)',
    che_묵가: '묵가(겸애학파)',
    che_덕가: '덕가(덕치주의)',
    che_병가: '병가(군사학파)',
    che_유가: '유가(유교)',
    che_법가: '법가(법치주의)',
};

/** Get nation type display label with explanation (e.g. `che_유가` → `유가(유교)`). */
export function getNationTypeLabel(typeCode: string): string {
    return NATION_TYPE_LABELS[typeCode] ?? stripCodePrefix(typeCode);
}

// --- Personality names (legacy che_* codes + compatibility aliases) ---

export const LEGACY_PERSONALITY_OPTIONS: { code: string; label: string; info: string }[] = [
    { code: 'Random', label: '랜덤', info: '성향을 무작위로 선택합니다.' },
    { code: 'che_안전', label: '안전', info: '사기 -5, 징·모병 비용 -20%' },
    { code: 'che_유지', label: '유지', info: '훈련 -5, 징·모병 비용 -20%' },
    { code: 'che_재간', label: '재간', info: '명성 -10%, 징·모병 비용 -20%' },
    { code: 'che_출세', label: '출세', info: '명성 +10%, 징·모병 비용 +20%' },
    { code: 'che_할거', label: '할거', info: '명성 -10%, 훈련 +5' },
    { code: 'che_정복', label: '정복', info: '명성 -10%, 사기 +5' },
    { code: 'che_패권', label: '패권', info: '훈련 +5, 징·모병 비용 +20%' },
    { code: 'che_의협', label: '의협', info: '사기 +5, 징·모병 비용 +20%' },
    { code: 'che_대의', label: '대의', info: '명성 +10%, 훈련 -5' },
    { code: 'che_왕좌', label: '왕좌', info: '명성 +10%, 사기 -5' },
];

export const PERSONALITY_NAMES: Record<string, string> = {
    ...Object.fromEntries(LEGACY_PERSONALITY_OPTIONS.map((option) => [option.code, option.label])),
    None: '-',
    Normal: '일반',
    Brave: '호전',
    Calm: '냉정',
    Loyal: '충성',
    Timid: '소심',
    Reckless: '저돌',
    Ambition: '야망',
};

export function getPersonalityName(code: string | undefined | null): string {
    if (!code) return '-';
    return PERSONALITY_NAMES[code] ?? code;
}

// --- Region names ---

export const REGION_NAMES: Record<number, string> = {
    1: '하북',
    2: '중원',
    3: '서북',
    4: '서촉',
    5: '남중',
    6: '초',
    7: '오월',
    8: '동이',
};

// --- Stat color (ability value → color) ---

export function statColor(value: number): string {
    if (value >= 90) return '#eab308'; // gold
    if (value >= 80) return '#f97316'; // orange
    if (value >= 70) return '#22c55e'; // green
    if (value >= 60) return '#06b6d4'; // cyan
    if (value >= 50) return '#94a3b8'; // gray
    return '#6b7280'; // dim
}

// --- Trust (민심) color ---

export function trustColor(trust: number): string {
    if (trust >= 80) return '#22c55e';
    if (trust >= 60) return '#eab308';
    if (trust >= 40) return '#f97316';
    return '#ef4444';
}

// --- Nation level labels ---

export const NATION_LEVEL_LABELS: Record<number, string> = {
    0: '방랑군',
    1: '도위',
    2: '주자사',
    3: '주목',
    4: '중랑장',
    5: '대장군',
    6: '대사마',
    7: '공',
    8: '왕',
    9: '황제',
};

// --- City level names ---

export const CITY_LEVEL_NAMES: Record<number, string> = {
    1: '수',
    2: '진',
    3: '관',
    4: '이',
    5: '소',
    6: '중',
    7: '대',
    8: '특',
};

// --- City level badge (legacy getCityLevelList) ---

export const CITY_LEVEL_BADGES: Record<number, string> = {
    1: '수',
    2: '진',
    3: '관',
    4: '이',
    5: '소',
    6: '중',
    7: '대',
    8: '특',
};

export function formatCityLevelBadge(level: number): string {
    return CITY_LEVEL_BADGES[level] ?? '?';
}

// --- Officer set bit check (legacy isOfficerSet) ---

export function isOfficerSet(officerSet: number, reqOfficerLevel: number): boolean {
    return (officerSet & (1 << reqOfficerLevel)) !== 0;
}

// --- Format city name (legacy formatCityName) ---

export function formatCityName(cityId: number, cityMap: Map<number, { name: string }>): string {
    const city = cityMap.get(cityId);
    if (!city) return `도시#${cityId}`;
    return city.name;
}

// --- Vote color (legacy formatVoteColor) ---

const VOTE_COLORS: string[] = [
    '#ff0000', // red
    '#ffa500', // orange
    '#ffff00', // yellow
    '#008000', // green
    '#0000ff', // blue
    '#000080', // navy
    '#800080', // purple
];

export function formatVoteColor(type: number): string {
    return VOTE_COLORS[type % VOTE_COLORS.length];
}

// --- Tournament term (legacy calcTournamentTerm) ---

export function calcTournamentTerm(turnTerm: number): number {
    return Math.min(Math.max(turnTerm, 5), 120);
}

// --- Tournament type & step formatting (legacy formatTournament) ---

const TOURNAMENT_TYPE_MAP = ['전력전(전투력 대결)', '통솔전(지휘력 대결)', '일기토(1:1 무력전)', '설전(1:1 지력전)'];

export function formatTournamentType(type: number | null | undefined): string {
    if (type === null || type === undefined) return '?';
    return TOURNAMENT_TYPE_MAP[type] ?? '?';
}

export interface TournamentStepInfo {
    availableJoin: boolean;
    state: string;
    nextText: string;
}

const TOURNAMENT_STEP_MAP: TournamentStepInfo[] = [
    { availableJoin: false, state: '경기 없음', nextText: '' },
    { availableJoin: true, state: '참가 모집중', nextText: '개막시간' },
    { availableJoin: false, state: '예선 진행중', nextText: '다음경기' },
    { availableJoin: false, state: '본선 추첨중', nextText: '다음추첨' },
    { availableJoin: false, state: '본선 진행중', nextText: '다음경기' },
    { availableJoin: false, state: '16강 배정중', nextText: '16강배정' },
    { availableJoin: true, state: '베팅 진행중', nextText: '베팅마감' },
    { availableJoin: false, state: '16강 진행중', nextText: '다음경기' },
    { availableJoin: false, state: '8강 진행중', nextText: '다음경기' },
    { availableJoin: false, state: '4강 진행중', nextText: '다음경기' },
    { availableJoin: false, state: '결승 진행중', nextText: '다음경기' },
];

export function formatTournamentStep(step: number | null | undefined): TournamentStepInfo {
    if (step === null || step === undefined || step < 0 || step >= TOURNAMENT_STEP_MAP.length) {
        return TOURNAMENT_STEP_MAP[0];
    }
    return TOURNAMENT_STEP_MAP[step];
}

// --- Post-filter nation command for troop dispatch (legacy postFilterNationCommandGen) ---

export interface TurnObj {
    action: string;
    brief: string;
    tooltip?: string;
    arg: Record<string, unknown>;
}

// --- Dedication level text (legacy func_converter.php getDedLevel) ---

export function getDedLevelText(dedLevel: number): string {
    if (dedLevel < 10) return '무품관';
    if (dedLevel >= 100) return '공신';
    return `${10 - Math.floor(dedLevel / 10)}품관`;
}

// --- Dexterity call (legacy func_converter.php getDexCall — simple grade) ---

const DEX_CALL_GRADES = [
    'F-',
    'F',
    'F+',
    'E-',
    'E',
    'E+',
    'D-',
    'D',
    'D+',
    'C-',
    'C',
    'C+',
    'B-',
    'B',
    'B+',
    'A-',
    'A',
    'A+',
    'S-',
    'S',
    'S+',
    'EX-',
    'EX',
    'EX+',
];

export function getDexCall(dex: number): string {
    return DEX_CALL_GRADES[Math.min(Math.floor(dex / 100), DEX_CALL_GRADES.length - 1)];
}

// --- Experience level (legacy func_converter.php getExpLevel) ---

export function getExpLevel(experience: number): number {
    return Math.floor(experience / 100);
}

// --- Level percentage (legacy func_converter.php getLevelPer) ---

export function getLevelPer(exp: number, level: number): number {
    return exp - level * 100;
}

// --- Post-filter nation command for troop dispatch (legacy postFilterNationCommandGen) ---

export function postFilterNationCommandGen<T extends TurnObj>(
    troopList: Record<number, string>,
    cityMap: Map<number, { name: string }>
): (turnObj: T) => T {
    return function (turnObj: T): T {
        if (turnObj.action !== 'che_발령') {
            return turnObj;
        }
        const destGeneralID = turnObj.arg.destGeneralID as number | undefined;
        if (destGeneralID === undefined || !(destGeneralID in troopList)) {
            return turnObj;
        }

        const troopName = troopList[destGeneralID];
        const destCityID = turnObj.arg.destCityID as number;
        const destCityName = formatCityName(destCityID, cityMap);
        // Korean josa "로/으로"
        const lastChar = destCityName.charCodeAt(destCityName.length - 1);
        const hasFinalConsonant = (lastChar - 0xac00) % 28 !== 0;
        const josaRo = hasFinalConsonant ? '으로' : '로';
        const brief = `《${troopName}》【${destCityName}】${josaRo} 발령`;
        const tooltip = `《${troopName}》${turnObj.brief}`;

        return {
            ...turnObj,
            brief,
            tooltip,
        };
    };
}
