'use client';

import { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, Map as MapIcon, Castle, ScrollText, MoreHorizontal, RefreshCw } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet';
import { useOfficerStore } from '@/stores/officerStore';
import { useWorldStore } from '@/stores/worldStore';

interface GameBottomBarProps {
    onRefresh?: () => void;
}

/* ── Legacy MainControlDropdown parity: 국가 메뉴 items ── */
type NavRequire = 'nation' | 'secret';
interface NavItem {
    href: string;
    label: string;
    require?: NavRequire;
    cond?: 'npcMode';
}

function isNpcModeEnabled(config: Record<string, unknown> | null | undefined): boolean {
    const raw = config?.npcMode ?? config?.npcmode;
    const numeric = typeof raw === 'number' ? raw : typeof raw === 'string' ? Number(raw) : 0;
    return Number.isFinite(numeric) && numeric > 0;
}

const NATION_MENU: NavItem[] = [
    { href: '/board', label: '회의실', require: 'nation' },
    { href: '/board?secret=true', label: '기밀실', require: 'secret' },
    { href: '/fleet', label: '함대편성', require: 'nation' },
    { href: '/diplomacy', label: '외교부', require: 'secret' },
    { href: '/personnel', label: '인사부', require: 'nation' },
    { href: '/internal-affairs', label: '내무부', require: 'secret' },
    { href: '/chief', label: '사령부', require: 'secret' },
    { href: '/npc-control', label: 'NPC정책', require: 'secret' },
    { href: '/spy', label: '암행부', require: 'secret' },
    { href: '/tournament', label: '토너먼트' },
    { href: '/faction', label: '진영정보', require: 'nation' },
    { href: '/faction-planets', label: '진영행성', require: 'nation' },
    { href: '/faction-officers', label: '진영제독', require: 'nation' },
    { href: '/global-diplomacy', label: '은하정보' },
    { href: '/planet', label: '현재행성' },
    { href: '/battle', label: '감찰부', require: 'secret' },
    { href: '/inherit', label: '유산관리' },
    { href: '/my-page', label: '내정보&설정' },
    { href: '/auction', label: '경매장' },
    { href: '/betting', label: '베팅장' },
];

/* ── Legacy GlobalMenuDropdown parity: 정보 메뉴 items ── */
const GLOBAL_MENU: NavItem[] = [
    { href: '/faction-betting', label: '진영 베팅' },
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
];

/* ── Legacy 빠른 이동: scroll-to-section items ── */
interface QuickNavItem {
    key: string;
    label: string;
    selector?: string;
    header?: boolean;
    divider?: boolean;
    lobby?: boolean;
}

const QUICK_NAV: QuickNavItem[] = [
    { key: 'header-nation', label: '진영 정보', header: true },
    { key: 'divider-nation', label: '', divider: true },
    { key: 'notice', label: '방침', selector: '.nationNotice' },
    { key: 'commands', label: '명령', selector: '.reservedCommandZone' },
    { key: 'nation', label: '진영', selector: '.nationInfo' },
    { key: 'general', label: '제독', selector: '.generalInfo' },
    { key: 'planet', label: '행성', selector: '.planetInfo' },
    { key: 'header-record', label: '동향 정보', header: true },
    { key: 'divider-record', label: '', divider: true },
    { key: 'map', label: '지도', selector: '.mapView' },
    { key: 'public-record', label: '동향', selector: '.PublicRecord' },
    { key: 'general-log', label: '개인', selector: '.GeneralLog' },
    { key: 'world-history', label: '정세', selector: '.WorldHistory' },
    { key: 'divider-message', label: '', divider: true },
    { key: 'header-message', label: '메시지', header: true },
    { key: 'divider-message-2', label: '', divider: true },
    { key: 'public-talk', label: '전체', selector: '.PublicTalk' },
    { key: 'national-talk', label: '진영', selector: '.NationalTalk' },
    { key: 'private-talk', label: '개인', selector: '.PrivateTalk' },
    { key: 'diplomacy-talk', label: '외교', selector: '.DiplomacyTalk' },
    { key: 'lobby', label: '로비로', lobby: true },
];

function scrollToSelector(selector: string) {
    const el = document.querySelector(selector);
    if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

/* ── Tab configuration ── */
interface TabConfig {
    id: string;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    href?: string;
}

const TABS: TabConfig[] = [
    { id: 'home', label: '홈', icon: Home, href: '/' },
    { id: 'map', label: '지도', icon: MapIcon, href: '/map' },
    { id: 'nation', label: '진영', icon: Castle, href: '/faction' },
    { id: 'info', label: '정보', icon: ScrollText, href: '/officers' },
    { id: 'more', label: '더보기', icon: MoreHorizontal },
];

export function GameBottomBar({ onRefresh }: GameBottomBarProps) {
    const [sheetOpen, setSheetOpen] = useState(false);
    const pathname = usePathname();

    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const npcMode = isNpcModeEnabled(currentWorld?.config as Record<string, unknown> | undefined);
    const officerLevel = myOfficer?.officerLevel ?? 0;
    const inNation = officerLevel >= 1;
    const showSecret = officerLevel >= 2 || Number(myOfficer?.permission ?? 0) >= 1;

    function isVisible(item: NavItem): boolean {
        if (!item.require) return true;
        if (item.require === 'nation') return inNation;
        if (item.require === 'secret') return showSecret;
        return true;
    }

    const filteredNation = NATION_MENU.filter(isVisible);
    const filteredGlobal = GLOBAL_MENU.filter((item) => !(item.cond === 'npcMode' && !npcMode));

    // Determine active tab based on pathname
    function getActiveTabId(): string {
        if (pathname === '/' || pathname === '/dashboard') return 'home';
        if (pathname.startsWith('/map')) return 'map';
        if (
            pathname.startsWith('/faction') ||
            pathname.startsWith('/board') ||
            pathname.startsWith('/fleet') ||
            pathname.startsWith('/diplomacy') ||
            pathname.startsWith('/personnel') ||
            pathname.startsWith('/internal-affairs') ||
            pathname.startsWith('/chief') ||
            pathname.startsWith('/npc-control') ||
            pathname.startsWith('/spy')
        )
            return 'nation';
        if (
            pathname.startsWith('/officers') ||
            pathname.startsWith('/factions') ||
            pathname.startsWith('/best-officers') ||
            pathname.startsWith('/hall-of-fame') ||
            pathname.startsWith('/sovereign') ||
            pathname.startsWith('/history') ||
            pathname.startsWith('/battle-simulator') ||
            pathname.startsWith('/traffic') ||
            pathname.startsWith('/npc-list') ||
            pathname.startsWith('/vote') ||
            pathname.startsWith('/faction-betting')
        )
            return 'info';
        return '';
    }

    const activeTabId = getActiveTabId();

    return (
        <>
            {/* ── Bottom Navigation Bar ── */}
            <nav className="fixed bottom-0 left-0 right-0 z-40 lg:hidden bg-card/95 backdrop-blur-md border-t border-border">
                <div className="flex items-stretch h-16">
                    {TABS.map((tab) => {
                        const isActive = activeTabId === tab.id;
                        const Icon = tab.icon;

                        if (tab.id === 'more') {
                            return (
                                <Sheet key={tab.id} open={sheetOpen} onOpenChange={setSheetOpen}>
                                    <SheetTrigger asChild>
                                        <button
                                            type="button"
                                            className={`flex-1 flex flex-col items-center justify-center gap-0.5 transition-all duration-150 ${
                                                isActive
                                                    ? 'text-primary'
                                                    : 'text-muted-foreground hover:text-foreground'
                                            }`}
                                        >
                                            <Icon
                                                className={`transition-all duration-150 ${
                                                    isActive ? 'w-6 h-6' : 'w-5 h-5'
                                                }`}
                                            />
                                            <span
                                                className={`text-[10px] transition-all duration-150 ${
                                                    isActive ? 'font-bold' : 'font-medium'
                                                }`}
                                            >
                                                {tab.label}
                                            </span>
                                            {isActive && (
                                                <span className="absolute bottom-1 w-1 h-1 rounded-full bg-primary" />
                                            )}
                                        </button>
                                    </SheetTrigger>
                                    <SheetContent
                                        side="bottom"
                                        className="h-[80vh] max-h-[600px] px-4 pb-[env(safe-area-inset-bottom)]"
                                    >
                                        <SheetHeader className="pb-2">
                                            <SheetTitle className="text-left">메뉴</SheetTitle>
                                        </SheetHeader>
                                        <div className="overflow-y-auto h-full pb-4">
                                            {/* Refresh Button */}
                                            <button
                                                type="button"
                                                className="w-full mb-4 flex items-center justify-center gap-2 py-3 bg-primary/10 hover:bg-primary/20 text-primary rounded-lg transition-colors"
                                                onClick={() => {
                                                    setSheetOpen(false);
                                                    onRefresh?.();
                                                }}
                                            >
                                                <RefreshCw className="w-4 h-4" />
                                                <span className="font-medium">갱신</span>
                                            </button>

                                            {/* Quick Navigation Section */}
                                            <div className="mb-4">
                                                <h3 className="text-xs font-bold text-muted-foreground mb-2 px-1">
                                                    빠른 이동
                                                </h3>
                                                <div className="grid grid-cols-3 gap-1">
                                                    {QUICK_NAV.map((item) => {
                                                        if (item.divider) {
                                                            return (
                                                                <div
                                                                    key={item.key}
                                                                    className="col-span-3 border-t border-border my-1"
                                                                />
                                                            );
                                                        }
                                                        if (item.header) {
                                                            return (
                                                                <div
                                                                    key={item.key}
                                                                    className="col-span-3 text-xs font-bold text-muted-foreground px-1 py-1"
                                                                >
                                                                    {item.label}
                                                                </div>
                                                            );
                                                        }
                                                        if (item.lobby) {
                                                            return (
                                                                <button
                                                                    key={item.key}
                                                                    type="button"
                                                                    className="col-span-3 mx-1 my-1 py-3 text-sm text-center bg-muted/50 hover:bg-muted rounded-lg transition-colors"
                                                                    onClick={() => {
                                                                        setSheetOpen(false);
                                                                        window.location.href = '/lobby';
                                                                    }}
                                                                >
                                                                    로비로
                                                                </button>
                                                            );
                                                        }
                                                        return (
                                                            <button
                                                                key={item.key}
                                                                type="button"
                                                                className="px-2 py-3 text-sm text-left hover:bg-muted/50 rounded-lg transition-colors min-h-[44px]"
                                                                onClick={() => {
                                                                    if (item.selector) {
                                                                        scrollToSelector(item.selector);
                                                                    }
                                                                    setSheetOpen(false);
                                                                }}
                                                            >
                                                                {item.label}
                                                            </button>
                                                        );
                                                    })}
                                                </div>
                                            </div>

                                            {/* Faction Menu Section */}
                                            <div className="mb-4">
                                                <h3 className="text-xs font-bold text-muted-foreground mb-2 px-1">
                                                    진영 메뉴
                                                </h3>
                                                <div className="grid grid-cols-3 gap-1">
                                                    {filteredNation.map((item) => (
                                                        <Link
                                                            key={`${item.href}-${item.label}`}
                                                            href={item.href}
                                                            className="px-2 py-3 text-sm hover:bg-muted/50 rounded-lg transition-colors min-h-[44px] flex items-center"
                                                            onClick={() => setSheetOpen(false)}
                                                        >
                                                            {item.label}
                                                        </Link>
                                                    ))}
                                                </div>
                                            </div>

                                            {/* Global Menu Section */}
                                            <div>
                                                <h3 className="text-xs font-bold text-muted-foreground mb-2 px-1">
                                                    외부 메뉴
                                                </h3>
                                                <div className="grid grid-cols-3 gap-1">
                                                    {filteredGlobal.map((item) => (
                                                        <Link
                                                            key={item.href}
                                                            href={item.href}
                                                            className="px-2 py-3 text-sm hover:bg-muted/50 rounded-lg transition-colors min-h-[44px] flex items-center"
                                                            onClick={() => setSheetOpen(false)}
                                                        >
                                                            {item.label}
                                                        </Link>
                                                    ))}
                                                </div>
                                            </div>
                                        </div>
                                    </SheetContent>
                                </Sheet>
                            );
                        }

                        // Regular navigation tabs
                        return (
                            <Link
                                key={tab.id}
                                href={tab.href || '#'}
                                className={`flex-1 flex flex-col items-center justify-center gap-0.5 transition-all duration-150 relative ${
                                    isActive ? 'text-primary' : 'text-muted-foreground hover:text-foreground'
                                }`}
                            >
                                <Icon className={`transition-all duration-150 ${isActive ? 'w-6 h-6' : 'w-5 h-5'}`} />
                                <span
                                    className={`text-[10px] transition-all duration-150 ${
                                        isActive ? 'font-bold' : 'font-medium'
                                    }`}
                                >
                                    {tab.label}
                                </span>
                                {isActive && <span className="absolute bottom-1 w-1 h-1 rounded-full bg-primary" />}
                            </Link>
                        );
                    })}
                </div>
                {/* Safe area padding */}
                <div className="h-[env(safe-area-inset-bottom)]" />
            </nav>
        </>
    );
}
