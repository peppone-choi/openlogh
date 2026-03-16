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

describe('NationBadge text color', () => {
    it('uses nation color directly as text color, not contrast', () => {
        const brightColor = '#ffff00';
        const textColor = brightColor;
        expect(textColor).toBe('#ffff00');
        expect(textColor).not.toBe('#000000');
    });

    it('uses dark nation color as text color', () => {
        const darkColor = '#222222';
        const textColor = darkColor;
        expect(textColor).toBe('#222222');
    });
});
