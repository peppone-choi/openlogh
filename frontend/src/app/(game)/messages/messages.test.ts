import { describe, expect, it } from 'vitest';

describe('messages internal log filter', () => {
    function isInternalLog(payload: Record<string, unknown>): boolean {
        const msg = String(payload?.message ?? '');
        return msg.startsWith('[HISTORY]') || msg.startsWith('[GLOBAL]');
    }

    it('filters [HISTORY] prefixed messages', () => {
        expect(isInternalLog({ message: '[HISTORY]인재를 발견' })).toBe(true);
    });

    it('filters [GLOBAL] prefixed messages', () => {
        expect(isInternalLog({ message: '[GLOBAL]<Y>허소</>이(가) 인재를 발견' })).toBe(true);
    });

    it('keeps normal messages', () => {
        expect(isInternalLog({ message: '생산 개간을 하여 63 상승했습니다.' })).toBe(false);
    });

    it('handles missing message field', () => {
        expect(isInternalLog({})).toBe(false);
    });
});
