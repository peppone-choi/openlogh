'use client';

import { Bell, LogOut, Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { TurnTimer } from '@/components/game/turn-timer';
import { ResourceDisplay } from '@/components/game/resource-display';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { useWorldStore } from '@/stores/worldStore';
import { useGeneralStore } from '@/stores/generalStore';
import { useRouter } from 'next/navigation';

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
        <header className="flex h-14 shrink-0 items-center gap-2 border-b bg-card px-4">
            <Button
                variant="ghost"
                size="icon"
                className="md:hidden"
                onClick={onMobileMenuClick}
                aria-label="메뉴 열기"
            >
                <Menu className="h-5 w-5" />
            </Button>

            <SidebarTrigger className="hidden md:flex" />

            <div className="flex items-center gap-2 text-sm font-semibold">
                <span className="hidden md:inline">오픈삼국</span>
                {worldDate && <span className="text-muted-foreground">| {worldDate}</span>}
            </div>

            <div className="mx-auto">
                <TurnTimer />
            </div>

            <div className="flex items-center gap-3">
                <div className="hidden md:block">
                    <ResourceDisplay gold={myGeneral.gold} rice={myGeneral.rice} crew={myGeneral.crew} />
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
