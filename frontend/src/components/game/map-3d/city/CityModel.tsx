'use client';
// Design Ref: §3.3 CityModel — 성곽 표현 + 깃발 + 바닥 원형
// 성능 최적화: Html 라벨 제거 (호버 시 툴팁으로 대체), 정적 깃발
import { useMemo } from 'react';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight } from '@/lib/map-3d-utils';
import { getLocationConfig } from './CastleLoader';

interface CityModelProps {
  city: RenderCity;
  heightMap?: Float32Array;
  segments?: number;
  onClick?: (cityId: number) => void;
  onHover?: (cityId: number | null) => void;
}

/** 레벨에 따른 간단한 3D 지오메트리 (GLB 대신) */
function CityGeometry({ level, nationColor }: { level: number; nationColor: string | null }) {
  const config = getLocationConfig(level);
  const color = nationColor ?? '#888888';
  const s = config.targetScale * 0.4;

  if (level <= 2) {
    // 수/진: 낮은 텐트/판잣집
    return (
      <mesh>
        <coneGeometry args={[s * 0.8, s, 4]} />
        <meshStandardMaterial color="#8B6914" roughness={0.8} />
      </mesh>
    );
  }
  if (level <= 4) {
    // 관/이: 작은 구조물
    return (
      <mesh>
        <boxGeometry args={[s, s * 0.6, s]} />
        <meshStandardMaterial color="#7a6b5a" roughness={0.7} />
      </mesh>
    );
  }
  // 도시 (5-8): 성벽 + 중심 탑
  const wallH = s * 0.4;
  const towerH = s * (0.6 + (level - 5) * 0.3);
  return (
    <group>
      {/* 성벽 (빈 상자) */}
      <mesh position={[0, wallH / 2, 0]}>
        <boxGeometry args={[s * 2, wallH, s * 2]} />
        <meshStandardMaterial color="#9a8b7a" roughness={0.7} />
      </mesh>
      {/* 중심 탑 */}
      <mesh position={[0, wallH + towerH / 2, 0]}>
        <boxGeometry args={[s * 0.6, towerH, s * 0.6]} />
        <meshStandardMaterial color={color} roughness={0.5} />
      </mesh>
      {/* 지붕 */}
      <mesh position={[0, wallH + towerH + s * 0.15, 0]}>
        <coneGeometry args={[s * 0.5, s * 0.3, 4]} />
        <meshStandardMaterial color="#5a3a1a" roughness={0.6} />
      </mesh>
    </group>
  );
}

/** 국가색 깃발 (정적 — useFrame 제거) */
function NationFlag({ color, height }: { color: string; height: number }) {
  return (
    <group position={[0, height, 0]}>
      <mesh>
        <cylinderGeometry args={[0.03, 0.03, height * 0.4, 4]} />
        <meshStandardMaterial color="#5c4033" />
      </mesh>
      <mesh position={[0.3, height * 0.15, 0]} rotation={[0, 0.1, 0]}>
        <planeGeometry args={[0.6, 0.4]} />
        <meshStandardMaterial color={color} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}

/** 국가색 바닥 원형 */
function NationBase({ color, radius }: { color: string | null; radius: number }) {
  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.1, 0]}>
      <circleGeometry args={[radius, 16]} />
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

  const position = useMemo(() => {
    const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
    return toWorld3d(city.x, city.y, h);
  }, [city.x, city.y, heightMap, segments]);

  const flagHeight = config.targetScale * 0.8;

  return (
    <group position={[position.x, position.y, position.z]}>
      <NationBase color={city.nationColor} radius={config.baseRadius} />

      {/* 클릭 히트박스 (투명) */}
      <mesh
        visible={false}
        onClick={(e) => { e.stopPropagation(); onClick?.(city.id); }}
        onPointerOver={() => onHover?.(city.id)}
        onPointerOut={() => onHover?.(null)}
      >
        <boxGeometry args={[config.baseRadius, config.targetScale, config.baseRadius]} />
      </mesh>

      <CityGeometry level={city.level} nationColor={city.nationColor} />

      {city.nationColor && (
        <NationFlag color={city.nationColor} height={flagHeight} />
      )}
    </group>
  );
}
