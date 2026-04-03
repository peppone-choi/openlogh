'use client';
// 도로 PNG를 지형 위에 투명 오버레이로 배치
import { useTexture } from '@react-three/drei';
import * as THREE from 'three';
import { getMapRoadUrl } from '@/lib/image';
import { WORLD_SCALE } from '@/lib/map-3d-utils';

const MAP_W = 700 * WORLD_SCALE;
const MAP_H = 500 * WORLD_SCALE;

interface RoadOverlayProps {
  mapCode: string;
}

export function RoadOverlay({ mapCode }: RoadOverlayProps) {
  const roadUrl = getMapRoadUrl(mapCode);
  const texture = useTexture(roadUrl);

  // 텍스쳐 설정: 투명 PNG
  texture.colorSpace = THREE.SRGBColorSpace;

  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.3, 0]}>
      <planeGeometry args={[MAP_W, MAP_H]} />
      <meshBasicMaterial
        map={texture}
        transparent
        opacity={0.85}
        depthWrite={false}
        side={THREE.DoubleSide}
      />
    </mesh>
  );
}
