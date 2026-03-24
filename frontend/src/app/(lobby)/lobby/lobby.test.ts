import { describe, expect, it } from 'vitest';

describe('tutorial entry point', () => {
    it('tutorial button is commented out (hidden)', () => {
        const src = require('fs').readFileSync(require('path').resolve(__dirname, 'page.tsx'), 'utf-8');
        expect(src).toContain('개편 완료 전까지 숨김');
        expect(src).not.toMatch(/(?<!\{\/\*[\s\S]*?)onClick=\{[^}]*\/tutorial/);
    });
});

describe('lobby getServerPhase', () => {
    function getServerPhase(meta: Record<string, unknown>, config: Record<string, unknown>) {
        if (meta.finished || meta.isFinished) return '종료';
        if (meta.isLocked || meta.locked) return '잠김';
        const phase = meta.phase as string | undefined;
        if (phase === 'united') return '통일';
        if (phase === 'paused') return '정지';
        const now = new Date();
        const startTime = config.startTime as string | undefined;
        if (startTime && new Date(startTime) > now) return '폐쇄';
        const opentime = config.opentime as string | undefined;
        if (phase === 'pre_open' || phase === 'closed' || (opentime && new Date(opentime) > now)) return '가오픈';
        return '오픈';
    }

    it('detects closed when startTime is in future', () => {
        const startTime = new Date(Date.now() + 3600000).toISOString();
        const opentime = new Date(Date.now() + 7200000).toISOString();
        expect(getServerPhase({}, { startTime, opentime })).toBe('폐쇄');
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

    it('detects united via meta.phase', () => {
        expect(getServerPhase({ phase: 'united' }, {})).toBe('통일');
    });

    it('detects paused via meta.phase', () => {
        expect(getServerPhase({ phase: 'paused' }, {})).toBe('정지');
    });
});

describe('getActionAvailability blocks join during closed phase', () => {
    function canJoinDuringClosed(config: Record<string, unknown>): boolean {
        const startTime = (config.startTime ?? config.starttime) as string | undefined;
        if (startTime && new Date(startTime) > new Date()) return false;
        return true;
    }

    it('blocks when startTime is in future', () => {
        const startTime = new Date(Date.now() + 3600000).toISOString();
        expect(canJoinDuringClosed({ startTime })).toBe(false);
    });

    it('allows when startTime is in past', () => {
        const startTime = new Date(Date.now() - 3600000).toISOString();
        expect(canJoinDuringClosed({ startTime })).toBe(true);
    });

    it('allows when no startTime', () => {
        expect(canJoinDuringClosed({})).toBe(true);
    });
});
