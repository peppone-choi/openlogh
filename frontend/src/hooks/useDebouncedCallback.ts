'use client';
import { useCallback, useRef } from 'react';

export function useDebouncedCallback<T extends (...args: unknown[]) => unknown>(callback: T, delay: number = 500) {
    const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    return useCallback(
        (...args: Parameters<T>) => {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
            timeoutRef.current = setTimeout(() => {
                callback(...args);
            }, delay);
        },
        [callback, delay]
    ) as (...args: Parameters<T>) => void;
}
