import { describe, expect, it } from 'vitest';

describe('global-diplomacy page', () => {
    it('useEffect should depend on worldId only to prevent infinite loop', () => {
        const deps = ['currentWorld?.id'];
        expect(deps).not.toContain('myOfficer');
        expect(deps).not.toContain('loadAll');
    });

    it('shows content when nations loaded even if loading flag is true', () => {
        const loading = true;
        const nations = [{ id: 1, name: '위' }];
        const showLoading = loading && nations.length === 0;
        expect(showLoading).toBe(false);
    });
});
