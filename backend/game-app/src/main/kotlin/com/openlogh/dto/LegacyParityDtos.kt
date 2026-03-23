package com.openlogh.dto

data class RaiseEventRequest(
    val event: String,
    val args: List<Any>? = null,
    val sessionId: Long? = null,
)
