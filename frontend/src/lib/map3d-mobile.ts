export interface Map3DQuality {
    dpr: [number, number];
    shadows: boolean;
    terrainSegments: number;
    maxVisibleLabels: number;
    showNationTerritory: boolean;
    showWarEffects: boolean;
    showTroopMarkers: boolean;
    antialias: boolean;
    pixelRatio: number;
}

export function getHighQuality(): Map3DQuality {
    return {
        dpr: [1, 2],
        shadows: true,
        terrainSegments: 50,
        maxVisibleLabels: Infinity,
        showNationTerritory: true,
        showWarEffects: true,
        showTroopMarkers: true,
        antialias: true,
        pixelRatio: 2,
    };
}

export function getLowQuality(): Map3DQuality {
    return {
        dpr: [1, 1],
        shadows: false,
        terrainSegments: 25,
        maxVisibleLabels: 20,
        showNationTerritory: false,
        showWarEffects: false,
        showTroopMarkers: false,
        antialias: false,
        pixelRatio: 1,
    };
}

export function detectQuality(): Map3DQuality {
    if (typeof window === 'undefined') {
        return getHighQuality();
    }
    const isTouchDevice = navigator.maxTouchPoints > 0;
    const isNarrowScreen = window.screen.width < 768;
    const isLowDpr = window.devicePixelRatio <= 1;
    const isMobile = isTouchDevice && (isNarrowScreen || isLowDpr);
    return isMobile ? getLowQuality() : getHighQuality();
}
