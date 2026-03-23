import { describe, expect, it } from 'vitest';
import { extractAuthErrorMessage } from '@/lib/auth-error';

describe('extractAuthErrorMessage', () => {
    it('prefers response.data.message', () => {
        const error = {
            response: {
                data: {
                    message: '로그인에 실패했습니다',
                    error: 'fallback error',
                    reason: 'fallback reason',
                },
            },
        };

        expect(extractAuthErrorMessage(error, 'fallback')).toBe('로그인에 실패했습니다');
    });

    it('uses response.data.error when message is missing', () => {
        const error = {
            response: {
                data: {
                    error: '회원가입이 차단되었습니다',
                },
            },
        };

        expect(extractAuthErrorMessage(error, 'fallback')).toBe('회원가입이 차단되었습니다');
    });

    it('uses response.data.reason when message and error are missing', () => {
        const error = {
            response: {
                data: {
                    reason: '유효하지 않은 인증 코드입니다',
                },
            },
        };

        expect(extractAuthErrorMessage(error, 'fallback')).toBe('유효하지 않은 인증 코드입니다');
    });

    it('falls back to plain error.message', () => {
        const error = { message: '네트워크 오류' };

        expect(extractAuthErrorMessage(error, 'fallback')).toBe('네트워크 오류');
    });

    it('returns fallback when nothing usable exists', () => {
        expect(extractAuthErrorMessage({}, 'fallback')).toBe('fallback');
    });
});
