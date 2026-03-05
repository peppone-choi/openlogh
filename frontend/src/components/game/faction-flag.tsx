import { getSuppliedFlagUrl, getDepletedFlagUrl } from "@/lib/image";

const GRAY = "A9A9A9";

const SUPPLIED_POLE_CLIP =
  "polygon(4px 0, 12px 0, 12px 12px, 3px 12px, 3px 5px, 4px 5px)";
const DEPLETED_POLE_CLIP =
  "polygon(3px 0, 16px 0, 16px 16px, 3px 16px, 3px 14px, 5px 4px, 3px 4px, 3px 0)";

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

  const bgProps: React.CSSProperties = {
    backgroundImage: `url(${src})`,
    backgroundSize: `${sizePx} ${sizePx}`,
    WebkitMaskImage: `url(${src})`,
    WebkitMaskSize: `${sizePx} ${sizePx}`,
    maskImage: `url(${src})`,
    maskSize: `${sizePx} ${sizePx}`,
  };

  return (
    <span
      className={className}
      style={{
        display: "inline-block",
        position: "relative",
        width: size,
        height: size,
        imageRendering: "pixelated",
        ...style,
      }}
    >
      <span style={{ position: "absolute", inset: 0, ...bgProps }} />
      <span
        style={{
          position: "absolute",
          inset: 0,
          ...bgProps,
          backgroundColor: color,
          backgroundBlendMode: "multiply",
          clipPath: supplied ? SUPPLIED_POLE_CLIP : DEPLETED_POLE_CLIP,
        }}
      />
    </span>
  );
}
