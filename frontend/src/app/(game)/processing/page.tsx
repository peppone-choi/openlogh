'use client';

import { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { commandApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import { LoadingState } from '@/components/game/loading-state';
import { CommandArgForm } from '@/components/game/command-arg-form';
import { PageHeader } from '@/components/game/page-header';
import { Card, CardContent } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { ClipboardList } from 'lucide-react';
import type { CommandArg } from '@/types';

function ProcessingContent() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const command = searchParams.get('command');
    const turnListStr = searchParams.get('turnList');
    const isNationCommand = searchParams.get('nation') === 'true';
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer } = useOfficerStore();
    const [isSubmitting, setIsSubmitting] = useState(false);

    const isFormMode = Boolean(command && turnListStr);
    const turnList = turnListStr ? turnListStr.split(',').map((t) => parseInt(t, 10)) : [];

    // Wait mode: WS listener (hooks must always run in same order)
    useEffect(() => {
        if (isFormMode || !currentWorld) return;
        const unsub = subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            router.replace('/');
        });
        return unsub;
    }, [currentWorld, router, isFormMode]);

    // Wait mode: 30s fallback timeout
    useEffect(() => {
        if (isFormMode) return;
        const timer = setTimeout(() => {
            router.replace('/');
        }, 30000);
        return () => clearTimeout(timer);
    }, [router, isFormMode]);

    // Form mode: command argument form
    if (isFormMode && command) {
        const handleFormSubmit = async (arg: CommandArg) => {
            if (!myOfficer) return;
            setIsSubmitting(true);
            try {
                const turns = turnList.map((turnIdx) => ({
                    turnIdx,
                    actionCode: command,
                    arg,
                }));
                if (isNationCommand && myOfficer.nationId) {
                    await commandApi.reserveNation(myOfficer.nationId, myOfficer.id, turns);
                    router.push('/commands?mode=nation');
                } else {
                    await commandApi.reserve(myOfficer.id, turns);
                    router.push('/commands');
                }
            } catch (error) {
                console.error('Failed to reserve command:', error);
                setIsSubmitting(false);
            }
        };

        return (
            <div className="p-4 space-y-4 max-w-3xl mx-auto">
                <PageHeader icon={ClipboardList} title={`Command Parameters — ${command}`} />
                <Card>
                    <CardContent className="space-y-4 pt-4">
                        <div className="space-y-2">
                            <p className="text-xs text-muted-foreground">Target Slots</p>
                            <div className="flex flex-wrap gap-1.5">
                                {turnList.map((turnIdx) => (
                                    <span
                                        key={turnIdx}
                                        className="inline-flex items-center gap-1 rounded-full bg-amber-900/30 px-2.5 py-0.5 text-xs font-medium text-amber-200"
                                    >
                                        <ClipboardList className="size-3" />Slot {turnIdx}
                                    </span>
                                ))}
                            </div>
                        </div>

                        <CommandArgForm actionCode={command} onSubmit={handleFormSubmit} />

                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => router.push('/commands')}
                            disabled={isSubmitting}
                            className="w-full mt-2"
                        >
                            Cancel
                        </Button>
                    </CardContent>
                </Card>
            </div>
        );
    }

    // Wait mode: turn processing
    return (
        <div className="flex flex-col items-center justify-center py-24">
            <LoadingState message="Processing command..." />
            <p className="mt-4 text-xs text-muted-foreground">You will be redirected when execution completes.</p>
        </div>
    );
}

export default function ProcessingPage() {
    return (
        <Suspense fallback={<LoadingState />}>
            <ProcessingContent />
        </Suspense>
    );
}
