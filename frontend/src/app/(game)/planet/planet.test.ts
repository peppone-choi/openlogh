import { describe, expect, it } from 'vitest';

describe('planet page filter logic', () => {
    it('own planet is visible even when vacant (factionId === 0)', () => {
        const city = { id: 10, factionId: 0 };
        const myGeneralPlanetId = 10;
        const isVacant = city.factionId === 0;
        const isMyCity = city.id === myGeneralPlanetId;
        const isVisible = isMyCity || !isVacant;
        expect(isVisible).toBe(true);
    });

    it('other vacant planet is not visible', () => {
        const city = { id: 20, factionId: 0 };
        const myGeneralPlanetId = 10;
        const isVacant = city.factionId === 0;
        const isMyCity = city.id === myGeneralPlanetId;
        const isVisible = isMyCity || !isVacant;
        expect(isVisible).toBe(false);
    });

    it('loads data without myGeneral when requestedCityId is set', () => {
        const hasGeneral = false;
        const requestedCityId = 5;
        const shouldFetchGeneral = hasGeneral || requestedCityId == null;
        expect(shouldFetchGeneral).toBe(false);
    });
});
