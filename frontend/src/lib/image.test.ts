import { describe, expect, it } from 'vitest';
import { getShipClassIconUrl, getPortraitUrl } from '@/lib/image';

describe('image helper URLs', () => {
    it('falls back to icons/0.jpg when portrait is missing', () => {
        expect(getPortraitUrl()).toContain('/icons/0.jpg');
    });

    it('maps default.jpg portrait to icons/0.jpg', () => {
        expect(getPortraitUrl('default.jpg')).toContain('/icons/0.jpg');
    });

    it('maps numeric portrait id to icons path', () => {
        expect(getPortraitUrl('1146')).toContain('/icons/1146.jpg');
    });

    it('maps numeric jpg portrait to icons path', () => {
        expect(getPortraitUrl('1146.jpg')).toContain('/icons/1146.jpg');
    });

    it('keeps explicit asset path under CDN base', () => {
        expect(getPortraitUrl('icons/77.jpg')).toContain('/icons/77.jpg');
    });

    it('maps legacy crewType 0 to crewtype1100 icon', () => {
        expect(getShipClassIconUrl(0)).toContain('/game/crewtype1100.png');
    });

    it('maps legacy crewType 3 to crewtype1400 icon', () => {
        expect(getShipClassIconUrl(3)).toContain('/game/crewtype1400.png');
    });

    it('keeps modern crewType ids as-is', () => {
        expect(getShipClassIconUrl(1207)).toContain('/game/crewtype1207.png');
    });
});
