package com.opensam.command

data class ArgError(val field: String, val message: String)

data class ValidatedArgs(
    val canonical: Map<String, Any?>,
    val errors: List<ArgError>
) {
    fun ok() = errors.isEmpty()
    fun longOrNull(name: String): Long? = canonical[name] as? Long
    fun intOrNull(name: String): Int? = canonical[name] as? Int
    fun stringOrNull(name: String): String? = canonical[name] as? String
    fun boolOrNull(name: String): Boolean? = canonical[name] as? Boolean

    fun toLegacyMap(schema: ArgSchema): Map<String, Any> {
        val out = mutableMapOf<String, Any>()
        schema.fields.forEach { f ->
            val v = canonical[f.name] ?: return@forEach
            (listOf(f.name) + f.aliases).forEach { key -> out[key] = v }
        }
        return out
    }
}

data class Field(
    val name: String,
    val aliases: List<String> = emptyList(),
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val parser: (Any?) -> Any?
)

class ArgSchema(val fields: List<Field>) {
    fun parse(raw: Map<String, Any>?): ValidatedArgs {
        val input = raw ?: emptyMap()
        val canonical = mutableMapOf<String, Any?>()
        val errors = mutableListOf<ArgError>()

        for (f in fields) {
            val keys = listOf(f.name) + f.aliases
            val rawValue = keys.firstNotNullOfOrNull { k -> input[k] }
            val parsed = f.parser(rawValue)

            when {
                parsed != null -> canonical[f.name] = parsed
                f.defaultValue != null -> canonical[f.name] = f.defaultValue
                f.required -> errors += ArgError(
                    f.name,
                    "필수 인자 '${f.name}' 누락 (허용 키: ${keys.joinToString(", ")})"
                )
            }
        }
        return ValidatedArgs(canonical, errors)
    }

    companion object {
        val NONE = ArgSchema(emptyList())
    }
}
