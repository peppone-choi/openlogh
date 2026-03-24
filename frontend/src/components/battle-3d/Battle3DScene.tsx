'use client';

import { Suspense, useMemo, useEffect, useState } from 'react';
import { Canvas } from '@react-three/fiber';
import type { AnimationSequence, BattleViewMode } from '@/types/battle3d';
import { useBattle3DStore } from '@/stores/battle3dStore';
import { isWebGLSupported } from '@/lib/battle3d-utils';
import { TerrainGenerator } from './terrain/TerrainGenerator';
import { CameraController } from './camera/CameraController';

interface Battle3DSceneProps {
    sequence: AnimationSequence;
    autoPlay?: boolean;
    onFinish?: () => void;
    className?: string;
}

function SceneContent({ sequence, viewMode }: { sequence: AnimationSequence; viewMode: BattleViewMode }) {
    return (
        <>
            <ambientLight intensity={0.5} />
            <directionalLight position={[10, 15, 10]} intensity={1} castShadow />
            <TerrainGenerator type={sequence.terrain} />
            <CameraController mode={viewMode} />
        </>
    );
}

function WebGLFallback() {
    return (
        <div className="flex items-center justify-center w-full h-full bg-gray-900 text-gray-300 text-sm">
            <p>3D 전투 시각화를 지원하지 않는 브라우저입니다.</p>
        </div>
    );
}

export function Battle3DScene({ sequence, autoPlay = false, onFinish, className }: Battle3DSceneProps) {
    const { viewMode, playState, loadSequence, play, setViewMode } = useBattle3DStore();
    const [webglSupported, setWebglSupported] = useState<boolean | null>(null);

    useEffect(() => {
        setWebglSupported(isWebGLSupported());
    }, []);

    useEffect(() => {
        loadSequence(sequence);
    }, [sequence, loadSequence]);

    useEffect(() => {
        if (autoPlay && playState === 'loading') {
            play();
        }
    }, [autoPlay, playState, play]);

    useEffect(() => {
        if (playState === 'finished' && onFinish) {
            onFinish();
        }
    }, [playState, onFinish]);

    const cameraDefaults = useMemo(
        () => ({
            position: [0, 20, 25] as [number, number, number],
            fov: 45,
        }),
        []
    );

    if (webglSupported === null) {
        return <div className={`relative ${className ?? ''}`} style={{ minHeight: 300 }} />;
    }

    if (!webglSupported) {
        return (
            <div className={`relative ${className ?? ''}`} style={{ minHeight: 300 }}>
                <WebGLFallback />
            </div>
        );
    }

    return (
        <div className={`relative ${className ?? ''}`} style={{ minHeight: 300 }}>
            <Canvas shadows dpr={[1, 2]} camera={cameraDefaults} gl={{ antialias: true }}>
                <Suspense fallback={null}>
                    <SceneContent sequence={sequence} viewMode={viewMode} />
                </Suspense>
            </Canvas>
            <div className="absolute top-2 right-2 flex gap-1">
                <button
                    onClick={() => setViewMode('3d')}
                    className={`px-2 py-1 text-xs rounded ${viewMode === '3d' ? 'bg-white text-black' : 'bg-black/50 text-white'}`}
                >
                    3D
                </button>
                <button
                    onClick={() => setViewMode('2d')}
                    className={`px-2 py-1 text-xs rounded ${viewMode === '2d' ? 'bg-white text-black' : 'bg-black/50 text-white'}`}
                >
                    2D
                </button>
            </div>
        </div>
    );
}
