import { describe, it, expect } from 'vitest';
import {
    Collapsible,
    CollapsibleTrigger,
    CollapsibleContent,
} from './collapsible';

describe('Collapsible', () => {
    it('exports Collapsible components', () => {
        expect(Collapsible).toBeDefined();
        expect(CollapsibleTrigger).toBeDefined();
        expect(CollapsibleContent).toBeDefined();
    });
});
