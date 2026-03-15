import { describe, expect, it } from 'vitest';

function decodeBase64Url(base64url: string): string {
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    return new TextDecoder().decode(bytes);
}

describe('decodeBase64Url', () => {
    it('decodes ASCII payload correctly', () => {
        const payload = { sub: 'user1', displayName: 'TestUser' };
        const encoded = btoa(JSON.stringify(payload));
        const decoded = JSON.parse(decodeBase64Url(encoded));
        expect(decoded.displayName).toBe('TestUser');
    });

    it('decodes Korean UTF-8 payload correctly', () => {
        const payload = { sub: 'user1', displayName: '공부맨' };
        const json = JSON.stringify(payload);
        const bytes = new TextEncoder().encode(json);
        const binary = String.fromCharCode(...bytes);
        const encoded = btoa(binary);
        const decoded = JSON.parse(decodeBase64Url(encoded));
        expect(decoded.displayName).toBe('공부맨');
    });

    it('handles base64url characters (- and _)', () => {
        const payload = { sub: 'user+test/name' };
        const json = JSON.stringify(payload);
        const bytes = new TextEncoder().encode(json);
        const binary = String.fromCharCode(...bytes);
        const encoded = btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        const decoded = JSON.parse(decodeBase64Url(encoded));
        expect(decoded.sub).toBe('user+test/name');
    });
});
