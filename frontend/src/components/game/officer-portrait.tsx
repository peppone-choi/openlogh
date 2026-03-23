'use client';

import { useState } from 'react';
import Image from 'next/image';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { User } from 'lucide-react';
import { getPortraitUrl } from '@/lib/image';

const sizes = {
    xs: 28,
    sm: 32,
    md: 48,
    lg: 80,
} as const;

interface OfficerPortraitProps {
    picture?: string | null;
    name: string;
    size?: keyof typeof sizes;
    className?: string;
}

export function OfficerPortrait({ picture, name, size = 'sm', className }: OfficerPortraitProps) {
    const px = sizes[size];
    const [error, setError] = useState(false);
    const src = error ? getPortraitUrl(null) : getPortraitUrl(picture);

    if (!error) {
        return (
            <Avatar className={className} style={{ width: px, height: px }}>
                <Image
                    src={src}
                    alt={name}
                    width={px}
                    height={px}
                    className="size-full object-cover"
                    onError={() => setError(true)}
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

/** @deprecated Use OfficerPortrait */
export { OfficerPortrait as GeneralPortrait };
