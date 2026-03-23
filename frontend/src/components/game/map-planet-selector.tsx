'use client';

import { ResponsiveSheet } from '@/components/responsive-sheet';
import { MapViewer } from '@/components/game/map-viewer';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';

interface MapPlanetSelectorProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSelect: (planetId: number) => void;
    title?: string;
}

export function MapPlanetSelector({ open, onOpenChange, onSelect, title = '행성 선택' }: MapPlanetSelectorProps) {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const cities = useGameStore((s) => s.cities);

    const handlePlanetClick = (planetId: number) => {
        onSelect(planetId);
        onOpenChange(false);
    };

    const selectedPlanet = cities.find((c) => c.id);
    const planetName = selectedPlanet?.name;

    return (
        <ResponsiveSheet open={open} onOpenChange={onOpenChange} title={title}>
            <div className="space-y-2">
                {planetName && (
                    <div className="text-xs text-muted-foreground px-2">지도에서 행성을 클릭하여 선택하세요</div>
                )}
                <div className="overflow-auto max-h-[60vh]">
                    {currentWorld && (
                        <MapViewer
                            worldId={currentWorld.id}
                            mapCode={(currentWorld.config as Record<string, string>)?.mapCode ?? 'che'}
                            interactive={true}
                            onCitySelect={handlePlanetClick}
                        />
                    )}
                </div>
            </div>
        </ResponsiveSheet>
    );
}

/** @deprecated Use MapPlanetSelector */
export { MapPlanetSelector as MapCitySelector };
