import { type VariantProps, cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

import { Input as ShadcnInput } from '@/components/ui/input';

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

export interface BitInputProps extends React.ComponentProps<typeof ShadcnInput>, VariantProps<typeof inputVariants> {}

function Input({ ...props }: BitInputProps) {
    const { className, font } = props;

    return (
        <div className={cn('relative border-y-2 border-foreground/20 !p-0 flex items-center', className)}>
            <ShadcnInput
                {...props}
                className={cn('rounded-none ring-0 !w-full', font !== 'normal' && 'retro', className)}
            />

            <div
                className="absolute inset-0 border-x-2 -mx-0.5 border-foreground/20 pointer-events-none"
                aria-hidden="true"
            />
        </div>
    );
}

export { Input };
