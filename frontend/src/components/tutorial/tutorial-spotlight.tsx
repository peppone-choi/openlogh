'use client';

import { useEffect, useState } from 'react';

interface TutorialSpotlightProps {
    selector: string;
}

export function TutorialSpotlight({ selector }: TutorialSpotlightProps) {
    const [rect, setRect] = useState<DOMRect | null>(null);

    useEffect(() => {
        const el = document.querySelector(selector);
        if (!el) return;

        const update = () => setRect(el.getBoundingClientRect());
        update();

        const observer = new ResizeObserver(update);
        observer.observe(el);
        window.addEventListener('scroll', update, true);
        window.addEventListener('resize', update);

        return () => {
            observer.disconnect();
            window.removeEventListener('scroll', update, true);
            window.removeEventListener('resize', update);
        };
    }, [selector]);

    if (!rect) return null;

    const pad = 8;
    const top = rect.top - pad;
    const left = rect.left - pad;
    const right = rect.right + pad;
    const bottom = rect.bottom + pad;

    return (
        <div
            className="fixed inset-0 bg-black/60 transition-all duration-300 pointer-events-auto"
            style={{
                clipPath: `polygon(
                    0% 0%, 0% 100%,
                    ${left}px 100%,
                    ${left}px ${top}px,
                    ${right}px ${top}px,
                    ${right}px ${bottom}px,
                    ${left}px ${bottom}px,
                    ${left}px 100%,
                    100% 100%, 100% 0%
                )`,
            }}
        />
    );
}
