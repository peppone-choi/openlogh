import { describe, expect, it } from 'vitest';
import type { AxiosError } from 'axios';
import { applyApiErrorMessage, type ApiErrorPayload } from '@/lib/api-error';

function createAxiosError(
    status: number,
    data: ApiErrorPayload | undefined,
    message = 'Request failed'
): AxiosError<ApiErrorPayload | undefined> {
    return {
        name: 'AxiosError',
        message,
        config: {},
        isAxiosError: true,
        toJSON: () => ({}),
        response: {
            status,
            statusText: 'error',
            headers: {},
            config: {},
            data,
        },
    } as AxiosError<ApiErrorPayload | undefined>;
}

describe('applyApiErrorMessage', () => {
    it('uses backend error for 400 responses', () => {
        const error = createAxiosError(400, { error: '능력치 합계가 350이어야 합니다.' });

        const result = applyApiErrorMessage(error);

        expect(result.message).toBe('능력치 합계가 350이어야 합니다.');
    });

    it('uses field validation messages for 400 responses', () => {
        const error = createAxiosError(400, { errors: { name: '이름을 입력해주세요.' } });

        const result = applyApiErrorMessage(error);

        expect(result.message).toBe('이름을 입력해주세요.');
    });

    it('does not override non-400 messages', () => {
        const error = createAxiosError(500, { error: 'server exploded' }, 'Internal Server Error');

        const result = applyApiErrorMessage(error);

        expect(result.message).toBe('Internal Server Error');
    });
});
