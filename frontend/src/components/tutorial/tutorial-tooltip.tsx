'use client';

import { useEffect, useRef, useState } from 'react';

interface TutorialTooltipProps {
    title: string;
    description: string;
    position: 'top' | 'bottom' | 'left' | 'right';
    targetSelector?: string;
}

export function TutorialTooltip({ title, description, position, targetSelector }: TutorialTooltipProps) {
    const tooltipRef = useRef<HTMLDivElement>(null);
    const [style, setStyle] = useState<React.CSSProperties>({});

    useEffect(() => {
        if (!targetSelector) {
            // No target — center the tooltip
            setStyle({
                position: 'fixed',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
            });
            return;
        }

        const el = document.querySelector(targetSelector);
        if (!el) {
            setStyle({
                position: 'fixed',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
            });
            return;
        }

        const update = () => {
            const rect = el.getBoundingClientRect();
            const tooltip = tooltipRef.current;
            const tooltipWidth = tooltip?.offsetWidth ?? 320;
            const tooltipHeight = tooltip?.offsetHeight ?? 120;
            const gap = 16;

            let top = 0;
            let left = 0;

            switch (position) {
                case 'top':
                    top = rect.top - tooltipHeight - gap;
                    left = rect.left + rect.width / 2 - tooltipWidth / 2;
                    break;
                case 'bottom':
                    top = rect.bottom + gap;
                    left = rect.left + rect.width / 2 - tooltipWidth / 2;
                    break;
                case 'left':
                    top = rect.top + rect.height / 2 - tooltipHeight / 2;
                    left = rect.left - tooltipWidth - gap;
                    break;
                case 'right':
                    top = rect.top + rect.height / 2 - tooltipHeight / 2;
                    left = rect.right + gap;
                    break;
            }

            // Clamp to viewport
            const vw = window.innerWidth;
            const vh = window.innerHeight;
            if (left < 8) left = 8;
            if (left + tooltipWidth > vw - 8) left = vw - tooltipWidth - 8;
            if (top < 8) top = 8;
            if (top + tooltipHeight > vh - 80) top = vh - tooltipHeight - 80;

            setStyle({ position: 'fixed', top, left });
        };

        update();
        window.addEventListener('scroll', update, true);
        window.addEventListener('resize', update);

        return () => {
            window.removeEventListener('scroll', update, true);
            window.removeEventListener('resize', update);
        };
    }, [targetSelector, position]);

    return (
        <div
            ref={tooltipRef}
            className="pointer-events-auto z-[52] max-w-sm rounded-lg border bg-background p-4 shadow-xl"
            style={style}
        >
            <h3 className="text-base font-bold mb-1">{title}</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">{description}</p>
        </div>
    );
}
