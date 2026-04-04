'use client';
// Design Ref: §3.2 TerrainMesh — 배경 텍스쳐 + 도로 오버레이 on 높이맵 지형
import { useEffect, useRef, useState } from 'react';
import { useTexture } from '@react-three/drei';
import * as THREE from 'three';
import type { CityConst } from '@/types';
import type { MapSeason } from '@/lib/map-constants';
import { getMapBgUrl, getMapRoadUrl } from '@/lib/image';
import {
  generateHeightMapFromImage,
  generateHeightMapFallback,
} from './HeightMapGenerator';
import { WORLD_SCALE } from '@/lib/map-3d-utils';

const MAP_W = 700;
const MAP_H = 500;
const SEGMENTS = 64;
const SCALED_W = MAP_W * WORLD_SCALE;
const SCALED_H = MAP_H * WORLD_SCALE;

interface TerrainMeshProps {
  cities: CityConst[];
  mapCode?: string;
  season?: MapSeason;
}

export function TerrainMesh({ cities, mapCode = 'che', season = 'spring' }: TerrainMeshProps) {
  const meshRef = useRef<THREE.Mesh>(null);
  const [geometry, setGeometry] = useState<THREE.PlaneGeometry | null>(null);

  // 배경 + 도로 텍스쳐 로딩 (Suspense 기반)
  const mapFolder = mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode.includes('miniche') ? 'che' : mapCode;
  const bgUrl = getMapBgUrl(mapFolder, season);
  const roadUrl = getMapRoadUrl(mapCode);

  const bgTexture = useTexture(bgUrl);
  const roadTexture = useTexture(roadUrl);

  bgTexture.colorSpace = THREE.SRGBColorSpace;
  roadTexture.colorSpace = THREE.SRGBColorSpace;

  useEffect(() => {
    let cancelled = false;

    async function buildTerrain() {
      const geo = new THREE.PlaneGeometry(SCALED_W, SCALED_H, SEGMENTS, SEGMENTS);
      geo.rotateX(-Math.PI / 2);

      let heightMap: Float32Array;

      try {
        heightMap = await generateHeightMapFromImage(bgUrl, cities, SEGMENTS);
      } catch {
        heightMap = generateHeightMapFallback(cities, SEGMENTS);
      }

      if (cancelled) { geo.dispose(); return; }

      // 높이 적용 (WORLD_SCALE 반영)
      const positions = geo.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        positions.setY(i, (heightMap[i] ?? 0) * WORLD_SCALE);
      }
      positions.needsUpdate = true;
      geo.computeVertexNormals();

      setGeometry(geo);
    }

    buildTerrain();
    return () => { cancelled = true; };
  }, [bgUrl, cities, mapCode, season]);

  if (!geometry) return null;

  return (
    <group>
      {/* 지형 + 배경 텍스쳐 */}
      <mesh ref={meshRef} geometry={geometry} receiveShadow={false}>
        <meshStandardMaterial
          map={bgTexture}
          roughness={0.85}
          metalness={0.05}
          flatShading={false}
        />
      </mesh>
      {/* 도로 오버레이 — 같은 지오메트리 위에 polygonOffset으로 z-fighting 방지 */}
      <mesh geometry={geometry}>
        <meshBasicMaterial
          map={roadTexture}
          transparent
          opacity={0.85}
          depthWrite={false}
          polygonOffset
          polygonOffsetFactor={-1}
          polygonOffsetUnits={-1}
          side={THREE.DoubleSide}
        />
      </mesh>
    </group>
  );
}
