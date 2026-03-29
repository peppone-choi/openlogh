'use client';

import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';

export interface OrgNodeWithHolder {
    title: string;
    titleKr: string;
    description: string;
    rankRequired?: string;
    positionType?: string;
    holder?: {
        officerName: string;
        officerPicture: string | null;
        officerRank: number;
    } | null;
    children?: OrgNodeWithHolder[];
}

interface OrgTreeNodeLiveProps {
    node: OrgNodeWithHolder;
    depth: number;
    factionColor: 'empire' | 'alliance';
    defaultExpanded?: boolean;
}

const FACTION_COLORS = {
    empire: 'var(--empire-gold, #c9a84c)',
    alliance: 'var(--alliance-blue, #1e4a8a)',
} as const;

export function OrgTreeNodeLive({ node, depth, factionColor, defaultExpanded }: OrgTreeNodeLiveProps) {
    const shouldExpand = defaultExpanded ?? depth < 3;
    const [isOpen, setIsOpen] = useState(shouldExpand);
    const hasChildren = node.children && node.children.length > 0;
    const color = FACTION_COLORS[factionColor];

    const holderDisplay = node.holder ? (
        <span className="text-xs ml-2 font-mono">{node.holder.officerName}</span>
    ) : (
        <span className="text-xs ml-2 text-muted-foreground">[&#x25A1; 공석]</span>
    );

    if (!hasChildren) {
        // Leaf node: no collapsible, just a dot marker
        return (
            <div
                className="flex items-start gap-2 w-full text-left px-3 py-1.5"
                style={{ paddingLeft: depth * 16 + 12 }}
            >
                <div className="size-4 shrink-0 flex items-center justify-center mt-0.5">
                    <div className="size-1.5 rounded-full" style={{ backgroundColor: color }} />
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-semibold" style={{ color }}>
                            {node.titleKr}
                        </span>
                        <span className="text-[10px] text-muted-foreground font-mono">{node.title}</span>
                        {holderDisplay}
                    </div>
                    <div className="text-[10px] text-muted-foreground mt-0.5">{node.description}</div>
                    {node.rankRequired && (
                        <Badge variant="outline" className="mt-1 text-[10px]">
                            {node.rankRequired}
                        </Badge>
                    )}
                </div>
            </div>
        );
    }

    return (
        <Collapsible open={isOpen} onOpenChange={setIsOpen}>
            <CollapsibleTrigger asChild>
                <button
                    type="button"
                    className="flex items-start gap-2 w-full text-left px-3 py-1.5 transition-colors hover:bg-muted/50"
                    style={{ paddingLeft: depth * 16 + 12 }}
                >
                    {isOpen ? (
                        <ChevronDown className="size-4 mt-0.5 shrink-0" style={{ color }} />
                    ) : (
                        <ChevronRight className="size-4 mt-0.5 shrink-0" style={{ color }} />
                    )}
                    <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-semibold" style={{ color }}>
                                {node.titleKr}
                            </span>
                            <span className="text-[10px] text-muted-foreground font-mono">{node.title}</span>
                            {holderDisplay}
                        </div>
                        <div className="text-[10px] text-muted-foreground mt-0.5">{node.description}</div>
                        {node.rankRequired && (
                            <Badge variant="outline" className="mt-1 text-[10px]">
                                {node.rankRequired}
                            </Badge>
                        )}
                    </div>
                </button>
            </CollapsibleTrigger>
            <CollapsibleContent>
                <div
                    className="border-l ml-5 pl-1"
                    style={{ borderColor: color }}
                >
                    {node.children!.map((child, i) => (
                        <OrgTreeNodeLive
                            key={`${child.titleKr}-${i}`}
                            node={child}
                            depth={depth + 1}
                            factionColor={factionColor}
                        />
                    ))}
                </div>
            </CollapsibleContent>
        </Collapsible>
    );
}
