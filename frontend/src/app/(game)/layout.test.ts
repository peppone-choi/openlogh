import { describe, expect, it } from 'vitest';

describe('game layout phase redirect', () => {
    function shouldRedirect(config: { startTime?: string; opentime?: string }, pathname: string): string | null {
        const now = new Date();
        if (config.startTime && now < new Date(config.startTime)) return '/lobby';
        const isPreOpen = config.opentime ? now < new Date(config.opentime) : false;
        if (isPreOpen && pathname !== '/my-page') return '/my-page';
        return null;
    }

    it('redirects to lobby when reserved (startTime in future)', () => {
        const startTime = new Date(Date.now() + 3600000).toISOString();
        const opentime = new Date(Date.now() + 7200000).toISOString();
        expect(shouldRedirect({ startTime, opentime }, '/map')).toBe('/lobby');
    });

    it('redirects to my-page during pre_open for non-my-page paths', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(shouldRedirect({ opentime }, '/commands')).toBe('/my-page');
    });

    it('allows my-page during pre_open', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(shouldRedirect({ opentime }, '/my-page')).toBeNull();
    });

    it('allows all paths when fully open', () => {
        expect(shouldRedirect({}, '/commands')).toBeNull();
    });

    it('allows all paths when opentime has passed', () => {
        const opentime = new Date(Date.now() - 3600000).toISOString();
        expect(shouldRedirect({ opentime }, '/map')).toBeNull();
    });
});
