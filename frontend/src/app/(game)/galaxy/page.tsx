'use client';

import { useWorldStore } from '@/stores/worldStore';
import { GalaxyMap } from '@/components/galaxy/GalaxyMap';

export default function GalaxyPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);

    if (!currentWorld) {
        return (
            <div className="flex items-center justify-center h-64 text-gray-500">
                Loading...
            </div>
        );
    }

    return (
        <div className="flex flex-col h-[calc(100vh-8rem)]">
            <h1 className="text-lg font-bold px-2 py-1">은하 지도</h1>
            <div className="flex-1 min-h-0">
                <GalaxyMap sessionId={currentWorld.id} />
            </div>
        </div>
    );
}
