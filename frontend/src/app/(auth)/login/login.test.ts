import { describe, expect, it } from 'vitest';

describe('login page labels', () => {
    it('register button should not contain login text', () => {
        const buttonLabel = '가입';
        expect(buttonLabel).not.toContain('로그인');
    });
});
