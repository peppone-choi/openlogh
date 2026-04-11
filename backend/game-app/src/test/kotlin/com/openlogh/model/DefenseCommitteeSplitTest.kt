package com.openlogh.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 24-17 (gap D1, gin7 manual p61 同盟軍組織構成表):
 *
 * 매뉴얼은 国防委員会 산하 11개 부장직을 각각 별도의 직무권한카드로 나열한다.
 * v2.4 까지는 `DEFENSE_DEPT_CHIEF` 한 장에 maxHolders=11로 뭉쳐져 있었는데,
 * 이를 11장으로 분리해 매뉴얼 별표와 1:1 정렬시키고 부장별로 서로 다른
 * CommandGroup 권한을 부여한다.
 *
 * 이 테스트는:
 * 1. 분리 후 11 카드가 실제로 존재하는지,
 * 2. 소속/진영/계급 제약이 정확한지,
 * 3. 원래 DEFENSE_DEPT_CHIEF 열거값이 더 이상 enum에 존재하지 않는지,
 * 4. 각 부장의 commandGroups가 업무 성격과 일치하는지 (예: 情報部長→INTELLIGENCE),
 * 5. PositionCard.entries 총수가 예상대로 증가했는지(76 → 86).
 */
class DefenseCommitteeSplitTest {

    private val splitCards: List<PositionCard> = listOf(
        PositionCard.DEFENSE_INVESTIGATION_DEPT,
        PositionCard.DEFENSE_STRATEGY_DEPT,
        PositionCard.DEFENSE_HR_DEPT,
        PositionCard.DEFENSE_COUNTERINTEL_DEPT,
        PositionCard.DEFENSE_INTEL_DEPT,
        PositionCard.DEFENSE_COMMUNICATIONS_DEPT,
        PositionCard.DEFENSE_EQUIPMENT_DEPT,
        PositionCard.DEFENSE_FACILITIES_DEPT,
        PositionCard.DEFENSE_ACCOUNTING_DEPT,
        PositionCard.DEFENSE_EDUCATION_DEPT,
        PositionCard.DEFENSE_HEALTH_DEPT,
    )

    @Test
    fun `11 defense committee department chief cards exist`() {
        assertEquals(11, splitCards.size)
        splitCards.forEach { card ->
            assertNotNull(card)
            assertEquals("alliance", card.factionType, "${card.code} must be alliance-scoped")
            assertEquals("국방위원회", card.department, "${card.code} must belong to 국방위원회")
            assertEquals(1, card.maxHolders, "${card.code} must be a single-holder chair after the split")
            assertEquals(7, card.minRank, "${card.code} must require ≥ tier 7 (Rear Admiral)")
            assertEquals(10, card.maxRank, "${card.code} must have no upper rank cap")
        }
    }

    @Test
    fun `legacy DEFENSE_DEPT_CHIEF is removed from the enum`() {
        // Enum 이름 기반 lookup으로 역참조해서 소멸 여부를 증명.
        val legacy = PositionCard.entries.firstOrNull { it.name == "DEFENSE_DEPT_CHIEF" }
        assertNull(legacy, "legacy DEFENSE_DEPT_CHIEF must be removed after Phase 24-17 split")
    }

    @Test
    fun `HR department chief holds PERSONNEL authority`() {
        val hr = PositionCard.DEFENSE_HR_DEPT
        assertTrue(CommandGroup.PERSONNEL in hr.commandGroups)
    }

    @Test
    fun `investigation department chief holds PERSONNEL and INTELLIGENCE authority`() {
        val inv = PositionCard.DEFENSE_INVESTIGATION_DEPT
        assertTrue(CommandGroup.PERSONNEL in inv.commandGroups)
        assertTrue(CommandGroup.INTELLIGENCE in inv.commandGroups)
    }

    @Test
    fun `intelligence and counter-intelligence department chiefs hold INTELLIGENCE only`() {
        val intel = PositionCard.DEFENSE_INTEL_DEPT
        val counter = PositionCard.DEFENSE_COUNTERINTEL_DEPT
        assertEquals(setOf(CommandGroup.INTELLIGENCE), intel.commandGroups)
        assertEquals(setOf(CommandGroup.INTELLIGENCE), counter.commandGroups)
    }

    @Test
    fun `strategy department chief holds COMMAND authority`() {
        assertTrue(CommandGroup.COMMAND in PositionCard.DEFENSE_STRATEGY_DEPT.commandGroups)
    }

    @Test
    fun `logistics-flavored department chiefs hold LOGISTICS authority`() {
        val logisticsDeps = listOf(
            PositionCard.DEFENSE_COMMUNICATIONS_DEPT,
            PositionCard.DEFENSE_EQUIPMENT_DEPT,
            PositionCard.DEFENSE_FACILITIES_DEPT,
            PositionCard.DEFENSE_ACCOUNTING_DEPT,
            PositionCard.DEFENSE_HEALTH_DEPT,
        )
        logisticsDeps.forEach { card ->
            assertTrue(
                CommandGroup.LOGISTICS in card.commandGroups,
                "${card.code} must grant LOGISTICS authority per manual p61"
            )
        }
    }

    @Test
    fun `education department chief holds PERSONNEL authority`() {
        assertTrue(CommandGroup.PERSONNEL in PositionCard.DEFENSE_EDUCATION_DEPT.commandGroups)
    }

    @Test
    fun `PositionCard total count reflects the split`() {
        // Phase 24-16 baseline: 82 cards; Phase 24-17 removes 1 (DEFENSE_DEPT_CHIEF)
        // and adds 11 (department chairs) → 92 cards total. Brings us one shy of
        // the gap-analysis target of ~93 — the remaining delta is documented in
        // gin7-manual-complete-gap.analysis.md §5.4 and will be closed case-by-case
        // when the final manual cross-walk identifies each remaining card.
        assertEquals(92, PositionCard.entries.size,
            "Position card total must be 92 after Phase 24-17 split")
    }

    @Test
    fun `all 11 department chiefs have unique codes`() {
        val codes = splitCards.map { it.code }.toSet()
        assertEquals(11, codes.size, "All 11 split codes must be unique")
    }
}
