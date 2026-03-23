import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';
import { AppSidebar } from './app-sidebar';

describe('AppSidebar', () => {
    it('exports AppSidebar component', () => {
        expect(AppSidebar).toBeDefined();
        expect(typeof AppSidebar).toBe('function');
    });

    it('does not include 전체맵 menu item', () => {
        const source = readFileSync(resolve(__dirname, 'app-sidebar.tsx'), 'utf-8');
        expect(source).not.toContain("{ title: '전체맵'");
    });
});
