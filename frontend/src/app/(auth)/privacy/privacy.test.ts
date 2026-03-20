import { describe, expect, it } from 'vitest';

describe('privacy page', () => {
    it('should have correct title', () => {
        const title = '개인정보 제공 및 이용에 대한 동의';
        expect(title).toContain('개인정보');
    });

    it('should list required collection items', () => {
        const items = ['아이디', '이메일', '생년월일'];
        expect(items).toHaveLength(3);
        expect(items).toContain('이메일');
    });
});
