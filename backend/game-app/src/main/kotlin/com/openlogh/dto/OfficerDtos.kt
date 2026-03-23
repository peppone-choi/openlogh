package com.openlogh.dto

import java.time.Instant

data class CreateOfficerRequest(
    val name: String? = null,
    val planetId: Long? = null,
    val factionId: Long? = 0,
    val leadership: Short = 50,
    val command: Short = 50,
    val intelligence: Short = 50,
    val politics: Short = 50,
    val administration: Short = 50,
    val shipClass: Short = 0,
    val personality: String? = "Random",
    val pic: Boolean? = null,
    val useOwnIcon: Boolean? = null,
    val inheritSpecial: String? = null,
    val inheritTurntimeZone: Int? = null,
    val inheritCity: Long? = null,
    val inheritBonusStat: List<Int>? = null,
)

data class SelectNpcRequest(val officerId: Long)

data class BuildPoolOfficerRequest(
    val name: String,
    val leadership: Short = 70,
    val command: Short = 70,
    val intelligence: Short = 70,
    val politics: Short = 70,
    val administration: Short = 70,
    val ego: String? = null,
    val personality: String? = null,
)

data class UpdatePoolOfficerRequest(
    val leadership: Short,
    val command: Short,
    val intelligence: Short,
    val politics: Short,
    val administration: Short,
)

data class RefreshNpcTokenRequest(
    val nonce: String,
    val keepIds: List<Long> = emptyList(),
)

data class SelectNpcWithTokenRequest(
    val nonce: String,
    val officerId: Long,
)

data class NpcCard(
    val id: Long,
    val name: String,
    val picture: String,
    val imageServer: Short,
    val leadership: Short,
    val command: Short,
    val intelligence: Short,
    val politics: Short,
    val administration: Short,
    val factionId: Long,
    val nationName: String,
    val nationColor: String,
    val personality: String,
    val special: String,
    val special2: String? = null,
    val dex: List<Int>? = null,
    val experience: Int? = null,
    val dedication: Int? = null,
    val expLevel: Short? = null,
    val personalityInfo: String? = null,
    val specialInfo: String? = null,
    val special2Info: String? = null,
    val keepCount: Int? = null,
)

data class NpcTokenResponse(
    val nonce: String,
    val npcs: List<NpcCard>,
    val validUntil: Instant,
    val pickMoreAfter: Instant,
    val keepCount: Int,
)

data class SelectNpcResult(
    val success: Boolean,
    val officer: OfficerResponse,
)
