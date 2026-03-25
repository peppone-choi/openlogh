package com.openlogh.shared.dto

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
    val maxOfficer: Int? = null,
    val maxFaction: Int? = null,
    val joinMode: String? = null,
    val blockOfficerCreate: Int? = null,
    val showImgLevel: Int? = null,
    val autorunUser: List<String>? = null,
    /** 가오픈 시작 시각 (ISO 8601). null이면 즉시 가오픈. */
    val startTime: String? = null,
    /** 정식 오픈 시각 (ISO 8601). null이면 기본 +3년. */
    val opentime: String? = null,
)

data class ResetWorldRequest(
    val scenarioCode: String? = null,
    val extend: Boolean? = null,
    val npcMode: Int? = null,
    val fiction: Int? = null,
    val maxOfficer: Int? = null,
    val maxFaction: Int? = null,
    val joinMode: String? = null,
    val blockOfficerCreate: Int? = null,
    val showImgLevel: Int? = null,
    val autorunUser: List<String>? = null,
    /** 가오픈 시작 시각 (ISO 8601). null이면 즉시 가오픈. */
    val startTime: String? = null,
    /** 정식 오픈 시각 (ISO 8601). null이면 기본 +3년. */
    val opentime: String? = null,
)
