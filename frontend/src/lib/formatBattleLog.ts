// Battle log HTML parser for legacy small_war_log template rendering

export function isBattleLogHtml(message: string): boolean {
    return message.includes('class="small_war_log"');
}

export function getWarTypeColor(warType: string): string {
    switch (warType) {
        case 'attack':
            return 'cyan';
        case 'defense':
            return 'magenta';
        case 'siege':
            return 'white';
        default:
            return 'white';
    }
}

export interface BattleLogData {
    attacker: { name: string; crewType: string; remainCrew: number; killedCrew: number };
    defender: { name: string; crewType: string; remainCrew: number; killedCrew: number };
    warType: 'attack' | 'defense' | 'siege';
    arrow: string;
}

export function parseBattleLogHtml(html: string): BattleLogData | null {
    if (!html.includes('class="small_war_log"')) return null;

    // Extract war type from class="war_type war_type_{type}"
    const warTypeMatch = /war_type_(\w+)/.exec(html);
    const warType = (warTypeMatch?.[1] ?? 'attack') as 'attack' | 'defense' | 'siege';

    // Extract arrow text
    const arrowMatch = /class="war_type[^"]*">([^<]*)</.exec(html);
    const arrow = arrowMatch?.[1] ?? '';

    // Split into me and you sections
    const meMatch = /<span class="me">([\s\S]*?)<\/span>\s*<span class="war_type/.exec(html);
    const youMatch = /<span class="you">([\s\S]*?)<\/span>\s*<\/div>/.exec(html);

    if (!meMatch || !youMatch) return null;

    const meSection = meMatch[1];
    const youSection = youMatch[1];

    function extractData(section: string): { name: string; crewType: string; remainCrew: number; killedCrew: number } {
        const nameMatch = /<span class="name">([^<]+)<\/span>/.exec(section);
        const crewTypeMatch = /<span class="crew_type">([^<]+)<\/span>/.exec(section);
        const remainCrewMatch = /<span class="remain_crew">([^<]+)<\/span>/.exec(section);
        const killedCrewMatch = /<span class="killed_crew">([^<]+)<\/span>/.exec(section);

        return {
            name: nameMatch?.[1] ?? '',
            crewType: crewTypeMatch?.[1] ?? '',
            remainCrew: parseInt(remainCrewMatch?.[1] ?? '0', 10),
            killedCrew: parseInt(killedCrewMatch?.[1] ?? '0', 10),
        };
    }

    return {
        attacker: extractData(meSection),
        defender: extractData(youSection),
        warType,
        arrow,
    };
}
