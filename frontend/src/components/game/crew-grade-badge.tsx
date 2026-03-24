import { Badge } from '@/components/ui/badge';

export type CrewGrade = 'elite' | 'veteran' | 'normal' | 'green';

interface CrewGradeBadgeProps {
    grade: CrewGrade;
    className?: string;
}

const GRADE_CONFIG: Record<CrewGrade, { label: string; className: string }> = {
    elite: {
        label: '엘리트',
        className: 'bg-yellow-500/20 text-yellow-300 border-yellow-500/50',
    },
    veteran: {
        label: '베테랑',
        className: 'bg-blue-500/20 text-blue-300 border-blue-500/50',
    },
    normal: {
        label: '노멀',
        className: 'bg-gray-500/20 text-gray-300 border-gray-500/50',
    },
    green: {
        label: '그린',
        className: 'bg-green-500/20 text-green-400 border-green-500/50',
    },
};

/**
 * Derives crew grade from training value (훈련도).
 * elite: 90+, veteran: 70+, normal: 40+, green: <40
 */
export function getCrewGrade(training: number): CrewGrade {
    if (training >= 90) return 'elite';
    if (training >= 70) return 'veteran';
    if (training >= 40) return 'normal';
    return 'green';
}

export function CrewGradeBadge({ grade, className }: CrewGradeBadgeProps) {
    const config = GRADE_CONFIG[grade];
    return (
        <Badge variant="outline" className={`${config.className} ${className ?? ''}`}>
            {config.label}
        </Badge>
    );
}
