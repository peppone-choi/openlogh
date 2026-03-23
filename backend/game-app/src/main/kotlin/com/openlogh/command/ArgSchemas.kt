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

    val recruit = ArgSchema(listOf(
        FieldDef(name = "amount", type = FieldType.INT, required = false, default = 0),
        FieldDef(name = "crewType", type = FieldType.INT, required = true),
    ))

    val trade = ArgSchema(listOf(
        FieldDef(name = "amount", type = FieldType.INT, required = false, default = 0),
        FieldDef(name = "isBuy", type = FieldType.BOOL, required = false, default = true),
    ))

    val foundNation = ArgSchema(listOf(
        FieldDef(name = "nationName", type = FieldType.STRING, required = false, default = "신국"),
        FieldDef(name = "nationType", type = FieldType.STRING, required = false, default = "che_도적"),
        FieldDef(name = "colorType", type = FieldType.INT, required = false, default = 0),
    ))

    val donation = ArgSchema(listOf(
        FieldDef(name = "isGold", type = FieldType.BOOL, required = false, default = true),
        FieldDef(name = "amount", type = FieldType.INT, required = false, default = 0),
    ))

    val gift = ArgSchema(listOf(
        FieldDef(name = "isGold", type = FieldType.BOOL, required = false, default = true),
        FieldDef(name = "amount", type = FieldType.INT, required = false, default = 0),
        FieldDef(name = "destGeneralId", type = FieldType.LONG, required = false),
    ))

    val nationResource = ArgSchema(listOf(
        FieldDef(name = "goldAmount", type = FieldType.INT, required = false, default = 0),
        FieldDef(name = "riceAmount", type = FieldType.INT, required = false, default = 0),
    ))

    val nationName = ArgSchema(listOf(
        FieldDef(name = "nationName", type = FieldType.STRING, required = false, default = ""),
    ))

    val color = ArgSchema(listOf(
        FieldDef(name = "colorType", type = FieldType.STRING, required = false, default = ""),
    ))

    val rate = ArgSchema(listOf(
        FieldDef(name = "rate", type = FieldType.INT, required = false, default = 0),
    ))

    val population = ArgSchema(listOf(
        FieldDef(name = "amount", type = FieldType.INT, required = false, default = 0),
        FieldDef(
            name = "destCityId",
            type = FieldType.LONG,
            required = false,
            aliases = listOf("cityId"),
        ),
    ))

    val equipment = ArgSchema(listOf(
        FieldDef(name = "slot", type = FieldType.STRING, required = false, default = "weapon"),
        FieldDef(name = "itemCode", type = FieldType.STRING, required = false, default = ""),
        FieldDef(name = "isBuy", type = FieldType.BOOL, required = false, default = true),
    ))

    val npcAction = ArgSchema(listOf(
        FieldDef(name = "optionText", type = FieldType.STRING, required = false, default = "행동"),
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

// ========== COMMAND_SCHEMAS: 151 entries (94 general + 57 nation) ==========

val COMMAND_SCHEMAS: Map<String, ArgSchema> = mapOf(
    // ===== General Commands (55) =====
    // Default (1)
    "휴식" to ArgSchemas.empty,

    // Civil / Domestic (18)
    "농지개간" to ArgSchemas.empty,
    "상업투자" to ArgSchemas.empty,
    "치안강화" to ArgSchemas.empty,
    "수비강화" to ArgSchemas.empty,
    "성벽보수" to ArgSchemas.empty,
    "정착장려" to ArgSchemas.empty,
    "주민선정" to ArgSchemas.empty,
    "기술연구" to ArgSchemas.empty,
    "모병" to ArgSchemas.recruit,
    "징병" to ArgSchemas.recruit,
    "훈련" to ArgSchemas.empty,
    "사기진작" to ArgSchemas.empty,
    "소집해제" to ArgSchemas.empty,
    "숙련전환" to ArgSchemas.empty,
    "물자조달" to ArgSchemas.empty,
    "군량매매" to ArgSchemas.trade,
    "헌납" to ArgSchemas.donation,
    "단련" to ArgSchemas.empty,

    // Military (15)
    "출병" to ArgSchemas.destCity,
    "이동" to ArgSchemas.destCity,
    "집합" to ArgSchemas.destCity,
    "귀환" to ArgSchemas.empty,
    "접경귀환" to ArgSchemas.empty,
    "강행" to ArgSchemas.destCity,
    "거병" to ArgSchemas.empty,
    "전투태세" to ArgSchemas.empty,
    "화계" to ArgSchemas.destCity,
    "첩보" to ArgSchemas.destCity,
    "선동" to ArgSchemas.destCity,
    "탈취" to ArgSchemas.destCity,
    "파괴" to ArgSchemas.destCity,
    "요양" to ArgSchemas.empty,
    "방랑" to ArgSchemas.empty,

    // Political (18)
    "견문" to ArgSchemas.empty,
    "등용" to ArgSchemas.destGeneral,
    "등용수락" to ArgSchemas.destNation,
    "임관" to ArgSchemas.destNation,
    "랜덤임관" to ArgSchemas.empty,
    "장수대상임관" to ArgSchemas.destNationAndGeneral,
    "하야" to ArgSchemas.empty,
    "은퇴" to ArgSchemas.empty,
    "건국" to ArgSchemas.foundNation,
    "무작위건국" to ArgSchemas.foundNation,
    "모반시도" to ArgSchemas.empty,
    "선양" to ArgSchemas.destGeneral,
    "해산" to ArgSchemas.empty,
    "인재탐색" to ArgSchemas.empty,
    "증여" to ArgSchemas.gift,
    "장비매매" to ArgSchemas.equipment,
    "내정특기초기화" to ArgSchemas.empty,
    "전투특기초기화" to ArgSchemas.empty,

    // Special (3)
    "NPC능동" to ArgSchemas.npcAction,
    "CR건국" to ArgSchemas.foundNation,
    "CR맹훈련" to ArgSchemas.empty,

    // ===== Nation Commands (43) =====
    // Default (1)
    "Nation휴식" to ArgSchemas.empty,

    // Resource management (10)
    "포상" to ArgSchemas.donation,
    "몰수" to ArgSchemas.donation,
    "감축" to ArgSchemas.destCity,
    "증축" to ArgSchemas.destCity,
    "발령" to ArgSchemas.destCityAndGeneral,
    "천도" to ArgSchemas.destCity,
    "백성동원" to ArgSchemas.destCity,
    "물자원조" to ArgSchemas.nationResource,
    "국기변경" to ArgSchemas.color,
    "국호변경" to ArgSchemas.nationName,

    // Diplomacy (7)
    "선전포고" to ArgSchemas.destNation,
    "종전제의" to ArgSchemas.destNation,
    "종전수락" to ArgSchemas.destNationAndGeneral,
    "불가침제의" to ArgSchemas.destNation,
    "불가침수락" to ArgSchemas.destNationAndGeneral,
    "불가침파기제의" to ArgSchemas.destNation,
    "불가침파기수락" to ArgSchemas.destNationAndGeneral,

    // Strategic (8)
    "급습" to ArgSchemas.destNation,
    "수몰" to ArgSchemas.destCity,
    "허보" to ArgSchemas.destCity,
    "초토화" to ArgSchemas.destCity,
    "필사즉생" to ArgSchemas.empty,
    "이호경식" to ArgSchemas.destNation,
    "피장파장" to ArgSchemas.destNation,
    "의병모집" to ArgSchemas.empty,

    // Research (9)
    "극병연구" to ArgSchemas.empty,
    "대검병연구" to ArgSchemas.empty,
    "무희연구" to ArgSchemas.empty,
    "산저병연구" to ArgSchemas.empty,
    "상병연구" to ArgSchemas.empty,
    "원융노병연구" to ArgSchemas.empty,
    "음귀병연구" to ArgSchemas.empty,
    "화륜차연구" to ArgSchemas.empty,
    "화시병연구" to ArgSchemas.empty,

    // Special (3)
    "무작위수도이전" to ArgSchemas.empty,
    "부대탈퇴지시" to ArgSchemas.destGeneral,
    "인구이동" to ArgSchemas.population,

    // Additional (5)
    "세율변경" to ArgSchemas.rate,
    "징병률변경" to ArgSchemas.rate,
    "국가해산" to ArgSchemas.empty,
    "항복" to ArgSchemas.destNation,
    "외교초기화" to ArgSchemas.empty,

    // ===== New General Commands (39) =====
    "연료보급" to ArgSchemas.empty,
    "기본훈련" to ArgSchemas.empty,
    "특수훈련" to ArgSchemas.empty,
    "맹훈련" to ArgSchemas.empty,
    "정비" to ArgSchemas.empty,
    "지상작전개시" to ArgSchemas.destCity,
    "지상전투개시" to ArgSchemas.destCity,
    "점령" to ArgSchemas.empty,
    "철수" to ArgSchemas.empty,
    "후퇴" to ArgSchemas.empty,
    "육전대출격" to ArgSchemas.empty,
    "육전대철수" to ArgSchemas.empty,
    "정찰" to ArgSchemas.destCity,
    "퇴역" to ArgSchemas.empty,
    "지원전환" to ArgSchemas.empty,
    "망명" to ArgSchemas.destNation,
    "회견" to ArgSchemas.destGeneral,
    "수강" to ArgSchemas.empty,
    "반의" to ArgSchemas.empty,
    "모의" to ArgSchemas.empty,
    "설득" to ArgSchemas.destGeneral,
    "반란참가" to ArgSchemas.empty,
    "자금투입" to ArgSchemas.empty,
    "기함구매" to ArgSchemas.empty,
    "작전계획" to ArgSchemas.empty,
    "작전철회" to ArgSchemas.empty,
    "장수발령" to ArgSchemas.destCityAndGeneral,
    "강의" to ArgSchemas.empty,
    "수송계획" to ArgSchemas.empty,
    "수송중지" to ArgSchemas.empty,
    "완전수리" to ArgSchemas.empty,
    "완전보급" to ArgSchemas.empty,
    "재편성" to ArgSchemas.empty,
    "반출입" to ArgSchemas.empty,
    "야회" to ArgSchemas.empty,
    "수렵" to ArgSchemas.empty,
    "회담" to ArgSchemas.empty,
    "담화" to ArgSchemas.empty,
    "연설" to ArgSchemas.empty,

    // ===== New Nation Commands (14) =====
    "발탁" to ArgSchemas.destGeneral,
    "강등" to ArgSchemas.destGeneral,
    "서작" to ArgSchemas.destGeneral,
    "서훈" to ArgSchemas.destGeneral,
    "사임" to ArgSchemas.empty,
    "봉토수여" to ArgSchemas.destCityAndGeneral,
    "봉토직할" to ArgSchemas.destCity,
    "국가목표설정" to ArgSchemas.empty,
    "납입률변경" to ArgSchemas.rate,
    "관세율변경" to ArgSchemas.rate,
    "분배" to ArgSchemas.empty,
    "처단" to ArgSchemas.destGeneral,
    "외교" to ArgSchemas.destNation,
    "통치목표" to ArgSchemas.empty,
)
