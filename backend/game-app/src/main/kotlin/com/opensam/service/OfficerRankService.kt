package com.opensam.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class OfficerRankService {

    private lateinit var defaultRanks: Map<String, String>
    private lateinit var byNationLevel: Map<String, Map<String, Any>>

    @PostConstruct
    fun init() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val resource = ClassPathResource("data/officer_ranks.json")
        val data: Map<String, Any> = mapper.readValue(resource.inputStream, object : TypeReference<Map<String, Any>>() {})

        defaultRanks = readStringStringMap(data["default"])
        byNationLevel = readNestedStringAnyMap(data["byNationLevel"])
    }

    fun getRankTitle(officerLevel: Int, nationLevel: Int?): String {
        if (officerLevel < 5) {
            return defaultRanks[officerLevel.toString()] ?: "???"
        }

        if (nationLevel == null) {
            return defaultRanks[officerLevel.toString()] ?: "???"
        }

        val nationMap = byNationLevel[nationLevel.toString()] ?: return defaultRanks[officerLevel.toString()] ?: "???"

        val ranks = readStringStringMapOrNull(nationMap["ranks"]) ?: return defaultRanks[officerLevel.toString()] ?: "???"

        return ranks[officerLevel.toString()] ?: defaultRanks[officerLevel.toString()] ?: "???"
    }

    fun getNationTitle(nationLevel: Int): String? {
        val nationMap = byNationLevel[nationLevel.toString()] ?: return null
        return nationMap["title"] as? String
    }

    private fun readStringStringMap(raw: Any?): Map<String, String> {
        return readStringStringMapOrNull(raw) ?: emptyMap()
    }

    private fun readStringStringMapOrNull(raw: Any?): Map<String, String>? {
        if (raw !is Map<*, *>) return null
        val result = mutableMapOf<String, String>()
        raw.forEach { (key, value) ->
            if (key is String && value is String) {
                result[key] = value
            }
        }
        return result
    }

    private fun readNestedStringAnyMap(raw: Any?): Map<String, Map<String, Any>> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Map<String, Any>>()
        raw.forEach { (key, value) ->
            if (key is String) {
                val nested = readStringAnyMap(value)
                if (nested.isNotEmpty()) {
                    result[key] = nested
                }
            }
        }
        return result
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }
}
