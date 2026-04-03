'use client';
// Design Ref: §3.3 CityModel — GLB 모델 + 깃발 + 바닥 원형 + 이름 라벨
// WORLD_SCALE: 모든 크기값에 1회만 균일 적용
import { useMemo, useRef } from 'react';
import { useGLTF, Text } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import type { RenderCity } from '@/components/game/map-canvas';
import { toWorld3d, sampleHeight, WORLD_SCALE } from '@/lib/map-3d-utils';
import { getLocationConfig, getModelUrl } from './CastleLoader';

interface CityModelProps {
  city: RenderCity;
  heightMap?: Float32Array;
  segments?: number;
  onClick?: (cityId: number) => void;
  onHover?: (cityId: number | null) => void;
}

// 모든 크기 = 기준값 * S
const S = WORLD_SCALE;

/** 펄럭이는 깃발 셰이더 머티리얼 */
const flagVertexShader = /* glsl */ `
  uniform float uTime;
  uniform float uAmplitude;
  varying vec2 vUv;
  void main() {
    vUv = uv;
    vec3 pos = position;
    // uv.x가 0(깃대쪽)→1(끝)으로 갈수록 진폭 증가
    float wave = sin(pos.x * 4.0 + uTime * 3.0) * uAmplitude * uv.x;
    wave += sin(pos.x * 7.0 + uTime * 5.0) * uAmplitude * 0.3 * uv.x;
    pos.z += wave;
    // 약간의 y축 출렁임
    pos.y += sin(pos.x * 5.0 + uTime * 4.0) * uAmplitude * 0.2 * uv.x;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
  }
`;

const flagFragmentShader = /* glsl */ `
  uniform vec3 uColor;
  uniform sampler2D uTexture;
  uniform float uHasTexture;
  varying vec2 vUv;
  void main() {
    float shade = 0.85 + 0.15 * vUv.x;
    if (uHasTexture > 0.5) {
      vec4 tex = texture2D(uTexture, vUv);
      gl_FragColor = vec4(tex.rgb * shade, tex.a);
    } else {
      gl_FragColor = vec4(uColor * shade, 1.0);
    }
  }
`;

/** Canvas 2D로 국가색 배경 + 국가명 텍스처 생성 */
function createFlagTexture(color: string, text: string): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 128;
  const ctx = canvas.getContext('2d')!;

  // 배경: 국가 색상
  ctx.fillStyle = color;
  ctx.fillRect(0, 0, 256, 128);

  // 테두리 (약간 어두운 색)
  ctx.strokeStyle = 'rgba(0,0,0,0.3)';
  ctx.lineWidth = 4;
  ctx.strokeRect(2, 2, 252, 124);

  // 국가명 텍스트
  ctx.fillStyle = '#ffffff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const fontSize = text.length <= 2 ? 64 : text.length <= 4 ? 48 : 36;
  ctx.font = `bold ${fontSize}px sans-serif`;
  ctx.shadowColor = 'rgba(0,0,0,0.6)';
  ctx.shadowBlur = 4;
  ctx.fillText(text, 128, 64);

  const texture = new THREE.CanvasTexture(canvas);
  texture.needsUpdate = true;
  return texture;
}

function WavingFlag({
  width,
  height,
  color,
  position,
  amplitude,
  nationName,
}: {
  width: number;
  height: number;
  color: string;
  position: [number, number, number];
  amplitude: number;
  nationName?: string | null;
}) {
  const meshRef = useRef<THREE.Mesh>(null);

  const texture = useMemo(
    () => (nationName ? createFlagTexture(color, nationName) : null),
    [color, nationName],
  );

  const uniforms = useMemo(
    () => ({
      uTime: { value: 0 },
      uAmplitude: { value: amplitude },
      uColor: { value: new THREE.Color(color) },
      uTexture: { value: texture ?? new THREE.Texture() },
      uHasTexture: { value: texture ? 1.0 : 0.0 },
    }),
    [amplitude, color, texture],
  );

  useFrame((_, delta) => {
    uniforms.uTime.value += delta;
  });

  return (
    <mesh ref={meshRef} position={position}>
      <planeGeometry args={[width, height, 16, 8]} />
      <shaderMaterial
        vertexShader={flagVertexShader}
        fragmentShader={flagFragmentShader}
        uniforms={uniforms}
        side={THREE.DoubleSide}
      />
    </mesh>
  );
}

export function CityModel({ city, heightMap, segments = 64, onClick, onHover }: CityModelProps) {
  const config = getLocationConfig(city.level);
  const modelUrl = getModelUrl(config.modelFile);
  const { scene } = useGLTF(modelUrl);

  const model = useMemo(() => {
    const cloned = scene.clone(true);
    const box = new THREE.Box3().setFromObject(cloned);
    const center = box.getCenter(new THREE.Vector3());
    const size = box.getSize(new THREE.Vector3());
    const maxDim = Math.max(size.x, size.y, size.z);
    const scale = (config.targetScale * S) / maxDim;

    cloned.scale.setScalar(scale);
    cloned.position.set(
      -center.x * scale,
      -box.min.y * scale, // 바닥 y=0
      -center.z * scale,
    );
    return cloned;
  }, [scene, config.targetScale]);

  const position = useMemo(() => {
    const h = heightMap ? sampleHeight(heightMap, city.x, city.y, segments) : 0;
    return toWorld3d(city.x, city.y, h);
  }, [city.x, city.y, heightMap, segments]);

  const modelH = config.targetScale * S;
  const flagH = modelH * 0.5;
  const labelY = modelH * 1.1 + 2 * S;
  const flagPoleR = 0.15 * S;
  const flagW = 2.4 * S;
  const flagFH = 1.5 * S;
  const baseR = config.baseRadius * S;
  const nationColor = city.nationColor;

  return (
    <group position={[position.x, position.y, position.z]}>
      {/* 바닥 원형 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.1, 0]}>
        <circleGeometry args={[baseR, 16]} />
        <meshStandardMaterial
          color={nationColor ?? '#555555'}
          transparent
          opacity={nationColor ? 0.35 : 0.15}
          roughness={1}
          depthWrite={false}
        />
      </mesh>

      {/* GLB 모델 */}
      <primitive
        object={model}
        onClick={(e: { stopPropagation?: () => void }) => {
          e.stopPropagation?.();
          onClick?.(city.id);
        }}
        onPointerOver={() => onHover?.(city.id)}
        onPointerOut={() => onHover?.(null)}
      />

      {/* 국가 깃발 (펄럭이는 셰이더) */}
      {nationColor && (
        <group position={[0, flagH, 0]}>
          {/* 깃대 */}
          <mesh>
            <cylinderGeometry args={[flagPoleR, flagPoleR, flagH * 0.4, 4]} />
            <meshStandardMaterial color="#5c4033" />
          </mesh>
          {/* 깃발 천 */}
          <WavingFlag
            width={flagW}
            height={flagFH}
            color={nationColor}
            position={[flagW * 0.5, flagH * 0.15, 0]}
            amplitude={0.4 * S}
            nationName={city.nationName}
          />
        </group>
      )}

      {/* 도시 이름 (GPU SDF 텍스트) */}
      <Text
        position={[0, labelY, 0]}
        fontSize={3 * S}
        color="white"
        anchorX="center"
        anchorY="middle"
        outlineWidth={0.4 * S}
        outlineColor="black"
      >
        {city.name}
      </Text>
    </group>
  );
}
