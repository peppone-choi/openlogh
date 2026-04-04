'use client';
// Design Ref: §4.1 — 2D/3D 모드 전환 토글 버튼
import { useMap3d } from '@/hooks/useMap3d';
import { cn } from '@/lib/utils';

export function MapModeToggle() {
  const { mapMode, toggleMode, webglSupported } = useMap3d();

  if (!webglSupported) return null;

  return (
    <button
      onClick={toggleMode}
      className={cn(
        'absolute top-2 right-2 z-20 rounded-md px-3 py-1.5 text-xs font-bold shadow-md transition-colors',
        mapMode === '3d'
          ? 'bg-cyan-600 text-white hover:bg-cyan-700'
          : 'bg-zinc-700 text-zinc-200 hover:bg-zinc-600',
      )}
      title={mapMode === '3d' ? '2D 맵으로 전환' : '3D 맵으로 전환'}
    >
      {mapMode === '3d' ? '2D' : '3D'}
    </button>
  );
}
