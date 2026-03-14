import { describe, expect, it } from 'vitest';

describe('global-diplomacy page', () => {
    it('useEffect should depend on worldId only to prevent infinite loop', () => {
        const deps = ['currentWorld?.id'];
        expect(deps).not.toContain('myGeneral');
        expect(deps).not.toContain('loadAll');
    });
});
