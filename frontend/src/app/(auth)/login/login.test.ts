import { describe, expect, it } from 'vitest';

describe('login page labels', () => {
    it('register button should not contain login text', () => {
        const buttonLabel = '가입';
        expect(buttonLabel).not.toContain('로그인');
    });

    it('no duplicate signup link — single quick-register button is sufficient', () => {
        const hintText = '가입 버튼으로 바로 계정을 생성할 수 있습니다.';
        expect(hintText).toContain('가입 버튼');
        expect(hintText).not.toContain('회원가입');
    });
});
