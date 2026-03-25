@file:Suppress("unused")

package com.openlogh.command

// ========== Schema Field Definition ==========

enum class FieldType { STRING, INT, LONG, BOOL, FLOAT }

data class FieldDef(
    val name: String,
    val type: FieldType,
    val required: Boolean = false,
    val default: Any? = null,
    val aliases: List<String> = emptyList(),
    val legacyKeys: List<String> = emptyList(),
)

data class ArgSchema(
    val fields: List<FieldDef>,
) {
    fun parse(raw: Map<String, Any>?): ValidatedArgs {
        val result = mutableMapOf<String, Any?>()
        val errors = mutableListOf<ValidationError>()

        for (field in fields) {
            val allKeys = listOf(field.name) + field.aliases
            val rawValue = allKeys.firstNotNullOfOrNull { raw?.get(it) }

            if (rawValue == null) {
                if (field.required && field.default == null) {
                    errors.add(ValidationError(field.name, "필수 필드입니다"))
                    continue
                }
                result[field.name] = field.default
                continue
            }

            val converted = convertValue(rawValue, field.type)
            if (converted == null && field.required) {
                errors.add(ValidationError(field.name, "유효하지 않은 값입니다: $rawValue"))
                continue
            }
            result[field.name] = converted ?: field.default
        }

        return ValidatedArgs(result, errors)
    }

    private fun convertValue(value: Any, type: FieldType): Any? = when (type) {
        FieldType.STRING -> value.toString()
        FieldType.INT -> when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
        FieldType.LONG -> when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        FieldType.BOOL -> when (value) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            is Number -> value.toInt() != 0
            else -> null
        }
        FieldType.FLOAT -> when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }
}

data class ValidationError(
    val field: String,
    val message: String,
)

class ValidatedArgs(
    private val values: Map<String, Any?>,
    val errors: List<ValidationError>,
) {
    fun ok(): Boolean = errors.isEmpty()

    fun stringOrNull(key: String): String? = values[key] as? String
    fun intOrNull(key: String): Int? = (values[key] as? Number)?.toInt()
    fun longOrNull(key: String): Long? = (values[key] as? Number)?.toLong()
    fun boolOrNull(key: String): Boolean? = values[key] as? Boolean
    fun floatOrNull(key: String): Float? = (values[key] as? Number)?.toFloat()

    fun toLegacyMap(schema: ArgSchema): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for (field in schema.fields) {
            val value = values[field.name] ?: continue
            result[field.name] = value
            for (legacy in field.legacyKeys) {
                result[legacy] = value
            }
            for (alias in field.aliases) {
                result[alias] = value
            }
        }
        return result
    }
}

// ========== Named Schemas ==========

object ArgSchemas {
    val empty = ArgSchema(emptyList())

    val destCity = ArgSchema(listOf(
        FieldDef(
            name = "destCityId",
            type = FieldType.LONG,
            required = false,
            aliases = listOf("cityId", "targetCityId"),
            legacyKeys = listOf("destCityID", "cityId", "targetCityId"),
        ),
    ))

    val destGeneral = ArgSchema(listOf(
        FieldDef(
            name = "destGeneralId",
            type = FieldType.LONG,
            required = false,
            aliases = listOf("generalId", "targetGeneralId"),
            legacyKeys = listOf("destGeneralID", "generalId", "targetGeneralId"),
        ),
    ))

    val destNation = ArgSchema(listOf(
        FieldDef(
            name = "destNationId",
            type = FieldType.LONG,
            required = false,
            aliases = listOf("nationId", "targetNationId"),
            legacyKeys = listOf("destNationID", "nationId", "targetNationId"),
        ),
    ))

    val rate = ArgSchema(listOf(
        FieldDef(name = "rate", type = FieldType.INT, required = false, default = 0),
    ))

    val destCityAndGeneral = ArgSchema(listOf(
        FieldDef(name = "destCityId", type = FieldType.LONG, required = false, aliases = listOf("cityId")),
        FieldDef(name = "destGeneralId", type = FieldType.LONG, required = false, aliases = listOf("generalId")),
    ))

    val destNationAndGeneral = ArgSchema(listOf(
        FieldDef(name = "destNationId", type = FieldType.LONG, required = false, aliases = listOf("nationId")),
        FieldDef(name = "destGeneralId", type = FieldType.LONG, required = false, aliases = listOf("generalId")),
    ))
}

// ========== COMMAND_SCHEMAS: LOGH commands only ==========

val COMMAND_SCHEMAS: Map<String, ArgSchema> = mapOf(
    // ===== Default (1) =====
    "휴식" to ArgSchemas.empty,

    // ===== Operations / MCP (25) =====
    "워프항행" to ArgSchemas.empty,
    "성계내항행" to ArgSchemas.destCity,
    "연료보급" to ArgSchemas.empty,
    "정찰" to ArgSchemas.destCity,
    "군기유지" to ArgSchemas.empty,
    "기본훈련" to ArgSchemas.empty,
    "특수훈련" to ArgSchemas.empty,
    "맹훈련" to ArgSchemas.empty,
    "육전훈련" to ArgSchemas.empty,
    "공전훈련" to ArgSchemas.empty,
    "경계출동" to ArgSchemas.empty,
    "무력진압" to ArgSchemas.empty,
    "분열행진" to ArgSchemas.empty,
    "징발" to ArgSchemas.empty,
    "특별경비" to ArgSchemas.empty,
    "정비" to ArgSchemas.empty,
    "지상작전개시" to ArgSchemas.destCity,
    "지상전투개시" to ArgSchemas.destCity,
    "점령" to ArgSchemas.empty,
    "철수" to ArgSchemas.empty,
    "후퇴" to ArgSchemas.empty,
    "육전대출격" to ArgSchemas.empty,
    "육전대철수" to ArgSchemas.empty,
    "육전전술훈련" to ArgSchemas.empty,
    "공전전술훈련" to ArgSchemas.empty,

    // ===== Personal / PCP (16) =====
    "퇴역" to ArgSchemas.empty,
    "지원전환" to ArgSchemas.empty,
    "망명" to ArgSchemas.destNation,
    "회견" to ArgSchemas.destGeneral,
    "수강" to ArgSchemas.empty,
    "기함구매" to ArgSchemas.empty,
    "자금투입" to ArgSchemas.empty,
    "귀환설정" to ArgSchemas.destCity,
    "원거리이동" to ArgSchemas.destCity,
    "근거리이동" to ArgSchemas.empty,
    "병기연습" to ArgSchemas.empty,
    "반의" to ArgSchemas.empty,
    "모의" to ArgSchemas.empty,
    "설득" to ArgSchemas.destGeneral,
    "반란참가" to ArgSchemas.empty,
    "반란" to ArgSchemas.empty,

    // ===== Command / Leadership (8) =====
    "작전계획" to ArgSchemas.empty,
    "장수발령" to ArgSchemas.destCityAndGeneral,
    "작전철회" to ArgSchemas.empty,
    "부대결성" to ArgSchemas.empty,
    "부대해산" to ArgSchemas.empty,
    "강의" to ArgSchemas.empty,
    "수송계획" to ArgSchemas.empty,
    "수송중지" to ArgSchemas.empty,

    // ===== Logistics (6) =====
    "재편성" to ArgSchemas.empty,
    "완전수리" to ArgSchemas.empty,
    "완전보급" to ArgSchemas.empty,
    "반출입" to ArgSchemas.empty,
    "보충" to ArgSchemas.empty,
    "할당" to ArgSchemas.empty,

    // ===== Influence / Social (5) =====
    "야회" to ArgSchemas.empty,
    "수렵" to ArgSchemas.empty,
    "회담" to ArgSchemas.empty,
    "담화" to ArgSchemas.empty,
    "연설" to ArgSchemas.empty,

    // ===== Personal (proposal/order) (2) =====
    "제안" to ArgSchemas.destGeneral,
    "명령" to ArgSchemas.destGeneral,

    // ===== Espionage / Intelligence (15) =====
    "일제수색" to ArgSchemas.empty,
    "체포허가" to ArgSchemas.destGeneral,
    "집행명령" to ArgSchemas.destGeneral,
    "체포명령" to ArgSchemas.destGeneral,
    "사열" to ArgSchemas.empty,
    "습격" to ArgSchemas.destGeneral,
    "감시" to ArgSchemas.destGeneral,
    "잠입공작" to ArgSchemas.destCity,
    "탈출공작" to ArgSchemas.empty,
    "정보공작" to ArgSchemas.empty,
    "파괴공작" to ArgSchemas.empty,
    "선동공작" to ArgSchemas.destCity,
    "귀환공작" to ArgSchemas.empty,
    "통신방해" to ArgSchemas.empty,
    "위장함대" to ArgSchemas.empty,

    // ===== Nation: Default (1) =====
    "Nation휴식" to ArgSchemas.empty,

    // ===== Nation: Personnel (10) =====
    "승진" to ArgSchemas.destGeneral,
    "발탁" to ArgSchemas.destGeneral,
    "강등" to ArgSchemas.destGeneral,
    "서작" to ArgSchemas.destGeneral,
    "서훈" to ArgSchemas.destGeneral,
    "임명" to ArgSchemas.destGeneral,
    "파면" to ArgSchemas.destGeneral,
    "사임" to ArgSchemas.empty,
    "봉토수여" to ArgSchemas.destCityAndGeneral,
    "봉토직할" to ArgSchemas.destCity,

    // ===== Nation: Political (9) =====
    "국가목표설정" to ArgSchemas.empty,
    "납입률변경" to ArgSchemas.rate,
    "관세율변경" to ArgSchemas.rate,
    "분배" to ArgSchemas.empty,
    "처단" to ArgSchemas.destGeneral,
    "외교" to ArgSchemas.destNation,
    "통치목표" to ArgSchemas.empty,
    "예산편성" to ArgSchemas.empty,
    "제안공작" to ArgSchemas.destGeneral,

    // ===== Nation: Diplomacy (7) =====
    "선전포고" to ArgSchemas.destNation,
    "불가침제의" to ArgSchemas.destNation,
    "불가침수락" to ArgSchemas.destNationAndGeneral,
    "불가침파기제의" to ArgSchemas.destNation,
    "불가침파기수락" to ArgSchemas.destNationAndGeneral,
    "종전제의" to ArgSchemas.destNation,
    "종전수락" to ArgSchemas.destNationAndGeneral,

    // ===== Nation: Resource / Administration (7) =====
    "감축" to ArgSchemas.destCity,
    "주민동원" to ArgSchemas.destCity,
    "외교공작" to ArgSchemas.destNation,
    "세율변경" to ArgSchemas.rate,
    "징병률변경" to ArgSchemas.rate,
    "국가해산" to ArgSchemas.empty,
    "항복" to ArgSchemas.destNation,
)
