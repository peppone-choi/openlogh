import { describe, expect, it } from 'vitest';

describe('city page filter logic', () => {
    it('falls back to all cities when filterCityId matches nothing', () => {
        const cities = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const filterCityId = 999;

        const filtered = cities.filter((c) => c.id === filterCityId);
        const result = filtered.length > 0 ? filtered : cities;

        expect(result).toHaveLength(3);
    });

    it('returns matching city when filterCityId exists', () => {
        const cities = [{ id: 1 }, { id: 2 }, { id: 3 }];
        const filterCityId = 2;

        const filtered = cities.filter((c) => c.id === filterCityId);
        const result = filtered.length > 0 ? filtered : cities;

        expect(result).toHaveLength(1);
        expect(result[0].id).toBe(2);
    });
});
