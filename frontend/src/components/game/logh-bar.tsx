'use client';

import { getProgressBarBg, getProgressBarFill } from '@/lib/image';

interface LoghBarProps {
    height: 7 | 10;
    percent: number;
    altText?: string;
}

/**
 * LoghBar — progress bar component using CDN gif images.
 * height: 7 (planet stats) or 10 (officer stats)
 * percent: 0-100
 */
export function LoghBar({ height, percent, altText }: LoghBarProps) {
    const clampedPercent = Math.max(0, Math.min(100, percent));
    const bgUrl = getProgressBarBg(height);
    const fillUrl = getProgressBarFill(height);

    return (
        <div
            className="relative mx-auto w-full overflow-hidden"
            style={{
                height: height + 2,
                borderTop: '1px solid #888',
                borderBottom: '1px solid #333',
            }}
            title={altText ?? `${Math.round(clampedPercent)}%`}
        >
            <div
                style={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    width: '100%',
                    height,
                    backgroundImage: `url('${bgUrl}')`,
                    backgroundRepeat: 'repeat-x',
                    backgroundPosition: 'center',
                    backgroundSize: `auto ${height}px`,
                }}
            />
            <div
                style={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    height,
                    width: `${clampedPercent}%`,
                    backgroundImage: `url('${fillUrl}')`,
                    backgroundRepeat: 'repeat-x',
                    backgroundPosition: 'left center',
                    backgroundSize: `auto ${height}px`,
                }}
            />
        </div>
    );
}
