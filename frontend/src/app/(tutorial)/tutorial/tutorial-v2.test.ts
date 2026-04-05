import { describe, expect, it } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';

describe('tutorial-v2: main page uses real GameDashboard', () => {
    const src = fs.readFileSync(path.resolve(__dirname, 'main/page.tsx'), 'utf-8');

    it('imports GameDashboard from components', () => {
        expect(src).toContain("from '@/components/game/game-dashboard'");
    });

    it('renders GameDashboard component', () => {
        expect(src).toContain('<GameDashboard');
    });

    it('does not contain simplified Card UI', () => {
        expect(src).not.toContain('data-tutorial="sidebar"');
    });
});

describe('tutorial-v2: command page uses real CommandPanel', () => {
    const src = fs.readFileSync(path.resolve(__dirname, 'command/page.tsx'), 'utf-8');

    it('imports CommandPanel from components', () => {
        expect(src).toContain("from '@/components/game/command-panel'");
    });

    it('renders CommandPanel component', () => {
        expect(src).toContain('<CommandPanel');
    });

    it('does not contain mock button list', () => {
        expect(src).not.toContain('MOCK_COMMAND_TABLE');
    });
});

describe('tutorial-v2: city page uses real CityBasicCard', () => {
    const src = fs.readFileSync(path.resolve(__dirname, 'city/page.tsx'), 'utf-8');

    it('imports CityBasicCard from components', () => {
        expect(src).toContain("from '@/components/game/city-basic-card'");
    });

    it('renders CityBasicCard component', () => {
        expect(src).toContain('<CityBasicCard');
    });

    it('does not contain SammoBar bars', () => {
        expect(src).not.toContain('SammoBar');
    });
});
