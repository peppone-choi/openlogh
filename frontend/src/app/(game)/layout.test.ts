import { describe, expect, it } from 'vitest';

describe('game layout pre_open redirect', () => {
    function shouldRedirectPreOpen(opentime: string | undefined, pathname: string): boolean {
        const isPreOpen = opentime ? new Date() < new Date(opentime) : false;
        return isPreOpen && pathname !== '/my-page';
    }

    it('redirects non-my-page paths during pre_open', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(shouldRedirectPreOpen(opentime, '/commands')).toBe(true);
    });

    it('allows my-page during pre_open', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(shouldRedirectPreOpen(opentime, '/my-page')).toBe(false);
    });

    it('allows all paths when not pre_open', () => {
        expect(shouldRedirectPreOpen(undefined, '/commands')).toBe(false);
    });
});
