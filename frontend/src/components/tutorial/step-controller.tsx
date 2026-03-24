'use client';

import { useRouter } from 'next/navigation';
import { useTutorialStore } from '@/stores/tutorialStore';
import { Button } from '@/components/ui/button';

export function StepController() {
    const { currentStep, steps, nextStep, prevStep, exit } = useTutorialStore();
    const router = useRouter();
    const step = steps[currentStep];
    const progress = `${currentStep + 1} / ${steps.length}`;
    const isLast = currentStep >= steps.length - 1;

    const handleNext = () => {
        if (isLast) {
            router.push('/tutorial/complete');
            return;
        }
        const next = steps[currentStep + 1];
        nextStep();
        if (next.route !== step.route) {
            router.push(next.route);
        }
    };

    const handlePrev = () => {
        if (currentStep <= 0) return;
        const prev = steps[currentStep - 1];
        prevStep();
        if (prev.route !== step.route) {
            router.push(prev.route);
        }
    };

    const handleExit = () => {
        exit();
        router.push('/lobby');
    };

    return (
        <div className="fixed bottom-0 left-0 right-0 z-[60] bg-background/95 backdrop-blur border-t p-3 pointer-events-auto">
            <div className="flex items-center justify-between max-w-4xl mx-auto">
                <Button variant="ghost" size="sm" onClick={handlePrev} disabled={currentStep === 0}>
                    이전
                </Button>
                <span className="text-sm text-muted-foreground">{progress}</span>
                <div className="flex gap-2">
                    <Button variant="ghost" size="sm" onClick={handleExit}>
                        건너뛰기
                    </Button>
                    <Button size="sm" onClick={handleNext}>
                        {isLast ? '완료' : '다음'}
                    </Button>
                </div>
            </div>
        </div>
    );
}
