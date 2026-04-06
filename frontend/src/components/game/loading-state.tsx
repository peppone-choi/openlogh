import { Loader2 } from 'lucide-react';

interface LoadingStateProps {
    message?: string;
}

export function LoadingState({ message = 'Loading...' }: LoadingStateProps) {
    return (
        <div className="flex items-center justify-center gap-2 py-12 text-muted-foreground">
            <Loader2 className="size-5 animate-spin" />
            <span className="text-sm">{message}</span>
        </div>
    );
}
