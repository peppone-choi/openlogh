'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
    Home,
    Building2,
    Castle,
    User,
    Map as MapIcon,
    ScrollText,
    MessageSquare,
    Gamepad2,
    Settings,
    ChevronDown,
} from 'lucide-react';
import {
    Sidebar,
    SidebarContent,
    SidebarFooter,
    SidebarGroup,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarMenuSub,
    SidebarMenuSubButton,
    SidebarMenuSubItem,
} from '@/components/ui/sidebar';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { useOfficerStore } from '@/stores/officerStore';
import { useWorldStore } from '@/stores/worldStore';

const navItems = [
    {
        title: '홈',
        url: '/',
        icon: Home,
    },
    {
        title: '행성',
        url: '/planet',
        icon: Building2,
    },
    {
        title: '진영',
        icon: Castle,
        requireNation: true,
        items: [
            { title: '진영정보', url: '/faction' },
            { title: '진영제독', url: '/faction-officers' },
            { title: '진영행성', url: '/faction-planets' },
            { title: '함대편성', url: '/fleet' },
            { title: '조직도', url: '/org-chart' },
            { title: '요새관리', url: '/fortress' },
            { title: '외교부', url: '/diplomacy', requireSecret: true as const },
            { title: '인사부', url: '/personnel' },
            { title: '제안/명령', url: '/proposals' },
            { title: '내무부', url: '/internal-affairs', requireSecret: true as const },
            { title: '사령부', url: '/chief', requireSecret: true as const },
        ],
    },
    {
        title: '내정보',
        icon: User,
        items: [
            { title: '내정보&설정', url: '/my-page' },
            { title: '내제독', url: '/officer' },
            { title: '영향력', url: '/influence' },
            { title: '직무권한카드', url: '/position-cards' },
            { title: '유산관리', url: '/inherit' },
        ],
    },
    {
        title: '은하',
        icon: MapIcon,
        items: [
            { title: '은하정보', url: '/global-diplomacy' },
            { title: '진영일람', url: '/factions' },
            { title: '제독일람', url: '/officers' },
            { title: '명제독일람', url: '/best-officers' },
        ],
    },
    {
        title: '기록',
        icon: ScrollText,
        items: [
            { title: '연감', url: '/history' },
            { title: '명예의전당', url: '/hall-of-fame' },
            { title: '왕조일람', url: '/sovereign' },
        ],
    },
    {
        title: '소통',
        icon: MessageSquare,
        items: [
            { title: '회의실', url: '/board', requireNation: true as const },
            { title: '기밀실', url: '/board?secret=true', requireSecret: true as const },
            { title: '메시지', url: '/messages' },
        ],
    },
    {
        title: '부가',
        icon: Gamepad2,
        items: [
            { title: '토너먼트', url: '/tournament' },
            { title: '경매장', url: '/auction' },
            { title: '베팅장', url: '/betting' },
            { title: '천통국베팅', url: '/nation-betting' },
            { title: '전투시뮬', url: '/battle-simulator' },
        ],
    },
    {
        title: '설정',
        icon: Settings,
        items: [
            { title: '서버정보', url: '/traffic' },
            { title: 'NPC정책', url: '/npc-control', requireSecret: true as const },
            { title: '암행부', url: '/spy', requireSecret: true as const },
            { title: '감찰부', url: '/battle', requireSecret: true as const },
            { title: 'NPC일람', url: '/npc-list', requireNpcMode: true as const },
            { title: '설문조사', url: '/vote' },
        ],
    },
];

function isNpcModeEnabled(config: Record<string, unknown> | null | undefined): boolean {
    const raw = config?.npcMode ?? config?.npcmode;
    const numeric = typeof raw === 'number' ? raw : typeof raw === 'string' ? Number(raw) : 0;
    return Number.isFinite(numeric) && numeric > 0;
}

export function AppSidebar() {
    const pathname = usePathname();
    const { myOfficer } = useOfficerStore();
    const { currentWorld } = useWorldStore();

    const officerLevel = myOfficer?.officerLevel ?? 0;
    const inNation = officerLevel >= 1;
    const showSecret = inNation && officerLevel >= 2;
    const npcMode = isNpcModeEnabled(currentWorld?.config as Record<string, unknown> | undefined);

    const filteredItems = navItems
        .map((item) => {
            if (item.requireNation && !inNation) return null;

            const filteredSubItems = item.items
                ? item.items.filter((subItem) => {
                      if ('requireNation' in subItem && subItem.requireNation && !inNation) return false;
                      if ('requireSecret' in subItem && subItem.requireSecret && !showSecret) return false;
                      if ('requireNpcMode' in subItem && subItem.requireNpcMode && !npcMode) return false;
                      return true;
                  })
                : undefined;

            if (item.items && filteredSubItems?.length === 0) return null;

            return {
                ...item,
                items: filteredSubItems,
            };
        })
        .filter((item): item is NonNullable<typeof item> => item !== null);

    return (
        <Sidebar collapsible="icon">
            <SidebarHeader>
                <div
                    className="group-data-[collapsible=icon]:hidden px-3 py-2 font-bold text-sm tracking-widest uppercase border-b border-border"
                    style={{ color: 'var(--empire-gold)', letterSpacing: '0.12em' }}
                >
                    Open LOGH
                </div>
                <div className="group-data-[collapsible=icon]:hidden px-3 pb-1.5 text-[10px] text-muted-foreground tracking-widest">
                    은하영웅전설
                </div>
            </SidebarHeader>

            <SidebarContent>
                {filteredItems.map((item) => {
                    if (!item.items) {
                        return (
                            <SidebarGroup key={item.title}>
                                <SidebarMenu>
                                    <SidebarMenuItem>
                                        <SidebarMenuButton asChild isActive={pathname === item.url}>
                                            <Link href={item.url!}>
                                                <item.icon className="h-4 w-4" />
                                                <span>{item.title}</span>
                                            </Link>
                                        </SidebarMenuButton>
                                    </SidebarMenuItem>
                                </SidebarMenu>
                            </SidebarGroup>
                        );
                    }

                    return (
                        <Collapsible key={item.title} defaultOpen className="group/collapsible">
                            <SidebarGroup>
                                <SidebarGroupLabel asChild>
                                    <CollapsibleTrigger className="flex w-full items-center gap-2">
                                        <item.icon className="h-4 w-4" />
                                        <span>{item.title}</span>
                                        <ChevronDown className="ml-auto h-4 w-4 transition-transform group-data-[state=open]/collapsible:rotate-180" />
                                    </CollapsibleTrigger>
                                </SidebarGroupLabel>

                                <CollapsibleContent>
                                    <SidebarMenuSub>
                                        {item.items.map((subItem) => (
                                            <SidebarMenuSubItem key={subItem.url}>
                                                <SidebarMenuSubButton asChild isActive={pathname === subItem.url}>
                                                    <Link href={subItem.url}>
                                                        <span>{subItem.title}</span>
                                                    </Link>
                                                </SidebarMenuSubButton>
                                            </SidebarMenuSubItem>
                                        ))}
                                    </SidebarMenuSub>
                                </CollapsibleContent>
                            </SidebarGroup>
                        </Collapsible>
                    );
                })}
            </SidebarContent>

            <SidebarFooter>
                <div className="group-data-[collapsible=icon]:hidden border-t border-border px-3 py-2">
                    {myOfficer ? (
                        <div className="flex items-center gap-2">
                            <div
                                className="flex-shrink-0 w-6 h-6 rounded flex items-center justify-center text-[10px] font-bold"
                                style={{
                                    background: 'linear-gradient(135deg, var(--empire-gold) 0%, #a07820 100%)',
                                    color: '#0a0e1a',
                                }}
                            >
                                {myOfficer.officerLevel}
                            </div>
                            <div className="min-w-0">
                                <div className="text-xs font-semibold truncate" style={{ color: 'var(--empire-gold)' }}>
                                    {myOfficer.name}
                                </div>
                                <div className="text-[10px] text-muted-foreground">제독</div>
                            </div>
                        </div>
                    ) : (
                        <div className="text-xs text-muted-foreground">제독 없음</div>
                    )}
                </div>
            </SidebarFooter>
        </Sidebar>
    );
}
