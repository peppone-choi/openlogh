import { createElement, type ReactNode } from 'react';

const COLOR_MAP: Record<string, string> = {
    R: '#ef4444',
    B: '#3b82f6',
    G: '#22c55e',
    M: '#a855f7',
    C: '#06b6d4',
    L: '#84cc16',
    S: '#94a3b8',
    O: '#f97316',
    D: '#6b7280',
    Y: '#eab308',
    W: '#f8fafc',
};

const TAG_REGEX = /<([RBGMCLSODYW]1?|1|\/|b|\/b)>/g;

export function formatLog(text: string): ReactNode[] {
    const parts: ReactNode[] = [];
    let lastIndex = 0;
    let currentColor: string | null = null;
    let small = false;
    let bold = false;
    let key = 0;

    // Reset regex state
    TAG_REGEX.lastIndex = 0;

    let match: RegExpExecArray | null = TAG_REGEX.exec(text);
    while (match !== null) {
        if (match.index > lastIndex) {
            const segment = text.slice(lastIndex, match.index);
            if (currentColor || small || bold) {
                const style: Record<string, string> = {};
                if (currentColor) style.color = currentColor;
                if (small) style.fontSize = '0.9em';
                if (bold) style.fontWeight = '700';
                parts.push(createElement('span', { key: key++, style }, segment));
            } else {
                parts.push(segment);
            }
        }

        const tag = match[1];
        if (tag === '/') {
            currentColor = null;
            small = false;
            bold = false;
        } else if (tag === 'b') {
            bold = true;
        } else if (tag === '/b') {
            bold = false;
        } else if (tag === '1') {
            small = true;
        } else if (tag.length === 2) {
            // Compound tag like R1 = color + small
            const colorChar = tag[0];
            if (COLOR_MAP[colorChar]) currentColor = COLOR_MAP[colorChar];
            if (tag[1] === '1') small = true;
        } else if (COLOR_MAP[tag]) {
            currentColor = COLOR_MAP[tag];
        }

        lastIndex = match.index + match[0].length;
        match = TAG_REGEX.exec(text);
    }

    if (lastIndex < text.length) {
        const segment = text.slice(lastIndex);
        if (currentColor || small || bold) {
            const style: Record<string, string> = {};
            if (currentColor) style.color = currentColor;
            if (small) style.fontSize = '0.9em';
            if (bold) style.fontWeight = '700';
            parts.push(createElement('span', { key: key++, style }, segment));
        } else {
            parts.push(segment);
        }
    }

    return parts;
}

export function formatLogHtml(text: string): string {
    const result: string[] = [];
    let lastIndex = 0;

    // Reset regex state
    TAG_REGEX.lastIndex = 0;

    let match: RegExpExecArray | null = TAG_REGEX.exec(text);
    while (match !== null) {
        const { 0: fullMatch, 1: tag, index } = match;
        if (lastIndex !== index) {
            result.push(text.slice(lastIndex, index));
        }

        if (tag === '/') {
            result.push('</span>');
        } else if (tag === 'b') {
            result.push('<span style="font-weight:700">');
        } else if (tag === '/b') {
            result.push('</span>');
        } else if (tag === '1') {
            result.push('<span style="font-size:0.9em">');
        } else if (tag.length === 2) {
            // Compound: e.g. R1 = color + small
            const colorChar = tag[0];
            const styles: string[] = [];
            if (COLOR_MAP[colorChar]) styles.push(`color:${COLOR_MAP[colorChar]}`);
            if (tag[1] === '1') styles.push('font-size:0.9em');
            result.push(`<span style="${styles.join(';')}">`);
        } else if (COLOR_MAP[tag]) {
            result.push(`<span style="color:${COLOR_MAP[tag]}">`);
        }

        lastIndex = index + fullMatch.length;
        match = TAG_REGEX.exec(text);
    }

    if (lastIndex !== text.length) {
        result.push(text.slice(lastIndex));
    }

    return result.join('');
}
