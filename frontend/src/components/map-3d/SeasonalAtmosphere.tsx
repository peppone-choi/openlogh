'use client';

import { useEffect } from 'react';
import { useThree } from '@react-three/fiber';
import { Fog, Color } from 'three';
import type { MapSeason } from '@/lib/map-constants';

interface SeasonalAtmosphereProps {
    season: MapSeason;
}

const SEASON_FOG_COLOR: Record<MapSeason, string> = {
    spring: '#c8e6c8',
    summer: '#e8d08a',
    fall: '#c89640',
    winter: '#d8e8f0',
};

const SEASON_FOG_FAR: Record<MapSeason, number> = {
    spring: 80,
    summer: 70,
    fall: 65,
    winter: 50,
};

const SEASON_SKY_COLOR: Record<MapSeason, string> = {
    spring: '#87CEEB',
    summer: '#4A90D9',
    fall: '#CD853F',
    winter: '#B0C4DE',
};

export function SeasonalAtmosphere({ season }: SeasonalAtmosphereProps) {
    const { scene, gl } = useThree();

    useEffect(() => {
        const fogColor = new Color(SEASON_FOG_COLOR[season]);
        const fogFar = SEASON_FOG_FAR[season];
        scene.fog = new Fog(fogColor, 10, fogFar);

        const skyColor = new Color(SEASON_SKY_COLOR[season]);
        gl.setClearColor(skyColor, 1);

        return () => {
            scene.fog = null;
        };
    }, [season, scene, gl]);

    return null;
}
