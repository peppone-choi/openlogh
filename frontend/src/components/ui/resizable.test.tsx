import { describe, it, expect } from 'vitest';
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from './resizable';

describe('Resizable', () => {
    it('exports Resizable components', () => {
        expect(ResizablePanelGroup).toBeDefined();
        expect(ResizablePanel).toBeDefined();
        expect(ResizableHandle).toBeDefined();
    });
});
