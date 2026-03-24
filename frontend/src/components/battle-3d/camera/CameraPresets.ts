import type { CameraPreset } from '@/types/battle3d';

export const CAMERA_PRESETS: Record<string, CameraPreset> = {
    perspective: { position: [0, 20, 25], target: [0, 0, 0], fov: 45, label: '3D' },
    topDown: { position: [0, 35, 0.1], target: [0, 0, 0], fov: 50, label: '2D' },
    victoryZoom: { position: [0, 5, 8], target: [0, 0, 0], fov: 35, label: '승리' },
};
