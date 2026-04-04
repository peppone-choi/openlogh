package com.openlogh.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class NationPolicyInfo(
    val rate: Int, val bill: Int, val secretLimit: Int, val strategicCmdLimit: Int,
    val notice: String, val scoutMsg: String,
    val blockWar: Boolean, val blockScout: Boolean,
)

data class UpdatePolicyRequest(
    @field:Min(5)
    @field:Max(30)
    val rate: Int? = null,
    @field:Min(20)
    @field:Max(200)
    val bill: Int? = null,
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

data class SetBillRequest(
    @field:Min(20)
    @field:Max(200)
    val amount: Int,
)

data class SetRateRequest(
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

data class SetNationNoticeRequest(
    @field:Size(max = 16384)
    val msg: String,
)

data class SetNationScoutMsgRequest(
    @field:Size(max = 1000)
    val msg: String,
)

data class NationMutationResponse(
    val result: Boolean,
    val reason: String? = null,
    val availableCnt: Int? = null,
)

data class OfficerInfo(val id: Long, val name: String, val picture: String, val officerLevel: Int, val cityId: Long)

data class AppointOfficerRequest(val generalId: Long, val officerLevel: Int, val officerCity: Int? = null)

data class ExpelRequest(val generalId: Long)
