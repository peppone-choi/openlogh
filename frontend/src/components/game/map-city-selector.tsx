'use client';

import { useState } from 'react';
import { ResponsiveSheet } from '@/components/responsive-sheet';
import { MapViewer } from '@/components/game/map-viewer';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';
import type { City } from '@/types';

interface MapCitySelectorProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSelect: (cityId: number) => void;
    title?: string;
}

export function MapCitySelector({ open, onOpenChange, onSelect, title = '행성 선택' }: MapCitySelectorProps) {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const cities = useGameStore((s) => s.cities);

    const handleCityClick = (cityId: number) => {
        onSelect(cityId);
        onOpenChange(false);
    };

    const selectedCity = cities.find((c) => c.id);
    const cityName = selectedCity?.name;

    return (
        <ResponsiveSheet open={open} onOpenChange={onOpenChange} title={title}>
            <div className="space-y-2">
                {cityName && (
                    <div className="text-xs text-muted-foreground px-2">지도에서 행성을 클릭하여 선택하세요</div>
                )}
                <div className="overflow-auto max-h-[60vh]">
                    {currentWorld && (
                        <MapViewer
                            worldId={currentWorld.id}
                            mapCode={(currentWorld.config as Record<string, string>)?.mapCode ?? 'che'}
                            interactive={true}
                            onCitySelect={handleCityClick}
                        />
                    )}
                </div>
            </div>
        </ResponsiveSheet>
    );
}
