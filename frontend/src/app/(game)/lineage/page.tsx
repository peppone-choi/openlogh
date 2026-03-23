'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function DynastyPage() {
    const router = useRouter();

    useEffect(() => {
        router.replace('/emperor');
    }, [router]);

    return null;
}
