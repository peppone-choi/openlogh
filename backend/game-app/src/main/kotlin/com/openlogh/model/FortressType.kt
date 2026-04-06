package com.openlogh.model

/**
 * Fortress types in the LOGH galaxy.
 * Each fortress has unique gun stats based on gin7 reference data.
 */
enum class FortressType(
    val gunPower: Int,
    val gunRange: Int,
    val gunCooldownTicks: Int,
    val garrisonCapacity: Int,
    val displayNameKo: String
) {
    NONE(0, 0, 0, 0, "없음"),
    ISERLOHN(9000, 3, 300, 15, "이제르론 요새"),
    GEIERSBURG(7000, 2, 360, 12, "가이에스부르크 요새"),
    RENTENBERG(5000, 2, 240, 8, "렌텐베르크 요새"),
    GARMISCH(5000, 2, 240, 8, "가르미슈 요새");
}
