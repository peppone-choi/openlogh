'use client';

import { useEffect, useState } from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/8bit/sheet';

interface ResponsiveSheetProps {
    trigger?: React.ReactNode;
    title: string;
    description?: string;
    children: React.ReactNode;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
}

export function ResponsiveSheet({
    trigger,
    title,
    description,
    children,
    open: controlledOpen,
    onOpenChange,
}: ResponsiveSheetProps) {
    const [internalOpen, setInternalOpen] = useState(false);
    const [isDesktop, setIsDesktop] = useState(true);

    useEffect(() => {
        const checkDesktop = () => setIsDesktop(window.innerWidth >= 768);
        checkDesktop();
        window.addEventListener('resize', checkDesktop);
        return () => window.removeEventListener('resize', checkDesktop);
    }, []);

    const open = controlledOpen ?? internalOpen;
    const handleOpenChange = onOpenChange ?? setInternalOpen;

    return (
        <Sheet open={open} onOpenChange={handleOpenChange}>
            {trigger}
            <SheetContent
                side={isDesktop ? 'right' : 'bottom'}
                className={isDesktop ? 'w-[400px] sm:w-[540px]' : 'h-[70vh] rounded-t-xl'}
            >
                <SheetHeader>
                    <SheetTitle>{title}</SheetTitle>
                    {description && <SheetDescription>{description}</SheetDescription>}
                </SheetHeader>
                <div
                    className={
                        isDesktop
                            ? 'mt-4 h-[calc(100vh-8rem)] overflow-y-auto'
                            : 'mt-4 h-[calc(70vh-4rem)] overflow-y-auto'
                    }
                >
                    {children}
                </div>
            </SheetContent>
        </Sheet>
    );
}
