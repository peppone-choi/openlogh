import type { City } from '@/types';

const NATION_TYPE_INCOME_MOD: Record<string, { gold: number; rice: number }> = {
    che_법가: { gold: 1.1, rice: 1.0 },
    che_오두미도: { gold: 1.0, rice: 1.1 },
    che_유가: { gold: 1.0, rice: 0.9 },
    che_덕가: { gold: 1.0, rice: 0.9 },
    che_명가: { gold: 1.0, rice: 0.9 },
    che_불가: { gold: 0.9, rice: 1.0 },
    che_도적: { gold: 0.9, rice: 1.0 },
    che_종횡가: { gold: 0.9, rice: 1.0 },
};

function getIncomeModifier(typeCode: string, resource: 'gold' | 'rice'): number {
    return NATION_TYPE_INCOME_MOD[typeCode]?.[resource] ?? 1.0;
}

export function calcCityGoldIncome(
    city: City,
    officerCnt: number,
    isCapital: boolean,
    nationLevel: number,
    typeCode: string
): number {
    if (!city.supplyState) return 0;
    if (city.commMax <= 0) return 0;
    const trustRatio = city.trust / 200 + 0.5;
    let v = (city.pop * (city.comm / city.commMax) * trustRatio) / 30;
    v *= 1 + (city.secuMax > 0 ? city.secu / city.secuMax / 10 : 0);
    v *= Math.pow(1.05, officerCnt);
    if (isCapital && nationLevel > 0) v *= 1 + 1 / (3 * nationLevel);
    v *= getIncomeModifier(typeCode, 'gold');
    return Math.round(v);
}

export function calcCityRiceIncome(
    city: City,
    officerCnt: number,
    isCapital: boolean,
    nationLevel: number,
    typeCode: string
): number {
    if (!city.supplyState) return 0;
    if (city.agriMax <= 0) return 0;
    const trustRatio = city.trust / 200 + 0.5;
    let v = (city.pop * (city.agri / city.agriMax) * trustRatio) / 30;
    v *= 1 + (city.secuMax > 0 ? city.secu / city.secuMax / 10 : 0);
    v *= Math.pow(1.05, officerCnt);
    if (isCapital && nationLevel > 0) v *= 1 + 1 / (3 * nationLevel);
    v *= getIncomeModifier(typeCode, 'rice');
    return Math.round(v);
}

export function calcCityWallRiceIncome(
    city: City,
    officerCnt: number,
    isCapital: boolean,
    nationLevel: number,
    typeCode: string
): number {
    if (!city.supplyState) return 0;
    if (city.wallMax <= 0) return 0;
    let v = (city.def * city.wall) / city.wallMax / 3;
    v *= 1 + (city.secuMax > 0 ? city.secu / city.secuMax / 10 : 0);
    v *= Math.pow(1.05, officerCnt);
    if (isCapital && nationLevel > 0) v *= 1 + 1 / (3 * nationLevel);
    v *= getIncomeModifier(typeCode, 'rice');
    return Math.round(v);
}

export function calcCityWarGoldIncome(city: City, typeCode: string): number {
    if (!city.supplyState) return 0;
    let v = city.dead / 10;
    v *= getIncomeModifier(typeCode, 'gold');
    return Math.round(v);
}

const MAX_DED_LEVEL = 30;

export function getDedLevel(dedication: number): number {
    return Math.min(Math.max(Math.ceil(Math.sqrt(dedication) / 10), 0), MAX_DED_LEVEL);
}

export function getBill(dedication: number): number {
    return getDedLevel(dedication) * 200 + 400;
}

export function countCityOfficers(
    generals: Array<{ officerLevel: number; officerCity: number; cityId: number }>,
    cityId: number
): number {
    return generals.filter(
        (g) => g.officerLevel >= 2 && g.officerLevel <= 4 && g.officerCity === cityId && g.cityId === cityId
    ).length;
}
