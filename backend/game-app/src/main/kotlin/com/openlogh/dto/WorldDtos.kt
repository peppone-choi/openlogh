package com.openlogh.dto

import jakarta.validation.constraints.NotBlank

data class CreateWorldRequest(
    @field:NotBlank val scenarioCode: String,
    val name: String? = null,
    val tickSeconds: Int = 300,
    val commitSha: String? = null,
    val gameVersion: String? = null,
    val extend: Boolean? = null,
    val npcMode: Int? = null,
    val fiction: Int? = null,
    val maxOfficer: Int? = null,
    val maxFaction: Int? = null,
    val joinMode: String? = null,
    val blockOfficerCreate: Int? = null,
    val showImgLevel: Int? = null,
    val autorunUser: List<String>? = null,
    val startTime: String? = null,
    val opentime: String? = null,
)

data class ResetWorldRequest(
    val scenarioCode: String? = null,
    val extend: Boolean? = null,
    val npcMode: Int? = null,
    val fiction: Int? = null,
    val maxOfficer: Int? = null,
    val maxFaction: Int? = null,
    val joinMode: String? = null,
    val blockOfficerCreate: Int? = null,
    val showImgLevel: Int? = null,
    val autorunUser: List<String>? = null,
    val startTime: String? = null,
    val opentime: String? = null,
)

data class WorldPlanetOwnershipSnapshotResponse(
    val planetId: Long,
    val factionId: Long,
)

data class WorldSnapshotResponse(
    val id: Long,
    val sessionId: Long,
    val year: Int,
    val month: Int,
    val createdAt: String,
    val phase: String? = null,
    val season: String? = null,
    val planetOwnership: List<WorldPlanetOwnershipSnapshotResponse>,
    val events: List<String> = emptyList(),
)
