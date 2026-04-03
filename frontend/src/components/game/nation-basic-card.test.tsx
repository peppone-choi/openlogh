// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';

const nationCardSrc = readFileSync(
    resolve(__dirname, 'nation-basic-card.tsx'),
    'utf-8',
);

describe('nation-basic-card.tsx source scan', () => {
    it('displays tech level grade with 등급 text', () => {
        expect(nationCardSrc).toContain('등급');
    });

    it('imports convTechLevel from game-utils', () => {
        expect(nationCardSrc).toContain('convTechLevel');
    });

    it('displays strategic command limit info', () => {
        expect(nationCardSrc).toContain('strategicCmdLimit');
    });
});
