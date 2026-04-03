'use client';
// 도로 PNG를 지형 위에 투명 오버레이로 배치
import { useTexture } from '@react-three/drei';
import * as THREE from 'three';
import { getMapRoadUrl } from '@/lib/image';

const MAP_W = 700;
const MAP_H = 500;

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
