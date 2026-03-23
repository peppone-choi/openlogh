@file:Suppress("unused")

package com.openlogh.dto

data class CreateGeneralRequest(
    val name: String? = null,
    val nationId: Long = 0,
)

data class BuildPoolGeneralRequest(
    val name: String,
    val leadership: Short = 70,
    val strength: Short = 70,
    val intel: Short = 70,
    val politics: Short = 70,
    val charm: Short = 70,
)

data class UpdatePoolGeneralRequest(
    val leadership: Short,
    val strength: Short,
    val intel: Short,
    val politics: Short,
    val charm: Short,
)

data class DiplomacyRespondRequest(
    val accept: Boolean,
)
