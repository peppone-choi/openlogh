'use client';

import { Suspense } from 'react';
import { TextureLoader } from 'three';
import { useLoader } from '@react-three/fiber';
import { getMapRoadUrl } from '@/lib/image';
import { WORLD_WIDTH, WORLD_DEPTH } from '@/lib/map3d-utils';

interface RoadOverlayProps {
    mapCode: string;
    width?: number;
    depth?: number;
}

function RoadMesh({ mapCode, width, depth }: Required<RoadOverlayProps>) {
    const url = getMapRoadUrl(mapCode);
    const roadTexture = useLoader(TextureLoader, url);

    return (
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.08, 0]}>
            <planeGeometry args={[width, depth]} />
            <meshBasicMaterial map={roadTexture} transparent={true} depthWrite={false} opacity={0.9} />
        </mesh>
    );
}

export function RoadOverlay({ mapCode, width = WORLD_WIDTH, depth = WORLD_DEPTH }: RoadOverlayProps) {
    return (
        <Suspense fallback={null}>
            <RoadMesh mapCode={mapCode} width={width} depth={depth} />
        </Suspense>
    );
}
