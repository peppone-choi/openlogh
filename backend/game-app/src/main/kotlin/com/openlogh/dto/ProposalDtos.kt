package com.openlogh.dto

data class SubmitProposalRequest(
    val approverId: Long,
    val actionCode: String,
    val args: Map<String, Any>? = null,
    val reason: String? = null,
)

data class ResolveProposalRequest(
    val approved: Boolean,
    val reason: String? = null,
)

data class ProposalResponse(
    val id: Long,
    val requesterId: Long,
    val requesterName: String,
    val approverId: Long,
    val approverName: String,
    val actionCode: String,
    val args: Map<String, Any>,
    val status: String,
    val reason: String?,
    val createdAt: String,
    val resolvedAt: String?,
)

data class EligibleApproverResponse(
    val officerId: Long,
    val officerName: String,
    val rank: Int,
    val cards: List<String>,
)
