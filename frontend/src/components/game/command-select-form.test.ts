import { describe, expect, it } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';

describe('command-select-form no instant-execute', () => {
    const source = fs.readFileSync(path.resolve(__dirname, 'command-select-form.tsx'), 'utf-8');

    it('does not contain execute handler or button', () => {
        expect(source).not.toContain('handleExecute');
        expect(source).not.toContain('executing');
        expect(source).not.toContain('즉시실행');
        expect(source).not.toContain('commandApi');
    });

    it('does not contain result display block', () => {
        expect(source).not.toContain('result.success');
        expect(source).not.toContain('result.logs');
    });

    it('still contains reserve button for turn-based command', () => {
        expect(source).toContain('예약');
    });
});
