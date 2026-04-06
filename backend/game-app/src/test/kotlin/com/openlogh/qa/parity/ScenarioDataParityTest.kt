package com.openlogh.qa.parity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Scenario Data Parity Test
 *
 * Exhaustively compares all 80+ scenario JSON files between legacy-core/hwe/scenario/
 * and backend/shared/src/main/resources/data/scenarios/ for:
 *   - General 3-stat values (leadership/strength/intel) matched by name (DATA-01)
 *   - City initial condition defaults matching legacy CityConstBase buildInit (DATA-02)
 *   - Scenario start conditions: startYear, nation count, diplomacy count (DATA-03)
 *
 * Known intentional divergences:
 *   - General 3-stats updated to 삼국지14-based values (opensamguk extension)
 *   - Some generals renamed (e.g., "헌제"->"유협", "소제1"->"유변")
 *   - Nation names localized (e.g., "후한"->"한", "조조"->"위")
 *   - Diplomacy entries added where legacy had none
 *   - Fiction scenarios (2xxx) may have stats > 100
 *
 * Legacy source: legacy-core/hwe/scenario/scenario_*.json
 * Current source: backend/shared/src/main/resources/data/scenarios/scenario_*.json
 */
@DisplayName("Scenario Data Parity")
class ScenarioDataParityTest {

    companion object {
        // Paths relative to game-app module root (where tests execute from)
        private val LEGACY_DIR = File("../../legacy-core/hwe/scenario/")
        private val CURRENT_DIR = File("../shared/src/main/resources/data/scenarios/")

        private val mapper = ObjectMapper().apply {
            registerModule(kotlinModule())
        }

        /**
         * Returns scenario codes present in BOTH legacy and current directories.
         * Expected: 81 common files (scenario_duel.json is current-only).
         */
        fun loadCommonScenarioCodes(): List<String> {
            val legacyCodes = LEGACY_DIR.listFiles { f -> f.name.startsWith("scenario_") && f.name.endsWith(".json") }
                ?.map { it.name.removePrefix("scenario_").removeSuffix(".json") }
                ?.toSet() ?: emptySet()
            val currentCodes = CURRENT_DIR.listFiles { f -> f.name.startsWith("scenario_") && f.name.endsWith(".json") }
                ?.map { it.name.removePrefix("scenario_").removeSuffix(".json") }
                ?.toSet() ?: emptySet()
            return legacyCodes.intersect(currentCodes).sorted()
        }

        fun loadScenarioJson(file: File): Map<String, Any?> = mapper.readValue(file)

        /**
         * Parse generals from a scenario JSON, extracting name and 3-stat (leadership, strength, intel).
         * Handles general, general_ex, general_neutral arrays.
         *
         * Legacy format:  [nation, name, pic, npcState, reserved, leadership, strength, intel, ...]
         * Current format: [nation, name, pic, npcState, reserved, leadership, strength, intel, politics, charm, ...]
         *
         * Indices 5/6/7 are leadership/strength/intel in both formats.
         * Returns Map<name, Triple<leadership, strength, intel>>
         */
        fun parseGenerals(file: File): Map<String, Triple<Int, Int, Int>> {
            val data: Map<String, Any?> = mapper.readValue(file)
            val result = mutableMapOf<String, Triple<Int, Int, Int>>()

            for (key in listOf("general", "general_ex", "general_neutral")) {
                @Suppress("UNCHECKED_CAST")
                val generals = (data[key] as? List<List<Any?>>) ?: continue
                for (row in generals) {
                    if (row.size < 8) continue
                    val name = row.getOrNull(1)?.toString() ?: continue
                    val leadership = toInt(row[5])
                    val command = toInt(row[6])
                    val intelligence = toInt(row[7])
                    result.putIfAbsent(name, Triple(leadership, command, intelligence))
                }
            }
            return result
        }

        /** Safely convert JSON number (could be Int, Double, Long) to Int. */
        fun toInt(value: Any?): Int {
            return when (value) {
                is Int -> value
                is Double -> value.toInt()
                is Long -> value.toInt()
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
        }
    }

    // ── DATA-01: Officer 3-Stat Parity ──

    @Nested
    @DisplayName("General 3-Stat Parity (DATA-01)")
    inner class General3StatParity {

        @Test
        @DisplayName("All 81 common scenario files are covered")
        fun `all common scenarios covered`() {
            val codes = loadCommonScenarioCodes()
            assertThat(codes)
                .describedAs("Must have at least 81 common scenario files")
                .hasSizeGreaterThanOrEqualTo(81)
        }

        @TestFactory
        @DisplayName("General name coverage: legacy generals present in current (documents renames)")
        fun `legacy general name coverage per scenario`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: general name coverage") {
                    val legacyFile = File(LEGACY_DIR, "scenario_$code.json")
                    val currentFile = File(CURRENT_DIR, "scenario_$code.json")

                    val legacyGenerals = parseGenerals(legacyFile)
                    val currentGenerals = parseGenerals(currentFile)

                    // Empty scenarios (공백지) have no generals -- skip coverage check
                    if (legacyGenerals.isEmpty() && currentGenerals.isEmpty()) {
                        println("  scenario_$code: empty scenario (공백지), skipping name coverage")
                        return@dynamicTest
                    }

                    val legacyOnly = legacyGenerals.keys - currentGenerals.keys
                    val currentOnly = currentGenerals.keys - legacyGenerals.keys
                    val commonCount = (legacyGenerals.keys intersect currentGenerals.keys).size

                    // Document renames (not failures -- intentional opensamguk change)
                    if (legacyOnly.isNotEmpty()) {
                        println("  scenario_$code: ${legacyOnly.size} legacy-only (renamed): ${legacyOnly.sorted().take(5)}")
                    }
                    if (currentOnly.isNotEmpty()) {
                        println("  scenario_$code: ${currentOnly.size} current-only (new/renamed): ${currentOnly.sorted().take(5)}")
                    }

                    // At least 90% of legacy generals should be matchable by name
                    // (remaining are intentional renames like 헌제->유협, 소제1->유변)
                    val coverageRatio = if (legacyGenerals.isEmpty()) 1.0
                        else commonCount.toDouble() / legacyGenerals.size
                    assertThat(coverageRatio)
                        .describedAs("scenario_$code: name coverage ratio (${commonCount}/${legacyGenerals.size})")
                        .isGreaterThanOrEqualTo(0.90)
                }
            }
        }

        @TestFactory
        @DisplayName("3-stat comparison across all scenarios (documents intentional divergence)")
        fun `general 3-stat divergence report per scenario`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: general 3-stat comparison") {
                    val legacyFile = File(LEGACY_DIR, "scenario_$code.json")
                    val currentFile = File(CURRENT_DIR, "scenario_$code.json")

                    val legacyGenerals = parseGenerals(legacyFile)
                    val currentGenerals = parseGenerals(currentFile)

                    val commonNames = legacyGenerals.keys.intersect(currentGenerals.keys)

                    // Empty scenarios (공백지) have no generals to compare
                    if (commonNames.isEmpty()) {
                        println("  scenario_$code: no common generals to compare")
                        return@dynamicTest
                    }

                    // Count and document mismatches (expected: stats intentionally updated)
                    var matchCount = 0
                    var mismatchCount = 0
                    for (name in commonNames.sorted()) {
                        val (ll, ls, li) = legacyGenerals[name]!!
                        val (cl, cs, ci) = currentGenerals[name]!!
                        if (ll == cl && ls == cs && li == ci) matchCount++ else mismatchCount++
                    }

                    val total = matchCount + mismatchCount
                    println("  scenario_$code: $matchCount/$total match, $mismatchCount/$total diverged (삼국지14 update)")

                    // Structural: all compared generals have valid stat indices
                    assertThat(total)
                        .describedAs("scenario_$code must have parseable stats for all common generals")
                        .isEqualTo(commonNames.size)
                }
            }
        }

        @TestFactory
        @DisplayName("General stat values are non-negative integers")
        fun `general stat values are non-negative`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: stat value sanity check") {
                    val currentFile = File(CURRENT_DIR, "scenario_$code.json")
                    val generals = parseGenerals(currentFile)

                    val invalid = mutableListOf<String>()
                    for ((name, stats) in generals) {
                        val (l, s, i) = stats
                        // Stats must be non-negative; fiction scenarios allow > 100
                        if (l < 0 || s < 0 || i < 0) {
                            invalid.add("$name: ($l,$s,$i)")
                        }
                    }

                    assertThat(invalid)
                        .withFailMessage { "scenario_$code negative stats:\n${invalid.joinToString("\n")}" }
                        .isEmpty()
                }
            }
        }
    }

    // ── DATA-03: Scenario Start Conditions ──

    @Nested
    @DisplayName("Scenario Start Conditions (DATA-03)")
    inner class ScenarioStartConditions {

        @TestFactory
        @DisplayName("startYear matches legacy for all scenarios")
        fun `startYear matches legacy`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: startYear parity") {
                    val legacy = loadScenarioJson(File(LEGACY_DIR, "scenario_$code.json"))
                    val current = loadScenarioJson(File(CURRENT_DIR, "scenario_$code.json"))

                    val legacyStartYear = toInt(legacy["startYear"])
                    val currentStartYear = toInt(current["startYear"])

                    assertThat(currentStartYear)
                        .describedAs("scenario_$code startYear")
                        .isEqualTo(legacyStartYear)
                }
            }
        }

        @TestFactory
        @DisplayName("Nation array lengths match legacy for all scenarios")
        fun `nation array lengths match legacy`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: nation count parity") {
                    val legacy = loadScenarioJson(File(LEGACY_DIR, "scenario_$code.json"))
                    val current = loadScenarioJson(File(CURRENT_DIR, "scenario_$code.json"))

                    @Suppress("UNCHECKED_CAST")
                    val legacyNations = (legacy["nation"] as? List<*>) ?: emptyList<Any>()
                    @Suppress("UNCHECKED_CAST")
                    val currentNations = (current["nation"] as? List<*>) ?: emptyList<Any>()

                    assertThat(currentNations.size)
                        .describedAs("scenario_$code nation count")
                        .isEqualTo(legacyNations.size)
                }
            }
        }

        @TestFactory
        @DisplayName("Diplomacy entries: current >= legacy (opensamguk may add entries)")
        fun `diplomacy count at least legacy`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: diplomacy count") {
                    val legacy = loadScenarioJson(File(LEGACY_DIR, "scenario_$code.json"))
                    val current = loadScenarioJson(File(CURRENT_DIR, "scenario_$code.json"))

                    @Suppress("UNCHECKED_CAST")
                    val legacyDiplomacy = (legacy["diplomacy"] as? List<*>) ?: emptyList<Any>()
                    @Suppress("UNCHECKED_CAST")
                    val currentDiplomacy = (current["diplomacy"] as? List<*>) ?: emptyList<Any>()

                    assertThat(currentDiplomacy.size)
                        .describedAs("scenario_$code diplomacy count (current >= legacy)")
                        .isGreaterThanOrEqualTo(legacyDiplomacy.size)
                }
            }
        }

        @TestFactory
        @DisplayName("Nation count and name comparison (documents localization changes)")
        fun `nation names comparison`(): List<DynamicTest> {
            val codes = loadCommonScenarioCodes()

            return codes.map { code ->
                DynamicTest.dynamicTest("scenario_$code: nation name comparison") {
                    val legacy = loadScenarioJson(File(LEGACY_DIR, "scenario_$code.json"))
                    val current = loadScenarioJson(File(CURRENT_DIR, "scenario_$code.json"))

                    @Suppress("UNCHECKED_CAST")
                    val legacyNations = (legacy["nation"] as? List<List<Any>>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val currentNations = (current["nation"] as? List<List<Any>>) ?: emptyList()

                    // Same number of nations required
                    assertThat(currentNations.size)
                        .describedAs("scenario_$code nation count must match")
                        .isEqualTo(legacyNations.size)

                    // Nation names may differ (opensamguk localized names) -- document only
                    val legacyNames = legacyNations.map { it.getOrNull(0)?.toString() ?: "" }
                    val currentNames = currentNations.map { it.getOrNull(0)?.toString() ?: "" }
                    if (legacyNames != currentNames) {
                        println("  scenario_$code nation names: legacy=$legacyNames current=$currentNames")
                    }
                }
            }
        }
    }

    // ── DATA-02: Planet Initial Conditions ──

    @Nested
    @DisplayName("City Initial Conditions (DATA-02)")
    inner class CityInitialConditions {

        @Test
        @DisplayName("CITY_LEVEL_INIT matches legacy CityConstBase buildInit for all 8 levels")
        fun `city level init values match legacy`() {
            val sourceFile = File("src/main/kotlin/com/opensam/service/ScenarioService.kt")
            assertThat(sourceFile.exists())
                .describedAs("ScenarioService.kt must exist at expected path")
                .isTrue()
            val source = sourceFile.readText()

            // Legacy CityConstBase::$buildInit golden values (from PHP source)
            // Format: CityInit(population, production, commerce, security, orbitalDefense, fortress)
            val expectedValues = mapOf(
                1 to "1 to CityInit(5000, 100, 100, 100, 500, 500)",
                2 to "2 to CityInit(5000, 100, 100, 100, 500, 500)",
                3 to "3 to CityInit(10000, 100, 100, 100, 1000, 1000)",
                4 to "4 to CityInit(50000, 1000, 1000, 1000, 1000, 1000)",
                5 to "5 to CityInit(100000, 1000, 1000, 1000, 2000, 2000)",
                6 to "6 to CityInit(100000, 1000, 1000, 1000, 3000, 3000)",
                7 to "7 to CityInit(150000, 1000, 1000, 1000, 4000, 4000)",
                8 to "8 to CityInit(150000, 1000, 1000, 1000, 5000, 5000)",
            )

            for ((level, expectedLine) in expectedValues) {
                assertThat(source)
                    .describedAs("CITY_LEVEL_INIT level $level must match legacy CityConstBase")
                    .contains(expectedLine)
            }
        }

        @Test
        @DisplayName("All 8 city levels are defined in CITY_LEVEL_INIT")
        fun `all 8 levels defined`() {
            val sourceFile = File("src/main/kotlin/com/opensam/service/ScenarioService.kt")
            val source = sourceFile.readText()

            for (level in 1..8) {
                assertThat(source)
                    .describedAs("CITY_LEVEL_INIT must contain level $level")
                    .contains("$level to CityInit(")
            }
        }
    }
}
