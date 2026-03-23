package com.openlogh.engine.modifier

object TraitSpecRegistry {

    data class TraitSpec(
        val key: String,
        val name: String,
        val info: String = "",
    )

    val war: List<TraitSpec> = listOf(
        TraitSpec("che_저격", "저격", "저격 전법 사용 가능"),
        TraitSpec("che_기병", "기병", "기병 보정"),
        TraitSpec("che_반계", "반계", "계략 반격"),
        TraitSpec("che_돌격", "돌격", "선제 돌격"),
        TraitSpec("che_연사", "연사", "연사 공격"),
        TraitSpec("che_화공", "화공", "화공 전법"),
    )

    val domestic: List<TraitSpec> = listOf(
        TraitSpec("농업", "농업", "농업 특기"),
        TraitSpec("상업", "상업", "상업 특기"),
        TraitSpec("기술", "기술", "기술 특기"),
        TraitSpec("인사", "인사", "인사 특기"),
    )
}
