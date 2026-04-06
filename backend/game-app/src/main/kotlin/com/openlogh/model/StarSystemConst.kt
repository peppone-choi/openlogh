package com.openlogh.model

data class StarSystemConst(
    val id: Int,
    val nameKo: String,
    val nameEn: String,
    val faction: String,
    val x: Int,
    val y: Int,
    val starRgb: List<Int>,
    val spectralType: String,
    val planets: List<String>,
    val connections: List<Int>,
    val fortressType: FortressType = FortressType.NONE
)
