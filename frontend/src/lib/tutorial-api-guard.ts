import api from '@/lib/api';
import { getTutorialMockResponse } from '@/data/tutorial';

let requestInterceptorId: number | null = null;
let responseInterceptorId: number | null = null;

/**
 * Register axios interceptors that block real API calls during tutorial mode
 * and return mock responses instead.
 *
 * Strategy:
 *   1. Request interceptor aborts the real request via AbortController
 *      and stashes the mock data on the config object.
 *   2. Response error interceptor catches the abort, detects the stashed
 *      mock data, and resolves with it as if the server had replied.
 */
export function registerTutorialApiGuard(): void {
    // Prevent double-registration
    if (requestInterceptorId !== null) return;

    requestInterceptorId = api.interceptors.request.use((config) => {
        const mockData = getTutorialMockResponse(config.method, config.url);
        if (mockData !== undefined) {
            const controller = new AbortController();
            controller.abort();
            config.signal = controller.signal;
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            (config as any).__tutorialMock = mockData;
        }
        return config;
    });

    responseInterceptorId = api.interceptors.response.use(undefined, (error) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const mock = (error.config as any)?.__tutorialMock;
        if (mock !== undefined) {
            return Promise.resolve({ data: mock, status: 200, statusText: 'OK', headers: {}, config: error.config });
        }
        return Promise.reject(error);
    });
}

/**
 * Remove the tutorial API guard interceptors.
 */
export function ejectTutorialApiGuard(): void {
    if (requestInterceptorId !== null) {
        api.interceptors.request.eject(requestInterceptorId);
        requestInterceptorId = null;
    }
    if (responseInterceptorId !== null) {
        api.interceptors.response.eject(responseInterceptorId);
        responseInterceptorId = null;
    }
}
