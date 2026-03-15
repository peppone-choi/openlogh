import { describe, it, expect } from 'vitest';
import { Sidebar, SidebarProvider } from './sidebar';

describe('Sidebar', () => {
    it('exports Sidebar and SidebarProvider', () => {
        expect(Sidebar).toBeDefined();
        expect(SidebarProvider).toBeDefined();
        expect(typeof Sidebar).toBe('function');
        expect(typeof SidebarProvider).toBe('function');
    });
});
