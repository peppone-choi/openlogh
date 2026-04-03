'use client';
// Design Ref: §5.3 — 3D 맵 상태/설정/모드 관리 (Zustand 공유 스토어)
import { create } from 'zustand';
import type { MapRenderMode, Map3dConfig } from '@/types';
import { isWebGLSupported, isMobileDevice } from '@/lib/map-3d-utils';

const STORAGE_KEY = 'opensamguk-map-mode';

interface Map3dState {
  config: Map3dConfig;
  webglSupported: boolean;
  isMobile: boolean;
  mapMode: MapRenderMode;
  setMapMode: (mode: MapRenderMode) => void;
  toggleMode: () => void;
}

function loadSavedMode(): MapRenderMode {
  if (typeof window === 'undefined') return '2d';
  try {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      if (parsed.mode === '3d' || parsed.mode === '2d') return parsed.mode;
    }
  } catch {
    // ignore
  }
  return '2d';
}

export const useMap3d = create<Map3dState>((set, get) => {
  const webgl = typeof window !== 'undefined' ? isWebGLSupported() : false;
  const mobile = typeof window !== 'undefined' ? isMobileDevice() : false;
  const savedMode = loadSavedMode();
  const initialMode: MapRenderMode = webgl ? savedMode : '2d';

  return {
    config: {
      mode: initialMode,
      quality: mobile ? 'medium' : 'high',
      showDecorations: true,
      showLabels: true,
      showNationOverlay: true,
    },
    webglSupported: webgl,
    isMobile: mobile,
    mapMode: initialMode,

    setMapMode: (mode: MapRenderMode) => {
      const { webglSupported } = get();
      if (!webglSupported && mode === '3d') return;
      set((state) => {
        const next = { ...state.config, mode };
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        return { config: next, mapMode: mode };
      });
    },

    toggleMode: () => {
      const { mapMode, setMapMode } = get();
      setMapMode(mapMode === '2d' ? '3d' : '2d');
    },
  };
});
