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
        title: 'Bridge',
        url: '/',
        icon: Home,
    },
    {
        title: 'Star System',
        url: '/city',
        icon: Globe,
    },
    {
        title: 'Faction',
        icon: Shield,
        requireNation: true,
        items: [
            { title: 'Faction Info', url: '/nation' },
            { title: 'Officers', url: '/nation-generals' },
            { title: 'Planets', url: '/nation-cities' },
            { title: 'Fleet Org', url: '/troop' },
            { title: 'Diplomacy', url: '/diplomacy', requireSecret: true as const },
            { title: 'Personnel', url: '/personnel' },
            { title: 'Administration', url: '/internal-affairs', requireSecret: true as const },
            { title: 'Command HQ', url: '/chief', requireSecret: true as const },
            { title: 'NPC Policy', url: '/npc-control', requireSecret: true as const },
            { title: 'Intelligence', url: '/spy', requireSecret: true as const },
            { title: 'Military Review', url: '/battle', requireSecret: true as const },
        ],
    },
    {
        title: 'My Officer',
        icon: User,
        items: [
            { title: 'Profile & Settings', url: '/my-page' },
            { title: 'Officer Detail', url: '/general' },
            { title: 'Legacy', url: '/inherit' },
        ],
    },
    {
        title: 'Galaxy',
        icon: MapIcon,
        items: [
            { title: 'Galaxy Overview', url: '/global-diplomacy' },
            { title: 'All Factions', url: '/nations' },
            { title: 'All Officers', url: '/generals' },
            { title: 'Distinguished Officers', url: '/best-generals' },
            { title: 'NPC Roster', url: '/npc-list', requireNpcMode: true as const },
        ],
    },
    {
        title: 'Records',
        icon: ScrollText,
        items: [
            { title: 'Chronicle', url: '/history' },
            { title: 'Hall of Fame', url: '/hall-of-fame' },
            { title: 'Sovereigns', url: '/emperor' },
        ],
    },
    {
        title: 'Comms',
        icon: MessageSquare,
        items: [
            { title: 'War Room', url: '/board', requireNation: true as const },
            { title: 'Classified', url: '/board?secret=true', requireSecret: true as const },
            { title: 'Messages', url: '/messages' },
        ],
    },
    {
        title: 'Services',
        icon: Gamepad2,
        items: [
            { title: 'Tournament', url: '/tournament' },
            { title: 'Auction', url: '/auction' },
            { title: 'Betting', url: '/betting' },
            { title: 'Faction Betting', url: '/nation-betting' },
            { title: 'Battle Sim', url: '/battle-simulator' },
        ],
    },
    {
        title: 'System',
        icon: Settings,
        items: [
            { title: 'Server Info', url: '/traffic' },
            { title: 'Survey', url: '/vote' },
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
                    OPEN LOGH
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
                    {myOfficer?.name || 'No officer'}
                </div>
            </SidebarFooter>
        </Sidebar>
    );
}
