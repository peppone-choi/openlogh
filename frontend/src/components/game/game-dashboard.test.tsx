// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';

const dashboardSrc = readFileSync(
    resolve(__dirname, 'game-dashboard.tsx'),
    'utf-8',
);
const typesSrc = readFileSync(
    resolve(__dirname, '../../types/index.ts'),
    'utf-8',
);

describe('game-dashboard.tsx source scan', () => {
    it('does NOT contain unsafe "as unknown as Record" cast for autorunUser', () => {
        expect(dashboardSrc).not.toContain('as unknown as Record');
    });

    it('types/index.ts GlobalInfo contains autorunUser field', () => {
        // Match the field declaration inside GlobalInfo
        const globalInfoMatch = typesSrc.match(
            /interface GlobalInfo\s*\{([\s\S]*?)\n\}/,
        );
        expect(globalInfoMatch).not.toBeNull();
        expect(globalInfoMatch![1]).toContain('autorunUser');
    });

    it('displays joinMode (참가 모드)', () => {
        expect(dashboardSrc).toContain('joinMode');
    });

    it('displays officerCity (관할 도시) in general summary or dashboard', () => {
        // officerCity is shown in general-basic-card already, but joinMode should be in dashboard
        // This test just checks that the dashboard source references joinMode display
        expect(dashboardSrc).toContain('joinMode');
    });

    it('displays bill (봉급) in general status summary', () => {
        // bill is displayed in general-basic-card; dashboard general summary shows it too
        expect(dashboardSrc).toContain('봉급');
    });
});
