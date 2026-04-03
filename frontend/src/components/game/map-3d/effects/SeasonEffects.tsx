'use client';
// Session 5: 계절별 시각 효과 — 눈, 낙엽, 벚꽃, 반딧불
import { useRef, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import type { MapSeason } from '@/lib/map-constants';
import { WORLD_SCALE } from '@/lib/map-3d-utils';

const S = WORLD_SCALE;
const PARTICLE_COUNT = 200;
const SPREAD_X = 350 * S;
const SPREAD_Z = 250 * S;
const MAX_Y = 150 * S;

interface SeasonEffectsProps {
  season: MapSeason;
}

const SEASON_CONFIG: Record<MapSeason, {
  color: string;
  size: number;
  speed: number;
  drift: number;
  opacity: number;
} | null> = {
  spring: { color: '#ffb7c5', size: 1.5, speed: 0.3, drift: 0.8, opacity: 0.7 }, // 벚꽃
  summer: { color: '#aaff44', size: 0.8, speed: 0.1, drift: 0.3, opacity: 0.5 },  // 반딧불
  fall: { color: '#d4722a', size: 1.2, speed: 0.5, drift: 1.0, opacity: 0.6 },    // 낙엽
  winter: { color: '#ffffff', size: 0.8, speed: 0.4, drift: 0.4, opacity: 0.8 },   // 눈
};

export function SeasonEffects({ season }: SeasonEffectsProps) {
  const config = SEASON_CONFIG[season];
  if (!config) return null;

  const pointsRef = useRef<THREE.Points>(null);

  const positions = useMemo(() => {
    const arr = new Float32Array(PARTICLE_COUNT * 3);
    for (let i = 0; i < PARTICLE_COUNT; i++) {
      arr[i * 3] = (Math.random() - 0.5) * SPREAD_X * 2;
      arr[i * 3 + 1] = Math.random() * MAX_Y;
      arr[i * 3 + 2] = (Math.random() - 0.5) * SPREAD_Z * 2;
    }
    return arr;
  }, []);

  useFrame(({ clock }) => {
    if (!pointsRef.current) return;
    const pos = pointsRef.current.geometry.attributes.position;
    const t = clock.elapsedTime;

    for (let i = 0; i < PARTICLE_COUNT; i++) {
      let y = pos.getY(i) - config.speed * S;
      const x = pos.getX(i) + Math.sin(t + i) * config.drift * 0.3;
      const z = pos.getZ(i) + Math.cos(t + i * 0.7) * config.drift * 0.3;

      // 바닥 도달 시 위로 리셋
      if (y < 0) y = MAX_Y;

      pos.setXYZ(i, x, y, z);
    }
    pos.needsUpdate = true;
  });

  const bufferGeometry = useMemo(() => {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    return geo;
  }, [positions]);

  return (
    <points ref={pointsRef} geometry={bufferGeometry}>
      <pointsMaterial
        color={config.color}
        size={config.size * S}
        transparent
        opacity={config.opacity}
        depthWrite={false}
        sizeAttenuation
      />
    </points>
  );
}
