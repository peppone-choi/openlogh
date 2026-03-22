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

    it('still contains reserve button text for cancel', () => {
        expect(source).toContain('취소');
    });
});

describe('command-select-form one-click reserve', () => {
    const source = fs.readFileSync(path.resolve(__dirname, 'command-select-form.tsx'), 'utf-8');

    it('handleArgSubmit calls onSelect directly', () => {
        expect(source).toContain('onSelect(selectedCmd, arg)');
    });

    it('no pendingArg state remains', () => {
        expect(source).not.toContain('pendingArg');
        expect(source).not.toContain('setPendingArg');
    });

    it('no separate reserve button for arg commands', () => {
        // The old code had a disabled reserve button gated by pendingArg
        expect(source).not.toContain('handleReserve');
    });
});

describe('command-select-form disabled command styling', () => {
    const source = fs.readFileSync(path.resolve(__dirname, 'command-select-form.tsx'), 'utf-8');

    it('disabled commands show red border instead of opacity', () => {
        expect(source).toContain('border-red-500/50');
        expect(source).toContain('text-red-400');
        expect(source).not.toContain('opacity-40');
        expect(source).not.toContain('cursor-not-allowed');
    });

    it('onClick does not gate on cmd.enabled', () => {
        // Old: onClick={() => { if (cmd.enabled) handleSelectCmd(...) }}
        // New: onClick={() => handleSelectCmd(cmd.actionCode)}
        expect(source).not.toContain('if (cmd.enabled)');
    });
});
