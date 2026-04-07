'use client';

import { useEffect, useMemo, useCallback, useRef, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import { useAuthStore } from '@/stores/authStore';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useHotkeys } from '@/hooks/useHotkeys';
import { useSoundEffects } from '@/hooks/useSoundEffects';
import { SidebarProvider, SidebarInset } from '@/components/ui/sidebar';
import { AppSidebar } from '@/components/app-sidebar';
import { TopBar } from '@/components/top-bar';
import { MobileMenuSheet } from '@/components/mobile-menu-sheet';
import { ResponsiveSheet } from '@/components/responsive-sheet';
import { GameBottomBar } from '@/components/game/game-bottom-bar';

type NavRequire = 'nation' | 'secret';

interface NavItem {
    href: string;
    label: string;
    require?: NavRequire;
    cond?: 'npcMode';
}

interface NavSection {
    label: string;
    items: NavItem[];
}

function isNpcModeEnabled(config: Record<string, unknown> | null | undefined): boolean {
    const raw = config?.npcMode ?? config?.npcmode;
    const numeric = typeof raw === 'number' ? raw : typeof raw === 'string' ? Number(raw) : 0;
    return Number.isFinite(numeric) && numeric > 0;
}

const navSections: NavSection[] = [
    {
        label: '진영',
        items: [
            { href: '/board', label: '작전실', require: 'nation' },
            { href: '/board?secret=true', label: '기밀', require: 'secret' },
            { href: '/troop', label: '함대 편성', require: 'nation' },
            { href: '/diplomacy', label: '외교', require: 'secret' },
            { href: '/personnel', label: '인사', require: 'nation' },
            { href: '/internal-affairs', label: '내정', require: 'secret' },
            { href: '/chief', label: '사령부', require: 'secret' },
            { href: '/npc-control', label: 'NPC 정책', require: 'secret' },
            { href: '/spy', label: '첩보', require: 'secret' },
            { href: '/tournament', label: '토너먼트' },
            { href: '/nation', label: '진영 현황', require: 'nation' },
            { href: '/nation-cities', label: '진영 행성', require: 'nation' },
            { href: '/nation-generals', label: '진영 장교', require: 'nation' },
            { href: '/global-diplomacy', label: '은하 정세' },
            { href: '/city', label: '현재 행성' },
            { href: '/general', label: '내 장교' },
            { href: '/galaxy', label: '은하 지도' },
            { href: '/battle', label: '전술전 목록', require: 'secret' },
            { href: '/inherit', label: '후계' },
            { href: '/my-page', label: '프로필 / 설정' },
            { href: '/auction', label: '경매' },
            { href: '/betting', label: '베팅' },
        ],
    },
    {
        label: '정보',
        items: [
            { href: '/nation-betting', label: '진영 베팅' },
            { href: '/nations', label: '전체 진영' },
            { href: '/generals', label: '전체 장교' },
            { href: '/best-generals', label: '명장 목록' },
            { href: '/hall-of-fame', label: '명예의 전당' },
            { href: '/emperor', label: '원수/의장' },
            { href: '/history', label: '연대기' },
            { href: '/battle-simulator', label: '전투 시뮬' },
            { href: '/traffic', label: '서버 정보' },
            { href: '/npc-list', label: 'NPC 명부', cond: 'npcMode' },
            { href: '/vote', label: '설문' },
        ],
    },
];

export default function GameLayout({ children }: { children: React.ReactNode }) {
    const router = useRouter();
    const pathname = usePathname();
    const { isAuthenticated, isInitialized, initAuth, logout } = useAuthStore();
    const { currentWorld, isHydrated: worldHydrated } = useWorldStore();
    const { myOfficer, loading: generalLoading, fetchMyOfficer, isHydrated: generalHydrated } = useOfficerStore();

    useEffect(() => {
        initAuth();
    }, [initAuth]);

    useEffect(() => {
        if (isInitialized && !isAuthenticated) {
            router.replace('/login');
        }
    }, [isInitialized, isAuthenticated, router]);

    useEffect(() => {
        if (currentWorld) {
            fetchMyOfficer(currentWorld.id);
        }
    }, [currentWorld, fetchMyOfficer]);

    const prevGeneralRef = useRef(myOfficer);
    useEffect(() => {
        if (!isAuthenticated) return;
        if (!worldHydrated || !generalHydrated) return;
        if (generalLoading) return;

        if (!currentWorld || myOfficer === null) {
            if (prevGeneralRef.current !== null && currentWorld) {
                toast.error(`장교 ${prevGeneralRef.current.name}이(가) 전사했습니다. 로비로 이동합니다.`, {
                    duration: 5000,
                });
            }
            router.replace('/lobby');
        }
        prevGeneralRef.current = myOfficer;
    }, [isAuthenticated, currentWorld, myOfficer, generalLoading, worldHydrated, generalHydrated, router]);

    const { enabled: wsEnabled, toggleRealtime } = useWebSocket();
    const { soundEnabled, toggleSound } = useSoundEffects();

    // Global keyboard shortcuts for navigation
    const goTo = useCallback((path: string) => router.push(path), [router]);
    useHotkeys([
        {
            key: 'm',
            alt: true,
            handler: () => goTo('/map'),
            description: 'Go to map',
        },
        {
            key: 'g',
            alt: true,
            handler: () => goTo('/general'),
            description: 'My general',
        },
        {
            key: 'c',
            alt: true,
            handler: () => goTo('/city'),
            description: 'Current city',
        },
        {
            key: 'k',
            alt: true,
            handler: () => goTo('/commands'),
            description: 'Commands',
        },
        {
            key: 'b',
            alt: true,
            handler: () => goTo('/board'),
            description: 'Board',
        },
        {
            key: 's',
            alt: true,
            handler: () => goTo('/messages'),
            description: 'Messages',
        },
        {
            key: 'n',
            alt: true,
            handler: () => goTo('/nation'),
            description: 'Nation info',
        },
        {
            key: 'h',
            alt: true,
            handler: () => goTo('/'),
            description: 'Home/Dashboard',
        },
    ]);

    const officerLevel = myOfficer?.officerLevel ?? 0;
    const inNation = officerLevel >= 1;
    const showSecret = inNation && officerLevel >= 2;

    const filteredSections = useMemo(() => {
        const npcMode = isNpcModeEnabled(currentWorld?.config as Record<string, unknown> | undefined);
        return navSections
            .map((section) => ({
                ...section,
                items: section.items.filter((item) => {
                    if (item.require === 'nation' && !inNation) return false;
                    if (item.require === 'secret' && !showSecret) return false;
                    if (item.cond === 'npcMode' && !npcMode) return false;
                    return true;
                }),
            }))
            .filter((section) => section.items.length > 0);
    }, [currentWorld, inNation, showSecret]);

    const [messageSheetOpen, setMessageSheetOpen] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    // Render guard: block during initial load and store hydration.
    // myOfficer keeps its value during re-fetches (not reset to null),
    // so children stay mounted when pages call fetchMyOfficer.
    if (!isInitialized || !worldHydrated || !generalHydrated) return null;
    if (!isAuthenticated || !currentWorld || myOfficer === null) return null;

    // Reserved phase (startTime 전): 접근 불가 → 로비로
    const config = currentWorld?.config as Record<string, string> | undefined;
    const startTime = config?.startTime;
    const isReserved = startTime ? new Date() < new Date(startTime) : false;
    if (isReserved) {
        router.replace('/lobby');
        return null;
    }
    // Pre-open phase: only allow /my-page (사전 거병, 장교 삭제)
    const opentime = config?.opentime;
    const isPreOpen = opentime ? new Date() < new Date(opentime) : false;
    if (isPreOpen && pathname !== '/my-page') {
        router.replace('/my-page');
        return null;
    }

    return (
        <SidebarProvider defaultOpen>
            <AppSidebar />
            <SidebarInset>
                <TopBar
                    onMessageClick={() => setMessageSheetOpen(true)}
                    onMobileMenuClick={() => setMobileMenuOpen(true)}
                />
                <main className="flex flex-1 flex-col gap-4 px-2 py-4 pb-20 lg:pb-4">{children}</main>
            </SidebarInset>

            <ResponsiveSheet open={messageSheetOpen} onOpenChange={setMessageSheetOpen} title="메시지">
                <div className="p-4">메시지 기능 준비 중...</div>
            </ResponsiveSheet>

            <MobileMenuSheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
                <AppSidebar />
            </MobileMenuSheet>

            <GameBottomBar />

            <Toaster position="top-right" theme="dark" />
        </SidebarProvider>
    );
}
