'use client';
// Design Ref: §3.1 RenderLoop — FPS 제한, 성능 모니터
import { useFrame, useThree } from '@react-three/fiber';
import { useRef } from 'react';

interface RenderLoopProps {
  maxFps: number;
}

export function RenderLoop({ maxFps }: RenderLoopProps) {
  const elapsed = useRef(0);
  const { invalidate } = useThree();
  const interval = 1 / maxFps;

  useFrame((_, delta) => {
    elapsed.current += delta;
    if (elapsed.current >= interval) {
      elapsed.current = 0;
      invalidate();
    }
  });

  return null;
}
