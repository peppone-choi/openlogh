'use client';

import { createContext, useContext, useMemo } from 'react';
import { detectQuality, getHighQuality } from '@/lib/map3d-mobile';
import type { Map3DQuality } from '@/lib/map3d-mobile';

const Map3DQualityContext = createContext<Map3DQuality>(getHighQuality());

export function Map3DQualityProvider({ children }: { children: React.ReactNode }) {
    const quality = useMemo(() => detectQuality(), []);
    return <Map3DQualityContext.Provider value={quality}>{children}</Map3DQualityContext.Provider>;
}

export function useMap3DQuality(): Map3DQuality {
    return useContext(Map3DQualityContext);
}
