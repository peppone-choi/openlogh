import type { AxiosError } from 'axios';

export interface ApiErrorPayload {
    error?: string;
    message?: string;
    errors?: Record<string, string>;
}

export function applyApiErrorMessage(
    error: AxiosError<ApiErrorPayload | undefined>
): AxiosError<ApiErrorPayload | undefined> {
    if (error.response?.status !== 400) {
        return error;
    }

    const payload = error.response.data;
    const validationErrors = payload?.errors ? Object.values(payload.errors).filter(Boolean) : [];
    const message = payload?.error || payload?.message || validationErrors[0];

    if (message) {
        error.message = message;
    }

    return error;
}
