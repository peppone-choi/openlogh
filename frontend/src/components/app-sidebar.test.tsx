import { describe, it, expect } from 'vitest';
import { AppSidebar } from './app-sidebar';

describe('AppSidebar', () => {
    it('exports AppSidebar component', () => {
        expect(AppSidebar).toBeDefined();
        expect(typeof AppSidebar).toBe('function');
    });
});
