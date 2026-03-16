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
    private lateinit var specialNations: Map<String, Map<String, String>>

    @PostConstruct
    fun init() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val resource = ClassPathResource("data/officer_ranks.json")
        val data: Map<String, Any> = mapper.readValue(resource.inputStream, object : TypeReference<Map<String, Any>>() {})

        defaultRanks = readStringStringMap(data["default"])
        byNationLevel = readNestedStringAnyMap(data["byNationLevel"])
        specialNations = readNestedStringStringMap(data["specialNations"])
    }

    fun getRankTitle(
        officerLevel: Int,
        nationLevel: Int?,
        nationTypeCode: String? = null,
        officerRankKey: String? = null
    ): String {
        if (officerLevel < 5) {
            return defaultRanks[officerLevel.toString()] ?: "???"
        }

        if (officerRankKey != null) {
            val specialMap = specialNations[officerRankKey]
            if (specialMap != null) {
                val title = specialMap[officerLevel.toString()]
                if (title != null) return title
            }
        }

        if (nationLevel == null) {
            return defaultRanks[officerLevel.toString()] ?: "???"
        }

        val nationMap = byNationLevel[nationLevel.toString()] ?: return defaultRanks[officerLevel.toString()] ?: "???"

        val ranks = readStringStringMapOrNull(nationMap["ranks"]) ?: return defaultRanks[officerLevel.toString()] ?: "???"

        return ranks[officerLevel.toString()] ?: findLowestRank(ranks) ?: "???"
    }

    private fun findLowestRank(ranks: Map<String, String>): String? {
        return ranks.entries
            .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
            .minByOrNull { it.first }
            ?.second
    }

    fun getNationTitle(nationLevel: Int): String? {
        val nationMap = byNationLevel[nationLevel.toString()] ?: return null
        return nationMap["title"] as? String
    }

    fun isOfficerLevelAvailable(
        officerLevel: Int,
        nationLevel: Int,
        officerRankKey: String? = null
    ): Boolean {
        if (officerLevel < 5) {
            return defaultRanks.containsKey(officerLevel.toString())
        }

        if (officerRankKey != null && specialNations.containsKey(officerRankKey)) {
            return specialNations[officerRankKey]?.containsKey(officerLevel.toString()) == true
        }

        val nationMap = byNationLevel[nationLevel.toString()] ?: return defaultRanks.containsKey(officerLevel.toString())
        val ranks = readStringStringMapOrNull(nationMap["ranks"]) ?: return defaultRanks.containsKey(officerLevel.toString())
        
        return ranks.containsKey(officerLevel.toString())
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

    private fun readNestedStringStringMap(raw: Any?): Map<String, Map<String, String>> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Map<String, String>>()
        raw.forEach { (key, value) ->
            if (key is String) {
                val nested = readStringStringMapOrNull(value)
                if (nested != null && nested.isNotEmpty()) {
                    result[key] = nested
                }
            }
        }
        return result
    }
}
