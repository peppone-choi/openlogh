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
        label: 'Faction',
        items: [
            { href: '/board', label: 'War Room', require: 'nation' },
            { href: '/board?secret=true', label: 'Classified', require: 'secret' },
            { href: '/troop', label: 'Fleet Org', require: 'nation' },
            { href: '/diplomacy', label: 'Diplomacy', require: 'secret' },
            { href: '/personnel', label: 'Personnel', require: 'nation' },
            { href: '/internal-affairs', label: 'Administration', require: 'secret' },
            { href: '/chief', label: 'Command HQ', require: 'secret' },
            { href: '/npc-control', label: 'NPC Policy', require: 'secret' },
            { href: '/spy', label: 'Intelligence', require: 'secret' },
            { href: '/tournament', label: 'Tournament' },
            { href: '/nation', label: 'Faction Info', require: 'nation' },
            { href: '/nation-cities', label: 'Faction Planets', require: 'nation' },
            { href: '/nation-generals', label: 'Faction Officers', require: 'nation' },
            { href: '/global-diplomacy', label: 'Galaxy Overview' },
            { href: '/city', label: 'Current Planet' },
            { href: '/battle', label: 'Military Review', require: 'secret' },
            { href: '/inherit', label: 'Legacy' },
            { href: '/my-page', label: 'Profile & Settings' },
            { href: '/auction', label: 'Auction' },
            { href: '/betting', label: 'Betting' },
        ],
    },
    {
        label: 'Information',
        items: [
            { href: '/nation-betting', label: 'Faction Betting' },
            { href: '/nations', label: 'All Factions' },
            { href: '/generals', label: 'All Officers' },
            { href: '/best-generals', label: 'Distinguished Officers' },
            { href: '/hall-of-fame', label: 'Hall of Fame' },
            { href: '/emperor', label: 'Sovereigns' },
            { href: '/history', label: 'Chronicle' },
            { href: '/battle-simulator', label: 'Battle Simulator' },
            { href: '/traffic', label: 'Server Info' },
            { href: '/npc-list', label: 'NPC Roster', cond: 'npcMode' },
            { href: '/vote', label: 'Survey' },
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
                toast.error(`Officer ${prevGeneralRef.current.name} has been KIA. Returning to lobby.`, {
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
