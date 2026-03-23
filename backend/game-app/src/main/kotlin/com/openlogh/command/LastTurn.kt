package com.openlogh.command

data class LastTurn(
    val command: String = "휴식",
    val arg: Map<String, Any>? = null,
    val term: Int? = null,
) {
    fun isSameCommand(cmd: String, arg: Map<String, Any>?): Boolean {
        val thisArgNorm = if (this.arg.isNullOrEmpty()) null else this.arg
        val otherArgNorm = if (arg.isNullOrEmpty()) null else arg
        return this.command == cmd && thisArgNorm == otherArgNorm
    }

    fun addTermStack(cmd: String, arg: Map<String, Any>?, maxTerm: Int): LastTurn {
        return if (isSameCommand(cmd, arg)) {
            val currentTerm = term ?: 0
            val nextTerm = (currentTerm + 1).coerceAtMost(maxTerm)
            copy(term = nextTerm)
        } else {
            LastTurn(command = cmd, arg = arg, term = 1)
        }
    }

    fun getTermStack(cmd: String, arg: Map<String, Any>?): Int {
        return if (isSameCommand(cmd, arg)) term ?: 0 else 0
    }

    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("command", command)
            if (arg != null) put("arg", arg)
            if (term != null) put("term", term)
        }
    }

    companion object {
        fun fromMap(map: Map<String, Any?>?): LastTurn {
            if (map.isNullOrEmpty()) return LastTurn()
            val command = map["command"] as? String ?: "휴식"
            @Suppress("UNCHECKED_CAST")
            val arg = map["arg"] as? Map<String, Any>
            val term = (map["term"] as? Number)?.toInt()
            return LastTurn(command = command, arg = arg, term = term)
        }
    }
}
