package com.opensam.shared.dto

import jakarta.validation.constraints.NotBlank

data class CreateWorldRequest(
    @field:NotBlank val scenarioCode: String,
    val name: String? = null,
    val tickSeconds: Int = 300,
    val commitSha: String? = null,
    val gameVersion: String? = null,
    val extend: Boolean? = null,
    val npcMode: Int? = null,
    val fiction: Int? = null,
    val maxGeneral: Int? = null,
    val maxNation: Int? = null,
    val joinMode: String? = null,
    val blockGeneralCreate: Int? = null,
    val showImgLevel: Int? = null,
    val autorunUser: List<String>? = null,
)

data class ResetWorldRequest(
    val scenarioCode: String? = null,
    val extend: Boolean? = null,
    val npcMode: Int? = null,
    val fiction: Int? = null,
    val maxGeneral: Int? = null,
    val maxNation: Int? = null,
    val joinMode: String? = null,
    val blockGeneralCreate: Int? = null,
    val showImgLevel: Int? = null,
    val autorunUser: List<String>? = null,
)
