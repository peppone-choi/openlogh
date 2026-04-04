'use client';

import { useState } from 'react';
import Image from 'next/image';
import { Avatar, AvatarFallback } from '@/components/ui/8bit/avatar';
import { User } from 'lucide-react';
import { getPortraitUrl } from '@/lib/image';

const sizes = {
    xs: 28,
    sm: 32,
    md: 48,
    lg: 80,
} as const;

interface GeneralPortraitProps {
    picture?: string | null;
    name: string;
    size?: keyof typeof sizes;
    className?: string;
}

export function GeneralPortrait({ picture, name, size = 'sm', className }: GeneralPortraitProps) {
    const px = sizes[size];
    // 0 = original picture, 1 = default silhouette, 2 = icon fallback
    const [stage, setStage] = useState(0);

    const src =
        stage === 0 ? getPortraitUrl(picture) : stage === 1 ? getPortraitUrl(null) : null;

    if (src) {
        return (
            <Avatar className={className} style={{ width: px, height: px }}>
                <Image
                    src={src}
                    alt={name}
                    width={px}
                    height={px}
                    className="size-full object-cover"
                    onError={() => setStage((s) => s + 1)}
                />
            </Avatar>
        );
    }

    return (
        <Avatar className={className} style={{ width: px, height: px }}>
            <AvatarFallback>
                <User className="size-4 text-muted-foreground" />
            </AvatarFallback>
        </Avatar>
    );
}
