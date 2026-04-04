'use client';

interface LoghBarProps {
    height: 7 | 10;
    percent: number;
    altText?: string;
}

export function LoghBar({ height, percent, altText }: LoghBarProps) {
    const clampedPercent = Math.max(0, Math.min(100, percent));
    return (
        <div
            className="relative mx-auto w-full overflow-hidden bg-muted/50"
            style={{ height: height + 2 }}
            title={altText ?? `${Math.round(clampedPercent)}%`}
        >
            <div
                className="h-full bg-emerald-500/80 transition-[width] duration-200"
                style={{ width: `${clampedPercent}%` }}
            />
            <div className="absolute inset-0 border border-foreground/15 pointer-events-none" aria-hidden="true" />
        </div>
    );
}
