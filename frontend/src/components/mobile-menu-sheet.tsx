'use client';

import { Sheet, SheetContent } from '@/components/ui/sheet';

interface MobileMenuSheetProps {
    children: React.ReactNode;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
}

export function MobileMenuSheet({ children, open, onOpenChange }: MobileMenuSheetProps) {
    return (
        <Sheet open={open} onOpenChange={onOpenChange}>
            <SheetContent side="left" className="w-[300px] p-0">
                <div onClick={() => onOpenChange?.(false)}>{children}</div>
            </SheetContent>
        </Sheet>
    );
}
