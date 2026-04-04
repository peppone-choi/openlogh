'use client';
// Design Ref: §3.6 CameraController — OrbitControls 래퍼
import { OrbitControls } from '@react-three/drei';
interface CameraControllerProps {
  compact?: boolean;
}

export function CameraController({ compact = false }: CameraControllerProps) {
  return (
    <OrbitControls
      target={[0, 0, 0]}
      minDistance={compact ? 30 : 20}
      maxDistance={compact ? 1200 : 1800}
      maxPolarAngle={Math.PI / 2.2}
      minPolarAngle={Math.PI / 8}
      enableDamping
      dampingFactor={0.05}
    />
  );
}
