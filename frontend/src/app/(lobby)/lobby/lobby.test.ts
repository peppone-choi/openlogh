import { describe, expect, it } from 'vitest';

describe('lobby getServerPhase', () => {
    function getServerPhase(meta: Record<string, unknown>, config: Record<string, unknown>) {
        if (meta.finished || meta.isFinished) return '종료';
        if (meta.isLocked || meta.locked) return '잠김';
        const now = new Date();
        const startTime = config.startTime as string | undefined;
        if (startTime && new Date(startTime) > now) return '예약중';
        const opentime = config.opentime as string | undefined;
        if (meta.phase === 'pre_open' || meta.phase === 'reserved' || (opentime && new Date(opentime) > now))
            return '가오픈';
        return '오픈';
    }

    it('detects reserved when startTime is in future', () => {
        const startTime = new Date(Date.now() + 3600000).toISOString();
        const opentime = new Date(Date.now() + 7200000).toISOString();
        expect(getServerPhase({}, { startTime, opentime })).toBe('예약중');
    });

    it('detects pre_open via config.opentime in future', () => {
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(getServerPhase({}, { opentime })).toBe('가오픈');
    });

    it('detects pre_open when startTime passed but opentime in future', () => {
        const startTime = new Date(Date.now() - 3600000).toISOString();
        const opentime = new Date(Date.now() + 3600000).toISOString();
        expect(getServerPhase({}, { startTime, opentime })).toBe('가오픈');
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
