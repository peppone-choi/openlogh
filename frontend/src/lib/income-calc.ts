import type { City } from '@/types';

const NATION_TYPE_INCOME_MOD: Record<string, { funds: number; supplies: number }> = {
    che_법가: { funds: 1.1, supplies: 1.0 },
    che_오두미도: { funds: 1.0, supplies: 1.1 },
    che_유가: { funds: 1.0, supplies: 0.9 },
    che_덕가: { funds: 1.0, supplies: 0.9 },
    che_명가: { funds: 1.0, supplies: 0.9 },
    che_불가: { funds: 0.9, supplies: 1.0 },
    che_도적: { funds: 0.9, supplies: 1.0 },
    che_종횡가: { funds: 0.9, supplies: 1.0 },
};

function getIncomeModifier(typeCode: string, resource: 'funds' | 'supplies'): number {
    return NATION_TYPE_INCOME_MOD[typeCode]?.[resource] ?? 1.0;
}

export function calcPlanetFundsIncome(
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
    v *= getIncomeModifier(typeCode, 'funds');
    return Math.round(v);
}

export function calcPlanetSuppliesIncome(
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
    v *= getIncomeModifier(typeCode, 'supplies');
    return Math.round(v);
}

export function calcPlanetFortressSuppliesIncome(
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
    v *= getIncomeModifier(typeCode, 'supplies');
    return Math.round(v);
}

export function calcPlanetWarFundsIncome(city: City, typeCode: string): number {
    if (!city.supplyState) return 0;
    let v = city.dead / 10;
    v *= getIncomeModifier(typeCode, 'funds');
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
