'use client';

import { TutorialProvider } from '@/components/tutorial/tutorial-provider';

export default function TutorialLayout({ children }: { children: React.ReactNode }) {
    return (
        <TutorialProvider>
            <div className="min-h-screen bg-background text-foreground">{children}</div>
        </TutorialProvider>
    );
}
