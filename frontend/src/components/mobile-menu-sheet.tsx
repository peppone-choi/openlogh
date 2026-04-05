'use client';

import Link from 'next/link';
import { LogOut } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/8bit/sheet';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { Button } from '@/components/ui/8bit/button';
import { useOfficerStore } from '@/stores/officerStore';
import { useWorldStore } from '@/stores/worldStore';

interface MobileMenuSheetProps {
    children: React.ReactNode;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
}

export function MobileMenuSheet({ children, open, onOpenChange }: MobileMenuSheetProps) {
    const myOfficer = useOfficerStore((s) => s.myOfficer);
    const currentWorld = useWorldStore((s) => s.currentWorld);

    const worldDate =
        currentWorld?.currentYear && currentWorld?.currentMonth
            ? `${currentWorld.currentYear}년 ${currentWorld.currentMonth}월`
            : '';

    return (
        <Sheet open={open} onOpenChange={onOpenChange}>
            <SheetContent side="left" className="w-[280px] p-0 bg-card flex flex-col">
                <SheetHeader className="sr-only">
                    <SheetTitle>메뉴</SheetTitle>
                </SheetHeader>

                <div className="bg-gradient-to-b from-primary/10 to-transparent px-4 pt-4 pb-3 border-b border-border">
                    {myOfficer && (
                        <Link href="/general" className="flex items-center gap-3" onClick={() => onOpenChange?.(false)}>
                            <GeneralPortrait picture={myOfficer.picture} name={myOfficer.name} size="sm" />
                            <div className="min-w-0 flex-1">
                                <p className="text-sm font-semibold truncate">{myOfficer.name}</p>
                                <p className="text-xs text-muted-foreground truncate">
                                    Lv.{myOfficer.expLevel} | 관직 {myOfficer.officerLevel}
                                </p>
                            </div>
                        </Link>
                    )}
                </div>

                <div className="flex-1 overflow-y-auto" onClick={() => onOpenChange?.(false)}>
                    {children}
                </div>

                <div className="border-t border-border px-4 py-3 space-y-2">
                    {worldDate && (
                        <p className="text-xs text-muted-foreground text-center">
                            {currentWorld?.name} | {worldDate}
                        </p>
                    )}
                    <Button
                        variant="outline"
                        size="sm"
                        className="w-full gap-2"
                        onClick={() => {
                            onOpenChange?.(false);
                            window.location.href = '/lobby';
                        }}
                    >
                        <LogOut className="h-4 w-4" />
                        로비로
                    </Button>
                </div>
            </SheetContent>
        </Sheet>
    );
}
