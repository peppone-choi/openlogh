package com.openlogh.dto

data class SetPermissionRequest(
    val requesterId: Long,
    val isAmbassador: Boolean,
    val generalIds: List<Long>,
)

data class RaiseEventRequest(
    val event: String,
    val args: List<Any>? = null,
    val worldId: Long? = null,
)
