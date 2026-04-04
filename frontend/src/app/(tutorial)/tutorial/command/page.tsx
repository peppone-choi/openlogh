'use client';

import { useGeneralStore } from '@/stores/generalStore';
import { useWorldStore } from '@/stores/worldStore';
import { CommandPanel } from '@/components/game/command-panel';

export default function TutorialCommandPage() {
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const currentWorld = useWorldStore((s) => s.currentWorld);

    if (!myGeneral || !currentWorld) return null;

    return (
        <div className="max-w-2xl mx-auto p-4 pb-20">
            <CommandPanel generalId={myGeneral.id} realtimeMode={currentWorld.realtimeMode} />
        </div>
    );
}
