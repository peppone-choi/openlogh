'use client';
// Session 4: 유닛 마커 3D + 진군 애니메이션 + 전투 이펙트
import { useRef, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import { Line, Text, Billboard } from '@react-three/drei';
import * as THREE from 'three';
import type { UnitMarker } from '@/components/game/unit-markers';
import type { CityConst } from '@/types';
import { toWorld3d, WORLD_SCALE } from '@/lib/map-3d-utils';
import { parseCrewTypeCode } from '@/lib/game-utils';
import { findCityPath, pathToPositions } from '@/lib/map-pathfinding';

interface UnitMarkers3dProps {
  markers: UnitMarker[];
  cities: CityConst[];
  onMarkerClick?: (generalId: number) => void;
}

const S = WORLD_SCALE;
const UNIT_Y_OFFSET = 12 * S; // 지형 위 높이

/** 병종 코드 → 색상 (계열별 구분) */
function crewColor(crewType: number): string {
  const base = crewType >= 1000 ? Math.floor(crewType / 100) * 100 : crewType;
  if (base === 0 || base === 1100) return '#4ade80'; // 보병 — 녹색
  if (base === 1 || base === 5 || base === 6 || base === 1200) return '#fbbf24'; // 궁병 — 황색
  if (base === 2 || base === 7 || base === 9 || base === 1300) return '#60a5fa'; // 기병 — 청색
  if (base === 3 || base === 1400) return '#a855f7'; // 귀병 — 보라
  if (base === 4 || base === 1500) return '#f97316'; // 공성 — 주황
  if (base === 11) return '#22d3ee'; // 수군 — 시안
  return '#94a3b8'; // 기타
}

/** 개별 유닛 마커 */
function UnitMarker3d({
  marker,
  cities,
  onClick,
}: {
  marker: UnitMarker;
  cities: CityConst[];
  onClick?: (id: number) => void;
}) {
  const groupRef = useRef<THREE.Group>(null);
  const crewCode = parseCrewTypeCode(marker.crewType);
  const color = crewColor(crewCode);

  const startPos = useMemo(
    () => toWorld3d(marker.posX, marker.posY, 0),
    [marker.posX, marker.posY],
  );

  // 가도 경로: 도시 connections를 따라가는 경유 좌표
  const routePoints = useMemo(() => {
    if (!marker.isMoving || marker.destX == null || marker.destY == null) return null;
    // 도시 좌표로 출발/도착 도시 ID 찾기
    const fromCity = cities.find((c) => c.x === marker.posX && c.y === marker.posY);
    const toCity = cities.find((c) => c.x === marker.destX && c.y === marker.destY);
    if (!fromCity || !toCity) {
      // 폴백: 직선
      return [
        new THREE.Vector3(startPos.x, UNIT_Y_OFFSET, startPos.z),
        toWorld3d(marker.destX, marker.destY, 0).setY(UNIT_Y_OFFSET),
      ];
    }
    const pathIds = findCityPath(cities, fromCity.id, toCity.id);
    const positions = pathToPositions(cities, pathIds);
    return positions.map((p) => {
      const w = toWorld3d(p.x, p.y, 0);
      return new THREE.Vector3(w.x, UNIT_Y_OFFSET, w.z);
    });
  }, [marker, cities, startPos]);

  // 진군 애니메이션: 경로를 따라 이동
  useFrame(({ clock }) => {
    if (!groupRef.current || !routePoints || routePoints.length < 2) return;
    // 전체 경로를 15초 주기로 왕복
    const totalT = (Math.sin(clock.elapsedTime * 0.4) + 1) / 2; // 0~1
    const segCount = routePoints.length - 1;
    const rawIdx = totalT * segCount;
    const segIdx = Math.min(Math.floor(rawIdx), segCount - 1);
    const segT = rawIdx - segIdx;

    groupRef.current.position.lerpVectors(routePoints[segIdx], routePoints[segIdx + 1], segT);
    // 바운스
    groupRef.current.position.y += Math.sin(clock.elapsedTime * 3) * S * 0.5;
  });

  const borderColor = marker.isEnemy ? '#ef4444' : marker.nationColor;

  // 경로선 좌표 (drei Line용)
  const linePoints = useMemo(
    () => routePoints?.map((p) => [p.x, p.y, p.z] as [number, number, number]) ?? null,
    [routePoints],
  );

  return (
    <>
      {/* 가도를 따르는 이동 경로선 */}
      {linePoints && linePoints.length >= 2 && (
        <Line
          points={linePoints}
          color={marker.nationColor}
          lineWidth={1.5}
          dashed
          dashSize={3 * S}
          gapSize={2 * S}
        />
      )}

      {/* 유닛 마커 */}
      <group
        ref={groupRef}
        position={[startPos.x, UNIT_Y_OFFSET, startPos.z]}
        onClick={(e) => {
          e.stopPropagation?.();
          onClick?.(marker.generalId);
        }}
      >
        {/* 마커 본체 (다이아몬드) */}
        <mesh rotation={[0, Math.PI / 4, 0]}>
          <boxGeometry args={[2.5 * S, 3 * S, 2.5 * S]} />
          <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.3} />
        </mesh>

        {/* 테두리 링 (적군=빨강) */}
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -1.5 * S, 0]}>
          <ringGeometry args={[2 * S, 2.5 * S, 16]} />
          <meshBasicMaterial color={borderColor} side={THREE.DoubleSide} />
        </mesh>

        {/* 장수 이름 */}
        <Billboard position={[0, 4 * S, 0]}>
          <Text
            fontSize={2 * S}
            color="white"
            outlineWidth={0.3 * S}
            outlineColor="black"
            anchorX="center"
            anchorY="middle"
          >
            {marker.name}
          </Text>
        </Billboard>

        {/* 병력 수 */}
        <Billboard position={[0, 2 * S, 0]}>
          <Text
            fontSize={1.5 * S}
            color="#fbbf24"
            outlineWidth={0.2 * S}
            outlineColor="black"
            anchorX="center"
            anchorY="middle"
          >
            {marker.crew.toLocaleString()}
          </Text>
        </Billboard>
      </group>
    </>
  );
}

/** 전투 이펙트: 전투 발생 도시에 파티클 */
function BattleEffect({ x, y }: { x: number; y: number }) {
  const meshRef = useRef<THREE.Mesh>(null);
  const pos = useMemo(() => toWorld3d(x, y, 0), [x, y]);

  useFrame(({ clock }) => {
    if (!meshRef.current) return;
    const s = 1 + Math.sin(clock.elapsedTime * 5) * 0.3;
    meshRef.current.scale.setScalar(s);
    meshRef.current.rotation.y = clock.elapsedTime * 2;
  });

  return (
    <mesh ref={meshRef} position={[pos.x, UNIT_Y_OFFSET + 2 * S, pos.z]}>
      <octahedronGeometry args={[3 * S]} />
      <meshStandardMaterial
        color="#ff4444"
        emissive="#ff2200"
        emissiveIntensity={0.8}
        transparent
        opacity={0.6}
        wireframe
      />
    </mesh>
  );
}

export function UnitMarkers3d({ markers, cities, onMarkerClick }: UnitMarkers3dProps) {
  // 전투 중인 도시 = 같은 도시에 적군+아군 존재
  const battleCities = useMemo(() => {
    const cityNations = new Map<string, Set<boolean>>();
    for (const m of markers) {
      const key = `${m.posX},${m.posY}`;
      if (!cityNations.has(key)) cityNations.set(key, new Set());
      cityNations.get(key)!.add(m.isEnemy);
    }
    const battles: { x: number; y: number }[] = [];
    for (const [key, sides] of cityNations) {
      if (sides.size > 1) {
        const [x, y] = key.split(',').map(Number);
        battles.push({ x, y });
      }
    }
    return battles;
  }, [markers]);

  return (
    <group>
      {markers.map((m) => (
        <UnitMarker3d key={m.generalId} marker={m} cities={cities} onClick={onMarkerClick} />
      ))}
      {battleCities.map(({ x, y }) => (
        <BattleEffect key={`battle-${x}-${y}`} x={x} y={y} />
      ))}
    </group>
  );
}
