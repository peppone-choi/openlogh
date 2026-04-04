'use client';

import { Bell, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/8bit/button';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { TurnTimer } from '@/components/game/turn-timer';
import { ResourceDisplay } from '@/components/game/resource-display';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { useWorldStore } from '@/stores/worldStore';
import { useGeneralStore } from '@/stores/generalStore';
import { useRouter } from 'next/navigation';

function formatCompact(n: number): string {
    if (n >= 10000) return `${(n / 10000).toFixed(1)}만`;
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
    const { myGeneral } = useGeneralStore();

    if (!currentWorld || !myGeneral) return null;

    const worldDate =
        currentWorld.currentYear && currentWorld.currentMonth
            ? `${currentWorld.currentYear}년 ${currentWorld.currentMonth}월`
            : '';

    return (
        <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border bg-card/95 backdrop-blur-md px-4">
            <Button
                variant="ghost"
                className="flex md:hidden items-center gap-2 h-auto p-1"
                onClick={() => router.push('/general')}
                aria-label="장수 정보"
            >
                <GeneralPortrait picture={myGeneral.picture} name={myGeneral.name} size="xs" />
                <div className="flex flex-col min-w-0 items-start">
                    <span className="text-xs font-medium truncate max-w-[80px]">{myGeneral.name}</span>
                    {worldDate && <span className="text-[10px] text-muted-foreground">{worldDate}</span>}
                </div>
            </Button>

            <SidebarTrigger className="hidden md:flex" />
            <div className="hidden md:flex items-center gap-2 text-sm font-semibold">
                <span>오픈삼국</span>
                {worldDate && <span className="text-muted-foreground">| {worldDate}</span>}
            </div>

            <div className="mx-auto md:mx-auto">
                <TurnTimer />
            </div>

            <div className="flex items-center gap-3">
                <div className="hidden md:block">
                    <ResourceDisplay gold={myGeneral.gold} rice={myGeneral.rice} crew={myGeneral.crew} />
                </div>

                <div className="flex md:hidden items-center gap-2">
                    <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-yellow-500/20">
                        <div className="w-3.5 h-3.5 rounded-full bg-yellow-500 flex items-center justify-center">
                            <span className="text-[8px] text-yellow-950 font-bold">금</span>
                        </div>
                        <span className="text-[11px] font-medium text-yellow-400">{formatCompact(myGeneral.gold)}</span>
                    </div>
                    <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-green-500/20">
                        <div className="w-3.5 h-3.5 rounded-full bg-green-500 flex items-center justify-center">
                            <span className="text-[8px] text-green-950 font-bold">미</span>
                        </div>
                        <span className="text-[11px] font-medium text-green-400">{formatCompact(myGeneral.rice)}</span>
                    </div>
                </div>

                <Button variant="ghost" size="icon" onClick={onMessageClick} aria-label="메시지">
                    <Bell className="h-5 w-5" />
                </Button>

                <div className="hidden md:block">
                    <GeneralPortrait picture={myGeneral.picture} name={myGeneral.name} size="sm" />
                </div>

                <Button variant="ghost" size="sm" onClick={() => router.push('/lobby')}>
                    <LogOut className="h-4 w-4 md:mr-2" />
                    <span className="hidden md:inline">로비</span>
                </Button>
            </div>
        </header>
    );
}
