'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
    Home,
    Swords,
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
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/8bit/collapsible';
import { useOfficerStore } from '@/stores/officerStore';
import { useWorldStore } from '@/stores/worldStore';

const navItems = [
    {
        title: '홈',
        url: '/',
        icon: Home,
    },
    {
        title: '도시',
        url: '/city',
        icon: Building2,
    },
    {
        title: '국가',
        icon: Castle,
        requireNation: true,
        items: [
            { title: '세력정보', url: '/nation' },
            { title: '세력장수', url: '/nation-generals' },
            { title: '세력도시', url: '/nation-cities' },
            { title: '부대편성', url: '/troop' },
            { title: '외교부', url: '/diplomacy', requireSecret: true as const },
            { title: '인사부', url: '/personnel' },
            { title: '내무부', url: '/internal-affairs', requireSecret: true as const },
            { title: '사령부', url: '/chief', requireSecret: true as const },
            { title: 'NPC정책', url: '/npc-control', requireSecret: true as const },
            { title: '암행부', url: '/spy', requireSecret: true as const },
            { title: '감찰부', url: '/battle', requireSecret: true as const },
        ],
    },
    {
        title: '내정보',
        icon: User,
        items: [
            { title: '내정보&설정', url: '/my-page' },
            { title: '내장수', url: '/general' },
            { title: '유산관리', url: '/inherit' },
        ],
    },
    {
        title: '중원',
        icon: MapIcon,
        items: [
            { title: '중원정보', url: '/global-diplomacy' },
            { title: '세력일람', url: '/nations' },
            { title: '장수일람', url: '/generals' },
            { title: '명장일람', url: '/best-generals' },
            { title: 'NPC일람', url: '/npc-list', requireNpcMode: true as const },
        ],
    },
    {
        title: '기록',
        icon: ScrollText,
        items: [
            { title: '연감', url: '/history' },
            { title: '명예의전당', url: '/hall-of-fame' },
            { title: '왕조일람', url: '/emperor' },
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
                <div className="group-data-[collapsible=icon]:hidden px-2 py-1 font-bold text-base">오픈삼국</div>
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
                <div className="group-data-[collapsible=icon]:hidden p-2 text-xs text-muted-foreground">
                    {myOfficer?.name || '장수 없음'}
                </div>
            </SidebarFooter>
        </Sidebar>
    );
}
