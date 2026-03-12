#!/usr/bin/env node

import { existsSync } from 'node:fs';

const requiredRoutes = [
    ['main', 'frontend/src/app/(game)/page.tsx'],
    ['login', 'frontend/src/app/(auth)/login/page.tsx'],
    ['register', 'frontend/src/app/(auth)/register/page.tsx'],
    ['join', 'frontend/src/app/(lobby)/lobby/join/page.tsx'],
    ['processing', 'frontend/src/app/(game)/processing/page.tsx'],
    ['history', 'frontend/src/app/(game)/history/page.tsx'],
    ['vote', 'frontend/src/app/(game)/vote/page.tsx'],
    ['auction', 'frontend/src/app/(game)/auction/page.tsx'],
    ['battle-center', 'frontend/src/app/(game)/battle-center/page.tsx'],
    ['chief', 'frontend/src/app/(game)/chief/page.tsx'],
    ['global-diplomacy', 'frontend/src/app/(game)/global-diplomacy/page.tsx'],
    ['inherit', 'frontend/src/app/(game)/inherit/page.tsx'],
    ['npc-control', 'frontend/src/app/(game)/npc-control/page.tsx'],
    ['nation-betting', 'frontend/src/app/(game)/nation-betting/page.tsx'],
    ['nation-generals', 'frontend/src/app/(game)/nation-generals/page.tsx'],
    ['nation-finance', 'frontend/src/app/(game)/nation-finance/page.tsx'],
    ['troop', 'frontend/src/app/(game)/troop/page.tsx'],
];

const requiredParitySpecs = [
    'frontend/e2e/parity/01-main.spec.ts',
    'frontend/e2e/parity/02-links.spec.ts',
    'frontend/e2e/parity/03-pages.spec.ts',
    'frontend/e2e/parity/04-commands.spec.ts',
    'frontend/e2e/parity/05-nation-commands.spec.ts',
];

const missing = [];

for (const [label, filePath] of requiredRoutes) {
    if (!existsSync(filePath)) {
        missing.push(`route:${label}:${filePath}`);
    }
}

for (const filePath of requiredParitySpecs) {
    if (!existsSync(filePath)) {
        missing.push(`parity-spec:${filePath}`);
    }
}

if (missing.length > 0) {
    console.error('[frontend-parity] Missing required parity assets:');
    for (const item of missing) {
        console.error(` - ${item}`);
    }
    process.exit(1);
}

console.log('[frontend-parity] Required route files and parity specs are present.');
