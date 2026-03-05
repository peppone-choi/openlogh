"use client";

import { getSuppliedFlagUrl, getDepletedFlagUrl } from "@/lib/image";

const GRAY = "A9A9A9";

interface FactionFlagProps {
  color: string;
  supplied: boolean;
  className?: string;
  style?: React.CSSProperties;
}

export function FactionFlag({
  color,
  supplied,
  className,
  style,
}: FactionFlagProps) {
  const src = supplied
    ? getSuppliedFlagUrl(GRAY)
    : getDepletedFlagUrl(GRAY);
  const size = supplied ? 12 : 16;
  const sizePx = `${size}px`;

  return (
    <span
      className={className}
      style={{
        display: "inline-block",
        width: size,
        height: size,
        backgroundImage: `url(${src})`,
        backgroundColor: color,
        backgroundBlendMode: "multiply",
        backgroundSize: `${sizePx} ${sizePx}`,
        WebkitMaskImage: `url(${src})`,
        WebkitMaskSize: `${sizePx} ${sizePx}`,
        maskImage: `url(${src})`,
        maskSize: `${sizePx} ${sizePx}`,
        imageRendering: "pixelated",
        ...style,
      }}
    />
  );
}
