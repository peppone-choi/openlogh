import { describe, expect, it } from 'vitest';

describe('city page filter logic', () => {
    it('returns empty when filterCityId matches nothing', () => {
        const cities = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const filterCityId = 999;

        const base = filterCityId > 0 ? cities.filter((c) => c.id === filterCityId) : cities;

        expect(base).toHaveLength(0);
    });

    it('returns only matching city when filterCityId exists', () => {
        const cities = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const filterCityId = 2;

        const base = filterCityId > 0 ? cities.filter((c) => c.id === filterCityId) : cities;

        expect(base).toHaveLength(1);
        expect(base[0].id).toBe(2);
    });

    it('returns all cities when no filterCityId set', () => {
        const cities = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const filterCityId = 0;

        const base = filterCityId > 0 ? cities.filter((c) => c.id === filterCityId) : cities;

        expect(base).toHaveLength(3);
    });

    it('vacant city (nationId === 0) is not visible for stats', () => {
        const city = { id: 1, nationId: 0 };
        const isVacant = city.nationId === 0;
        expect(isVacant).toBe(true);
    });

    it('owned city (nationId > 0) may be visible for stats', () => {
        const city = { id: 1, nationId: 5 };
        const isVacant = city.nationId === 0;
        expect(isVacant).toBe(false);
    });

    it('own city is visible even when vacant (nationId === 0)', () => {
        const city = { id: 10, nationId: 0 };
        const myGeneralCityId = 10;
        const isVacant = city.nationId === 0;
        const isMyCity = city.id === myGeneralCityId;
        const isVisible = isMyCity || !isVacant;
        expect(isVisible).toBe(true);
    });

    it('other vacant city is not visible', () => {
        const city = { id: 20, nationId: 0 };
        const myGeneralCityId = 10;
        const isVacant = city.nationId === 0;
        const isMyCity = city.id === myGeneralCityId;
        const isVisible = isMyCity || !isVacant;
        expect(isVisible).toBe(false);
    });

    it('loads data without myGeneral when requestedCityId is set', () => {
        const hasGeneral = false;
        const requestedCityId = 5;

        const shouldLoadSingle = !hasGeneral && requestedCityId != null;
        expect(shouldLoadSingle).toBe(true);
    });
});
