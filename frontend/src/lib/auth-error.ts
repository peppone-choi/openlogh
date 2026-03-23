export function extractAuthErrorMessage(error: unknown, fallback: string): string {
    const responseData = (
        error as {
            response?: {
                data?: {
                    message?: string;
                    error?: string;
                    reason?: string;
                };
            };
            message?: string;
        }
    )?.response?.data;

    const message = responseData?.message || responseData?.error || responseData?.reason;
    if (message) {
        return message;
    }

    const plainMessage = (error as { message?: string })?.message;
    if (plainMessage && plainMessage.trim().length > 0) {
        return plainMessage;
    }

    return fallback;
}
