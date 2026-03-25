import { type VariantProps, cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

import { Textarea as ShadcnTextarea } from '@/components/ui/textarea';

import '@/components/ui/8bit/styles/retro.css';

export const inputVariants = cva('', {
    variants: {
        font: {
            normal: '',
            retro: 'retro',
        },
    },
    defaultVariants: {
        font: 'retro',
    },
});

export interface BitTextareaProps
    extends React.TextareaHTMLAttributes<HTMLTextAreaElement>, VariantProps<typeof inputVariants> {
    asChild?: boolean;
}

function Textarea({ ...props }: BitTextareaProps) {
    const { className, font } = props;

    return (
        <div className={cn('relative w-full', className)}>
            <ShadcnTextarea
                {...props}
                className={cn(
                    'rounded-none transition-transform ring-0 border-0',
                    font !== 'normal' && 'retro',
                    className
                )}
            />

            <div
                className="absolute inset-0 border-y-2 -my-0.5 border-foreground/20 pointer-events-none"
                aria-hidden="true"
            />

            <div
                className="absolute inset-0 border-x-2 -mx-0.5 border-foreground/20 pointer-events-none"
                aria-hidden="true"
            />
        </div>
    );
}

export { Textarea };
