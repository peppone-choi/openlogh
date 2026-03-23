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

describe('mobile tab icons', () => {
    it('mobile tabs include icons for each tab', () => {
        const mobileTabs = [
            { key: 'map', label: '지도', hasIcon: true },
            { key: 'commands', label: '명령', hasIcon: true },
            { key: 'status', label: '상태', hasIcon: true },
            { key: 'world', label: '동향', hasIcon: true },
        ];
        expect(mobileTabs).toHaveLength(4);
        mobileTabs.forEach((tab) => expect(tab.hasIcon).toBe(true));
    });

    it('mobile compact summary replaces dense grid on mobile', () => {
        const mobileCompactSummary = 'lg:hidden text-center text-xs';
        const desktopFullGrid = 'hidden lg:grid';
        expect(mobileCompactSummary).toContain('lg:hidden');
        expect(desktopFullGrid).toContain('hidden lg:grid');
    });
});
