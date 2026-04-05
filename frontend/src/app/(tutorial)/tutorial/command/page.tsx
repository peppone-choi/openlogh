'use client';

import { useOfficerStore } from '@/stores/officerStore';
import { useWorldStore } from '@/stores/worldStore';
import { CommandPanel } from '@/components/game/command-panel';

export default function TutorialCommandPage() {
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const currentWorld = useWorldStore((s) => s.currentWorld);

    if (!myOfficer || !currentWorld) return null;

    return (
        <div className="max-w-2xl mx-auto p-4 pb-20">
            <CommandPanel generalId={myOfficer.id} realtimeMode={currentWorld.realtimeMode} />
        </div>
    );
}
