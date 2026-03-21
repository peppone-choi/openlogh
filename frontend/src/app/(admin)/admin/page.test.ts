import { describe, expect, it } from 'vitest';
import type { TimeControlRequest } from '@/types';

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
