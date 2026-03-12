export function formatGameLogDate(source: {
    payload?: unknown;
    meta?: unknown;
    year?: unknown;
    month?: unknown;
}): string | null {
    const payload = (source.payload as Record<string, unknown> | null | undefined) ?? null;
    const meta = (source.meta as Record<string, unknown> | null | undefined) ?? null;
    const year = readNumber(source.year) ?? readNumber(payload?.year) ?? readNumber(meta?.year);
    const month = readNumber(source.month) ?? readNumber(payload?.month) ?? readNumber(meta?.month);

    if (year == null || month == null) return null;
    return `${year}년 ${month}월`;
}

function readNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string') {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
}
