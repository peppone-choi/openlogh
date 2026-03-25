import { describe, expect, it } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';

const src = fs.readFileSync(path.resolve(__dirname, 'game-dashboard.tsx'), 'utf-8');

describe('GameDashboard extraction', () => {
    it('exports named GameDashboard function', () => {
        expect(src).toContain('export function GameDashboard()');
    });

    it('game page re-exports GameDashboard as default', () => {
        const pageSrc = fs.readFileSync(path.resolve(__dirname, '../../app/(game)/page.tsx'), 'utf-8');
        expect(pageSrc).toContain('GameDashboard as default');
    });

    it('includes data-tutorial="map-viewer" attribute', () => {
        expect(src).toContain('data-tutorial="map-viewer"');
    });

    it('includes data-tutorial="mobile-tabs" attribute', () => {
        expect(src).toContain('data-tutorial="mobile-tabs"');
    });
});

describe('GameDashboard WebSocket subscriptions', () => {
    it('subscribes to /update topic for world-wide events', () => {
        expect(src).toContain('/topic/world/${currentWorld.id}/update');
    });

    it('subscribes to /command topic for command changes', () => {
        expect(src).toContain('/topic/world/${currentWorld.id}/command');
    });

    it('subscribes to /turn topic', () => {
        expect(src).toContain('/topic/world/${currentWorld.id}/turn');
    });

    it('subscribes to /message topic', () => {
        expect(src).toContain('/topic/world/${currentWorld.id}/message');
    });

    it('uses debounced reload for WebSocket events', () => {
        expect(src).toContain('useDebouncedCallback');
        expect(src).toContain('debouncedReload');
    });
});
