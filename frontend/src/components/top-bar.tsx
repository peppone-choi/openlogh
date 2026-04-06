'use client';

import { Bell, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/8bit/button';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { TurnTimer } from '@/components/game/turn-timer';
import { ResourceDisplay } from '@/components/game/resource-display';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useRouter } from 'next/navigation';

function formatCompact(n: number): string {
    if (n >= 10000) return `${(n / 10000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
    return n.toLocaleString();
}

interface TopBarProps {
    onMessageClick?: () => void;
    onMobileMenuClick?: () => void;
}

export function TopBar({ onMessageClick, onMobileMenuClick }: TopBarProps) {
    const router = useRouter();
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();

    if (!currentWorld || !myOfficer) return null;

    const worldDate =
        currentWorld.currentYear && currentWorld.currentMonth
            ? `UC ${currentWorld.currentYear}.${currentWorld.currentMonth}`
            : '';

    return (
        <header className="flex h-14 shrink-0 items-center gap-2 border-b border-[#1a2040] bg-[#0f1429]/95 backdrop-blur-md px-4">
            <Button
                variant="ghost"
                className="flex md:hidden items-center gap-2 h-auto p-1"
                onClick={() => router.push('/general')}
                aria-label="장교 정보"
            >
                <GeneralPortrait picture={myOfficer.picture} name={myOfficer.name} size="xs" />
                <div className="flex flex-col min-w-0 items-start">
                    <span className="text-xs font-medium truncate max-w-[80px]">{myOfficer.name}</span>
                    {worldDate && <span className="text-[10px] text-muted-foreground">{worldDate}</span>}
                </div>
            </Button>

            <SidebarTrigger className="hidden md:flex" />
            <div className="hidden md:flex items-center gap-2 text-sm font-semibold">
                <span className="text-[#00d4ff] tracking-wider">오픈은하영웅전설</span>
                {worldDate && <span className="text-muted-foreground">| {worldDate}</span>}
            </div>

            <div className="mx-auto md:mx-auto">
                <TurnTimer />
            </div>

            <div className="flex items-center gap-3">
                <div className="hidden md:block">
                    <ResourceDisplay gold={myOfficer.gold} rice={myOfficer.rice} crew={myOfficer.crew} />
                </div>

                <div className="flex md:hidden items-center gap-2">
                    <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-[#c9a84c]/20">
                        <div className="w-3.5 h-3.5 rounded-full bg-[#c9a84c] flex items-center justify-center">
                            <span className="text-[8px] text-black font-bold">자</span>
                        </div>
                        <span className="text-[11px] font-medium text-[#c9a84c]">{formatCompact(myOfficer.gold)}</span>
                    </div>
                    <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-[#00d4ff]/20">
                        <div className="w-3.5 h-3.5 rounded-full bg-[#00d4ff] flex items-center justify-center">
                            <span className="text-[8px] text-black font-bold">물</span>
                        </div>
                        <span className="text-[11px] font-medium text-[#00d4ff]">{formatCompact(myOfficer.rice)}</span>
                    </div>
                </div>

                <Button variant="ghost" size="icon" onClick={onMessageClick} aria-label="메일">
                    <Bell className="h-5 w-5" />
                </Button>

                <div className="hidden md:block">
                    <GeneralPortrait picture={myOfficer.picture} name={myOfficer.name} size="sm" />
                </div>

                <Button variant="ghost" size="sm" onClick={() => router.push('/lobby')}>
                    <LogOut className="h-4 w-4 md:mr-2" />
                    <span className="hidden md:inline">로비</span>
                </Button>
            </div>
        </header>
    );
}
