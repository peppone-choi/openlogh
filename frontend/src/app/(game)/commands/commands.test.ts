import { describe, expect, it } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';

const src = fs.readFileSync(path.resolve(__dirname, 'page.tsx'), 'utf-8');

describe('CommandsPage WebSocket subscriptions', () => {
    it('subscribes to /turn topic for turn advance updates', () => {
        expect(src).toContain('/topic/world/${currentWorld.id}/turn');
    });

    it('subscribes to /command topic for command reservation changes', () => {
        expect(src).toContain('/topic/world/${currentWorld.id}/command');
    });

    it('uses debounced callback for WebSocket handlers', () => {
        expect(src).toContain('useDebouncedCallback');
    });

    it('imports subscribeWebSocket for topic subscriptions', () => {
        expect(src).toContain("import { subscribeWebSocket } from '@/lib/websocket'");
    });
});
