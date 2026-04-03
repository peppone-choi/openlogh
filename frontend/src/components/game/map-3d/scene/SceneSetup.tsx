'use client';
// Design Ref: §3.1 SceneSetup — 조명, 안개, 환경
import { useThree } from '@react-three/fiber';
import { useEffect } from 'react';
import type { MapSeason } from '@/lib/map-constants';

const SEASON_LIGHT: Record<MapSeason, { ambient: string; sun: string; intensity: number }> = {
  spring: { ambient: '#fff8e7', sun: '#ffe4b5', intensity: 1.2 },
  summer: { ambient: '#fffff0', sun: '#fff8dc', intensity: 1.5 },
  fall: { ambient: '#ffecd2', sun: '#daa520', intensity: 1.0 },
  winter: { ambient: '#e8eaf6', sun: '#b0c4de', intensity: 0.8 },
};

interface SceneSetupProps {
  season: MapSeason;
}

export function SceneSetup({ season }: SceneSetupProps) {
  const { scene } = useThree();
  const light = SEASON_LIGHT[season];

  useEffect(() => {
    scene.background = null;
  }, [scene]);

  return (
    <>
      <ambientLight intensity={0.85} color={light.ambient} />
      <directionalLight
        position={[200, 400, 100]}
        intensity={light.intensity}
        castShadow={false}
      />
      <hemisphereLight
        args={['#87CEEB', '#8B7355', 0.3]}
      />
      <fog attach="fog" args={['#d4e4f7', 400, 900]} />
    </>
  );
}
