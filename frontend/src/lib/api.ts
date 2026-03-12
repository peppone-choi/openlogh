import axios, { AxiosError } from 'axios';

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
    headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
    if (typeof window !== 'undefined') {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
    }
    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            // Skip redirect for auth endpoints — let the login/register page handle errors directly
            const url = error.config?.url ?? '';
            const isAuthEndpoint = url.startsWith('/auth/');
            if (!isAuthEndpoint && typeof window !== 'undefined') {
                localStorage.removeItem('token');
                window.location.href = '/login';
            }
        }

        const apiError = error as AxiosError<
            | { error?: string; message?: string; errors?: Record<string, string> }
            | undefined
        >;
        const payload = apiError.response?.data;
        if (apiError.response?.status === 400) {
            const validationErrors = payload?.errors ? Object.values(payload.errors).filter(Boolean) : [];
            const message = payload?.error || payload?.message || validationErrors[0];
            if (message) {
                apiError.message = message;
            }
        }

        return Promise.reject(apiError);
    }
);

export default api;
