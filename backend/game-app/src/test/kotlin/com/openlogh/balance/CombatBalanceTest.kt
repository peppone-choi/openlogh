package com.openlogh.balance

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail

/**
 * 전투 밸런스 검증 테스트 (TEST-01)
 *
 * ship_stats_empire.json + ship_stats_alliance.json을 직접 파싱하여
 * 88개 서브타입 모두 DPS > 0, HP > 0이고
 * 동일 함종 내에서 레벨 I→VIII로 갈수록 DPS가 단조증가하는지 검증한다.
 *
 * 밸런스 이슈 발견 시 이 블록에 @Disabled + 코멘트로 문서화:
 * (현재 발견된 이슈 없음)
 */
class CombatBalanceTest {

    private val mapper = jacksonObjectMapper()

    // JSON 구조: { "shipClasses": [ { "classId": "...", "subtypes": [ { "subtype": "I", "beam": {...}, "gun": {...}, "armor": {...}, "shield": {...} } ] } ] }
    data class SubtypeStat(
        val classId: String,
        val subtype: String,
        val dps: Int,   // beamDamage + gunDamage
        val hp: Int,    // armor.front + shield.capacity
    )

    @Suppress("UNCHECKED_CAST")
    private fun loadSubtypes(resourcePath: String): List<SubtypeStat> {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: fail<Nothing>("Resource not found: $resourcePath")
        val root: Map<String, Any> = mapper.readValue(stream, Map::class.java) as Map<String, Any>
        val shipClasses = root["shipClasses"] as? List<*>
            ?: fail<Nothing>("Missing shipClasses in $resourcePath")

        val result = mutableListOf<SubtypeStat>()
        for (classEntry in shipClasses) {
            val classMap = classEntry as? Map<*, *> ?: continue
            val classId = classMap["classId"] as? String ?: continue
            val subtypes = classMap["subtypes"] as? List<*> ?: continue

            for (subtypeEntry in subtypes) {
                val st = subtypeEntry as? Map<*, *> ?: continue
                val subtypeId = st["subtype"] as? String ?: continue

                val beamMap = st["beam"] as? Map<*, *>
                val gunMap  = st["gun"]  as? Map<*, *>
                val armorMap  = st["armor"]  as? Map<*, *>
                val shieldMap = st["shield"] as? Map<*, *>

                val beamDmg = (beamMap?.get("damage") as? Number)?.toInt() ?: 0
                val gunDmg  = (gunMap?.get("damage")  as? Number)?.toInt() ?: 0
                val armorFront = (armorMap?.get("front") as? Number)?.toInt() ?: 0
                val shieldCap  = (shieldMap?.get("capacity") as? Number)?.toInt() ?: 0

                result += SubtypeStat(
                    classId = classId,
                    subtype = subtypeId,
                    dps     = beamDmg + gunDmg,
                    hp      = armorFront + shieldCap,
                )
            }
        }
        return result
    }

    // 로마 숫자 혹은 순서 있는 서브타입을 비교 가능한 정수로 변환
    private fun subtypeOrder(subtype: String): Int = when (subtype.uppercase()) {
        "I"    -> 1
        "II"   -> 2
        "III"  -> 3
        "IV"   -> 4
        "V"    -> 5
        "VI"   -> 6
        "VII"  -> 7
        "VIII" -> 8
        else   -> 99 // 이름형 서브타입(fast, strike 등)은 단조증가 검증 제외
    }

    // 비전투 함종 — 민간함/수송함/병원선은 무장이 없어 DPS=0이 정상
    private val nonCombatClasses = setOf("civilian", "transport", "hospital")

    @Test
    fun `전투 함종의 DPS와 HP가 0보다 크다`() {
        val empireStats   = loadSubtypes("data/ship_stats_empire.json")
        val allianceStats = loadSubtypes("data/ship_stats_alliance.json")
        // 비전투 함종 제외
        val all = (empireStats + allianceStats).filter { it.classId !in nonCombatClasses }

        val zeroDps = all.filter { it.dps <= 0 }
        val zeroHp  = all.filter { it.hp  <= 0 }

        assertThat(zeroDps)
            .withFailMessage("DPS=0인 전투 서브타입 발견: %s", zeroDps.map { "${it.classId}_${it.subtype}" })
            .isEmpty()

        assertThat(zeroHp)
            .withFailMessage("HP=0인 전투 서브타입 발견: %s", zeroHp.map { "${it.classId}_${it.subtype}" })
            .isEmpty()
    }

    @Test
    fun `전체 서브타입 수가 80개 이상이다`() {
        val empireStats   = loadSubtypes("data/ship_stats_empire.json")
        val allianceStats = loadSubtypes("data/ship_stats_alliance.json")
        val total = empireStats.size + allianceStats.size

        assertThat(total)
            .withFailMessage("서브타입 총 수: $total (기대값 >= 80)")
            .isGreaterThanOrEqualTo(80)
    }

    @Test
    fun `동일 함종 내 레벨 순 DPS 단조증가`() {
        listOf("data/ship_stats_empire.json", "data/ship_stats_alliance.json").forEach { path ->
            val subtypes = loadSubtypes(path).filter { it.classId !in nonCombatClasses }
            // 함종별로 그룹화
            val byClass = subtypes.groupBy { it.classId }

            for ((classId, entries) in byClass) {
                // 이름형(named) 서브타입 제외 (fast, strike 등)
                val numbered = entries
                    .filter { subtypeOrder(it.subtype) < 90 }
                    .sortedBy { subtypeOrder(it.subtype) }

                if (numbered.size < 2) continue // 레벨이 1개 이하면 단조증가 검증 불가

                for (i in 0 until numbered.size - 1) {
                    val lower  = numbered[i]
                    val higher = numbered[i + 1]
                    assertThat(higher.dps)
                        .withFailMessage(
                            "[$path] $classId: ${lower.subtype}(dps=${lower.dps}) → ${higher.subtype}(dps=${higher.dps}) — DPS가 증가하지 않음"
                        )
                        .isGreaterThanOrEqualTo(lower.dps)
                }
            }
        }
    }

    @Test
    fun `제국 vs 동맹 battleship 동일 서브타입 DPS 격차 30퍼센트 이내`() {
        val empireStats   = loadSubtypes("data/ship_stats_empire.json")
        val allianceStats = loadSubtypes("data/ship_stats_alliance.json")

        val empireBattleships   = empireStats.filter   { it.classId == "battleship" }.associateBy { it.subtype }
        val allianceBattleships = allianceStats.filter { it.classId == "battleship" }.associateBy { it.subtype }

        val commonSubtypes = empireBattleships.keys.intersect(allianceBattleships.keys)
        assertThat(commonSubtypes)
            .withFailMessage("제국/동맹 공통 battleship 서브타입이 없음")
            .isNotEmpty()

        for (sub in commonSubtypes) {
            val empireDps   = empireBattleships[sub]!!.dps
            val allianceDps = allianceBattleships[sub]!!.dps
            val maxDps = maxOf(empireDps, allianceDps)
            val diff = kotlin.math.abs(empireDps - allianceDps).toDouble()
            val diffRatio = if (maxDps > 0) diff / maxDps else 0.0

            assertThat(diffRatio)
                .withFailMessage(
                    "battleship $sub: 제국DPS=$empireDps, 동맹DPS=$allianceDps, 격차=${String.format("%.1f", diffRatio * 100)}% (한계 30%%)"
                )
                .isLessThanOrEqualTo(0.30)
        }
    }
}
