package com.opensam.command

data class CommandResult(
    val success: Boolean,
    val logs: List<String> = emptyList(),
    val message: String? = null
) {
    companion object {
        fun fail(message: String) = CommandResult(success = false, message = message)
        fun success(logs: List<String>) = CommandResult(success = true, logs = logs)
    }
}

data class CommandCost(
    val gold: Int = 0,
    val rice: Int = 0
)
