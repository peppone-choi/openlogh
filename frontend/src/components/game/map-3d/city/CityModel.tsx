'use client';
// Design Ref: §3.3 CityModel — GLB 모델 + 깃발 + 바닥 원형 + 이름 라벨
// WORLD_SCALE: 모든 크기값에 1회만 균일 적용
import { useMemo } from 'react';
import { useGLTF, Text } from '@react-three/drei';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight, WORLD_SCALE } from '@/lib/map-3d-utils';
import { getLocationConfig, getModelUrl } from './CastleLoader';

interface CityModelProps {
  city: RenderCity;
  heightMap?: Float32Array;
  segments?: number;
  onClick?: (cityId: number) => void;
  onHover?: (cityId: number | null) => void;
}

// 모든 크기 = 기준값 * S
const S = WORLD_SCALE;

export function CityModel({ city, heightMap, segments = 64, onClick, onHover }: CityModelProps) {
  const config = getLocationConfig(city.level);
  const modelUrl = getModelUrl(config.modelFile);
  const { scene } = useGLTF(modelUrl);

  const model = useMemo(() => {
    const cloned = scene.clone(true);
    const box = new THREE.Box3().setFromObject(cloned);
    const center = box.getCenter(new THREE.Vector3());
    const size = box.getSize(new THREE.Vector3());
    const maxDim = Math.max(size.x, size.y, size.z);
    const scale = (config.targetScale * S) / maxDim;

    cloned.scale.setScalar(scale);
    cloned.position.set(
      -center.x * scale,
      -box.min.y * scale, // 바닥 y=0
      -center.z * scale,
    );
    return cloned;
  }, [scene, config.targetScale]);

  const position = useMemo(() => {
    const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
    return toWorld3d(city.x, city.y, h);
  }, [city.x, city.y, heightMap, segments]);

  const modelH = config.targetScale * S;
  const flagH = modelH * 0.5;
  const labelY = modelH * 1.1 + 2 * S;
  const flagPoleR = 0.15 * S;
  const flagW = 2.4 * S;
  const flagFH = 1.5 * S;
  const baseR = config.baseRadius * S;
  const nationColor = city.nationColor;

  return (
    <group position={[position.x, position.y, position.z]}>
      {/* 바닥 원형 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.1, 0]}>
        <circleGeometry args={[baseR, 16]} />
        <meshStandardMaterial
          color={nationColor ?? '#555555'}
          transparent
          opacity={nationColor ? 0.35 : 0.15}
          roughness={1}
          depthWrite={false}
        />
      </mesh>

      {/* GLB 모델 */}
      <primitive
        object={model}
        onClick={(e: { stopPropagation?: () => void }) => {
          e.stopPropagation?.();
          onClick?.(city.id);
        }}
        onPointerOver={() => onHover?.(city.id)}
        onPointerOut={() => onHover?.(null)}
      />

      {/* 국가 깃발 */}
      {nationColor && (
        <group position={[0, flagH, 0]}>
          <mesh>
            <cylinderGeometry args={[flagPoleR, flagPoleR, flagH * 0.4, 4]} />
            <meshStandardMaterial color="#5c4033" />
          </mesh>
          <mesh position={[flagW * 0.5, flagH * 0.15, 0]} rotation={[0, 0.1, 0]}>
            <planeGeometry args={[flagW, flagFH]} />
            <meshStandardMaterial color={nationColor} side={THREE.DoubleSide} />
          </mesh>
        </group>
      )}

      {/* 도시 이름 (GPU SDF 텍스트) */}
      <Text
        position={[0, labelY, 0]}
        fontSize={3 * S}
        color="white"
        anchorX="center"
        anchorY="middle"
        outlineWidth={0.4 * S}
        outlineColor="black"
      >
        {city.name}
      </Text>
    </group>
  );
}
