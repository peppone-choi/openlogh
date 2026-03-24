'use client';

import type { RenderCity } from '@/components/game/map-canvas';
import { CityModel } from './CityModel';
import { CityLabel } from './CityLabel';

interface CityInteractionProps {
    cities: RenderCity[];
    positions: Map<number, [number, number, number]>;
    onCityClick?: (cityId: number) => void;
}

export function CityInteraction({ cities, positions, onCityClick }: CityInteractionProps) {
    return (
        <>
            {cities.map((city) => {
                const pos = positions.get(city.id);
                if (!pos) return null;
                return (
                    <group key={city.id}>
                        <CityModel city={city} position={pos} onClick={() => onCityClick?.(city.id)} />
                        <CityLabel city={city} position={pos} />
                    </group>
                );
            })}
        </>
    );
}
