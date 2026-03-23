import { describe, expect, it } from 'vitest';
import type { TimeControlRequest } from '@/types';

/** ISO 문자열을 datetime-local 포맷 (로컬 시간)으로 변환 (page.tsx와 동일) */
function toLocalDatetime(iso: string | undefined | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** datetime-local 포맷으로 현재시각 + hours 시간 후 반환 (page.tsx와 동일) */
function futureLocal(hours: number): string {
    const d = new Date(Date.now() + hours * 3600_000);
    d.setMinutes(0, 0, 0);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

describe('futureLocal defaults', () => {
    it('returns datetime-local format (YYYY-MM-DDTHH:MM)', () => {
        const result = futureLocal(1);
        expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    });

    it('returns a future time', () => {
        const result = futureLocal(1);
        expect(new Date(result).getTime()).toBeGreaterThan(Date.now());
    });

    it('가오픈 default (+1h) is before 오픈 default (+24h)', () => {
        const preOpen = futureLocal(1);
        const open = futureLocal(24);
        expect(new Date(preOpen).getTime()).toBeLessThan(new Date(open).getTime());
    });
});

describe('toLocalDatetime', () => {
    it('converts ISO string to datetime-local format', () => {
        const result = toLocalDatetime('2026-04-01T09:00:00+09:00');
        expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
        expect(result).toContain('2026-04-01');
    });

    it('returns empty for undefined/null', () => {
        expect(toLocalDatetime(undefined)).toBe('');
        expect(toLocalDatetime(null)).toBe('');
    });

    it('returns empty for invalid date', () => {
        expect(toLocalDatetime('not-a-date')).toBe('');
    });

    it('falls back to opentime when reserveOpen is empty', () => {
        const reserveOpen = '';
        const opentime = '2026-04-01T00:00:00Z';
        const result = String(reserveOpen) || toLocalDatetime(opentime);
        expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    });
});

describe('admin settings save builds correct TimeControlRequest', () => {
    function buildTimeControlPayload(opts: {
        reserveOpen?: string;
        preReserveOpen?: string;
    }): Partial<TimeControlRequest> {
        const { reserveOpen, preReserveOpen } = opts;
        return {
            reserveOpen: reserveOpen || undefined,
            preReserveOpen: preReserveOpen || undefined,
            opentime: reserveOpen ? new Date(reserveOpen).toISOString() : undefined,
            startTime: preReserveOpen ? new Date(preReserveOpen).toISOString() : undefined,
        };
    }

    it('includes opentime and startTime when both dates are set', () => {
        const payload = buildTimeControlPayload({
            reserveOpen: '2026-04-01T00:00',
            preReserveOpen: '2026-03-25T00:00',
        });
        expect(payload.opentime).toBe(new Date('2026-04-01T00:00').toISOString());
        expect(payload.startTime).toBe(new Date('2026-03-25T00:00').toISOString());
        expect(payload.reserveOpen).toBe('2026-04-01T00:00');
        expect(payload.preReserveOpen).toBe('2026-03-25T00:00');
    });

    it('omits opentime and startTime when dates are empty', () => {
        const payload = buildTimeControlPayload({});
        expect(payload.opentime).toBeUndefined();
        expect(payload.startTime).toBeUndefined();
        expect(payload.reserveOpen).toBeUndefined();
        expect(payload.preReserveOpen).toBeUndefined();
    });

    it('includes only opentime when only reserveOpen is set', () => {
        const payload = buildTimeControlPayload({ reserveOpen: '2026-04-01T00:00' });
        expect(payload.opentime).toBe(new Date('2026-04-01T00:00').toISOString());
        expect(payload.startTime).toBeUndefined();
    });

    it('includes only startTime when only preReserveOpen is set', () => {
        const payload = buildTimeControlPayload({ preReserveOpen: '2026-03-25T00:00' });
        expect(payload.opentime).toBeUndefined();
        expect(payload.startTime).toBe(new Date('2026-03-25T00:00').toISOString());
    });
});
