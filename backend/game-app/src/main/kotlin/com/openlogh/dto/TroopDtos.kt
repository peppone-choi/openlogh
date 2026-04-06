package com.openlogh.dto

import com.openlogh.entity.Fleet
import com.openlogh.entity.UnitCrew
import com.openlogh.service.FormationCap

// --- Legacy DTOs (backward compat aliases) ---

@Deprecated("Use CreateUnitRequest", replaceWith = ReplaceWith("CreateUnitRequest"))
data class CreateTroopRequest(val worldId: Long, val leaderGeneralId: Long, val nationId: Long, val name: String)

data class TroopActionRequest(val generalId: Long)

data class RenameTroopRequest(val name: String)

data class TroopMemberInfo(val id: Long, val name: String, val picture: String)

data class TroopWithMembers(val troop: Fleet, val members: List<TroopMemberInfo>)

// --- New DTOs ---

data class CreateUnitRequest(
    val sessionId: Long,
    val commanderOfficerId: Long,
    val factionId: Long,
    val name: String,
    val unitType: String = "FLEET",
    val planetId: Long? = null,
)

data class AssignCrewRequest(
    val officerId: Long,
    val slotRole: String,
)

data class UnitResponse(
    val id: Long,
    val sessionId: Long,
    val leaderOfficerId: Long,
    val factionId: Long,
    val name: String,
    val unitType: String,
    val maxUnits: Int,
    val currentUnits: Int,
    val maxCrew: Int,
    val planetId: Long?,
    val meta: Map<String, Any>,
    val crew: List<CrewMemberResponse> = emptyList(),
) {
    companion object {
        fun from(fleet: Fleet, crew: List<UnitCrew> = emptyList()) = UnitResponse(
            id = fleet.id,
            sessionId = fleet.sessionId,
            leaderOfficerId = fleet.leaderOfficerId,
            factionId = fleet.factionId,
            name = fleet.name,
            unitType = fleet.unitType,
            maxUnits = fleet.maxUnits,
            currentUnits = fleet.currentUnits,
            maxCrew = fleet.maxCrew,
            planetId = fleet.planetId,
            meta = fleet.meta,
            crew = crew.map { CrewMemberResponse.from(it) },
        )
    }
}

data class CrewMemberResponse(
    val id: Long,
    val officerId: Long,
    val slotRole: String,
) {
    companion object {
        fun from(uc: UnitCrew) = CrewMemberResponse(
            id = uc.id,
            officerId = uc.officerId,
            slotRole = uc.slotRole,
        )
    }
}

data class FormationCapEntry(
    val current: Int,
    val max: Int,
    val available: Int,
) {
    companion object {
        fun from(cap: FormationCap) = FormationCapEntry(
            current = cap.current,
            max = cap.max,
            available = cap.available,
        )
    }
}

data class FormationCapResponse(
    val caps: Map<String, FormationCapEntry>,
)
