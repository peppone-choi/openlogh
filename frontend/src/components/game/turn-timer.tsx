'use client';

import { useEffect, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';

export function TurnTimer() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const [remaining, setRemaining] = useState(0);

    useEffect(() => {
        if (!currentWorld?.tickSeconds || currentWorld.tickSeconds <= 0 || !currentWorld.updatedAt) return;
        const tickMs = currentWorld.tickSeconds * 1000;
        const updatedAtMs = new Date(currentWorld.updatedAt).getTime();

        const update = () => {
            const nextTurnAt = updatedAtMs + tickMs;
            const rem = Math.max(0, nextTurnAt - Date.now());
            setRemaining(Math.ceil(rem / 1000));
        };

        update();
        const interval = setInterval(update, 1000);
        return () => clearInterval(interval);
    }, [currentWorld?.tickSeconds, currentWorld?.updatedAt]);

    if (!currentWorld?.tickSeconds || currentWorld.tickSeconds <= 0) return null;

    const minutes = Math.floor(remaining / 60);
    const seconds = remaining % 60;

    return (
        <span className="text-xs text-muted-foreground tabular-nums">
            {minutes}:{seconds.toString().padStart(2, '0')}
        </span>
    );
}
