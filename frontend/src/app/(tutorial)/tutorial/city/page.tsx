'use client';

import { useState, useEffect } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { frontApi } from '@/lib/gameApi';
import type { FrontInfoResponse } from '@/types';
import { CityBasicCard } from '@/components/game/city-basic-card';

export default function TutorialCityPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const [frontInfo, setFrontInfo] = useState<FrontInfoResponse | null>(null);

    useEffect(() => {
        if (!currentWorld) return;
        frontApi
            .getInfo(currentWorld.id)
            .then(({ data }) => setFrontInfo(data))
            .catch(() => {});
    }, [currentWorld]);

    return (
        <div className="max-w-2xl mx-auto p-4 pb-20">
            <CityBasicCard city={frontInfo?.city ?? null} region={frontInfo?.city?.region} />
        </div>
    );
}
