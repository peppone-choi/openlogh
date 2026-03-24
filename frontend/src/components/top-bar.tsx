'use client';

import { useEffect, useState } from 'react';
import { Bell, LogOut, Zap } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { TurnTimer } from '@/components/game/turn-timer';
import { ResourceDisplay } from '@/components/game/resource-display';
import { OfficerPortrait } from '@/components/game/officer-portrait';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useRouter } from 'next/navigation';

function formatCompact(n: number): string {
    if (n >= 10000) return `${(n / 10000).toFixed(1)}만`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
    return n.toLocaleString();
}

/** Format game date as "우주력 XXX년 X월" */
function formatGameDate(year?: number, month?: number): string {
    if (!year) return '';
    const monthStr = month ? ` ${month}월` : '';
    return `우주력 ${year}년${monthStr}`;
}

/** CP recovery countdown (every 5 real-time minutes) */
function useCpCountdown() {
    const [remaining, setRemaining] = useState(0);
    useEffect(() => {
        const CP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
        const update = () => {
            const now = Date.now();
            const elapsed = now % CP_INTERVAL_MS;
            setRemaining(Math.ceil((CP_INTERVAL_MS - elapsed) / 1000));
        };
        update();
        const interval = setInterval(update, 1000);
        return () => clearInterval(interval);
    }, []);
    const m = Math.floor(remaining / 60);
    const s = remaining % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
}

interface TopBarProps {
    onMessageClick?: () => void;
    onMobileMenuClick?: () => void;
}

export function TopBar({ onMessageClick, onMobileMenuClick }: TopBarProps) {
    const router = useRouter();
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();
    const cpCountdown = useCpCountdown();

    if (!currentWorld || !myOfficer) return null;

    const worldDate =
        currentWorld.currentYear && currentWorld.currentMonth
            ? `${currentWorld.currentYear}년 ${currentWorld.currentMonth}월`
            : '';
    const gameDate = formatGameDate(currentWorld.currentYear, currentWorld.currentMonth);
    const timeRatio = currentWorld.tickSeconds ? `${Math.round(86400 / currentWorld.tickSeconds)}x` : '';

    return (
        <header
            className="flex h-14 shrink-0 items-center gap-2 px-4 backdrop-blur-md"
            style={{
                background: 'rgba(8, 12, 20, 0.97)',
                borderBottom: '1px solid rgba(201, 168, 76, 0.2)',
                boxShadow: '0 1px 0 rgba(201, 168, 76, 0.08), 0 2px 12px rgba(0,0,0,0.5)',
            }}
        >
            <Button
                variant="ghost"
                className="flex md:hidden items-center gap-2 h-auto p-1"
                onClick={() => router.push('/general')}
                aria-label="제독 정보"
            >
                <OfficerPortrait picture={myOfficer.picture} name={myOfficer.name} size="xs" />
                <div className="flex flex-col min-w-0 items-start">
                    <span className="text-xs font-medium truncate max-w-[80px]">{myOfficer.name}</span>
                    {worldDate && <span className="text-[10px] text-muted-foreground">{worldDate}</span>}
                </div>
            </Button>

            <SidebarTrigger className="hidden md:flex" />
            <div className="hidden md:flex items-center gap-2 text-sm font-semibold">
                <span className="tracking-tight" style={{ color: 'var(--empire-gold)' }}>
                    오픈LOGH
                </span>
                {gameDate && <span className="text-muted-foreground text-xs font-mono">| {gameDate}</span>}
                {timeRatio && <span className="text-[10px] text-muted-foreground/60 font-mono">({timeRatio})</span>}
            </div>

            <div className="mx-auto md:mx-auto flex items-center gap-3">
                <TurnTimer />
                <div
                    className="hidden lg:flex items-center gap-1 text-[10px] text-muted-foreground font-mono"
                    title="CP 회복까지"
                >
                    <Zap className="size-3 text-amber-500/60" />
                    <span className="tabular-nums">{cpCountdown}</span>
                </div>
            </div>

            <div className="flex items-center gap-3">
                <div className="hidden md:block">
                    <ResourceDisplay funds={myOfficer.funds} supplies={myOfficer.supplies} ships={myOfficer.ships} />
                </div>

                <div className="flex md:hidden items-center gap-2">
                    <div className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-[#c9a84c]/15 border border-[#c9a84c]/25">
                        <span className="text-[9px] font-bold" style={{ color: 'var(--empire-gold)' }}>
                            자금
                        </span>
                        <span className="text-[11px] font-medium tabular-nums" style={{ color: 'var(--empire-gold)' }}>
                            {formatCompact(myOfficer.funds)}
                        </span>
                    </div>
                    <div className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-sky-500/15 border border-sky-500/25">
                        <span className="text-[9px] font-bold text-sky-400">물자</span>
                        <span className="text-[11px] font-medium tabular-nums text-sky-400">
                            {formatCompact(myOfficer.supplies)}
                        </span>
                    </div>
                </div>

                <Button variant="ghost" size="icon" onClick={onMessageClick} aria-label="메시지">
                    <Bell className="h-5 w-5" />
                </Button>

                <div className="hidden md:block">
                    <OfficerPortrait picture={myOfficer.picture} name={myOfficer.name} size="sm" />
                </div>

                <Button variant="ghost" size="sm" onClick={() => router.push('/lobby')}>
                    <LogOut className="h-4 w-4 md:mr-2" />
                    <span className="hidden md:inline">로비</span>
                </Button>
            </div>
        </header>
    );
}
