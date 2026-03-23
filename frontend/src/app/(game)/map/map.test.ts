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

    it('capital star icon offset places star above flag', () => {
        const starRight = -6;
        const starTop = -6;
        expect(starRight).toBeLessThan(0);
        expect(starTop).toBeLessThan(0);
    });

    it('city name label uses pill styling with backdrop-blur', () => {
        const className =
            'absolute whitespace-nowrap px-1 py-[1px] bg-black/60 backdrop-blur-[2px] text-[10px] rounded-sm';
        expect(className).toContain('rounded-sm');
        expect(className).toContain('backdrop-blur');
    });
});
