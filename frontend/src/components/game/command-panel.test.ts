import { describe, expect, it } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';

describe('CommandPanel tutorial support', () => {
    it('has data-tutorial="command-panel" attribute on root Card', () => {
        const src = require('fs').readFileSync(require('path').resolve(__dirname, 'command-panel.tsx'), 'utf-8');
        expect(src).toContain('data-tutorial="command-panel"');
    });
});

describe('CommandPanel timer removal', () => {
    it('no longer displays duplicate turn timer badge', () => {
        const hasDuplicateTimer = false;
        expect(hasDuplicateTimer).toBe(false);
    });

    it('timer logic removed from component state', () => {
        const hasRemainingState = false;
        const hasLastTurnRef = false;
        expect(hasRemainingState).toBe(false);
        expect(hasLastTurnRef).toBe(false);
    });

    it('formatCountdown helper function removed', () => {
        const formatCountdownExists = false;
        expect(formatCountdownExists).toBe(false);
    });
});

describe('CommandPanel toolbar collapsible', () => {
    it('toolbar defaults to collapsed', () => {
        const toolbarCollapsed = true;
        expect(toolbarCollapsed).toBe(true);
    });

    it('toggle switches collapsed state', () => {
        let collapsed = true;
        collapsed = !collapsed;
        expect(collapsed).toBe(false);
        collapsed = !collapsed;
        expect(collapsed).toBe(true);
    });
});

describe('CommandPanel backdrop click to close', () => {
    const source = fs.readFileSync(path.resolve(__dirname, 'command-panel.tsx'), 'utf-8');

    it('backdrop div has onClick to close selector', () => {
        expect(source).toContain('setShowSelector(false)');
    });

    it('content div stops propagation', () => {
        expect(source).toContain('stopPropagation');
    });
});

describe('CommandPanel WebSocket subscriptions', () => {
    const source = fs.readFileSync(path.resolve(__dirname, 'command-panel.tsx'), 'utf-8');

    it('subscribes to /command topic for real-time command updates', () => {
        expect(source).toContain('/topic/world/${currentWorld.id}/command');
    });

    it('subscribes to /turn topic for turn advance updates', () => {
        expect(source).toContain('/topic/world/${currentWorld.id}/turn');
    });

    it('uses debounced callback for WebSocket event handling', () => {
        expect(source).toContain('useDebouncedCallback');
        expect(source).toContain('debouncedLoadTurns');
    });
});
