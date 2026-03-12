'use client';

import { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import { useGeneralStore } from '@/stores/generalStore';
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
    { href: '/troop', label: '부대편성', require: 'nation' },
    { href: '/diplomacy', label: '외교부', require: 'secret' },
    { href: '/personnel', label: '인사부', require: 'nation' },
    { href: '/internal-affairs', label: '내무부', require: 'secret' },
    { href: '/chief', label: '사령부', require: 'secret' },
    { href: '/npc-control', label: 'NPC정책', require: 'secret' },
    { href: '/spy', label: '암행부', require: 'secret' },
    { href: '/tournament', label: '토너먼트' },
    { href: '/nation', label: '세력정보', require: 'nation' },
    { href: '/nation-cities', label: '세력도시', require: 'nation' },
    { href: '/nation-generals', label: '세력장수', require: 'nation' },
    { href: '/global-diplomacy', label: '중원정보' },
    { href: '/city', label: '현재도시' },
    { href: '/battle', label: '감찰부', require: 'secret' },
    { href: '/inherit', label: '유산관리' },
    { href: '/my-page', label: '내정보&설정' },
    { href: '/auction', label: '경매장' },
    { href: '/betting', label: '베팅장' },
];

/* ── Legacy GlobalMenuDropdown parity: 정보 메뉴 items ── */
const GLOBAL_MENU: NavItem[] = [
    { href: '/nation-betting', label: '천통국 베팅' },
    { href: '/nations', label: '세력일람' },
    { href: '/generals', label: '장수일람' },
    { href: '/best-generals', label: '명장일람' },
    { href: '/hall-of-fame', label: '명예의전당' },
    { href: '/emperor', label: '왕조일람' },
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
    { key: 'header-nation', label: '국가 정보', header: true },
    { key: 'divider-nation', label: '', divider: true },
    { key: 'notice', label: '방침', selector: '.nationNotice' },
    { key: 'commands', label: '명령', selector: '.reservedCommandZone' },
    { key: 'nation', label: '국가', selector: '.nationInfo' },
    { key: 'general', label: '장수', selector: '.generalInfo' },
    { key: 'city', label: '도시', selector: '.cityInfo' },
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
    { key: 'national-talk', label: '국가', selector: '.NationalTalk' },
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

export function GameBottomBar({ onRefresh }: GameBottomBarProps) {
    const [openMenu, setOpenMenu] = useState<string | null>(null);
    const barRef = useRef<HTMLDivElement>(null);

    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const npcMode = isNpcModeEnabled(currentWorld?.config as Record<string, unknown> | undefined);
    const officerLevel = myGeneral?.officerLevel ?? 0;
    const inNation = officerLevel >= 1;
    const showSecret = officerLevel >= 2 || Number(myGeneral?.permission ?? 0) >= 1;

    function isVisible(item: NavItem): boolean {
        if (!item.require) return true;
        if (item.require === 'nation') return inNation;
        if (item.require === 'secret') return showSecret;
        return true;
    }

    // Close menu on outside click
    useEffect(() => {
        if (!openMenu) return;
        function handleClick(e: MouseEvent) {
            if (barRef.current && !barRef.current.contains(e.target as Node)) {
                setOpenMenu(null);
            }
        }
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, [openMenu]);

    function toggle(menu: string) {
        setOpenMenu((prev) => (prev === menu ? null : menu));
    }

    const filteredNation = NATION_MENU.filter(isVisible);
    const filteredGlobal = GLOBAL_MENU.filter((item) => !(item.cond === 'npcMode' && !npcMode));

    return (
        <div ref={barRef} className="fixed bottom-0 left-0 right-0 z-40 lg:hidden">
            {/* ── Dropup panels ── */}
            {openMenu === 'global' && (
                <DropupPanel columns={3}>
                    {filteredGlobal.map((item) => (
                        <Link
                            key={item.href}
                            href={item.href}
                            className="block px-3 py-1.5 text-sm hover:bg-muted/50"
                            onClick={() => setOpenMenu(null)}
                        >
                            {item.label}
                        </Link>
                    ))}
                </DropupPanel>
            )}

            {openMenu === 'nation' && (
                <DropupPanel columns={3}>
                    {filteredNation.map((item) => (
                        <Link
                            key={`${item.href}-${item.label}`}
                            href={item.href}
                            className="block px-3 py-1.5 text-sm hover:bg-muted/50"
                            onClick={() => setOpenMenu(null)}
                        >
                            {item.label}
                        </Link>
                    ))}
                </DropupPanel>
            )}

            {openMenu === 'quick' && (
                <DropupPanel columns={3}>
                    {QUICK_NAV.map((item) => {
                        if (item.divider) {
                            return <div key={item.key} className="col-span-3 border-t border-gray-600 my-0.5" />;
                        }
                        if (item.header) {
                            return (
                                <div
                                    key={item.key}
                                    className="col-span-3 px-3 py-1 text-xs text-muted-foreground font-bold"
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
                                    className="col-span-3 mx-2 my-1 px-3 py-1.5 text-sm text-center bg-muted/50 hover:bg-muted rounded"
                                    onClick={() => {
                                        setOpenMenu(null);
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
                                className="block w-full text-left px-3 py-1.5 text-sm hover:bg-muted/50"
                                onClick={() => {
                                    if (item.selector) scrollToSelector(item.selector);
                                    setOpenMenu(null);
                                }}
                            >
                                {item.label}
                            </button>
                        );
                    })}
                </DropupPanel>
            )}

            {/* ── Bottom bar buttons ── */}
            <div className="border-t border-border bg-card flex">
                <BottomBtn label="외부 메뉴" active={openMenu === 'global'} onClick={() => toggle('global')} />
                <BottomBtn
                    label="국가 메뉴"
                    active={openMenu === 'nation'}
                    onClick={() => toggle('nation')}
                    className="bg-[#00582c]"
                />
                <BottomBtn label="빠른 이동" active={openMenu === 'quick'} onClick={() => toggle('quick')} />
                <button
                    type="button"
                    className="flex-1 border-l border-gray-600 bg-[#00582c] py-2.5 text-center text-sm font-bold text-white hover:bg-[#006a33]"
                    onClick={() => {
                        setOpenMenu(null);
                        onRefresh?.();
                    }}
                >
                    갱신
                </button>
            </div>
        </div>
    );
}

/* ── Sub-components ── */

function DropupPanel({ columns, children }: { columns: number; children: React.ReactNode }) {
    return (
        <div
            className="border-t border-gray-600 bg-[#111] overflow-y-auto"
            style={{
                maxHeight: 'calc(100vh - 50px)',
                display: 'grid',
                gridTemplateColumns: `repeat(${columns}, 1fr)`,
            }}
        >
            {children}
        </div>
    );
}

function BottomBtn({
    label,
    active,
    onClick,
    className,
}: {
    label: string;
    active: boolean;
    onClick: () => void;
    className?: string;
}) {
    return (
        <button
            type="button"
            className={`flex-1 border-l border-gray-600 py-2.5 text-center text-sm font-bold ${
                active ? 'bg-[#141c65] text-white' : 'bg-[#111] text-white hover:bg-[#1c1c1c]'
            } ${className ?? ''}`}
            onClick={onClick}
        >
            {label}
        </button>
    );
}
