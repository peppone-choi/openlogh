import { describe, expect, it } from 'vitest';

describe('lobby getServerPhase', () => {
    function getServerPhase(meta: Record<string, unknown>, config: Record<string, unknown>) {
        if (meta.finished || meta.isFinished) return '종료';
        if (meta.isLocked || meta.locked) return '잠김';
        const opentime = config.opentime as string | undefined;
        if (
            meta.phase === 'pre_open' ||
            meta.isReserved ||
            meta.reserved ||
            (opentime && new Date(opentime) > new Date())
        )
            return '가오픈';
        return '오픈';
    }

    it('detects pre_open via config.opentime in future', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(getServerPhase({}, { opentime })).toBe('가오픈');
    });

    it('detects pre_open via meta.phase', () => {
        expect(getServerPhase({ phase: 'pre_open' }, {})).toBe('가오픈');
    });

    it('detects open when opentime is past', () => {
        const opentime = new Date(Date.now() - 3600000).toISOString();
        expect(getServerPhase({}, { opentime })).toBe('오픈');
    });

    it('detects open when no opentime', () => {
        expect(getServerPhase({}, {})).toBe('오픈');
    });

    it('detects finished', () => {
        expect(getServerPhase({ finished: true }, {})).toBe('종료');
    });

    it('detects locked', () => {
        expect(getServerPhase({ locked: true }, {})).toBe('잠김');
    });
});
