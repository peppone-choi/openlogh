'use client';

import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import type { General, Nation } from '@/types';
import { TroopMarker } from './TroopMarker';

interface TroopMarkersProps {
    generals: General[];
    cityPositions: Map<number, [number, number, number]>;
    nations: Nation[];
}

interface MarkerState {
    generalId: number;
    progress: number;
    direction: number; // 1 = forward, -1 = backward
}

export function TroopMarkers({ generals, cityPositions, nations }: TroopMarkersProps) {
    const CYCLE_DURATION = 3; // seconds for one pass

    // Build nation color lookup
    const nationColorMap = new Map<number, string>();
    for (const nation of nations) {
        nationColorMap.set(nation.id, nation.color);
    }

    // Filter moving generals
    const movingGenerals = generals.filter((g) => g.destCityId != null && g.destCityId !== g.cityId);

    // Per-general progress refs (keyed by generalId)
    const progressRefs = useRef<Map<number, MarkerState>>(new Map());

    // Initialize states for new generals
    for (const g of movingGenerals) {
        if (!progressRefs.current.has(g.id)) {
            progressRefs.current.set(g.id, { generalId: g.id, progress: 0, direction: 1 });
        }
    }

    useFrame((_, delta) => {
        for (const state of progressRefs.current.values()) {
            state.progress += (delta / CYCLE_DURATION) * state.direction;
            if (state.progress >= 1) {
                state.progress = 1;
                state.direction = -1;
            } else if (state.progress <= 0) {
                state.progress = 0;
                state.direction = 1;
            }
        }
    });

    return (
        <>
            {movingGenerals.map((general) => {
                const from = cityPositions.get(general.cityId);
                const to = cityPositions.get(general.destCityId!);
                if (!from || !to) return null;

                const state = progressRefs.current.get(general.id);
                const progress = state?.progress ?? 0;
                const nationColor = nationColorMap.get(general.nationId) ?? '#ffffff';

                return (
                    <TroopMarker
                        key={general.id}
                        generalName={general.name}
                        nationColor={nationColor}
                        from={from}
                        to={to}
                        progress={progress}
                    />
                );
            })}
        </>
    );
}
