import { describe, expect, it } from 'vitest';
import { getCrewTypeIconUrl, getPortraitUrl } from '@/lib/image';

describe('image helper URLs', () => {
    it('falls back to default silhouette when portrait is missing', () => {
        expect(getPortraitUrl()).toContain('/icons/default_silhouette.png');
    });

    it('maps default.jpg portrait to default silhouette', () => {
        expect(getPortraitUrl('default.jpg')).toContain('/icons/default_silhouette.png');
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

    it('resolves uploaded icon path against API base, not CDN', () => {
        const url = getPortraitUrl('/uploads/icons/abc.jpg');
        expect(url).toContain('/uploads/icons/abc.jpg');
        expect(url).not.toContain('cdn.jsdelivr.net');
    });

    it('maps legacy crewType 0 to crewtype1100 icon', () => {
        expect(getCrewTypeIconUrl(0)).toContain('/game/crewtype1100.png');
    });

    it('maps legacy crewType 3 to crewtype1400 icon', () => {
        expect(getCrewTypeIconUrl(3)).toContain('/game/crewtype1400.png');
    });

    it('keeps modern crewType ids as-is', () => {
        expect(getCrewTypeIconUrl(1207)).toContain('/game/crewtype1207.png');
    });
});
