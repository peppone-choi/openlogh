package com.openlogh.dto

data class RealtimeExecuteRequest(
    val officerId: Long,
    val actionCode: String,
    val arg: Map<String, Any>? = null,
)
