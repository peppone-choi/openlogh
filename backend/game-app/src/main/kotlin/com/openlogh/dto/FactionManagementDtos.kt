package com.openlogh.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class FactionPolicyInfo(
    val conscriptionRate: Int, val taxRate: Int, val secretLimit: Int, val strategicCmdLimit: Int,
    val notice: String, val scoutMsg: String,
    val blockWar: Boolean, val blockScout: Boolean,
)

data class UpdatePolicyRequest(
    @field:Min(5)
    @field:Max(30)
    val conscriptionRate: Int? = null,
    @field:Min(20)
    @field:Max(200)
    val taxRate: Int? = null,
    @field:Min(1)
    @field:Max(99)
    val secretLimit: Int? = null,
    val strategicCmdLimit: Int? = null,
)

data class UpdateNoticeRequest(
    @field:Size(max = 16384)
    val notice: String,
)

data class UpdateScoutMsgRequest(
    @field:Size(max = 1000)
    val scoutMsg: String,
)

data class SetTaxRateRequest(
    @field:Min(20)
    @field:Max(200)
    val amount: Int,
)

data class SetConscriptionRateRequest(
    @field:Min(5)
    @field:Max(30)
    val amount: Int,
)

data class SetSecretLimitRequest(
    @field:Min(1)
    @field:Max(99)
    val amount: Int,
)

data class SetToggleRequest(val value: Boolean)

data class SetFactionNoticeRequest(
    @field:Size(max = 16384)
    val msg: String,
)

data class SetFactionScoutMsgRequest(
    @field:Size(max = 1000)
    val msg: String,
)

data class FactionMutationResponse(
    val success: Boolean,
    val reason: String? = null,
    val availableCnt: Int? = null,
)

data class OfficerInfo(val id: Long, val name: String, val picture: String, val rank: Int, val planetId: Long)

data class AppointOfficerRequest(val officerId: Long, val rank: Int, val stationedSystem: Int? = null)

data class ExpelRequest(val officerId: Long)

data class NpcPolicyInfo(val policies: Map<String, Any> = emptyMap(), val priorities: Map<String, Any> = emptyMap())

data class SetPermissionRequest(val requesterId: Long, val isAmbassador: Boolean, val generalIds: List<Long>)
