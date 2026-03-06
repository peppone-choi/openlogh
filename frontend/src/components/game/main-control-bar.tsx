'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { useGeneralStore } from '@/stores/generalStore';
import { useWorldStore } from '@/stores/worldStore';
import { frontApi } from '@/lib/gameApi';

type CtrlRequire = 'nation' | 'secret';

interface CtrlItem {
    href: string;
    label: string;
    require?: CtrlRequire;
    highlight?: 'tournament' | 'betting';
}

// Legacy MainControlBar.vue parity — exact 20 items from legacy-core/hwe/ts/components/MainControlBar.vue
const CONTROLS: CtrlItem[] = [
    { href: '/board', label: '회의실', require: 'nation' },
    { href: '/board?secret=true', label: '기밀실', require: 'secret' },
    { href: '/troop', label: '부대편성', require: 'nation' },
    { href: '/diplomacy', label: '외교부', require: 'secret' },
    { href: '/personnel', label: '인사부', require: 'nation' },
    { href: '/internal-affairs', label: '내무부', require: 'secret' },
    { href: '/chief', label: '사령부', require: 'secret' },
    { href: '/npc-control', label: 'NPC정책', require: 'secret' },
    { href: '/spy', label: '암행부', require: 'secret' },
    { href: '/tournament', label: '토너먼트', highlight: 'tournament' },
    { href: '/nation', label: '세력정보', require: 'nation' },
    { href: '/nation-cities', label: '세력도시', require: 'nation' },
    { href: '/nation-generals', label: '세력장수', require: 'nation' },
    { href: '/diplomacy', label: '중원정보' },
    { href: '/city', label: '현재도시' },
    { href: '/battle', label: '감찰부', require: 'secret' },
    { href: '/inherit', label: '유산관리' },
    { href: '/my-page', label: '내정보&설정' },
    { href: '/auction', label: '경매장' },
    { href: '/betting', label: '베팅장', highlight: 'betting' },
];

export function MainControlBar() {
    const { currentWorld } = useWorldStore();
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const [isTournamentApplicationOpen, setIsTournamentApplicationOpen] = useState(false);
    const [isBettingActive, setIsBettingActive] = useState(false);

    const officerLevel = myGeneral?.officerLevel ?? 0;
    const inNation = officerLevel >= 1;
    const showSecret = inNation && officerLevel >= 2;

    const loadHighlights = useCallback(async () => {
        if (!currentWorld) {
            setIsTournamentApplicationOpen(false);
            setIsBettingActive(false);
            return;
        }
        try {
            const { data } = await frontApi.getInfo(currentWorld.id);
            setIsTournamentApplicationOpen(Boolean(data.global.isTournamentApplicationOpen));
            setIsBettingActive(Boolean(data.global.isBettingActive));
        } catch {
            setIsTournamentApplicationOpen(false);
            setIsBettingActive(false);
        }
    }, [currentWorld]);

    useEffect(() => {
        loadHighlights();
    }, [loadHighlights]);

    useEffect(() => {
        const timer = setInterval(() => {
            loadHighlights();
        }, 30_000);
        return () => clearInterval(timer);
    }, [loadHighlights]);

    function isDisabled(item: CtrlItem): boolean {
        if (!item.require) return false;
        if (item.require === 'nation') return !inNation;
        if (item.require === 'secret') return !showSecret;
        return false;
    }

    function isHighlighted(item: CtrlItem): boolean {
        if (item.highlight === 'tournament') return isTournamentApplicationOpen;
        if (item.highlight === 'betting') return isBettingActive;
        return false;
    }

    return (
        <div className="grid grid-cols-4 gap-[1px] bg-gray-600 lg:grid-cols-10">
            {CONTROLS.map((item, idx) => {
                const disabled = isDisabled(item);
                const highlighted = isHighlighted(item);
                return (
                    <Button
                        key={`${item.href}-${idx}`}
                        variant="outline"
                        size="sm"
                        asChild={!disabled}
                        disabled={disabled}
                        className={`h-7 border-0 bg-[#00582c] px-1 text-[11px] leading-none text-white hover:bg-[#006a33] ${disabled ? 'opacity-40 pointer-events-none' : ''} ${highlighted ? 'relative ring-1 ring-yellow-300 ring-offset-0 ring-inset' : ''}`}
                    >
                        {disabled ? (
                            <span className="truncate text-center">{item.label}</span>
                        ) : (
                            <Link href={item.href}>
                                <span className="relative inline-flex items-center justify-center gap-1 truncate text-center">
                                    {item.label}
                                    {highlighted && (
                                        <span className="inline-block size-1.5 rounded-full bg-yellow-300 animate-pulse" />
                                    )}
                                </span>
                            </Link>
                        )}
                    </Button>
                );
            })}
        </div>
    );
}
