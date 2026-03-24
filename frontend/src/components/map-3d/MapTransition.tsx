'use client';

import { useState, useEffect } from 'react';

interface MapTransitionProps {
    viewMode: '2d' | '3d';
    children2D: React.ReactNode;
    children3D: React.ReactNode;
    duration?: number;
}

export function MapTransition({ viewMode, children2D, children3D, duration = 600 }: MapTransitionProps) {
    const [activeMode, setActiveMode] = useState<'2d' | '3d'>(viewMode);
    const [transitioning, setTransitioning] = useState(false);

    useEffect(() => {
        if (viewMode === activeMode) return;

        setTransitioning(true);

        const timer = setTimeout(() => {
            setActiveMode(viewMode);
            setTransitioning(false);
        }, duration);

        return () => clearTimeout(timer);
    }, [viewMode, duration, activeMode]);

    const show2D = activeMode === '2d' || transitioning;
    const show3D = activeMode === '3d' || transitioning;

    const opacity2D = transitioning ? (viewMode === '2d' ? 1 : 0) : activeMode === '2d' ? 1 : 0;
    const opacity3D = transitioning ? (viewMode === '3d' ? 1 : 0) : activeMode === '3d' ? 1 : 0;

    const scale3D = transitioning && viewMode === '3d' ? 1 : activeMode === '3d' ? 1 : 0.95;
    const scale2D = transitioning && viewMode === '2d' ? 1 : activeMode === '2d' ? 1 : 0.95;

    return (
        <div className="relative w-full h-full">
            <div
                className="absolute inset-0"
                style={{
                    opacity: opacity2D,
                    transform: `scale(${scale2D})`,
                    transition: `opacity ${duration}ms ease-in-out, transform ${duration}ms ease-in-out`,
                    pointerEvents: activeMode === '2d' && !transitioning ? 'auto' : 'none',
                }}
            >
                {show2D && children2D}
            </div>
            <div
                className="absolute inset-0"
                style={{
                    opacity: opacity3D,
                    transform: `scale(${scale3D})`,
                    transition: `opacity ${duration}ms ease-in-out, transform ${duration}ms ease-in-out`,
                    pointerEvents: activeMode === '3d' && !transitioning ? 'auto' : 'none',
                }}
            >
                {show3D && children3D}
            </div>
        </div>
    );
}
