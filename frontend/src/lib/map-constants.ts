// Shared map rendering constants

export const detailMapCitySizes: Record<number, number[]> = {
    1: [48, 45, 16, 15, -8, -4],
    2: [60, 42, 20, 14, -8, -4],
    3: [42, 42, 14, 14, -8, -4],
    4: [60, 45, 20, 15, -6, -3],
    5: [72, 48, 24, 16, -6, -4],
    6: [78, 54, 26, 18, -6, -4],
    7: [84, 60, 28, 20, -6, -4],
    8: [96, 72, 32, 24, -6, -3],
};

export const MAP_WIDTH = 700;
export const MAP_HEIGHT = 500;

export type MapSeason = 'spring' | 'summer' | 'fall' | 'winter';

export function getSeason(month: number | null | undefined): MapSeason {
    if (!month) return 'spring';
    if (month <= 3) return 'spring';
    if (month <= 6) return 'summer';
    if (month <= 9) return 'fall';
    return 'winter';
}

export const SEASON_LABELS: Record<string, string> = {
    spring: '봄',
    summer: '여름',
    fall: '가을',
    winter: '겨울',
};

export const CITY_STATE_NAMES: Record<number, string> = {
    1: '풍작',
    2: '호황',
    3: '한파/폭설',
    4: '역병',
    5: '지진',
    6: '태풍',
    7: '홍수',
    8: '메뚜기/흉년',
    9: '황건적',
    31: '파괴',
    32: '파괴',
    33: '약탈',
    34: '약탈',
    41: '분쟁중',
    42: '분쟁중',
    43: '분쟁중',
};
