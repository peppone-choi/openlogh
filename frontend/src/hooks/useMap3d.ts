'use client';
// Design Ref: §5.3 — 3D 맵 상태/설정/모드 관리
import { useState, useCallback, useEffect } from 'react';
import type { MapRenderMode, Map3dConfig } from '@/types';
import { isWebGLSupported, isMobileDevice } from '@/lib/map-3d-utils';

const STORAGE_KEY = 'opensamguk-map-mode';

const DEFAULT_CONFIG: Map3dConfig = {
  mode: '2d',
  quality: 'high',
  showDecorations: true,
  showLabels: true,
  showNationOverlay: true,
};

export function useMap3d() {
  const [config, setConfig] = useState<Map3dConfig>(() => {
    if (typeof window === 'undefined') return DEFAULT_CONFIG;
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (saved) {
      try {
        return { ...DEFAULT_CONFIG, ...JSON.parse(saved) };
      } catch {
        // ignore
      }
    }
    return DEFAULT_CONFIG;
  });

  const [webglSupported] = useState(() => isWebGLSupported());
  const [isMobile] = useState(() => isMobileDevice());

  // 모바일이면 품질 자동 조절
  useEffect(() => {
    if (isMobile && config.quality === 'high') {
      setConfig((prev) => ({ ...prev, quality: 'medium' }));
    }
  }, [isMobile, config.quality]);

  const mapMode: MapRenderMode = webglSupported ? config.mode : '2d';

  const setMapMode = useCallback(
    (mode: MapRenderMode) => {
      if (!webglSupported && mode === '3d') return;
      const next = { ...config, mode };
      setConfig(next);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    },
    [config, webglSupported],
  );

  const toggleMode = useCallback(() => {
    setMapMode(mapMode === '2d' ? '3d' : '2d');
  }, [mapMode, setMapMode]);

  return {
    config,
    mapMode,
    setMapMode,
    toggleMode,
    webglSupported,
    isMobile,
  };
}
