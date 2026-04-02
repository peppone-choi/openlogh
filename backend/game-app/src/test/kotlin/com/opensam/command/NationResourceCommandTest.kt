package com.opensam.command

import com.opensam.command.constraint.ConstraintResult
import com.opensam.command.nation.Nation휴식
import com.opensam.command.nation.che_감축
import com.opensam.command.nation.che_포상
import com.opensam.command.nation.che_국기변경
import com.opensam.command.nation.che_국호변경
import com.opensam.command.nation.che_몰수
import com.opensam.command.nation.che_물자원조
import com.opensam.command.nation.che_발령
import com.opensam.command.nation.che_백성동원
import com.opensam.command.nation.che_증축
import com.opensam.command.nation.che_천도
import com.opensam.engine.DiplomacyService
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.NationRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.OffsetDateTime
import kotlin.random.Random

class NationResourceCommandTest {

    private val fixedRng = Random(42)

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        officerLevel: Short = 20,
        gold: Int = 1000,
        rice: Int = 1000,
    ): General = General(
        id = id,
        worldId = 1,
        name = "테스트장수$id",
        nationId = nationId,
        cityId = cityId,
        officerLevel = officerLevel,
        gold = gold,
        rice = rice,
        leadership = 50,
        strength = 50,
        intel = 50,
        politics = 50,
        charm = 50,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        id: Long = 1,
        nationId: Long = 1,
        supplyState: Short = 1,
    ): City = City(
        id = id,
        worldId = 1,
        name = "테스트도시$id",
        nationId = nationId,
        supplyState = supplyState,
        agri = 500,
        agriMax = 1000,
        comm = 500,
        commMax = 1000,
        secu = 500,
        secuMax = 1000,
        def = 500,
        defMax = 1000,
        wall = 500,
        wallMax = 1000,
        pop = 10000,
        popMax = 50000,
        trust = 80,
    )

    private fun createNation(
        id: Long = 1,
        gold: Int = 200000,
        rice: Int = 200000,
        strategicCmdLimit: Short = 0,
    ): Nation = Nation(
        id = id,
        worldId = 1,
        name = "테스트국가$id",
        color = "#FF0000",
        gold = gold,
        rice = rice,
        level = 7,
        strategicCmdLimit = strategicCmdLimit,
        chiefGeneralId = 1,
    )

    private fun env(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
    ): CommandEnv = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        worldId = 1,
        realtimeMode = false,
    )

    // ========== Golden Value Parity Tests ==========

    @Test
    fun `Nation휴식 always passes condition and runs`() {
        val cmd = Nation휴식(createGeneral(officerLevel = 1), env())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun `Nation휴식 golden value -- no entity mutation`() {
        val general = createGeneral(officerLevel = 1, gold = 500, rice = 600)
        val beforeGold = general.gold
        val beforeRice = general.rice
        val cmd = Nation휴식(general, env())

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP: Nation휴식 does nothing -- no entity mutation
        assertEquals(beforeGold, general.gold, "gold should not change")
        assertEquals(beforeRice, general.rice, "rice should not change")
        assertEquals(0, result.logs.size, "no logs emitted")
    }

    @Test
    fun `포상 golden value -- rice reward entity diff and color-tagged log`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val nation = createNation(id = 1, gold = 50000, rice = 50000)
        val target = createGeneral(id = 2, nationId = 1, gold = 1000, rice = 2000)
        val cmd = che_포상(chief, env(), mapOf("isGold" to false, "amount" to 3000))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destGeneral = target

        val beforeNationRice = nation.rice
        val beforeTargetRice = target.rice
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.rice -= amount, target.rice += amount
        assertEquals(beforeNationRice - 3000, nation.rice, "nation rice reduced by 3000")
        assertEquals(beforeTargetRice + 3000, target.rice, "target rice increased by 3000")
        // Color-tagged log (per D-05)
        assertTrue(result.logs.any { it.contains("수여") && it.contains("쌀") })
    }

    @Test
    fun `포상 golden value -- gold reward entity diff`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val nation = createNation(id = 1, gold = 50000, rice = 50000)
        val target = createGeneral(id = 2, nationId = 1, gold = 1000, rice = 2000)
        val cmd = che_포상(chief, env(), mapOf("isGold" to true, "amount" to 5000))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destGeneral = target

        val beforeNationGold = nation.gold
        val beforeTargetGold = target.gold
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.gold -= amount, target.gold += amount
        assertEquals(beforeNationGold - 5000, nation.gold, "nation gold reduced by 5000")
        assertEquals(beforeTargetGold + 5000, target.gold, "target gold increased by 5000")
        assertTrue(result.logs.any { it.contains("수여") && it.contains("금") })
    }

    @Test
    fun `몰수 fails for non-chief and runs with resource transfer`() {
        val nonChief = createGeneral(officerLevel = 5, nationId = 1)
        val failCmd = che_몰수(nonChief, env(), mapOf("isGold" to true, "amount" to 500))
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1)
        failCmd.destGeneral = createGeneral(id = 2, nationId = 1, gold = 1500)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        val target = createGeneral(id = 2, nationId = 1, gold = 1500, rice = 2000)
        val cmd = che_몰수(chief, env(), mapOf("isGold" to true, "amount" to 500))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destGeneral = target

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(1000, target.gold)
        assertEquals(10500, nation.gold)
        assertEquals(1, target.betray.toInt())
        assertTrue(result.logs.any { it.contains("몰수") })
    }

    @Test
    fun `감축 fails for non-chief and runs with level down and capacity shrink`() {
        val nonChief = createGeneral(officerLevel = 5)
        val failCmd = che_감축(nonChief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        val city = createCity(nationId = 1)
        city.level = 2
        city.popMax = 50000
        val cmd = che_감축(chief, env())
        cmd.city = city
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(1, city.level.toInt())
        // PHP: reduces all 6 stats + maxes, refunds cost
        assertTrue(city.popMax < 50000) // popMax reduced
        assertTrue(city.agriMax < 1000) // agriMax reduced
        assertTrue(nation.gold > 10000) // cost refunded (gold increased)
        assertTrue(nation.rice > 10000) // cost refunded (rice increased)
    }

    @Test
    fun `증축 fails with low nation resource and runs with level up and max increase`() {
        // cost = develCost(100) * 500 + 60000 = 110000
        // basegold=0, baserice=2000 → need 110000 gold, 112000 rice
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_증축(chief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1, gold = 2000, rice = 3000)
        failCmd.destCity = createCity(id = 1, nationId = 1).also { it.level = 5 }
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1, gold = 200000, rice = 200000)
        val city = createCity(nationId = 1)
        city.level = 5
        city.popMax = 40000
        city.agriMax = 1000
        city.commMax = 1000
        city.secuMax = 1000
        city.defMax = 1000
        city.wallMax = 1000
        val cmd = che_증축(chief, env())
        cmd.city = city
        cmd.destCity = city
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(6, city.level.toInt())
        assertEquals(140000, city.popMax)
        assertEquals(3000, city.agriMax)
        assertEquals(3000, city.commMax)
        assertEquals(3000, city.secuMax)
        assertEquals(3000, city.defMax)
        assertEquals(3000, city.wallMax)
        assertEquals(90000, nation.gold)
        assertEquals(90000, nation.rice)
        assertTrue(result.logs.any { it.contains("증축") })
    }

    @Test
    fun `발령 fails without destination general and runs to move officer city`() {
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_발령(chief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1)
        failCmd.destCity = createCity(id = 2, nationId = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val target = createGeneral(id = 2, nationId = 1, cityId = 1)
        target.troopId = 99
        val cmd = che_발령(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.nation = createNation(id = 1)
        cmd.destGeneral = target
        cmd.destCity = createCity(id = 2, nationId = 1)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(2L, target.cityId)
        assertEquals(0L, target.troopId)
        assertTrue(result.logs.any { it.contains("발령") })
    }

    @Test
    fun `천도 fails for non-chief and runs to change nation capital`() {
        val nonChief = createGeneral(officerLevel = 5)
        val failCmd = che_천도(nonChief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.destCity = createCity(id = 2, nationId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        nation.capitalCityId = 1
        val cmd = che_천도(chief, env())
        cmd.city = createCity(id = 1, nationId = 1)
        cmd.destCity = createCity(id = 2, nationId = 1)
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(2L, nation.capitalCityId)
        assertEquals(8000, nation.gold)
        assertEquals(8000, nation.rice)
        assertTrue(result.logs.any { it.contains("천도") })
    }

    @Test
    fun `천도 back to original city succeeds after first move`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        nation.capitalCityId = 1
        val cityA = createCity(id = 1, nationId = 1)
        val cityB = createCity(id = 2, nationId = 1)

        // First move: A → B
        val cmd1 = che_천도(chief, env())
        cmd1.city = cityA
        cmd1.destCity = cityB
        cmd1.nation = nation
        val r1 = runBlocking { cmd1.run(fixedRng) }
        assertTrue(r1.success)
        assertEquals(2L, nation.capitalCityId)

        // Second move: B → A (back to original)
        val cmd2 = che_천도(chief, env())
        cmd2.city = cityB
        cmd2.destCity = cityA
        cmd2.nation = nation
        val check2 = cmd2.checkFullCondition()
        assertTrue(check2 is ConstraintResult.Pass, "Constraints should pass for return 천도: $check2")
        val r2 = runBlocking { cmd2.run(fixedRng) }
        assertTrue(r2.success, "Return 천도 should succeed")
        assertEquals(1L, nation.capitalCityId)
        assertEquals(6000, nation.gold)
        assertEquals(6000, nation.rice)
    }

    @Test
    fun `천도 to current capital fails with already-capital message`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        nation.capitalCityId = 1

        val cmd = che_천도(chief, env())
        cmd.city = createCity(id = 1, nationId = 1)
        cmd.destCity = createCity(id = 1, nationId = 1)
        cmd.nation = nation
        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(!result.success)
    }

    @Test
    fun `백성동원 fails when strategic command is blocked and runs with NPC saves`() {
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_백성동원(chief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.destCity = createCity(id = 2, nationId = 1)
        failCmd.nation = createNation(id = 1, strategicCmdLimit = 3)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val generalRepository = mock(GeneralRepository::class.java)
        val mockServices = CommandServices(
            generalRepository = generalRepository,
            cityRepository = mock(CityRepository::class.java),
            nationRepository = mock(NationRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destCity = createCity(id = 2, nationId = 1)
        destCity.pop = 25000

        val cmd = che_백성동원(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.destCity = destCity
        cmd.nation = nation
        cmd.services = mockServices

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP: def = GREATEST(def_max * 0.8, def), wall = GREATEST(wall_max * 0.8, wall)
        assertEquals(maxOf((destCity.defMax * 0.8).toInt(), 500), destCity.def)
        assertEquals(maxOf((destCity.wallMax * 0.8).toInt(), 500), destCity.wall)
        assertTrue(result.logs.any { it.contains("백성") })
    }

    @Test
    fun `물자원조 fails for same destination nation and runs with resource transfer`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val failCmd = che_물자원조(chief, env(), mapOf("goldAmount" to 500, "riceAmount" to 600))
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1, gold = 10000, rice = 10000)
        failCmd.destNation = createNation(id = 1, gold = 1000, rice = 1000)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        val allyNation = createNation(id = 2, gold = 1000, rice = 2000)
        val cmd = che_물자원조(chief, env(), mapOf("goldAmount" to 500, "riceAmount" to 600))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destNation = allyNation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9500, nation.gold)
        assertEquals(9400, nation.rice)
        assertEquals(1500, allyNation.gold)
        assertEquals(2600, allyNation.rice)
        assertTrue(result.logs.any { it.contains("지원") })
    }

    @Test
    fun `국기변경 fails for non-chief and runs to mutate nation color`() {
        val failCmd = che_국기변경(createGeneral(officerLevel = 5), env(), mapOf("colorType" to "blue"))
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1)
        val cmd = che_국기변경(createGeneral(officerLevel = 20), env(), mapOf("colorType" to "blue"))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals("blue", nation.color)
        assertTrue(result.logs.any { it.contains("국기") })
    }

    @Test
    fun `국호변경 fails for non-chief and runs to mutate nation name`() {
        val failCmd = che_국호변경(createGeneral(officerLevel = 5), env(), mapOf("nationName" to "신국호"))
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1)
        val cmd = che_국호변경(createGeneral(officerLevel = 20), env(), mapOf("nationName" to "신국호"))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals("신국호", nation.name)
        assertTrue(result.logs.any { it.contains("국호") })
    }

    // ========== Golden Value: Color-tagged Log Parity (D-05) ==========

    @Test
    fun `몰수 golden value -- entity diff and betray increment`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        val target = createGeneral(id = 2, nationId = 1, gold = 3000, rice = 4000)
        target.betray = 0
        val cmd = che_몰수(chief, env(), mapOf("isGold" to true, "amount" to 1500))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destGeneral = target

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: target.gold -= amount, nation.gold += amount, target.betray++
        assertEquals(1500, target.gold, "target gold reduced from 3000 by 1500")
        assertEquals(11500, nation.gold, "nation gold increased from 10000 by 1500")
        assertEquals(1, target.betray.toInt(), "betray incremented by 1")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("몰수") })
    }

    @Test
    fun `감축 golden value -- level down and capacity shrink with cost refund`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        val city = createCity(nationId = 1)
        city.level = 3
        city.popMax = 80000
        city.agriMax = 2000
        city.commMax = 2000
        city.secuMax = 2000
        city.defMax = 2000
        city.wallMax = 2000
        val cmd = che_감축(chief, env())
        cmd.city = city
        cmd.nation = nation

        val beforeLevel = city.level.toInt()
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: level decremented by 1
        assertEquals(beforeLevel - 1, city.level.toInt(), "city level decremented by 1")
        // PHP: all max values reduced, current values clamped
        assertTrue(city.popMax < 80000, "popMax reduced")
        assertTrue(city.agriMax < 2000, "agriMax reduced")
        assertTrue(city.commMax < 2000, "commMax reduced")
        // PHP: nation receives cost refund
        assertTrue(nation.gold > 10000, "gold refunded")
        assertTrue(nation.rice > 10000, "rice refunded")
    }

    @Test
    fun `증축 golden value -- level 5 to 6 exact values`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 200000, rice = 200000)
        val city = createCity(nationId = 1)
        city.level = 5
        city.popMax = 40000
        city.agriMax = 1000
        city.commMax = 1000
        city.secuMax = 1000
        city.defMax = 1000
        city.wallMax = 1000
        val cmd = che_증축(chief, env())
        cmd.city = city
        cmd.destCity = city
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: level 5 → 6
        assertEquals(6, city.level.toInt())
        // PHP: popMax += 100000, each max += 2000
        assertEquals(140000, city.popMax, "popMax = 40000 + 100000")
        assertEquals(3000, city.agriMax, "agriMax = 1000 + 2000")
        assertEquals(3000, city.commMax, "commMax = 1000 + 2000")
        assertEquals(3000, city.secuMax, "secuMax = 1000 + 2000")
        assertEquals(3000, city.defMax, "defMax = 1000 + 2000")
        assertEquals(3000, city.wallMax, "wallMax = 1000 + 2000")
        // PHP: cost = develCost(100) * 500 + 60000 = 110000
        assertEquals(90000, nation.gold, "gold = 200000 - 110000")
        assertEquals(90000, nation.rice, "rice = 200000 - 110000")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("증축") })
    }

    @Test
    fun `발령 golden value -- officerCity change and troopId reset`() {
        val chief = createGeneral(officerLevel = 20)
        val target = createGeneral(id = 2, nationId = 1, cityId = 1)
        target.troopId = 99
        val destCity = createCity(id = 3, nationId = 1)
        val cmd = che_발령(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.nation = createNation(id = 1)
        cmd.destGeneral = target
        cmd.destCity = destCity

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: target.cityId = destCity.id, target.troopId = 0
        assertEquals(3L, target.cityId, "target moved to dest city")
        assertEquals(0L, target.troopId, "troopId reset to 0")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("발령") })
    }

    @Test
    fun `천도 golden value -- capital change and gold rice cost`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 20000, rice = 20000)
        nation.capitalCityId = 1
        val cmd = che_천도(chief, env())
        cmd.city = createCity(id = 1, nationId = 1)
        cmd.destCity = createCity(id = 5, nationId = 1)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.capitalCityId = destCity.id, cost = 2000 gold + 2000 rice
        assertEquals(5L, nation.capitalCityId, "capital changed to dest city")
        assertEquals(18000, nation.gold, "gold = 20000 - 2000")
        assertEquals(18000, nation.rice, "rice = 20000 - 2000")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("천도") })
    }

    @Test
    fun `백성동원 golden value -- population halved and def wall clamped`() {
        val chief = createGeneral(officerLevel = 20)
        val generalRepository = mock(GeneralRepository::class.java)
        val mockServices = CommandServices(
            generalRepository = generalRepository,
            cityRepository = mock(CityRepository::class.java),
            nationRepository = mock(NationRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destCity = createCity(id = 2, nationId = 1)
        destCity.pop = 40000
        destCity.def = 200
        destCity.wall = 300
        destCity.defMax = 1000
        destCity.wallMax = 1000

        val cmd = che_백성동원(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.destCity = destCity
        cmd.nation = nation
        cmd.services = mockServices

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP: def = GREATEST(def_max * 0.8, def) → max(800, 200) = 800
        assertEquals(800, destCity.def, "def clamped to defMax * 0.8")
        // PHP: wall = GREATEST(wall_max * 0.8, wall) → max(800, 300) = 800
        assertEquals(800, destCity.wall, "wall clamped to wallMax * 0.8")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("백성") })
    }

    @Test
    fun `물자원조 golden value -- exact resource transfer both directions`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val nation = createNation(id = 1, gold = 50000, rice = 30000)
        val allyNation = createNation(id = 2, gold = 5000, rice = 8000)
        val cmd = che_물자원조(chief, env(), mapOf("goldAmount" to 12000, "riceAmount" to 7000))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destNation = allyNation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: src -= amount, dest += amount
        assertEquals(38000, nation.gold, "nation gold = 50000 - 12000")
        assertEquals(23000, nation.rice, "nation rice = 30000 - 7000")
        assertEquals(17000, allyNation.gold, "ally gold = 5000 + 12000")
        assertEquals(15000, allyNation.rice, "ally rice = 8000 + 7000")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("지원") })
    }

    @Test
    fun `국기변경 golden value -- exact color mutation`() {
        val nation = createNation(id = 1)
        assertEquals("#FF0000", nation.color, "initial color is red")
        val cmd = che_국기변경(createGeneral(officerLevel = 20), env(), mapOf("colorType" to "#00FF00"))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.color = arg.colorType
        assertEquals("#00FF00", nation.color, "color mutated to green")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("국기") })
    }

    @Test
    fun `국호변경 golden value -- exact name mutation`() {
        val nation = createNation(id = 1)
        assertEquals("테스트국가1", nation.name, "initial name")
        val cmd = che_국호변경(createGeneral(officerLevel = 20), env(), mapOf("nationName" to "대한제국"))
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.name = arg.nationName
        assertEquals("대한제국", nation.name, "name mutated to 대한제국")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("국호") })
    }
}
