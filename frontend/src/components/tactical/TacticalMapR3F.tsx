'use client';
// TacticalMapR3F — bottom 3D tactical overview grid using React Three Fiber
import { Canvas } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import { Suspense } from 'react';
import { useTacticalStore } from '@/stores/tacticalStore';
import type { TacticalUnit } from '@/types/tactical';

const EMPIRE_COLOR = '#4466ff';
const ALLIANCE_COLOR = '#ff4444';
const FLAGSHIP_CONE_COLOR = '#ffd700';

interface UnitBlockProps {
    unit: TacticalUnit;
    color: string;
    isFlagship: boolean;
}

function TacticalUnitBlock({ unit, color, isFlagship }: UnitBlockProps) {
    const blockWidth = Math.max(0.3, Math.min(3.0, unit.ships / 300)) * 1.5;
    const blockHeight = 0.4;
    const blockDepth = 1.0;
    const posX = unit.posX * 0.4;
    const posZ = unit.posY * 0.4;

    return (
        <group position={[posX, 0, posZ]}>
            {/* Main unit block */}
            <mesh position={[0, blockHeight / 2, 0]}>
                <boxGeometry args={[blockWidth, blockHeight, blockDepth]} />
                <meshStandardMaterial color={color} />
            </mesh>

            {/* Flagship cone marker above block */}
            {isFlagship && (
                <mesh position={[0, blockHeight + 0.5, 0]} rotation={[Math.PI, 0, 0]}>
                    <coneGeometry args={[0.3, 0.6, 8]} />
                    <meshStandardMaterial color={FLAGSHIP_CONE_COLOR} emissive={FLAGSHIP_CONE_COLOR} emissiveIntensity={0.5} />
                </mesh>
            )}
        </group>
    );
}

function TacticalSceneContent() {
    const { units, currentBattle } = useTacticalStore();
    const attackerFactionId = currentBattle?.attackerFactionId ?? -1;

    const aliveUnits = units.filter((u) => u.isAlive);

    function getUnitColor(unit: TacticalUnit): string {
        return unit.factionId === attackerFactionId ? EMPIRE_COLOR : ALLIANCE_COLOR;
    }

    return (
        <>
            <ambientLight intensity={0.5} />
            <directionalLight position={[10, 20, 10]} intensity={0.8} />

            {/* Grid plane background */}
            <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]} receiveShadow>
                <planeGeometry args={[40, 40]} />
                <meshStandardMaterial color="#000010" />
            </mesh>

            {/* Grid lines */}
            <gridHelper args={[40, 40, '#1a3060', '#0d1830']} position={[0, 0, 0]} />

            {/* Unit blocks */}
            {aliveUnits.map((unit) => (
                <TacticalUnitBlock
                    key={unit.fleetId}
                    unit={unit}
                    color={getUnitColor(unit)}
                    isFlagship={unit.unitType === 'FLAGSHIP'}
                />
            ))}

            <OrbitControls enablePan={true} enableZoom={true} enableRotate={true} />
        </>
    );
}

export function TacticalMapR3F() {
    return (
        <Canvas
            style={{ width: '100%', height: '100%', background: '#000010' }}
            camera={{ fov: 50, position: [0, 20, 15], near: 0.1, far: 1000 }}
            gl={{ antialias: true }}
        >
            <Suspense fallback={null}>
                <TacticalSceneContent />
            </Suspense>
        </Canvas>
    );
}
