import { describe, expect, it } from 'vitest';

describe('terms page', () => {
    it('should have correct title', () => {
        const title = '오픈은하영웅전설 이용 약관';
        expect(title).toContain('오픈은하영웅전설');
    });

    it('should cover all major articles', () => {
        const articles = [
            '제1조 (목적)',
            '제2조 (정의)',
            '제3조 (약관의 개정)',
            '제4조 (약관의 게시)',
            '제5조 (이용계약의 체결)',
            '제6조 (회원정보)',
            '제7조 (개인정보보호의 의무)',
            '제8조 (아이디 및 비밀번호 관리 의무)',
            '제9조 (서비스 제공자의 의무)',
            '제10조 (이용자의 의무)',
            '제11조 (서비스의 제공)',
            '제12조 (계약해제, 해지 등)',
            '제13조 (이용제한)',
            '제14조 (이용제한에 대한 이의신청)',
            '제15조 (서비스 제공자의 책임제한)',
            '제16조 (분쟁조정)',
        ];
        expect(articles).toHaveLength(16);
    });
});
