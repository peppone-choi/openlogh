'use client';

import { useTutorialStore } from '@/stores/tutorialStore';
import { TutorialSpotlight } from './tutorial-spotlight';
import { TutorialTooltip } from './tutorial-tooltip';

export function GuideOverlay() {
    const { currentStep, steps } = useTutorialStore();
    const step = steps[currentStep];

    if (!step) return null;

    return (
        <div className="fixed inset-0 z-50 pointer-events-none">
            {/* Spotlight: dark backdrop with cutout around target */}
            {step.targetSelector && <TutorialSpotlight selector={step.targetSelector} />}

            {/* Tooltip: floating description */}
            <TutorialTooltip
                title={step.title}
                description={step.description}
                position={step.tooltipPosition ?? 'bottom'}
                targetSelector={step.targetSelector}
            />

            {/* Progress bar */}
            <div className="fixed top-0 left-0 right-0 pointer-events-none z-[51]">
                <div className="h-1 bg-muted">
                    <div
                        className="h-full bg-primary transition-all duration-300"
                        style={{ width: `${((currentStep + 1) / steps.length) * 100}%` }}
                    />
                </div>
            </div>
        </div>
    );
}
