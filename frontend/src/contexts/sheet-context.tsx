'use client';

import { createContext, useContext, useState, useCallback, useEffect, ReactNode } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

interface SheetContextValue {
    activeSheet: string | null;
    openSheet: (sheetId: string) => void;
    closeSheet: () => void;
}

const SheetContext = createContext<SheetContextValue | null>(null);

export function SheetProvider({ children }: { children: ReactNode }) {
    const router = useRouter();
    const searchParams = useSearchParams();
    const [activeSheet, setActiveSheet] = useState<string | null>(null);

    useEffect(() => {
        const sheet = searchParams.get('sheet');
        if (sheet && sheet !== activeSheet) {
            setActiveSheet(sheet);
        } else if (!sheet && activeSheet) {
            setActiveSheet(null);
        }
    }, [searchParams, activeSheet]);

    const openSheet = useCallback(
        (sheetId: string) => {
            setActiveSheet(sheetId);
            const params = new URLSearchParams(searchParams.toString());
            params.set('sheet', sheetId);
            router.push(`?${params.toString()}`, { scroll: false });
        },
        [router, searchParams]
    );

    const closeSheet = useCallback(() => {
        setActiveSheet(null);
        const params = new URLSearchParams(searchParams.toString());
        params.delete('sheet');
        const query = params.toString();
        router.push(query ? `?${query}` : window.location.pathname, { scroll: false });
    }, [router, searchParams]);

    return <SheetContext.Provider value={{ activeSheet, openSheet, closeSheet }}>{children}</SheetContext.Provider>;
}

export function useSheet() {
    const context = useContext(SheetContext);
    if (!context) {
        throw new Error('useSheet must be used within SheetProvider');
    }
    return context;
}
