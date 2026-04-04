'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Skeleton } from '@/components/ui/8bit/skeleton';
import type { PublicCachedMapResponse } from '@/types';
import { MapViewer } from '@/components/game/map-viewer';
import { formatGameLogDate } from '@/lib/gameLogDate';
import { formatLog } from '@/lib/formatLog';
import { publicApi } from '@/lib/gameApi';

function LoadingCard() {
    return (
        <Card className="w-full max-w-[700px]">
            <CardHeader>
                <CardTitle>서버 현황</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                <Skeleton className="h-44 w-full" />
                <Skeleton className="h-4 w-24" />
                <div className="space-y-2">
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-5/6" />
                </div>
            </CardContent>
        </Card>
    );
}

export function ServerStatusCard() {
    const [data, setData] = useState<PublicCachedMapResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [selectedWorldId, setSelectedWorldId] = useState<number | null>(null);
    const [switching, setSwitching] = useState(false);

    const fetchMap = useCallback(async (worldId?: number) => {
        try {
            const { data: payload } = await publicApi.getCachedMap(worldId);
            return payload;
        } catch {
            return null;
        }
    }, []);

    useEffect(() => {
        const init = async () => {
            const payload = await fetchMap();
            setData(payload);
            if (payload?.worldId) {
                setSelectedWorldId(payload.worldId);
            }
            setLoading(false);
        };
        init();
    }, [fetchMap]);

    const handleTabClick = async (worldId: number) => {
        if (worldId === selectedWorldId) return;
        setSelectedWorldId(worldId);
        setSwitching(true);
        const payload = await fetchMap(worldId);
        if (payload) {
            setData(payload);
        }
        setSwitching(false);
    };

    if (loading) {
        return <LoadingCard />;
    }

    const worlds = data?.worlds ?? [];

    return (
        <Card className="w-full max-w-[700px]">
            <CardHeader className="pb-2">
                <CardTitle>서버 현황</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                {worlds.length > 1 && (
                    <div className="flex gap-1 overflow-x-auto">
                        {worlds.map((w) => (
                            <button
                                key={w.id}
                                type="button"
                                onClick={() => handleTabClick(w.id)}
                                className={`shrink-0 rounded-t px-3 py-1.5 text-xs font-medium transition-colors ${
                                    selectedWorldId === w.id
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-zinc-200'
                                }`}
                            >
                                {w.name}
                            </button>
                        ))}
                    </div>
                )}

                {!data?.available ? (
                    <div className="flex h-44 items-center justify-center border border-gray-700 bg-black/30 text-sm text-muted-foreground">
                        현재 가동 중인 서버가 없습니다
                    </div>
                ) : switching ? (
                    <div className="flex h-44 items-center justify-center border border-gray-700 bg-black/30 text-sm text-muted-foreground">
                        지도 로딩중...
                    </div>
                ) : (
                    <MapViewer publicData={data} interactive={false} />
                )}

                <div>
                    <h3 className="mb-2 text-sm font-semibold">중원 정세</h3>
                    {!data?.available || data.history.length === 0 ? (
                        <p className="text-sm text-muted-foreground">표시할 기록이 없습니다</p>
                    ) : (
                        <ul className="space-y-1">
                            {data.history.map((item) => (
                                <li key={item.id} className="text-sm leading-relaxed text-zinc-200">
                                    {formatGameLogDate(item) && (
                                        <span className="mr-2 text-xs text-muted-foreground">
                                            [{formatGameLogDate(item)}]
                                        </span>
                                    )}
                                    <span>{formatLog(item.text)}</span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
