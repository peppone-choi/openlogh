'use client';
// Design Ref: §3.3 CityModel — GLB 성곽 배치 + 깃발 + 바닥 원형
import { useRef, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import { useGLTF, Html } from '@react-three/drei';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight } from '@/lib/map-3d-utils';
import { getLocationConfig, getModelUrl, normalizeModel } from './CastleLoader';

interface CityModelProps {
  city: RenderCity;
  heightMap?: Float32Array;
  segments?: number;
  onClick?: (cityId: number) => void;
  onHover?: (cityId: number | null) => void;
}

/** 국가색 깃발 */
function NationFlag({ color, height }: { color: string; height: number }) {
  const flagRef = useRef<THREE.Mesh>(null);

  // 바람에 흔들리는 애니메이션
  useFrame(({ clock }) => {
    if (flagRef.current) {
      flagRef.current.rotation.y = Math.sin(clock.elapsedTime * 2) * 0.15;
    }
  });

  return (
    <group position={[0, height, 0]}>
      {/* 깃대 */}
      <mesh>
        <cylinderGeometry args={[0.05, 0.05, height * 0.4, 6]} />
        <meshStandardMaterial color="#5c4033" roughness={0.8} />
      </mesh>
      {/* 깃발 */}
      <mesh ref={flagRef} position={[0.4, height * 0.15, 0]}>
        <planeGeometry args={[0.8, 0.5]} />
        <meshStandardMaterial
          color={color}
          side={THREE.DoubleSide}
          roughness={0.6}
        />
      </mesh>
    </group>
  );
}

/** 국가색 바닥 원형 */
function NationBase({ color, radius }: { color: string | null; radius: number }) {
  const baseColor = color ?? '#555555';
  const opacity = color ? 0.35 : 0.15;

  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.1, 0]}>
      <circleGeometry args={[radius, 32]} />
      <meshStandardMaterial
        color={baseColor}
        transparent
        opacity={opacity}
        roughness={1}
        depthWrite={false}
      />
    </mesh>
  );
}

export function CityModel({ city, heightMap, segments = 128, onClick, onHover }: CityModelProps) {
  const config = getLocationConfig(city.level);
  const modelUrl = getModelUrl(config.modelFile);

  // GLB 로드
  const { scene } = useGLTF(modelUrl);

  // 모델 복제 + 정규화
  const model = useMemo(() => {
    const cloned = scene.clone(true);
    normalizeModel(cloned);
    cloned.scale.multiplyScalar(config.targetScale);
    return cloned;
  }, [scene, config.targetScale]);

  // 3D 위치 계산
  const position = useMemo(() => {
    const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
    return toWorld3d(city.x, city.y, h);
  }, [city.x, city.y, heightMap, segments]);

  const flagHeight = config.targetScale * 1.2;

  return (
    <group position={[position.x, position.y, position.z]}>
      {/* 바닥 원형 (국가색) */}
      <NationBase color={city.nationColor} radius={config.baseRadius} />

      {/* 성곽/거점 모델 */}
      <primitive
        object={model}
        onClick={(e: { stopPropagation?: () => void }) => {
          e.stopPropagation?.();
          onClick?.(city.id);
        }}
        onPointerOver={() => onHover?.(city.id)}
        onPointerOut={() => onHover?.(null)}
      />

      {/* 국가 깃발 (소속 국가가 있을 때만) */}
      {city.nationColor && (
        <NationFlag color={city.nationColor} height={flagHeight} />
      )}

      {/* 도시 이름 라벨 */}
      <Html
        position={[0, flagHeight + 1, 0]}
        center
        distanceFactor={200}
        zIndexRange={[16, 0]}
        occlude
      >
        <div className="pointer-events-none select-none whitespace-nowrap text-center">
          <span className="rounded bg-black/60 px-1.5 py-0.5 text-[10px] font-bold text-white drop-shadow-md">
            {city.name}
          </span>
        </div>
      </Html>
    </group>
  );
}
