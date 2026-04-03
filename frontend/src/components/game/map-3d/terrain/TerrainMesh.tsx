'use client';
// Design Ref: §3.2 TerrainMesh — 배경 이미지 기반 높이맵 + vertex color
import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import type { CityConst } from '@/types';
import type { MapSeason } from '@/lib/map-constants';
import { getMapBgUrl } from '@/lib/image';
import {
  generateHeightMapFromImage,
  generateHeightMapFallback,
  loadImagePixels,
} from './HeightMapGenerator';

const MAP_W = 700;
const MAP_H = 500;
const SEGMENTS = 64;

interface TerrainMeshProps {
  cities: CityConst[];
  mapCode?: string;
  season?: MapSeason;
}

/** 이미지 픽셀에서 vertex color 생성 (배경 이미지 색상 재활용) */
function applyVertexColorsFromImage(
  geo: THREE.PlaneGeometry,
  imgData: Uint8ClampedArray,
  imgW: number,
  imgH: number,
) {
  const positions = geo.attributes.position;
  const colors = new Float32Array(positions.count * 3);

  for (let i = 0; i < positions.count; i++) {
    // PlaneGeometry rotateX(-PI/2) 후의 좌표에서 u,v 추출
    const x = positions.getX(i);
    const z = positions.getZ(i);
    const u = (x + MAP_W / 2) / MAP_W;
    const v = (z + MAP_H / 2) / MAP_H;

    const imgX = Math.min(Math.floor(u * imgW), imgW - 1);
    const imgY = Math.min(Math.floor(v * imgH), imgH - 1);
    const px = (imgY * imgW + imgX) * 4;

    // 배경 이미지 색상을 약간 어둡게 (지형 느낌)
    colors[i * 3] = (imgData[px] / 255) * 0.85;
    colors[i * 3 + 1] = (imgData[px + 1] / 255) * 0.85;
    colors[i * 3 + 2] = (imgData[px + 2] / 255) * 0.85;
  }

  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
}

/** 높이맵 기반 fallback vertex color */
function applyVertexColorsFromHeight(geo: THREE.PlaneGeometry) {
  const positions = geo.attributes.position;
  const colors = new Float32Array(positions.count * 3);

  for (let i = 0; i < positions.count; i++) {
    const h = positions.getY(i);

    if (h < 0) {
      colors[i * 3] = 0.2; colors[i * 3 + 1] = 0.35; colors[i * 3 + 2] = 0.5;
    } else if (h < 3) {
      colors[i * 3] = 0.35; colors[i * 3 + 1] = 0.55; colors[i * 3 + 2] = 0.25;
    } else if (h < 7) {
      colors[i * 3] = 0.4; colors[i * 3 + 1] = 0.5; colors[i * 3 + 2] = 0.28;
    } else {
      const t = Math.min(1, (h - 7) / 8);
      colors[i * 3] = 0.5 + t * 0.1;
      colors[i * 3 + 1] = 0.42 + t * 0.05;
      colors[i * 3 + 2] = 0.3 + t * 0.1;
    }
  }

  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
}

export function TerrainMesh({ cities, mapCode = 'che', season = 'spring' }: TerrainMeshProps) {
  const meshRef = useRef<THREE.Mesh>(null);
  const [geometry, setGeometry] = useState<THREE.PlaneGeometry | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function buildTerrain() {
      const geo = new THREE.PlaneGeometry(MAP_W, MAP_H, SEGMENTS, SEGMENTS);
      geo.rotateX(-Math.PI / 2);

      // 맵 배경 이미지 폴더 결정
      const mapFolder = mapCode === 'ludo_rathowm' ? 'ludo_rathowm' : mapCode.includes('miniche') ? 'che' : mapCode;
      const bgUrl = getMapBgUrl(mapFolder, season);

      let heightMap: Float32Array;
      let hasImage = false;

      try {
        // 배경 이미지에서 높이맵 + 색상 추출
        heightMap = await generateHeightMapFromImage(bgUrl, cities, SEGMENTS);
        const { data, width, height } = await loadImagePixels(bgUrl);
        applyVertexColorsFromImage(geo, data, width, height);
        hasImage = true;
      } catch {
        // 이미지 로드 실패 시 region 기반 폴백
        heightMap = generateHeightMapFallback(cities, SEGMENTS);
      }

      if (cancelled) { geo.dispose(); return; }

      // 높이 적용
      const positions = geo.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        positions.setY(i, heightMap[i] ?? 0);
      }
      positions.needsUpdate = true;
      geo.computeVertexNormals();

      if (!hasImage) {
        applyVertexColorsFromHeight(geo);
      }

      setGeometry(geo);
    }

    buildTerrain();
    return () => { cancelled = true; };
  }, [cities, mapCode, season]);

  if (!geometry) return null;

  return (
    <mesh ref={meshRef} geometry={geometry} receiveShadow={false}>
      <meshStandardMaterial
        vertexColors
        roughness={0.85}
        metalness={0.05}
        flatShading={false}
      />
    </mesh>
  );
}
