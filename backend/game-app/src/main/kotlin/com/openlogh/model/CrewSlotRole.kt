package com.openlogh.model

/**
 * Crew slot roles for unit organization.
 * Based on gin7 manual: fleet has 10 slots, patrol/transport have 3, ground/garrison have 1, solo has 0.
 */
enum class CrewSlotRole(val displayName: String, val displayNameKo: String) {
    COMMANDER("Commander", "사령관"),
    VICE_COMMANDER("Vice Commander", "부사령관"),
    CHIEF_OF_STAFF("Chief of Staff", "참모장"),
    STAFF_OFFICER_1("Staff Officer 1", "참모1"),
    STAFF_OFFICER_2("Staff Officer 2", "참모2"),
    STAFF_OFFICER_3("Staff Officer 3", "참모3"),
    STAFF_OFFICER_4("Staff Officer 4", "참모4"),
    STAFF_OFFICER_5("Staff Officer 5", "참모5"),
    STAFF_OFFICER_6("Staff Officer 6", "참모6"),
    ADJUTANT("Adjutant", "부관"),
}
