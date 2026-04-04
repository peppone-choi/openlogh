import { type VariantProps, cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

import {
    Select as ShadcnSelect,
    SelectContent as ShadcnSelectContent,
    SelectGroup as ShadcnSelectGroup,
    SelectItem as ShadcnSelectItem,
    SelectLabel as ShadcnSelectLabel,
    SelectScrollDownButton as ShadcnSelectScrollDownButton,
    SelectScrollUpButton as ShadcnSelectScrollUpButton,
    SelectSeparator as ShadcnSelectSeparator,
    SelectTrigger as ShadcnSelectTrigger,
    SelectValue as ShadcnSelectValue,
} from '@/components/ui/select';

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

export interface BitSelectProps
    extends React.SelectHTMLAttributes<HTMLSelectElement>, VariantProps<typeof inputVariants> {
    asChild?: boolean;
}

function Select({ ...props }: React.ComponentProps<typeof ShadcnSelect>) {
    return <ShadcnSelect {...props} />;
}

function SelectGroup({ ...props }: React.ComponentProps<typeof ShadcnSelectGroup>) {
    return <ShadcnSelectGroup {...props} />;
}

interface BitSelectValueProps
    extends React.ComponentProps<typeof ShadcnSelectValue>, VariantProps<typeof inputVariants> {}

function SelectValue({ ...props }: BitSelectValueProps) {
    const { font } = props;

    return <ShadcnSelectValue className={cn(font !== 'normal' && 'retro')} {...props} />;
}

interface BitSelectTriggerProps
    extends React.ComponentProps<typeof ShadcnSelectTrigger>, VariantProps<typeof inputVariants> {}

function SelectTrigger({ children, ...props }: BitSelectTriggerProps) {
    const { className, font } = props;

    return (
        <div className={cn('relative border-y-2 border-foreground/20', className, font !== 'normal' && 'retro')}>
            <ShadcnSelectTrigger {...props} className={cn('rounded-none ring-0 w-full border-0', className)}>
                {children}
            </ShadcnSelectTrigger>

            <div
                className="absolute inset-0 border-x-2 -mx-0.5 border-foreground/20 pointer-events-none"
                aria-hidden="true"
            />
        </div>
    );
}

export interface BitSelectContentProps
    extends React.ComponentProps<typeof ShadcnSelectContent>, VariantProps<typeof inputVariants> {}

function SelectContent({ className, children, ...props }: BitSelectContentProps) {
    const { font } = props;

    return (
        <ShadcnSelectContent
            className={cn(
                font !== 'normal' && 'retro',
                className,
                'relative rounded-none border-2 border-foreground/20 -ml-1 mt-1'
            )}
            {...props}
        >
            {children}
        </ShadcnSelectContent>
    );
}

function SelectLabel({ className, ...props }: React.ComponentProps<typeof ShadcnSelectLabel>) {
    return <ShadcnSelectLabel className={cn(className)} {...props} />;
}

function SelectItem({ className, children, ...props }: React.ComponentProps<typeof ShadcnSelectItem>) {
    return (
        <ShadcnSelectItem
            className={cn(className, 'rounded-none border-y border-dashed border-ring/0 hover:border-foreground/20')}
            {...props}
        >
            {children}
        </ShadcnSelectItem>
    );
}

function SelectSeparator({ className, ...props }: React.ComponentProps<typeof ShadcnSelectSeparator>) {
    return <ShadcnSelectSeparator className={cn(className)} {...props} />;
}

function SelectScrollUpButton({ className, ...props }: React.ComponentProps<typeof ShadcnSelectScrollUpButton>) {
    return <ShadcnSelectScrollUpButton className={cn(className)} {...props} />;
}

function SelectScrollDownButton({ className, ...props }: React.ComponentProps<typeof ShadcnSelectScrollDownButton>) {
    return <ShadcnSelectScrollDownButton className={cn(className)} {...props} />;
}

export {
    Select,
    SelectContent,
    SelectGroup,
    SelectItem,
    SelectLabel,
    SelectScrollDownButton,
    SelectScrollUpButton,
    SelectSeparator,
    SelectTrigger,
    SelectValue,
};
