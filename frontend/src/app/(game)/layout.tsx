'use client';

import { useEffect, useMemo, useCallback, useRef, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { toast, Toaster } from 'sonner';
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

// Nav sections — LOGH domain terminology
const navSections: NavSection[] = [
    {
        label: '진영',
        items: [
            { href: '/board', label: '작전회의실', require: 'nation' },
            { href: '/board?secret=true', label: '기밀실', require: 'secret' },
            { href: '/fleet', label: '함대편성', require: 'nation' },
            { href: '/diplomacy', label: '외교부', require: 'secret' },
            { href: '/personnel', label: '인사부', require: 'nation' },
            { href: '/internal-affairs', label: '내무부', require: 'secret' },
            { href: '/chief', label: '사령부', require: 'secret' },
            { href: '/npc-control', label: 'NPC정책', require: 'secret' },
            { href: '/spy', label: '정보부', require: 'secret' },
            { href: '/tournament', label: '토너먼트' },
            { href: '/faction', label: '진영정보', require: 'nation' },
            { href: '/faction-planets', label: '진영성계', require: 'nation' },
            { href: '/faction-officers', label: '진영제독', require: 'nation' },
            { href: '/global-diplomacy', label: '은하정세' },
            { href: '/planet', label: '현재성계' },
            { href: '/battle', label: '감찰부', require: 'secret' },
            { href: '/inherit', label: '유산관리' },
            { href: '/my-page', label: '내정보&설정' },
            { href: '/auction', label: '경매장' },
            { href: '/betting', label: '베팅장' },
        ],
    },
    {
        label: '정보',
        items: [
            { href: '/faction-betting', label: '통일진영 베팅' },
            { href: '/factions', label: '진영일람' },
            { href: '/officers', label: '제독일람' },
            { href: '/best-officers', label: '명제독일람' },
            { href: '/hall-of-fame', label: '명예의전당' },
            { href: '/sovereign', label: '왕조일람' },
            { href: '/history', label: '연감' },
            { href: '/battle-simulator', label: '전투 시뮬레이터' },
            { href: '/traffic', label: '접속량정보' },
            { href: '/npc-list', label: '빙의일람', cond: 'npcMode' },
            { href: '/vote', label: '설문조사' },
        ],
    },
];

export default function GameLayout({ children }: { children: React.ReactNode }) {
    const router = useRouter();
    const pathname = usePathname();
    const { isAuthenticated, isInitialized, initAuth, logout } = useAuthStore();
    const { currentWorld } = useWorldStore();
    const { myOfficer, loading: generalLoading, fetchMyOfficer } = useOfficerStore();

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
        if (generalLoading) return;

        if (!currentWorld || myOfficer === null) {
            if (prevGeneralRef.current !== null && currentWorld) {
                toast.error(`${prevGeneralRef.current.name} 제독이 사망하였습니다. 로비로 이동합니다.`, {
                    duration: 5000,
                });
            }
            router.replace('/lobby');
        }
        prevGeneralRef.current = myOfficer;
    }, [isAuthenticated, currentWorld, myOfficer, generalLoading, router]);

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
            handler: () => goTo('/officer'),
            description: 'My general',
        },
        {
            key: 'c',
            alt: true,
            handler: () => goTo('/planet'),
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
            handler: () => goTo('/faction'),
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

    // Render guard: block only during initial load.
    // myGeneral keeps its value during re-fetches (not reset to null),
    // so children stay mounted when pages call fetchMyOfficer.
    if (!isAuthenticated || !currentWorld || myOfficer === null) return null;

    // Reserved phase (startTime 전): 접근 불가 → 로비로
    const config = currentWorld?.config as Record<string, string> | undefined;
    const startTime = config?.startTime;
    const isReserved = startTime ? new Date() < new Date(startTime) : false;
    if (isReserved) {
        router.replace('/lobby');
        return null;
    }
    // Pre-open phase: only allow /my-page (사전 거병, 제독 삭제)
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
