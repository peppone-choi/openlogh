package com.openlogh.dto

data class SendLetterRequest(val sessionId: Long, val destFactionId: Long, val type: String, val content: String? = null)

data class RespondLetterRequest(val accept: Boolean)
