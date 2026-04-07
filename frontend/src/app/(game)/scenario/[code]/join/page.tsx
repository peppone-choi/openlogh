'use client';

import { useState, useEffect } from 'react';
import { useParams, useSearchParams, useRouter } from 'next/navigation';
import axios from 'axios';
import { toast } from 'sonner';
import { CharacterCreator } from '@/components/scenario/CharacterCreator';
import type { CreateCharacterRequest, ScenarioDetailResponse, ScenarioFactionInfo } from '@/types';

export default function ScenarioJoinPage() {
    const params = useParams();
    const searchParams = useSearchParams();
    const router = useRouter();

    const code = params.code as string;
    const worldId = searchParams.get('worldId');

    const [factions, setFactions] = useState<ScenarioFactionInfo[]>([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!code) return;

        const fetchScenario = async (): Promise<void> => {
            try {
                const response = await axios.get<ScenarioDetailResponse>(`/api/scenarios/${code}`);
                setFactions(response.data.factions);
            } catch (error) {
                console.error('Failed to fetch scenario:', error);
                toast.error('시나리오 정보를 불러오는 데 실패했습니다.');
            } finally {
                setLoading(false);
            }
        };

        void fetchScenario();
    }, [code]);

    const handleSubmit = async (request: CreateCharacterRequest): Promise<void> => {
        if (!worldId) {
            toast.error('월드 ID가 없습니다. 올바른 경로로 접근해주세요.');
            return;
        }

        setSubmitting(true);
        try {
            await axios.post(`/api/worlds/${worldId}/generals`, {
                ...request,
                statMode: '8stat',
            });
            router.push(`/game/${worldId}`);
        } catch (error) {
            console.error('Failed to create officer:', error);
            if (axios.isAxiosError(error)) {
                const message = error.response?.data?.message as string | undefined;
                toast.error(message ?? '캐릭터 생성에 실패했습니다.');
            } else {
                toast.error('캐릭터 생성 중 오류가 발생했습니다.');
            }
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-950 flex items-center justify-center">
                <div className="text-gray-400 text-sm">시나리오 정보를 불러오는 중...</div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-950 text-white">
            <div className="max-w-2xl mx-auto px-4 py-8">
                <div className="mb-6">
                    <h1 className="text-2xl font-bold text-yellow-400">시나리오 참가</h1>
                    <p className="text-gray-400 text-sm mt-1">
                        8스탯 배분으로 나만의 장교를 생성하세요. 합계 400점, 각 스탯 20~95 범위.
                    </p>
                </div>
                <div className="bg-gray-900 border border-gray-800 rounded-lg p-6">
                    <CharacterCreator
                        factions={factions}
                        onSubmit={(req) => { void handleSubmit(req); }}
                        submitting={submitting}
                    />
                </div>
            </div>
        </div>
    );
}
