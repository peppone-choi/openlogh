'use client';
// Design Ref: §3.3 CityModel — GLB 모델 + 깃발 + 바닥 원형
// 성능: Html 라벨 제거, 깃발 정적, GLB useGLTF 캐시 활용
import { useMemo } from 'react';
import { useGLTF } from '@react-three/drei';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight, WORLD_SCALE } from '@/lib/map-3d-utils';
import { getLocationConfig, getModelUrl, normalizeModel } from './CastleLoader';

interface CityModelProps {
  city: RenderCity;
  heightMap?: Float32Array;
  segments?: number;
  onClick?: (cityId: number) => void;
  onHover?: (cityId: number | null) => void;
}

/** 국가색 깃발 (정적) */
function NationFlag({ color, height }: { color: string; height: number }) {
  return (
    <group position={[0, height, 0]}>
      <mesh>
        <cylinderGeometry args={[0.05, 0.05, height * 0.4, 4]} />
        <meshStandardMaterial color="#5c4033" />
      </mesh>
      <mesh position={[0.4, height * 0.15, 0]} rotation={[0, 0.1, 0]}>
        <planeGeometry args={[0.8, 0.5]} />
        <meshStandardMaterial color={color} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}

/** 국가색 바닥 원형 */
function NationBase({ color, radius }: { color: string | null; radius: number }) {
  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.1, 0]}>
      <circleGeometry args={[radius * WORLD_SCALE, 16]} />
      <meshStandardMaterial
        color={color ?? '#555555'}
        transparent
        opacity={color ? 0.35 : 0.15}
        roughness={1}
        depthWrite={false}
      />
    </mesh>
  );
}

export function CityModel({ city, heightMap, segments = 64, onClick, onHover }: CityModelProps) {
  const config = getLocationConfig(city.level);
  const modelUrl = getModelUrl(config.modelFile);
  const { scene } = useGLTF(modelUrl);

  const model = useMemo(() => {
    const cloned = scene.clone(true);
    normalizeModel(cloned);
    cloned.scale.multiplyScalar(config.targetScale * WORLD_SCALE);
    return cloned;
  }, [scene, config.targetScale]);

  const position = useMemo(() => {
    const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
    return toWorld3d(city.x, city.y, h);
  }, [city.x, city.y, heightMap, segments]);

  const flagHeight = config.targetScale * WORLD_SCALE * 1.2;

  return (
    <group position={[position.x, position.y, position.z]}>
      <NationBase color={city.nationColor} radius={config.baseRadius} />

      <primitive
        object={model}
        onClick={(e: { stopPropagation?: () => void }) => {
          e.stopPropagation?.();
          onClick?.(city.id);
        }}
        onPointerOver={() => onHover?.(city.id)}
        onPointerOut={() => onHover?.(null)}
      />

      {city.nationColor && (
        <NationFlag color={city.nationColor} height={flagHeight} />
      )}
    </group>
  );
}
