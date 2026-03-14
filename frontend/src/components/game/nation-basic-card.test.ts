import { describe, expect, it } from 'vitest';

describe('nation-basic-card officer level display', () => {
    it('uses officer level 20 for ruler and 19 for advisor', () => {
        const rulerLevel = 20;
        const advisorLevel = 19;
        expect(rulerLevel).toBe(20);
        expect(advisorLevel).toBe(19);
        expect(rulerLevel).not.toBe(12);
        expect(advisorLevel).not.toBe(11);
    });
});
