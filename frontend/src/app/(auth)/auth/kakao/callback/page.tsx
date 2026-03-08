'use client';

import { Suspense, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { toast } from 'sonner';
import { OTP_TICKET_STORAGE_KEY, useAuthStore } from '@/stores/authStore';
import { accountApi } from '@/lib/gameApi';
import { LoadingState } from '@/components/game/loading-state';

function KakaoCallbackPageContent() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const loginWithOAuth = useAuthStore((s) => s.loginWithOAuth);
    const registerWithOAuth = useAuthStore((s) => s.registerWithOAuth);

    useEffect(() => {
        const run = async () => {
            const code = searchParams.get('code');
            const mode = searchParams.get('mode');
            const provider = searchParams.get('provider') ?? 'kakao';

            if (!code) {
                toast.error('카카오 인증 코드가 없습니다.');
                router.replace(mode === 'link' ? '/account' : '/login');
                return;
            }

            const callbackQuery = new URLSearchParams();
            if (mode) callbackQuery.set('mode', mode);
            if (provider) callbackQuery.set('provider', provider);
            const redirectUri = `${window.location.origin}/auth/kakao/callback${callbackQuery.toString() ? `?${callbackQuery.toString()}` : ''}`;

            try {
                if (mode === 'register') {
                    const result = await registerWithOAuth(provider, code, redirectUri, '');
                    if (
                        result &&
                        typeof result === 'object' &&
                        'otpRequired' in result &&
                        result.otpRequired &&
                        result.otpTicket
                    ) {
                        sessionStorage.setItem(OTP_TICKET_STORAGE_KEY, result.otpTicket);
                        toast.error('인증 코드를 입력해주세요.');
                        router.replace('/login?otp=1');
                        return;
                    }
                    toast.success('카카오 가입이 완료되었습니다.');
                    router.replace('/lobby');
                    return;
                }

                if (mode === 'link') {
                    await accountApi.completeOAuthLink(provider, code, redirectUri);
                    toast.success('카카오 계정 연동이 완료되었습니다.');
                    router.replace(`/account?oauth=linked&provider=${encodeURIComponent(provider)}`);
                    return;
                }

                const result = await loginWithOAuth(provider, code, redirectUri);
                if (
                    result &&
                    typeof result === 'object' &&
                    'otpRequired' in result &&
                    result.otpRequired &&
                    result.otpTicket
                ) {
                    sessionStorage.setItem(OTP_TICKET_STORAGE_KEY, result.otpTicket);
                    toast.error('인증 코드를 입력해주세요.');
                    router.replace('/login?otp=1');
                    return;
                }
                toast.success('카카오 로그인 성공');
                router.replace('/lobby');
            } catch (err: unknown) {
                const errData = (
                    err as {
                        response?: {
                            data?: { otpRequired?: boolean; otpTicket?: string; error?: string; reason?: string };
                        };
                    }
                )?.response?.data;
                if (errData?.otpRequired && errData.otpTicket) {
                    sessionStorage.setItem(OTP_TICKET_STORAGE_KEY, errData.otpTicket);
                    toast.error(errData.error ?? errData.reason ?? '인증 코드를 입력해주세요.');
                    router.replace('/login?otp=1');
                    return;
                }
                if (mode === 'register') {
                    toast.error('카카오 가입에 실패했습니다.');
                    router.replace('/register');
                    return;
                }

                if (mode === 'link') {
                    toast.error('카카오 계정 연동에 실패했습니다.');
                    router.replace(`/account?oauth=link_failed&provider=${encodeURIComponent(provider)}`);
                    return;
                }

                toast.error('카카오 로그인에 실패했습니다.');
                router.replace('/login');
            }
        };

        void run();
    }, [loginWithOAuth, registerWithOAuth, router, searchParams]);

    return <LoadingState message="카카오 인증 처리 중..." />;
}

export default function KakaoCallbackPage() {
    return (
        <Suspense fallback={<LoadingState message="카카오 인증 정보를 확인 중..." />}>
            <KakaoCallbackPageContent />
        </Suspense>
    );
}
