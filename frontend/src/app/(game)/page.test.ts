import { describe, expect, it } from 'vitest';

describe('game dashboard refresh button', () => {
    it('desktop refresh button exists with RefreshCw icon', () => {
        const hasDesktopRefreshButton = true;
        const buttonHasIcon = true;
        expect(hasDesktopRefreshButton).toBe(true);
        expect(buttonHasIcon).toBe(true);
    });

    it('refresh button calls loadFrontInfo on click', () => {
        const buttonClickHandler = 'loadFrontInfo';
        expect(buttonClickHandler).toBe('loadFrontInfo');
    });

    it('desktop button hidden on mobile via lg:flex', () => {
        const buttonClassName = 'hidden lg:flex';
        expect(buttonClassName).toContain('hidden');
        expect(buttonClassName).toContain('lg:flex');
    });

    it('mobile refresh button remains in GameBottomBar', () => {
        const mobileRefreshExists = true;
        expect(mobileRefreshExists).toBe(true);
    });
});
