import { describe, it, expect } from 'vitest';

describe('RecordZone stripYear logic', () => {
    it('should strip year pattern from message', () => {
        const message = '<C>●</>UC 795.9:<L><b>【이벤트】</b></>진정한 영웅들만이 재야로 등장하는 가상 시나리오.';
        const stripped = message.replace(/\d+년\s+\d+월:/g, '');
        expect(stripped).toBe('<C>●</><L><b>【이벤트】</b></>진정한 영웅들만이 재야로 등장하는 가상 시나리오.');
    });

    it('should preserve message without year pattern', () => {
        const message = '조조가 허창으로 이동하였습니다.';
        const stripped = message.replace(/\d+년\s+\d+월:/g, '');
        expect(stripped).toBe('조조가 허창으로 이동하였습니다.');
    });
});

describe('RecordZone stripYear date prefix', () => {
    it('should strip year from date prefix, keeping month', () => {
        const date = 'UC 795.9';
        const stripped = date.replace(/\d+년\s*/, '');
        expect(stripped).toBe('1월');
    });

    it('should handle date without year gracefully', () => {
        const date = '3월';
        const stripped = date.replace(/\d+년\s*/, '');
        expect(stripped).toBe('3월');
    });

    it('should return empty string if date is only year', () => {
        const date = 'UC 795.';
        const stripped = date.replace(/\d+년\s*/, '');
        expect(stripped).toBe('');
    });
});
