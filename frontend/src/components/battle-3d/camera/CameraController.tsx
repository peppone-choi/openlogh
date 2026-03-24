'use client';

import { useRef, useEffect, type ComponentRef } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import * as THREE from 'three';
import type { BattleViewMode } from '@/types/battle3d';
import { CAMERA_PRESETS } from './CameraPresets';

interface CameraControllerProps {
    mode: BattleViewMode;
    followTarget?: [number, number, number];
}

const LERP_DURATION_MS = 500;

type OrbitControlsRef = ComponentRef<typeof OrbitControls>;

export function CameraController({ mode, followTarget }: CameraControllerProps) {
    const { camera } = useThree();
    const controlsRef = useRef<OrbitControlsRef>(null);

    const targetPosition = useRef(new THREE.Vector3());
    const targetLookAt = useRef(new THREE.Vector3());
    const lerpStartTime = useRef<number | null>(null);
    const lerpFromPosition = useRef(new THREE.Vector3());
    const lerpFromLookAt = useRef(new THREE.Vector3());
    const isLerping = useRef(false);

    useEffect(() => {
        const preset = mode === '2d' ? CAMERA_PRESETS.topDown : CAMERA_PRESETS.perspective;
        const dest = followTarget ?? preset.position;

        targetPosition.current.set(dest[0], dest[1], dest[2]);
        targetLookAt.current.set(preset.target[0], preset.target[1], preset.target[2]);

        lerpFromPosition.current.copy(camera.position);
        if (controlsRef.current) {
            lerpFromLookAt.current.copy(controlsRef.current.target);
        } else {
            lerpFromLookAt.current.copy(targetLookAt.current);
        }

        lerpStartTime.current = performance.now();
        isLerping.current = true;
    }, [mode, followTarget, camera]);

    useFrame(() => {
        if (!isLerping.current || lerpStartTime.current === null) return;

        const elapsed = performance.now() - lerpStartTime.current;
        const t = Math.min(elapsed / LERP_DURATION_MS, 1);
        const eased = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

        camera.position.lerpVectors(lerpFromPosition.current, targetPosition.current, eased);

        if (controlsRef.current) {
            controlsRef.current.target.lerpVectors(lerpFromLookAt.current, targetLookAt.current, eased);
            controlsRef.current.update();
        }

        if (t >= 1) {
            isLerping.current = false;
        }
    });

    return (
        <OrbitControls
            ref={controlsRef}
            minDistance={5}
            maxDistance={60}
            enablePan={true}
            maxPolarAngle={Math.PI / 2}
        />
    );
}
