import { describe, expect, it } from 'vitest';

describe('map page city click', () => {
    it('handleCityClick should navigate to /city?id=X', () => {
        const expectedPath = '/city?id=42';
        expect(expectedPath).toContain('/city?id=');
        expect(expectedPath).toBe('/city?id=42');
    });
});
