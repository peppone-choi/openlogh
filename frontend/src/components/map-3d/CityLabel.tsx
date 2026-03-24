'use client';

import { Html } from '@react-three/drei';
import type { RenderCity } from '@/components/game/map-canvas';
import { getCityHeight } from '@/lib/map3d-utils';

interface CityLabelProps {
    city: RenderCity;
    position: [number, number, number];
}

export function CityLabel({ city, position }: CityLabelProps) {
    const labelY = getCityHeight(city.level) + 0.6;
    const labelPosition: [number, number, number] = [position[0], position[1] + labelY, position[2]];

    return (
        <Html position={labelPosition} center distanceFactor={12}>
            <div
                style={{
                    background: city.nationColor ? `${city.nationColor}cc` : '#333c',
                    color: 'white',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    fontSize: '10px',
                    whiteSpace: 'nowrap',
                    textAlign: 'center',
                    pointerEvents: 'none',
                    userSelect: 'none',
                }}
            >
                <div style={{ fontWeight: 'bold' }}>{city.name}</div>
                {city.nationAbbr && <div style={{ fontSize: '8px', opacity: 0.8 }}>{city.nationAbbr}</div>}
            </div>
        </Html>
    );
}
