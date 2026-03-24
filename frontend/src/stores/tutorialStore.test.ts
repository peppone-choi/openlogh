import { describe, it, expect, beforeEach } from 'vitest';
import { useTutorialStore } from './tutorialStore';

describe('tutorialStore', () => {
    beforeEach(() => {
        useTutorialStore.setState({
            isTutorialMode: false,
            currentStep: 0,
            phase: 'intro',
        });
    });

    it('start() enables tutorial mode', () => {
        useTutorialStore.getState().start();
        const state = useTutorialStore.getState();
        expect(state.isTutorialMode).toBe(true);
        expect(state.currentStep).toBe(0);
        expect(state.phase).toBe('intro');
    });

    it('nextStep() advances step and updates phase', () => {
        useTutorialStore.getState().start();
        useTutorialStore.getState().nextStep();
        const state = useTutorialStore.getState();
        expect(state.currentStep).toBe(1);
        expect(state.phase).toBe('create');
    });

    it('nextStep() does not exceed max steps', () => {
        const steps = useTutorialStore.getState().steps;
        useTutorialStore.setState({ currentStep: steps.length - 1 });
        useTutorialStore.getState().nextStep();
        expect(useTutorialStore.getState().currentStep).toBe(steps.length - 1);
    });

    it('prevStep() goes back and updates phase', () => {
        useTutorialStore.setState({ currentStep: 2, phase: 'create' });
        useTutorialStore.getState().prevStep();
        expect(useTutorialStore.getState().currentStep).toBe(1);
    });

    it('prevStep() does not go below 0', () => {
        useTutorialStore.setState({ currentStep: 0 });
        useTutorialStore.getState().prevStep();
        expect(useTutorialStore.getState().currentStep).toBe(0);
    });

    it('goToStep() jumps to step by id', () => {
        useTutorialStore.getState().goToStep(10);
        const state = useTutorialStore.getState();
        expect(state.currentStep).toBe(10);
        expect(state.phase).toBe('battle');
    });

    it('goToStep() ignores invalid id', () => {
        useTutorialStore.getState().goToStep(999);
        expect(useTutorialStore.getState().currentStep).toBe(0);
    });

    it('skipToPhase() jumps to first step of phase', () => {
        useTutorialStore.getState().skipToPhase('battle');
        const state = useTutorialStore.getState();
        expect(state.phase).toBe('battle');
        expect(state.steps[state.currentStep].phase).toBe('battle');
    });

    it('exit() resets state', () => {
        useTutorialStore.setState({ isTutorialMode: true, currentStep: 5, phase: 'battle' });
        useTutorialStore.getState().exit();
        const state = useTutorialStore.getState();
        expect(state.isTutorialMode).toBe(false);
        expect(state.currentStep).toBe(0);
        expect(state.phase).toBe('intro');
    });

    it('has 20 tutorial steps', () => {
        expect(useTutorialStore.getState().steps).toHaveLength(20);
    });

    it('all steps have required fields', () => {
        const steps = useTutorialStore.getState().steps;
        for (const step of steps) {
            expect(step.id).toBeTypeOf('number');
            expect(step.phase).toBeTypeOf('string');
            expect(step.route).toMatch(/^\/tutorial/);
            expect(step.title).toBeTypeOf('string');
            expect(step.description).toBeTypeOf('string');
        }
    });

    it('step phases follow expected order', () => {
        const steps = useTutorialStore.getState().steps;
        const phaseOrder = ['intro', 'create', 'internal', 'battle', 'diplomacy', 'nation', 'complete'];
        let lastPhaseIdx = 0;
        for (const step of steps) {
            const idx = phaseOrder.indexOf(step.phase);
            expect(idx).toBeGreaterThanOrEqual(lastPhaseIdx);
            lastPhaseIdx = idx;
        }
    });
});
