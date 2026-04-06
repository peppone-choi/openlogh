import api from '../api';
import type {
    RankLadderEntry,
    PersonnelInfo,
    PromoteDemoteRequest,
    PersonnelActionResponse,
} from '@/types/personnel';

/** Fetch the rank ladder for a faction in a session */
export async function fetchRankLadder(
    sessionId: number,
    factionId: number
): Promise<RankLadderEntry[]> {
    const { data } = await api.get<RankLadderEntry[]>(
        `/world/${sessionId}/personnel/ladder/${factionId}`
    );
    return data;
}

/** Fetch personnel info for a specific officer */
export async function fetchPersonnelInfo(
    sessionId: number,
    officerId: number
): Promise<PersonnelInfo> {
    const { data } = await api.get<PersonnelInfo>(
        `/world/${sessionId}/personnel/info/${officerId}`
    );
    return data;
}

/** Promote an officer (requires promoter authority) */
export async function promoteOfficer(
    sessionId: number,
    promoterId: number,
    request: PromoteDemoteRequest
): Promise<PersonnelActionResponse> {
    const { data } = await api.post<PersonnelActionResponse>(
        `/world/${sessionId}/personnel/promote/${promoterId}`,
        request
    );
    return data;
}

/** Demote an officer (requires demoter authority) */
export async function demoteOfficer(
    sessionId: number,
    demoterId: number,
    request: PromoteDemoteRequest
): Promise<PersonnelActionResponse> {
    const { data } = await api.post<PersonnelActionResponse>(
        `/world/${sessionId}/personnel/demote/${demoterId}`,
        request
    );
    return data;
}
