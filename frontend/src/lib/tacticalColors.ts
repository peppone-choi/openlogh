/**
 * Phase 14 D-02 — Tactical map faction colors.
 *
 * Single source of truth for CRC stroke color + unit icon rings on the tactical
 * battle map. Distinct from the `--logh-*` CSS vars used by the strategic galaxy
 * map (which renders in HTML, not Konva).
 *
 * Do not introduce per-commander palette variants — D-02 explicitly defers that
 * decision and the CRC multi-render (14-10) is expected to keep a single faction
 * color with HSL lighten for selection.
 */
export const FACTION_TACTICAL_COLORS = {
    /** 은하제국 — cobalt blue */
    empire: '#4466ff',
    /** 자유행성동맹 — signal red */
    alliance: '#ff4444',
    /** 페잔 자치령 / 중립 — neutral gray */
    fezzan: '#888888',
} as const;

export type FactionColorKey = keyof typeof FACTION_TACTICAL_COLORS;

/**
 * Side-to-color helper for tactical battles.
 *
 * NOTE: neither ATTACKER nor DEFENDER is hard-coded to a specific faction —
 * this helper exists for components that know the side but not the factionId.
 * For factionId-aware rendering, call `FACTION_TACTICAL_COLORS[factionCode]`
 * directly. Phase 14 D-01/D-02 establishes ATTACKER → empire / DEFENDER →
 * alliance as the conventional default pairing for the CRC outer frame.
 */
export function sideToDefaultColor(side: 'ATTACKER' | 'DEFENDER'): string {
    return side === 'ATTACKER'
        ? FACTION_TACTICAL_COLORS.empire
        : FACTION_TACTICAL_COLORS.alliance;
}

/**
 * Phase 14 D-02 hover/select rule: HSL lightness + 15 percentage points.
 *
 * Implemented as a pure RGB→HSL→RGB round-trip so no new hex literals need to
 * be declared per state. Clamps at L=100 to avoid wrap-around on already-bright
 * source colors (pure white stays pure white).
 *
 * Accepts 7-character `#RRGGBB` hex only. Inputs without a leading `#` or with
 * 3-character shorthand (`#abc`) are not supported because the tactical color
 * palette never uses them.
 */
export function lightenHex(hex: string, lPercent = 15): string {
    const r = parseInt(hex.slice(1, 3), 16) / 255;
    const g = parseInt(hex.slice(3, 5), 16) / 255;
    const b = parseInt(hex.slice(5, 7), 16) / 255;

    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    let h = 0;
    let s = 0;
    const l = (max + min) / 2;

    if (max !== min) {
        const d = max - min;
        s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
        switch (max) {
            case r:
                h = (g - b) / d + (g < b ? 6 : 0);
                break;
            case g:
                h = (b - r) / d + 2;
                break;
            case b:
                h = (r - g) / d + 4;
                break;
        }
        h /= 6;
    }

    const newL = Math.min(1, l + lPercent / 100);

    const hue2rgb = (p: number, q: number, t: number): number => {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6) return p + (q - p) * 6 * t;
        if (t < 1 / 2) return q;
        if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
        return p;
    };

    const q = newL < 0.5 ? newL * (1 + s) : newL + s - newL * s;
    const p = 2 * newL - q;

    const toHex = (n: number): string =>
        Math.round(n * 255)
            .toString(16)
            .padStart(2, '0');

    // Achromatic edge case: if s === 0 (source was pure gray/white/black),
    // skip the hue conversion and return `newL` in all three channels.
    if (s === 0) {
        const v = toHex(newL);
        return `#${v}${v}${v}`;
    }

    return (
        '#' +
        toHex(hue2rgb(p, q, h + 1 / 3)) +
        toHex(hue2rgb(p, q, h)) +
        toHex(hue2rgb(p, q, h - 1 / 3))
    );
}
