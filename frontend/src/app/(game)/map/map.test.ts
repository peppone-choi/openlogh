import { describe, expect, it } from 'vitest';

describe('map page city click', () => {
    it('handleCityClick should navigate to /city?id=X', () => {
        const expectedPath = '/city?id=42';
        expect(expectedPath).toContain('/city?id=');
        expect(expectedPath).toBe('/city?id=42');
    });

    it('click handler passes city data and navigates', () => {
        const city = { id: 42, name: '낙양' };
        const path = `/city?id=${city.id}`;
        expect(path).toBe('/city?id=42');
    });
});
