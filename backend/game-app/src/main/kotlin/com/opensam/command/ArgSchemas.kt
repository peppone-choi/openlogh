package com.opensam.command

private fun parseLong(x: Any?): Long? = when (x) {
    is Number -> x.toLong()
    is String -> x.toLongOrNull()
    else -> null
}

private fun parseInt(x: Any?): Int? = when (x) {
    is Number -> x.toInt()
    is String -> x.toIntOrNull()
    else -> null
}

private fun parseString(x: Any?): String? = when (x) {
    is String -> x.ifBlank { null }
    else -> x?.toString()?.ifBlank { null }
}

private fun parseBool(x: Any?): Boolean? = when (x) {
    is Boolean -> x
    is Number -> x.toInt() != 0
    is String -> x.toBooleanStrictOrNull()
    else -> null
}

object ArgSchemas {
    val none = ArgSchema.NONE

    val destCity = ArgSchema(
        listOf(
            Field(
                "destCityId",
                aliases = listOf("destCityID", "cityId", "targetCityId"),
                required = true,
                parser = ::parseLong,
            )
        )
    )

    val destNation = ArgSchema(
        listOf(
            Field(
                "destNationId",
                aliases = listOf("destNationID", "targetNationId", "nationId"),
                required = true,
                parser = ::parseLong,
            )
        )
    )

    val destGeneral = ArgSchema(
        listOf(
            Field(
                "destGeneralId",
                aliases = listOf("destGeneralID", "targetGeneralId", "generalId"),
                required = true,
                parser = ::parseLong,
            )
        )
    )

    val recruit = ArgSchema(
        listOf(
            Field("crewType", required = true, parser = ::parseInt),
            Field("amount", required = true, parser = ::parseInt),
        )
    )

    val trade = ArgSchema(
        listOf(
            Field("amount", required = true, parser = ::parseInt),
            Field("isBuy", defaultValue = true, parser = ::parseBool),
        )
    )

    val crewTypeOnly = ArgSchema(
        listOf(
            Field("crewType", required = true, parser = ::parseInt)
        )
    )

    val textInput = ArgSchema(
        listOf(
            Field("name", aliases = listOf("text", "value"), required = true, parser = ::parseString)
        )
    )

    val destCityOptional = ArgSchema(
        listOf(
            Field(
                "destCityId",
                aliases = listOf("destCityID", "cityId", "targetCityId"),
                required = false,
                parser = ::parseLong,
            )
        )
    )

    val destGeneralOptional = ArgSchema(
        listOf(
            Field(
                "destGeneralId",
                aliases = listOf("destGeneralID", "targetGeneralId", "generalId"),
                required = false,
                parser = ::parseLong,
            )
        )
    )

    val destNationOptional = ArgSchema(
        listOf(
            Field(
                "destNationId",
                aliases = listOf("destNationID", "targetNationId", "nationId"),
                required = false,
                parser = ::parseLong,
            )
        )
    )

    val amountWithDirection = ArgSchema(
        listOf(
            Field("amount", required = true, parser = ::parseInt),
            Field("isBuy", defaultValue = true, parser = ::parseBool),
        )
    )

    val deployTarget = ArgSchema(
        listOf(
            Field(
                "destGeneralId",
                aliases = listOf("destGeneralID", "targetGeneralId", "generalId"),
                required = true,
                parser = ::parseLong,
            ),
            Field(
                "destCityId",
                aliases = listOf("destCityID", "cityId", "targetCityId"),
                required = true,
                parser = ::parseLong,
            ),
        )
    )

    val equipment = ArgSchema(
        listOf(
            Field("slotType", required = true, parser = ::parseString),
            Field("itemCode", required = false, parser = ::parseInt),
            Field("isBuy", defaultValue = true, parser = ::parseBool),
        )
    )

    val diplomacyWithTerm = ArgSchema(
        listOf(
            Field(
                "destNationId",
                aliases = listOf("destNationID", "targetNationId", "nationId"),
                required = true,
                parser = ::parseLong,
            ),
            Field("term", required = false, defaultValue = 12, parser = ::parseInt),
        )
    )
}

val COMMAND_SCHEMAS: Map<String, ArgSchema> = mapOf(
    "휴식" to ArgSchemas.none,

    "농지개간" to ArgSchemas.none,
    "상업투자" to ArgSchemas.none,
    "치안강화" to ArgSchemas.none,
    "수비강화" to ArgSchemas.none,
    "성벽보수" to ArgSchemas.none,
    "정착장려" to ArgSchemas.none,
    "주민선정" to ArgSchemas.none,
    "기술연구" to ArgSchemas.none,

    "모병" to ArgSchemas.recruit,
    "징병" to ArgSchemas.recruit,
    "훈련" to ArgSchemas.none,
    "사기진작" to ArgSchemas.none,
    "소집해제" to ArgSchemas.none,
    "숙련전환" to ArgSchemas.crewTypeOnly,

    "물자조달" to ArgSchemas.amountWithDirection,
    "군량매매" to ArgSchemas.amountWithDirection,
    "헌납" to ArgSchemas.trade,

    "출병" to ArgSchemas.destCity,
    "이동" to ArgSchemas.destCity,
    "집합" to ArgSchemas.destCity,
    "귀환" to ArgSchemas.none,
    "접경귀환" to ArgSchemas.none,
    "강행" to ArgSchemas.destCity,
    "거병" to ArgSchemas.none,
    "전투태세" to ArgSchemas.none,

    "화계" to ArgSchemas.destCity,
    "첩보" to ArgSchemas.destCity,
    "선동" to ArgSchemas.destCity,
    "탈취" to ArgSchemas.destGeneralOptional,
    "파괴" to ArgSchemas.destCity,

    "등용" to ArgSchemas.destGeneral,
    "등용수락" to ArgSchemas.destGeneral,
    "임관" to ArgSchemas.destNation,
    "랜덤임관" to ArgSchemas.none,
    "장수대상임관" to ArgSchemas.destGeneral,
    "하야" to ArgSchemas.none,
    "은퇴" to ArgSchemas.none,

    "건국" to ArgSchemas.textInput,
    "무작위건국" to ArgSchemas.none,
    "모반시도" to ArgSchemas.none,
    "선양" to ArgSchemas.destGeneral,
    "해산" to ArgSchemas.none,

    "단련" to ArgSchemas.none,
    "요양" to ArgSchemas.none,
    "방랑" to ArgSchemas.destCityOptional,
    "견문" to ArgSchemas.none,
    "인재탐색" to ArgSchemas.none,
    "증여" to ArgSchemas.destGeneral,
    "장비매매" to ArgSchemas.equipment,
    "내정특기초기화" to ArgSchemas.none,
    "전투특기초기화" to ArgSchemas.none,

    "NPC능동" to ArgSchemas.destCityOptional,
    "CR건국" to ArgSchemas.none,
    "CR맹훈련" to ArgSchemas.none,

    "Nation휴식" to ArgSchemas.none,

    "포상" to ArgSchemas.destGeneral,
    "몰수" to ArgSchemas.destGeneral,
    "감축" to ArgSchemas.none,
    "증축" to ArgSchemas.none,
    "발령" to ArgSchemas.deployTarget,
    "천도" to ArgSchemas.destCity,
    "백성동원" to ArgSchemas.destCity,
    "물자원조" to ArgSchemas.destNation,
    "국기변경" to ArgSchemas.textInput,
    "국호변경" to ArgSchemas.textInput,

    "선전포고" to ArgSchemas.destNation,
    "종전제의" to ArgSchemas.destNation,
    "종전수락" to ArgSchemas.destNation,
    "불가침제의" to ArgSchemas.diplomacyWithTerm,
    "불가침수락" to ArgSchemas.destNation,
    "불가침파기제의" to ArgSchemas.destNation,
    "불가침파기수락" to ArgSchemas.destNation,

    "급습" to ArgSchemas.destCity,
    "수몰" to ArgSchemas.destCity,
    "허보" to ArgSchemas.destNation,
    "초토화" to ArgSchemas.destCity,
    "필사즉생" to ArgSchemas.none,
    "이호경식" to ArgSchemas.destNation,
    "피장파장" to ArgSchemas.destNation,
    "의병모집" to ArgSchemas.destCity,

    "극병연구" to ArgSchemas.none,
    "대검병연구" to ArgSchemas.none,
    "무희연구" to ArgSchemas.none,
    "산저병연구" to ArgSchemas.none,
    "상병연구" to ArgSchemas.none,
    "원융노병연구" to ArgSchemas.none,
    "음귀병연구" to ArgSchemas.none,
    "화륜차연구" to ArgSchemas.none,
    "화시병연구" to ArgSchemas.none,

    "무작위수도이전" to ArgSchemas.none,
    "부대탈퇴지시" to ArgSchemas.destGeneral,
    "인구이동" to ArgSchemas.destCity,
)
