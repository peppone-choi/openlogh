'use client';
// Design Ref: §3.6 CameraController — OrbitControls 래퍼
import { OrbitControls } from '@react-three/drei';
import { useThree } from '@react-three/fiber';
import { useEffect } from 'react';
import * as THREE from 'three';

interface CameraControllerProps {
  compact?: boolean;
}

export function CameraController({ compact = false }: CameraControllerProps) {
  const { camera, invalidate } = useThree();

  useEffect(() => {
    // 초기 뷰: 비스듬한 탑다운
    camera.position.set(0, 350, 250);
    camera.lookAt(0, 0, 0);
    invalidate();
  }, [camera, invalidate]);

  return (
    <OrbitControls
      target={[0, 0, 0]}
      minDistance={compact ? 50 : 80}
      maxDistance={compact ? 400 : 600}
      maxPolarAngle={Math.PI / 2.2}
      minPolarAngle={Math.PI / 8}
      enableDamping
      dampingFactor={0.05}
      onChange={() => invalidate()}
    />
  );
}
