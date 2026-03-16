import { describe, expect, it } from 'vitest';

// Mirrors the content resolution in message-plate.tsx:
//   (message.payload.content as string) ?? (message.payload.text as string) ?? ''
function resolveContent(payload: Record<string, unknown>): string {
    return (payload.content as string) ?? (payload.text as string) ?? '';
}

describe('message plate content resolution', () => {
    it('uses content when payload has content field', () => {
        expect(resolveContent({ content: '안녕하세요' })).toBe('안녕하세요');
    });

    it('falls back to text when payload has only text (e.g. recruitment)', () => {
        expect(resolveContent({ text: '귀하를 모시고 싶습니다.' })).toBe('귀하를 모시고 싶습니다.');
    });

    it('prefers content over text when both are present', () => {
        expect(resolveContent({ content: '내용', text: '텍스트' })).toBe('내용');
    });

    it('returns empty string when payload has neither content nor text', () => {
        expect(resolveContent({})).toBe('');
    });

    it('returns empty string when both content and text are undefined', () => {
        expect(resolveContent({ content: undefined, text: undefined })).toBe('');
    });
});
