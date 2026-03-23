import { Badge } from '@/components/ui/badge';

interface FactionBadgeProps {
    name?: string | null;
    color?: string | null;
    size?: 'sm' | 'md';
}

export function FactionBadge({ name, color, size = 'sm' }: FactionBadgeProps) {
    if (!name) {
        return (
            <Badge variant="outline" className="text-muted-foreground">
                재야
            </Badge>
        );
    }

    const dotSize = size === 'sm' ? 'size-2' : 'size-2.5';

    return (
        <Badge variant="outline" className="gap-1.5" style={color ? { borderColor: color } : undefined}>
            <span
                className={`inline-block ${dotSize} rounded-full shrink-0`}
                style={{ backgroundColor: color ?? '#888' }}
            />
            <span style={{ color: color ?? undefined }}>{name}</span>
        </Badge>
    );
}

/** Get a CSS variable-friendly style for a faction color */
export function getFactionColorStyle(color?: string | null): React.CSSProperties {
    if (!color) return {};
    return { '--faction-color': color } as React.CSSProperties;
}

/** @deprecated Use FactionBadge */
export { FactionBadge as NationBadge };
/** @deprecated Use getFactionColorStyle */
export { getFactionColorStyle as getNationColorStyle };
