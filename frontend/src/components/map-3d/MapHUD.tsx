'use client';

import { SEASON_LABELS } from '@/lib/map-constants';
import type { MapSeason } from '@/lib/map-constants';

interface MapHUDProps {
    viewMode: '2d' | '3d';
    onViewModeChange: (mode: '2d' | '3d') => void;
    season: MapSeason;
    mapCode: string;
}

export function MapHUD({ viewMode, onViewModeChange, season, mapCode }: MapHUDProps) {
    const seasonLabel = SEASON_LABELS[season] ?? '春';

    return (
        <div className="absolute inset-0 pointer-events-none">
            {/* Top-right: view mode toggle */}
            <div className="absolute top-2 right-2 flex gap-1 pointer-events-auto">
                <button
                    onClick={() => onViewModeChange('2d')}
                    className={`px-3 py-1.5 text-xs rounded font-medium transition-colors ${
                        viewMode === '2d' ? 'bg-white text-black' : 'bg-black/60 text-white hover:bg-black/80'
                    }`}
                >
                    2D
                </button>
                <button
                    onClick={() => onViewModeChange('3d')}
                    className={`px-3 py-1.5 text-xs rounded font-medium transition-colors ${
                        viewMode === '3d' ? 'bg-white text-black' : 'bg-black/60 text-white hover:bg-black/80'
                    }`}
                >
                    3D
                </button>
            </div>

            {/* Bottom-left: season + mapCode badge */}
            <div className="absolute bottom-2 left-2 pointer-events-none">
                <span className="px-2 py-1 text-xs rounded bg-black/60 text-white/80 font-medium">
                    {seasonLabel} · {mapCode}
                </span>
            </div>
        </div>
    );
}
