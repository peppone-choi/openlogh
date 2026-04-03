// Design Ref: §3.3 CastleLoader — GLB 로더 + 캐시 + 크기 정규화
import * as THREE from 'three';

const CDN_BASE =
  process.env.NEXT_PUBLIC_IMAGE_CDN_BASE ||
  'https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master';

// 거점 모델 (Lv1-4, 비도시)
const SPOT_MODELS = {
  naval: 'spot_naval.glb',
  camp: 'spot_camp.glb',
  gate: 'spot_gate.glb',
  tribal: 'spot_tribal.glb',
} as const;

// 성곽 도시 모델 (Lv5-8)
const CITY_MODELS = {
  small: 'city_small.glb',
  medium: 'city_medium.glb',
  large: 'city_large.glb',
  grand: 'city_grand.glb',
} as const;

export type SpotKey = keyof typeof SPOT_MODELS;
export type CityKey = keyof typeof CITY_MODELS;

export interface LocationConfig {
  type: 'spot' | 'city';
  modelFile: string;
  /** 정규화 후 적용할 최종 스케일 (레벨에 따라 크기 차등) */
  targetScale: number;
  /** 바닥 원형 반지름 */
  baseRadius: number;
}

/** 레벨 → 모델 + 스케일 매핑 */
export function getLocationConfig(level: number): LocationConfig {
  switch (level) {
    case 1:
      return { type: 'spot', modelFile: SPOT_MODELS.naval, targetScale: 8, baseRadius: 12 };
    case 2:
      return { type: 'spot', modelFile: SPOT_MODELS.camp, targetScale: 9, baseRadius: 13 };
    case 3:
      return { type: 'spot', modelFile: SPOT_MODELS.gate, targetScale: 10, baseRadius: 14 };
    case 4:
      return { type: 'spot', modelFile: SPOT_MODELS.tribal, targetScale: 10, baseRadius: 14 };
    case 5:
      return { type: 'city', modelFile: CITY_MODELS.small, targetScale: 14, baseRadius: 18 };
    case 6:
      return { type: 'city', modelFile: CITY_MODELS.medium, targetScale: 17, baseRadius: 22 };
    case 7:
      return { type: 'city', modelFile: CITY_MODELS.large, targetScale: 22, baseRadius: 26 };
    case 8:
      return { type: 'city', modelFile: CITY_MODELS.grand, targetScale: 28, baseRadius: 30 };
    default:
      return { type: 'spot', modelFile: SPOT_MODELS.camp, targetScale: 3.0, baseRadius: 8 };
  }
}

/** GLB CDN URL 생성 */
export function getModelUrl(modelFile: string): string {
  return `${CDN_BASE}/game/3d/${modelFile}`;
}

/** 모든 GLB URL 목록 (사전 로드용) */
export function getAllModelUrls(): string[] {
  const files = [...Object.values(SPOT_MODELS), ...Object.values(CITY_MODELS)];
  return files.map(getModelUrl);
}

/**
 * GLB 모델을 정규화: 중심 정렬 + 1 unit 기준 스케일링
 * 이후 targetScale을 곱해서 최종 크기 결정
 */
export function normalizeModel(scene: THREE.Group): { normalized: THREE.Group; originalSize: THREE.Vector3 } {
  const box = new THREE.Box3().setFromObject(scene);
  const center = box.getCenter(new THREE.Vector3());
  const size = box.getSize(new THREE.Vector3());
  const maxDim = Math.max(size.x, size.y, size.z);

  // 1 unit으로 정규화
  const scale = 1 / maxDim;
  scene.scale.setScalar(scale);

  // 중심 정렬 (바닥이 y=0에 오도록)
  scene.position.set(
    -center.x * scale,
    -box.min.y * scale,
    -center.z * scale,
  );

  return { normalized: scene, originalSize: size };
}
