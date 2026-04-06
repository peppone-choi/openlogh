'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
    Home,
    Swords,
    Globe,
    Shield,
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
        title: '대시보드',
        url: '/',
        icon: Home,
    },
    {
        title: '행성 관리',
        url: '/city',
        icon: Globe,
    },
    {
        title: '진영 정보',
        icon: Shield,
        requireNation: true,
        items: [
            { title: '진영 현황', url: '/nation' },
            { title: '장교 목록', url: '/nation-generals' },
            { title: '행성 목록', url: '/nation-cities' },
            { title: '함대 편성', url: '/troop' },
            { title: '외교', url: '/diplomacy', requireSecret: true as const },
            { title: '인사', url: '/personnel' },
            { title: '내정', url: '/internal-affairs', requireSecret: true as const },
            { title: '사령부', url: '/chief', requireSecret: true as const },
            { title: 'NPC 정책', url: '/npc-control', requireSecret: true as const },
            { title: '첩보', url: '/spy', requireSecret: true as const },
            { title: '전투 검토', url: '/battle', requireSecret: true as const },
        ],
    },
    {
        title: '내 장교',
        icon: User,
        items: [
            { title: '프로필 / 설정', url: '/my-page' },
            { title: '장교 상세', url: '/general' },
            { title: '후계', url: '/inherit' },
        ],
    },
    {
        title: '은하',
        icon: MapIcon,
        items: [
            { title: '은하 정세', url: '/global-diplomacy' },
            { title: '전체 진영', url: '/nations' },
            { title: '전체 장교', url: '/generals' },
            { title: '명장 목록', url: '/best-generals' },
            { title: 'NPC 명부', url: '/npc-list', requireNpcMode: true as const },
        ],
    },
    {
        title: '기록',
        icon: ScrollText,
        items: [
            { title: '연대기', url: '/history' },
            { title: '명예의 전당', url: '/hall-of-fame' },
            { title: '원수/의장', url: '/emperor' },
        ],
    },
    {
        title: '통신',
        icon: MessageSquare,
        items: [
            { title: '작전실', url: '/board', requireNation: true as const },
            { title: '기밀', url: '/board?secret=true', requireSecret: true as const },
            { title: '메일', url: '/messages' },
        ],
    },
    {
        title: '서비스',
        icon: Gamepad2,
        items: [
            { title: '토너먼트', url: '/tournament' },
            { title: '경매', url: '/auction' },
            { title: '베팅', url: '/betting' },
            { title: '진영 베팅', url: '/nation-betting' },
            { title: '전투 시뮬', url: '/battle-simulator' },
        ],
    },
    {
        title: '설정',
        icon: Settings,
        items: [
            { title: '서버 정보', url: '/traffic' },
            { title: '설문', url: '/vote' },
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
                <div className="group-data-[collapsible=icon]:hidden px-2 py-1 font-bold text-base tracking-wider text-[#00d4ff]">
                    오픈은하영웅전설
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
                <div className="group-data-[collapsible=icon]:hidden p-2 text-xs text-muted-foreground">
                    {myOfficer?.name || '장교 없음'}
                </div>
            </SidebarFooter>
        </Sidebar>
    );
}
