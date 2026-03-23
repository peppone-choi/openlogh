package com.openlogh.command

data class CommandResult(
    val success: Boolean = false,
    val logs: List<String> = emptyList(),
    val message: String? = null,
)
