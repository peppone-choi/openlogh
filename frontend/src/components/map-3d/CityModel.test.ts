import { describe, it, expect } from 'vitest';
import { getCityHeight } from '@/lib/map3d-utils';
import { getCityLevelIcon } from '@/lib/image';

describe('CityModel dependencies', () => {
    it('getCityHeight returns positive for all levels', () => {
        for (let level = 1; level <= 8; level++) {
            expect(getCityHeight(level)).toBeGreaterThan(0);
        }
    });

    it('getCityHeight increases with level', () => {
        expect(getCityHeight(8)).toBeGreaterThan(getCityHeight(1));
    });

    it('getCityLevelIcon returns valid URL for all levels', () => {
        for (let level = 1; level <= 8; level++) {
            const url = getCityLevelIcon(level);
            expect(url).toContain(`cast_${level}`);
            expect(url).toContain('.gif');
        }
    });
});
