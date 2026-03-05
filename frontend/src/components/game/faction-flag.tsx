"use client";

import { useEffect, useRef, useCallback } from "react";

const SUPPLIED_FRAMES = [
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAA40lEQVR42pWSsUoEMRCGv10shcVHCMJ2QiCND2E1sJW1xRVW0VKEbW31Ga6L5TWHlYWNsLDlcgRrQUl1WI2NWVYPF/0gZMj8HwxD4AsHKiLKX3Ggr/1KAURklHPtvVeAvZ+iiGjTNAzDAKDGGKqqous6doR9NuRwSglr7dgzxgBQ5HEe+1sAbu7fAEgp7YwdY6ScPpxeP/waBrDWfhfmwpmSf1KKiF4slyzOrmaDeVPlSwjUdc353Xps5DufGOMoFg406PiA/umdZyi89zrd+5QQQoEDdaCXJ4f6sd1q27azX+QT5IBemNoWEhIAAAAASUVORK5CYII=",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAA80lEQVR42oWRP0oEMRjFfzMMyFZf4QmCzNQD3kAsrQJTWW8hYmcr4hnUYk+whRA8gFiJbBEhMNtNETzBkhPEYs2QHV32NfnDe/yS98GvTiFqrSMHVOWHxcMcIAIYY4qDAQClFACJNg0W+ZM++ide18dYa0eD937ct227JSQzgLUWESGEgPd+JIoIzjnKHHd5/w5ACAER2THv/UNSCk1VTi9yUwhhZwUotdbxdrnkan73J/RfuPw2hqZpuHl+A6Cua/ZRjTFFBfB4fU6/2vC1rTl2XccwDDjnUEohImO9FUC/2nB2ccLnyzoezWZFPu00wNTYD1lzXRcQUmhqAAAAAElFTkSuQmCC",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAA2klEQVR42o3QMUrEQBTG8X/CggSEOUOQpA7kBmJpNfAqawsRO1vxEOoZUgSmtZGtliXNQiB2QYK1RXgnGAtNMFGz+8rH/Oab+eB7cvDWWs+hk4P/aJ73otV8kWUZgAdwzgWL4Jg3VHVAAH6OVsNzNs3juFTVf9MmCRf3a+I4nsCfaQDhvjJUdQJ/AWPM4gWhtdbfFgVXl3eLaEgJ350jTVNunl4ASJLkT2SMoa7rr08/XJ/RVD07CAAvIrRtS9d1kxLGlpqq5/T8hG356o+iKBgaERHKshwPiwifKy9TVUaKrYoAAAAASUVORK5CYII=",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAA80lEQVR42oWRP0oEMRjFfzMMyFZf4QmCzNQD3kAsrQJTWW8hYmcr4hnUYk+whRA8gFiJbBEhMNtNETzBkhPEYs2QHV32NfnDe/yS98GvTiFqrSMHVOWHxcMcIAIYY4qDAQClFACJNg0W+ZM++ide18dYa0eD937ct227JSQzgLUWESGEgPd+JIoIzjnKHHd5/w5ACAER2THv/UNSCk1VTi9yUwhhZwUotdbxdrnkan73J/RfuPw2hqZpuHl+A6Cua/ZRjTFFBfB4fU6/2vC1rTl2XccwDDjnUEohImO9FUC/2nB2ccLnyzoezWZFPu00wNTYD1lzXRcQUmhqAAAAAElFTkSuQmCC",
];

const DEPLETED_FRAME =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAApklEQVR42mNkgAIbG5v/DEjgyJEjjAxEACZkjrm5OUNdXR0DKYAJXWDnzp1YXUSUARISEgwSEhIM5ubm5Lkgx4OT4c2bN5R5gVTAhMu/5ubmRIUD3AW7p0cwNCx5zPDnz58B8AIuQIw3mIiNb7wuINf/cAOm7PjOICIiQpY3mMhNQFgD8eTJkxgK3N3d8ZtgY2PzH4YZGBgYdu3a9d/GxuY/jCbkBABiTT6SOrddoAAAAABJRU5ErkJggg==";

const FRAME_DURATION = 1000;
const SUPPLIED_SIZE = 12;
const DEPLETED_SIZE = 16;

interface FactionFlagProps {
  color: string;
  supplied: boolean;
  className?: string;
  style?: React.CSSProperties;
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  return [
    parseInt(h.substring(0, 2), 16),
    parseInt(h.substring(2, 4), 16),
    parseInt(h.substring(4, 6), 16),
  ];
}

type CompositeCache = Map<string, ImageBitmap[]>;
const compositeCache: CompositeCache = new Map();

function cacheKey(color: string, supplied: boolean): string {
  return `${color}-${supplied ? "s" : "d"}`;
}

async function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

async function compositeFrames(
  color: string,
  supplied: boolean,
): Promise<ImageBitmap[]> {
  const key = cacheKey(color, supplied);
  const cached = compositeCache.get(key);
  if (cached) return cached;

  const [tr, tg, tb] = hexToRgb(color);
  const sources = supplied ? SUPPLIED_FRAMES : [DEPLETED_FRAME];
  const size = supplied ? SUPPLIED_SIZE : DEPLETED_SIZE;

  const canvas = new OffscreenCanvas(size, size);
  const ctx = canvas.getContext("2d")!;
  const bitmaps: ImageBitmap[] = [];

  for (const src of sources) {
    const img = await loadImage(src);
    ctx.clearRect(0, 0, size, size);
    ctx.drawImage(img, 0, 0);
    const imageData = ctx.getImageData(0, 0, size, size);
    const d = imageData.data;

    for (let i = 0; i < d.length; i += 4) {
      if (d[i + 3] === 0) continue;
      const r = d[i], g = d[i + 1], b = d[i + 2];
      if (r === g && g === b) {
        d[i] = Math.round((r * tr) / 255);
        d[i + 1] = Math.round((g * tg) / 255);
        d[i + 2] = Math.round((b * tb) / 255);
      }
    }

    ctx.putImageData(imageData, 0, 0);
    bitmaps.push(await createImageBitmap(canvas));
  }

  compositeCache.set(key, bitmaps);
  return bitmaps;
}

export function FactionFlag({
  color,
  supplied,
  className,
  style,
}: FactionFlagProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const frameRef = useRef(0);
  const bitmapsRef = useRef<ImageBitmap[]>([]);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    const bitmaps = bitmapsRef.current;
    if (!canvas || bitmaps.length === 0) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmaps[frameRef.current], 0, 0);
  }, []);

  useEffect(() => {
    let cancelled = false;

    compositeFrames(color, supplied).then((bitmaps) => {
      if (cancelled) return;
      bitmapsRef.current = bitmaps;
      frameRef.current = 0;
      draw();

      if (timerRef.current) clearInterval(timerRef.current);

      if (bitmaps.length > 1) {
        timerRef.current = setInterval(() => {
          frameRef.current = (frameRef.current + 1) % bitmaps.length;
          draw();
        }, FRAME_DURATION);
      }
    });

    return () => {
      cancelled = true;
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [color, supplied, draw]);

  const size = supplied ? SUPPLIED_SIZE : DEPLETED_SIZE;

  return (
    <canvas
      ref={canvasRef}
      width={size}
      height={size}
      className={className}
      style={{ imageRendering: "pixelated", ...style }}
    />
  );
}
