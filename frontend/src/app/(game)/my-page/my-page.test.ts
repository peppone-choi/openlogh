import { describe, expect, it } from 'vitest';

describe('my-page pre_open phase', () => {
    it('detects pre_open when opentime is in the future', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        const isPreOpen = opentime ? new Date() < new Date(opentime) : false;
        expect(isPreOpen).toBe(true);
    });

    it('detects normal mode when opentime is in the past', () => {
        const opentime = new Date(Date.now() - 3600000).toISOString();
        const isPreOpen = opentime ? new Date() < new Date(opentime) : false;
        expect(isPreOpen).toBe(false);
    });

    it('detects normal mode when opentime is not set', () => {
        const opentime = undefined;
        const isPreOpen = opentime ? new Date() < new Date(opentime) : false;
        expect(isPreOpen).toBe(false);
    });
});
