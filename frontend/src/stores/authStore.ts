import { create } from 'zustand';
import api from '@/lib/api';

const LOGIN_TOKEN_KEY = 'sammo_login_token';
export const OTP_TICKET_STORAGE_KEY = 'sammo_pending_otp_ticket';

interface User {
    id: number;
    loginId: string;
    displayName: string;
    role: string;
    picture?: string;
    oauthProviders?: import('@/types').OAuthProviderInfo[];
    thirdUse?: boolean;
}

interface LoginResult {
    otpRequired?: boolean;
    otpTicket?: string;
    validUntil?: string;
}

interface AuthApiResponse {
    token: string;
    user: User;
    nextToken?: [number, string];
    validUntil?: string;
    otpRequired?: boolean;
    otpTicket?: string;
}

interface AuthState {
    user: User | null;
    token: string | null;
    isAuthenticated: boolean;
    isInitialized: boolean;
    login: (loginId: string, password: string) => Promise<LoginResult | void>;
    loginWithToken: (token: string) => Promise<LoginResult | void>;
    verifyOtp: (otpTicket: string, otpCode: string) => Promise<LoginResult | void>;
    loginWithOAuth: (provider: string, code: string, redirectUri: string) => Promise<LoginResult | void>;
    register: (
        loginId: string,
        displayName: string,
        password: string,
        agreements?: { terms: boolean; privacy: boolean; thirdUse?: boolean }
    ) => Promise<LoginResult | void>;
    registerWithOAuth: (
        provider: string,
        code: string,
        redirectUri: string,
        displayName: string,
        agreements?: { terms: boolean; privacy: boolean; thirdUse?: boolean }
    ) => Promise<LoginResult | void>;
    logout: () => void;
    initAuth: () => void;
}

function parseTokenUser(token: string): User {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return {
        id: payload.userId,
        loginId: payload.sub,
        displayName: payload.displayName,
        role: payload.role || 'USER',
    };
}

function storePersistentLoginToken(nextToken?: [number, string]) {
    if (typeof window === 'undefined' || !nextToken) return;
    localStorage.setItem(LOGIN_TOKEN_KEY, JSON.stringify([1, nextToken, Date.now()]));
}

function applyAuthResult(
    set: (partial: Partial<AuthState>) => void,
    data: AuthApiResponse
): LoginResult {
    localStorage.setItem('token', data.token);
    storePersistentLoginToken(data.nextToken);
    if (typeof window !== 'undefined') {
        sessionStorage.removeItem(OTP_TICKET_STORAGE_KEY);
    }
    const parsed = parseTokenUser(data.token);
    const user = { ...data.user, role: parsed.role };
    set({ user, token: data.token, isAuthenticated: true });
    return data.validUntil ? { validUntil: data.validUntil } : {};
}

function extractOtpResult(data: AuthApiResponse): LoginResult | null {
    if (!data.otpRequired || !data.otpTicket) {
        return null;
    }
    if (typeof window !== 'undefined') {
        sessionStorage.setItem(OTP_TICKET_STORAGE_KEY, data.otpTicket);
    }
    return {
        otpRequired: true,
        otpTicket: data.otpTicket,
    };
}

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    token: null,
    isAuthenticated: false,
    isInitialized: false,

    login: async (loginId, password) => {
        const { data } = await api.post<AuthApiResponse>('/auth/login', { loginId, password });
        const otpResult = extractOtpResult(data);
        if (otpResult) {
            return otpResult;
        }
        return applyAuthResult(set, data);
    },

    loginWithToken: async (token: string) => {
        try {
            const { data } = await api.post<AuthApiResponse>('/auth/token-login', { token });
            const otpResult = extractOtpResult(data);
            if (otpResult) {
                return otpResult;
            }
            return applyAuthResult(set, data);
        } catch (error) {
            if (token.split('.').length === 3) {
                const user = parseTokenUser(token);
                const payload = JSON.parse(atob(token.split('.')[1]));
                if (payload.exp && payload.exp * 1000 < Date.now()) {
                    throw new Error('Token expired');
                }
                set({ user, token, isAuthenticated: true });
                return;
            }
            throw error;
        }
    },

    verifyOtp: async (otpTicket, otpCode) => {
        const { data } = await api.post<AuthApiResponse>('/auth/otp/verify', { otpTicket, otpCode });
        return applyAuthResult(set, data);
    },

    loginWithOAuth: async (provider, code, redirectUri) => {
        const { data } = await api.post<AuthApiResponse>('/auth/oauth/login', {
            provider,
            code,
            redirectUri,
        });
        const otpResult = extractOtpResult(data);
        if (otpResult) {
            return otpResult;
        }
        return applyAuthResult(set, data);
    },

    register: async (loginId, displayName, password, agreements) => {
        const { data } = await api.post<AuthApiResponse>('/auth/register', {
            loginId,
            displayName,
            password,
            ...(agreements
                ? {
                      agreeTerms: agreements.terms,
                      agreePrivacy: agreements.privacy,
                      agreeThirdUse: agreements.thirdUse ?? false,
                  }
                : {}),
        });
        const otpResult = extractOtpResult(data);
        if (otpResult) {
            return otpResult;
        }
        return applyAuthResult(set, data);
    },

    registerWithOAuth: async (provider, code, redirectUri, displayName, agreements) => {
        const { data } = await api.post<AuthApiResponse>('/auth/oauth/register', {
            provider,
            code,
            redirectUri,
            displayName,
            ...(agreements
                ? {
                      agreeTerms: agreements.terms,
                      agreePrivacy: agreements.privacy,
                      agreeThirdUse: agreements.thirdUse ?? false,
                  }
                : {}),
        });
        const otpResult = extractOtpResult(data);
        if (otpResult) {
            return otpResult;
        }
        return applyAuthResult(set, data);
    },

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem(LOGIN_TOKEN_KEY);
        if (typeof window !== 'undefined') {
            sessionStorage.removeItem(OTP_TICKET_STORAGE_KEY);
        }
        set({ user: null, token: null, isAuthenticated: false });
    },

    initAuth: () => {
        if (typeof window === 'undefined') return;
        const token = localStorage.getItem('token');
        if (token) {
            try {
                const user = parseTokenUser(token);
                const payload = JSON.parse(atob(token.split('.')[1]));
                if (payload.exp && payload.exp * 1000 < Date.now()) {
                    localStorage.removeItem('token');
                    set({ token: null, isAuthenticated: false, isInitialized: true });
                    return;
                }
                set({
                    token,
                    isAuthenticated: true,
                    isInitialized: true,
                    user,
                });
            } catch {
                localStorage.removeItem('token');
                set({ token: null, isAuthenticated: false, isInitialized: true });
            }
        } else {
            set({ isInitialized: true });
        }
    },
}));
