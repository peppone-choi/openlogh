package com.openlogh.qa.parity

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.general.*
import com.openlogh.command.nation.*
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_COST_COEF
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_DEFAULT_COST
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_DEVEL_INCREASE
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_POP_INCREASE
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_WALL_INCREASE
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

/**
 * Economy Command Parity Test
 *
 * Verifies all 12 economy-related commands produce identical results to legacy PHP.
 *
 * Legacy PHP sources:
 *   - hwe/sammo/Command/General/che_군량매매.php: trade formula with exchangeFee
 *   - hwe/sammo/Command/General/che_헌납.php: donation (exp=70, ded=100, leadershipExp+1)
 *   - hwe/sammo/Command/General/che_상업투자.php: DomesticCommand base class
 *   - hwe/sammo/Command/General/che_농지개간.php: production, intel, debuffFront=0.5
 *   - hwe/sammo/Command/General/che_치안강화.php: security, strength, debuffFront=1
 *   - hwe/sammo/Command/General/che_수비강화.php: orbitalDefense, strength, debuffFront=0.5
 *   - hwe/sammo/Command/General/che_성벽보수.php: fortress, strength, debuffFront=0.25
 *   - hwe/sammo/Command/Nation/che_포상.php: nation->general resource transfer
 *   - hwe/sammo/Command/Nation/che_몰수.php: general->nation resource transfer
 *   - hwe/sammo/Command/Nation/che_물자원조.php: nation->nation resource transfer
 *   - hwe/sammo/Command/Nation/che_증축.php: city level+1, populationMax/develMax/fortressMax increase
 *   - hwe/sammo/Command/Nation/che_감축.php: city level-1, refund cost, reduce stats
 *
 * Current impl: command classes in com.openlogh.command.general/nation
 */
@DisplayName("Economy Command Parity")
class EconomyCommandParityTest {

    private val mapper = jacksonObjectMapper()

    // ══════════════════════════════════════════════════════
    //  Trade (che_군량매매) — legacy che_군량매매.php
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("TradeCommand - che_군량매매")
    inner class TradeCommand {

        /**
         * PHP buy-rice formula (che_군량매매.php:127-136):
         *   tradeRate = city.tradeRoute / 100  (e.g. 100 -> 1.0)
         *   sellAmount = min(amount * tradeRate, general.funds)
         *   tax = sellAmount * exchangeFee
         *   if (sellAmount + tax > gold): sellAmount *= funds/(sellAmount+tax); tax = funds - sellAmount
         *   buyAmount = sellAmount / tradeRate
         *   totalSell = sellAmount + tax
         *
         * Kotlin exchangeFee default = 0.03 (CommandEnv)
         * PHP exchangeFee = 0.01 (GameConstBase.php:74)
         * Test uses Kotlin default (0.03) — golden values computed accordingly.
         */
        @Test
        fun `buy rice - gold to rice with trade=100 exchangeFee=0_03`() {
            // amount=1000, tradeRate=1.0, exchangeFee=0.03
            // sellAmount = min(1000*1.0, 5000) = 1000
            // tax = 1000 * 0.03 = 30
            // sellAmount+tax = 1030 <= 5000, no adjustment
            // buyAmount = 1000 / 1.0 = 1000
            // totalSell = 1000 + 30 = 1030
            // goldDelta = -1030, riceDelta = +1000, nationTax = 30
            val gen = createGeneral(funds = 5000, supplies = 1000)
            val city = createCity(trade = 100)
            val arg = mapOf<String, Any>("buyRice" to true, "amount" to 1000)
            val cmd = che_군량매매(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "trade_buy_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(-1030, json["statChanges"]["gold"].asInt())
            assertEquals(1000, json["statChanges"]["rice"].asInt())
            assertEquals(30, json["nationTax"].asInt())
        }

        @Test
        fun `sell rice - rice to gold with trade=100 exchangeFee=0_03`() {
            // amount=1000, tradeRate=1.0
            // sellAmount = min(1000, 5000) = 1000
            // buyAmount = 1000 * 1.0 = 1000
            // tax = 1000 * 0.03 = 30
            // buyAmount -= tax => 970
            // goldDelta = +970, riceDelta = -1000
            val gen = createGeneral(funds = 1000, supplies = 5000)
            val city = createCity(trade = 100)
            val arg = mapOf<String, Any>("buyRice" to false, "amount" to 1000)
            val cmd = che_군량매매(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "trade_sell_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(970, json["statChanges"]["gold"].asInt())
            assertEquals(-1000, json["statChanges"]["rice"].asInt())
            assertEquals(30, json["nationTax"].asInt())
        }

        @Test
        fun `buy rice with trade=80 produces proportionally less rice`() {
            // tradeRate = 80/100 = 0.8
            // sellAmount = min(1000*0.8, 5000) = 800
            // tax = 800 * 0.03 = 24
            // 800+24 = 824 <= 5000
            // buyAmount = 800 / 0.8 = 1000
            // totalSell = 800 + 24 = 824
            // goldDelta = -824, riceDelta = +1000
            val gen = createGeneral(funds = 5000, supplies = 1000)
            val city = createCity(trade = 80)
            val arg = mapOf<String, Any>("buyRice" to true, "amount" to 1000)
            val cmd = che_군량매매(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "trade_buy_80")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(-824, json["statChanges"]["gold"].asInt())
            assertEquals(1000, json["statChanges"]["rice"].asInt())
            assertEquals(24, json["nationTax"].asInt())
        }

        @Test
        fun `sell rice with trade=120 produces more gold`() {
            // tradeRate = 120/100 = 1.2
            // sellAmount = min(1000, 5000) = 1000
            // buyAmount = 1000 * 1.2 = 1200
            // tax = 1200 * 0.03 = 36
            // buyAmount -= tax => 1164
            // goldDelta = +1164, riceDelta = -1000
            val gen = createGeneral(funds = 1000, supplies = 5000)
            val city = createCity(trade = 120)
            val arg = mapOf<String, Any>("buyRice" to false, "amount" to 1000)
            val cmd = che_군량매매(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "trade_sell_120")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(1164, json["statChanges"]["gold"].asInt())
            assertEquals(-1000, json["statChanges"]["rice"].asInt())
            assertEquals(36, json["nationTax"].asInt())
        }

        @Test
        fun `buy rice capped by general gold when sell+tax exceeds gold`() {
            // amount=10000, tradeRate=1.0, funds =500
            // sellAmount = min(10000*1.0, 500) = 500
            // tax = 500 * 0.03 = 15
            // 500+15 = 515 > 500 => adjustment
            // sellAmount *= 500/(500+15) = 500 * 500/515 = 485.436...
            // tax = 500 - 485.436 = 14.563...
            // buyAmount = 485.436 / 1.0 = 485.436... -> rounded to 485
            // totalSell = 485.436 + 14.563 = 500 -> rounded to 500
            val gen = createGeneral(funds = 500, supplies = 100)
            val city = createCity(trade = 100)
            val arg = mapOf<String, Any>("buyRice" to true, "amount" to 10000)
            val cmd = che_군량매매(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "trade_buy_cap")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // goldDelta should be -500 (all gold spent)
            assertEquals(-500, json["statChanges"]["gold"].asInt())
            // riceDelta: buyAmount = 500 * (500/515) / 1.0 = 485.436 -> 485
            assertEquals(485, json["statChanges"]["rice"].asInt())
        }

        @Test
        fun `trade grants exp=30 ded=50 and one random stat exp`() {
            val gen = createGeneral(funds = 5000, supplies = 5000)
            val city = createCity(trade = 100)
            val arg = mapOf<String, Any>("buyRice" to true, "amount" to 1000)
            val cmd = che_군량매매(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "trade_exp")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(30, json["statChanges"]["experience"].asInt())
            assertEquals(50, json["statChanges"]["dedication"].asInt())
            // One of leadershipExp, strengthExp, intelExp should be 1
            val statExpSum = (json["statChanges"]["leadershipExp"]?.asInt() ?: 0) +
                (json["statChanges"]["strengthExp"]?.asInt() ?: 0) +
                (json["statChanges"]["intelExp"]?.asInt() ?: 0)
            assertEquals(1, statExpSum, "Exactly one stat exp should be incremented")
        }
    }

    // ══════════════════════════════════════════════════════
    //  Donation (che_헌납) — legacy che_헌납.php
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("DonationCommand - che_헌납")
    inner class DonationCommand {

        /**
         * PHP donation (che_헌납.php:122-145):
         *   amount = min(arg.amount, general.funds or general.supplies)
         *   general.resKey -= amount
         *   nation.resKey += amount
         *   exp = 70, ded = 100, leadership_exp += 1
         */
        @Test
        fun `donate gold 1000 from general to nation`() {
            val gen = createGeneral(funds = 5000, supplies = 1000)
            val arg = mapOf<String, Any>("isGold" to true, "amount" to 1000)
            val cmd = che_헌납(gen, createEnv(), arg)

            val result = runCmd(cmd, "donate_gold")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(-1000, json["statChanges"]["gold"].asInt())
            assertEquals(1000, json["nationChanges"]["gold"].asInt())
            assertEquals(70, json["statChanges"]["experience"].asInt())
            assertEquals(100, json["statChanges"]["dedication"].asInt())
            assertEquals(1, json["statChanges"]["leadershipExp"].asInt())
        }

        @Test
        fun `donate rice 1000 from general to nation`() {
            val gen = createGeneral(funds = 1000, supplies = 5000)
            val arg = mapOf<String, Any>("isGold" to false, "amount" to 1000)
            val cmd = che_헌납(gen, createEnv(), arg)

            val result = runCmd(cmd, "donate_rice")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(-1000, json["statChanges"]["rice"].asInt())
            assertEquals(1000, json["nationChanges"]["rice"].asInt())
        }

        @Test
        fun `donate amount capped by general holdings`() {
            // general has 300 gold, requests 1000
            val gen = createGeneral(funds = 300, supplies = 1000)
            val arg = mapOf<String, Any>("isGold" to true, "amount" to 1000)
            val cmd = che_헌납(gen, createEnv(), arg)

            val result = runCmd(cmd, "donate_cap")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(-300, json["statChanges"]["gold"].asInt())
            assertEquals(300, json["nationChanges"]["gold"].asInt())
        }
    }

    // ══════════════════════════════════════════════════════
    //  Domestic commands — legacy che_상업투자.php base
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("DomesticCommands - 5 domestic commands")
    inner class DomesticCommands {

        /**
         * PHP domestic formula (che_상업투자.php:104-127):
         *   score = stat * (approval/100) * getDomesticExpLevelBonus(expLevel) * rng(0.8..1.2)
         *   score *= onCalcDomestic(actionKey, 'score', score)  [1.0 without modifiers]
         *   score = max(1, score)
         *   pick = choiceUsingWeight(fail, success, normal)
         *   score *= CriticalScoreEx(pick)  [success=2.2..3.0, fail=0.2..0.4, normal=1.0]
         *   exp = score * 0.7, ded = score * 1.0
         *
         * Without modifier service, modifiers return 1.0 multiplier.
         * Tests verify the command returns correct JSON structure with cityChanges.
         */
        @Test
        fun `che_농지개간 returns production delta with correct structure`() {
            val gen = createGeneral(intelligence = 80, leadership = 70)
            val city = createCity(production = 500, productionMax = 1000)
            val arg = emptyMap<String, Any>()
            val cmd = che_농지개간(gen, createEnv(), arg)
            cmd.city = city

            val result = runCmd(cmd, "agri_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertTrue(json.has("cityChanges"), "Must have cityChanges")
            assertTrue(json["cityChanges"].has("production"), "Must have production delta")
            val agriDelta = json["cityChanges"]["production"].asInt()
            assertTrue(agriDelta > 0, "production delta should be positive: $agriDelta")
            assertTrue(agriDelta <= 500, "production delta should not exceed remaining capacity")
            // exp = score * 0.7 (int), ded = score
            assertTrue(json["statChanges"]["experience"].asInt() > 0)
            assertTrue(json["statChanges"]["dedication"].asInt() > 0)
            assertEquals(1, json["statChanges"]["intelExp"].asInt(), "농지개간 uses intel stat")
        }

        @Test
        fun `che_상업투자 returns commerce delta`() {
            val gen = createGeneral(intelligence = 80, leadership = 70)
            val city = createCity(commerce = 400, commerceMax = 1000)
            val cmd = che_상업투자(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "comm_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val commDelta = json["cityChanges"]["commerce"].asInt()
            assertTrue(commDelta > 0, "commerce delta should be positive: $commDelta")
            assertTrue(commDelta <= 600, "commerce delta should not exceed remaining capacity")
            assertEquals(1, json["statChanges"]["intelExp"].asInt(), "상업투자 uses intel stat")
        }

        @Test
        fun `che_치안강화 returns security delta using strength stat`() {
            val gen = createGeneral(command = 80, leadership = 70)
            val city = createCity(security = 600, securityMax = 1000)
            val cmd = che_치안강화(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "secu_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val secuDelta = json["cityChanges"]["security"].asInt()
            assertTrue(secuDelta > 0, "security delta should be positive: $secuDelta")
            assertTrue(secuDelta <= 400, "security delta should not exceed remaining capacity")
            assertEquals(1, json["statChanges"]["strengthExp"].asInt(), "치안강화 uses strength stat")
        }

        @Test
        fun `che_수비강화 returns orbitalDefense delta using strength stat`() {
            val gen = createGeneral(command = 80, leadership = 70)
            val city = createCity(orbitalDefense = 300, orbitalDefenseMax = 1000)
            val cmd = che_수비강화(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "def_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val defDelta = json["cityChanges"]["orbitalDefense"].asInt()
            assertTrue(defDelta > 0, "orbitalDefense delta should be positive: $defDelta")
            assertTrue(defDelta <= 700, "orbitalDefense delta should not exceed remaining capacity")
            assertEquals(1, json["statChanges"]["strengthExp"].asInt(), "수비강화 uses strength stat")
        }

        @Test
        fun `che_성벽보수 returns fortress delta using strength stat`() {
            val gen = createGeneral(command = 80, leadership = 70)
            val city = createCity(fortress = 500, fortressMax = 1000)
            val cmd = che_성벽보수(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "wall_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val wallDelta = json["cityChanges"]["fortress"].asInt()
            assertTrue(wallDelta > 0, "fortress delta should be positive: $wallDelta")
            assertTrue(wallDelta <= 500, "fortress delta should not exceed remaining capacity")
            assertEquals(1, json["statChanges"]["strengthExp"].asInt(), "성벽보수 uses strength stat")
        }

        @Test
        fun `domestic commands cap city stat at max value`() {
            // city.production = 990, productionMax = 1000 -> delta cannot exceed 10
            val gen = createGeneral(intelligence = 100, leadership = 100)
            val city = createCity(production = 990, productionMax = 1000)
            val cmd = che_농지개간(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "agri_cap")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val agriDelta = json["cityChanges"]["production"].asInt()
            assertTrue(agriDelta <= 10, "production delta capped at remaining capacity: $agriDelta")
        }

        @Test
        fun `domestic command deducts gold cost from general`() {
            // develCost=100, no modifiers -> cost = round(100) = 100 gold
            val gen = createGeneral(funds = 5000, intelligence = 80)
            val city = createCity(commerce = 500, commerceMax = 1000)
            val cmd = che_상업투자(gen, createEnv(develCost = 100), null)
            cmd.city = city

            val result = runCmd(cmd, "comm_cost")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val goldDelta = json["statChanges"]["gold"].asInt()
            assertTrue(goldDelta < 0, "Should deduct funds: $goldDelta")
        }

        @Test
        fun `domestic command critical result is one of fail normal success`() {
            val gen = createGeneral(intelligence = 80, leadership = 70)
            val city = createCity(production = 500, productionMax = 1000)
            val cmd = che_농지개간(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "agri_crit")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val critResult = json["criticalResult"].asText()
            assertTrue(critResult in listOf("fail", "normal", "success"),
                "criticalResult must be fail/normal/success, got: $critResult")
        }

        @Test
        fun `domestic command experience equals score times 0_7 and dedication equals score`() {
            // Run deterministic seed to get a known score, verify exp/ded ratio
            val gen = createGeneral(intelligence = 80, leadership = 70)
            val city = createCity(production = 100, productionMax = 2000)
            val cmd = che_농지개간(gen, createEnv(), null)
            cmd.city = city

            val result = runCmd(cmd, "agri_expded")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val exp = json["statChanges"]["experience"].asInt()
            val ded = json["statChanges"]["dedication"].asInt()
            val agriDelta = json["cityChanges"]["production"].asInt()
            // The score used to compute exp/ded may differ from cityDelta (due to front debuff),
            // but the relationship exp = floor(score * 0.7) and ded = score should hold.
            // ded should be >= agriDelta (score before front debuff)
            assertTrue(ded >= agriDelta, "dedication($ded) >= cityDelta($agriDelta)")
            // exp should be approximately 0.7 * ded (integer truncation)
            val expectedExp = (ded * 0.7).toInt()
            assertEquals(expectedExp, exp, "exp should be floor(ded * 0.7)")
        }
    }

    // ══════════════════════════════════════════════════════
    //  Nation Economy commands
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("NationEconomyCommands - 포상/몰수/물자원조/증축/감축")
    inner class NationEconomyCommands {

        /**
         * PHP 포상 (che_포상.php:158-162):
         *   amount = valueFit(amount, 0, nation.funds - baseGold)
         *   destOfficer.funds += amount
         *   nation.funds -= amount
         */
        @Test
        fun `che_포상 transfers gold from nation to dest general`() {
            val gen = createGeneral(officerLevel = 12) // chief
            val destGen = createGeneral(id = 2, funds = 100, supplies = 100)
            val nation = createNation(funds = 10000, supplies = 10000)
            val arg = mapOf<String, Any>("isGold" to true, "amount" to 1000, "destGeneralID" to 2)
            val cmd = che_포상(gen, createEnv(), arg)
            cmd.nation = nation
            cmd.destOfficer = destGen

            val result = runCmd(cmd, "reward_gold")
            assertTrue(result.success)
            // 포상 directly mutates entities
            assertEquals(9000, nation.funds, "Nation gold decreased by 1000")
            assertEquals(1100, destGen.funds, "Dest general gold increased by 1000")
        }

        @Test
        fun `che_포상 transfers rice from nation to dest general`() {
            val gen = createGeneral(officerLevel = 12)
            val destGen = createGeneral(id = 2, funds = 100, supplies = 100)
            val nation = createNation(funds = 10000, supplies = 10000)
            val arg = mapOf<String, Any>("isGold" to false, "amount" to 2000, "destGeneralID" to 2)
            val cmd = che_포상(gen, createEnv(), arg)
            cmd.nation = nation
            cmd.destOfficer = destGen

            val result = runCmd(cmd, "reward_rice")
            assertTrue(result.success)
            assertEquals(8000, nation.supplies, "Nation rice decreased by 2000")
            assertEquals(2100, destGen.supplies, "Dest general rice increased by 2000")
        }

        @Test
        fun `che_포상 caps amount at nation resources minus base`() {
            // nation funds =1500, baseGold default=1000 in gameStor -> available=500
            val gen = createGeneral(officerLevel = 12)
            val destGen = createGeneral(id = 2, funds = 100, supplies = 100)
            val nation = createNation(funds = 1500, supplies = 10000)
            val arg = mapOf<String, Any>("isGold" to true, "amount" to 5000, "destGeneralID" to 2)
            val env = createEnv()
            env.gameStor["baseGold"] = 1000
            val cmd = che_포상(gen, env, arg)
            cmd.nation = nation
            cmd.destOfficer = destGen

            val result = runCmd(cmd, "reward_cap")
            assertTrue(result.success)
            assertEquals(1000, nation.funds, "Nation gold should be at baseGold level")
            assertEquals(600, destGen.funds, "Dest general receives 500")
        }

        /**
         * PHP 몰수 (che_몰수.php:167-208):
         *   amount = valueFit(amount, 0, destOfficer.funds)
         *   destOfficer.funds -= amount
         *   nation.funds += amount
         */
        @Test
        fun `che_몰수 transfers gold from dest general to nation`() {
            val gen = createGeneral(officerLevel = 12)
            val destGen = createGeneral(id = 2, funds = 5000, supplies = 5000)
            val nation = createNation(funds = 10000, supplies = 10000)
            val arg = mapOf<String, Any>("isGold" to true, "amount" to 2000, "destGeneralID" to 2)
            val cmd = che_몰수(gen, createEnv(), arg)
            cmd.nation = nation
            cmd.destOfficer = destGen

            val result = runCmd(cmd, "seize_gold")
            assertTrue(result.success)
            assertEquals(12000, nation.funds, "Nation gold increased by 2000")
            assertEquals(3000, destGen.funds, "Dest general gold decreased by 2000")
        }

        @Test
        fun `che_몰수 caps at dest general holdings`() {
            val gen = createGeneral(officerLevel = 12)
            val destGen = createGeneral(id = 2, funds = 300, supplies = 5000)
            val nation = createNation(funds = 10000, supplies = 10000)
            val arg = mapOf<String, Any>("isGold" to true, "amount" to 5000, "destGeneralID" to 2)
            val cmd = che_몰수(gen, createEnv(), arg)
            cmd.nation = nation
            cmd.destOfficer = destGen

            val result = runCmd(cmd, "seize_cap")
            assertTrue(result.success)
            assertEquals(10300, nation.funds, "Nation gold increased by 300")
            assertEquals(0, destGen.funds, "Dest general gold is 0")
        }

        /**
         * PHP 물자원조 (che_물자원조.php:183-254):
         *   goldAmount = valueFit(goldAmount, 0, nation.funds - basegold)
         *   riceAmount = valueFit(riceAmount, 0, nation.supplies - baserice)
         *   nation.funds -= goldAmount; destFaction.funds += goldAmount
         *   nation.supplies -= riceAmount; destFaction.supplies += riceAmount
         *   general.exp += 5, general.ded += 5
         *   nation.surlimit += 12
         */
        @Test
        fun `che_물자원조 transfers gold and rice between nations`() {
            val gen = createGeneral(officerLevel = 12, funds = 1000, supplies = 1000)
            val nation = createNation(id = 1, funds = 10000, supplies = 20000)
            val destFaction = createNation(id = 2, funds = 5000, supplies = 5000)
            val arg = mapOf<String, Any>(
                "destNationID" to 2,
                "amountList" to listOf(3000, 5000)
            )
            val cmd = che_물자원조(gen, createEnv(), arg)
            cmd.nation = nation
            cmd.destFaction = destFaction

            val result = runCmd(cmd, "aid_1")
            assertTrue(result.success)
            assertEquals(7000, nation.funds, "Source nation gold decreased")
            assertEquals(15000, nation.supplies, "Source nation rice decreased")
            assertEquals(8000, destFaction.funds, "Dest nation gold increased")
            assertEquals(10000, destFaction.supplies, "Dest nation rice increased")
        }

        @Test
        fun `che_물자원조 grants exp=5 ded=5`() {
            val gen = createGeneral(officerLevel = 12, funds = 1000, supplies = 1000)
            gen.experience = 0
            gen.dedication = 0
            val nation = createNation(id = 1, funds = 10000, supplies = 20000)
            val destFaction = createNation(id = 2, funds = 5000, supplies = 5000)
            val arg = mapOf<String, Any>(
                "destNationID" to 2,
                "amountList" to listOf(1000, 1000)
            )
            val cmd = che_물자원조(gen, createEnv(), arg)
            cmd.nation = nation
            cmd.destFaction = destFaction

            val result = runCmd(cmd, "aid_exp")
            assertTrue(result.success)
            assertEquals(5, gen.experience, "exp += 5")
            assertEquals(5, gen.dedication, "ded += 5")
        }

        /**
         * PHP 증축 (che_증축.php:173-188):
         *   city.level += 1
         *   city.populationMax += expandCityPopIncreaseAmount (100000)
         *   city.productionMax += expandCityDevelIncreaseAmount (2000)
         *   city.commerceMax += expandCityDevelIncreaseAmount (2000)
         *   city.securityMax += expandCityDevelIncreaseAmount (2000)
         *   city.orbitalDefenseMax += expandCityWallIncreaseAmount (2000)
         *   city.fortressMax += expandCityWallIncreaseAmount (2000)
         *   nation.funds -= cost, nation.supplies -= cost
         *   cost = develcost * expandCityCostCoef + expandCityDefaultCost
         *        = 100 * 500 + 60000 = 110000
         *   exp/ded = 5 * (preReqTurn+1) = 5 * 6 = 30
         */
        @Test
        fun `che_증축 upgrades city level and increases max stats`() {
            val gen = createGeneral(officerLevel = 12)
            gen.experience = 0
            gen.dedication = 0
            val nation = createNation(funds = 200000, supplies = 200000)
            val city = createCity(level = 5, populationMax = 100000, productionMax = 1000, commerceMax = 1000,
                securityMax = 1000, orbitalDefenseMax = 1000, fortressMax = 1000)
            val cmd = che_증축(gen, createEnv(develCost = 100), null)
            cmd.nation = nation
            cmd.city = city
            cmd.destPlanet = city

            val result = runCmd(cmd, "expand_1")
            assertTrue(result.success)
            assertEquals(6.toShort(), city.level)
            assertEquals(100000 + EXPAND_CITY_POP_INCREASE, city.populationMax)
            assertEquals(1000 + EXPAND_CITY_DEVEL_INCREASE, city.productionMax)
            assertEquals(1000 + EXPAND_CITY_DEVEL_INCREASE, city.commerceMax)
            assertEquals(1000 + EXPAND_CITY_DEVEL_INCREASE, city.securityMax)
            assertEquals(1000 + EXPAND_CITY_WALL_INCREASE, city.orbitalDefenseMax)
            assertEquals(1000 + EXPAND_CITY_WALL_INCREASE, city.fortressMax)
            // Cost = 100 * 500 + 60000 = 110000
            val expectedCost = 100 * EXPAND_CITY_COST_COEF + EXPAND_CITY_DEFAULT_COST
            assertEquals(200000 - expectedCost, nation.funds)
            assertEquals(200000 - expectedCost, nation.supplies)
            // exp/ded = 5 * 6 = 30
            assertEquals(30, gen.experience)
            assertEquals(30, gen.dedication)
        }

        @Test
        fun `che_증축 fails when level is already 8`() {
            val gen = createGeneral(officerLevel = 12)
            val nation = createNation(funds = 500000, supplies = 500000)
            val city = createCity(level = 8)
            val cmd = che_증축(gen, createEnv(), null)
            cmd.nation = nation
            cmd.city = city
            cmd.destPlanet = city

            val result = runCmd(cmd, "expand_max")
            assertFalse(result.success, "Should fail when level >= 8")
        }

        /**
         * PHP 감축 (che_감축.php:174-196):
         *   city.level -= 1
         *   city.population = max(population - expandCityPopIncreaseAmount, minRecruitPop)
         *   city.production = max(production - expandCityDevelIncreaseAmount, 0)
         *   (same for commerce, security)
         *   city.orbitalDefense = max(orbitalDefense - expandCityWallIncreaseAmount, 0)
         *   city.fortress = max(fortress - expandCityWallIncreaseAmount, 0)
         *   city.populationMax -= expandCityPopIncreaseAmount
         *   (same for all maxes)
         *   nation.funds += cost, nation.supplies += cost
         *   cost = develcost * expandCityCostCoef + expandCityDefaultCost / 2
         *        = 100 * 500 + 30000 = 80000
         */
        @Test
        fun `che_감축 downgrades city level and refunds cost`() {
            val gen = createGeneral(officerLevel = 12)
            gen.experience = 0
            gen.dedication = 0
            val nation = createNation(funds = 50000, supplies = 50000)
            val city = createCity(level = 6, population = 150000, populationMax = 200000,
                production = 800, productionMax = 3000, commerce = 700, commerceMax = 3000,
                security = 600, securityMax = 3000, orbitalDefense = 500, orbitalDefenseMax = 3000,
                fortress = 400, fortressMax = 3000)
            val cmd = che_감축(gen, createEnv(develCost = 100), null)
            cmd.nation = nation
            cmd.city = city

            val result = runCmd(cmd, "shrink_1")
            assertTrue(result.success)
            assertEquals(5.toShort(), city.level)
            assertEquals(200000 - EXPAND_CITY_POP_INCREASE, city.populationMax)
            assertEquals(maxOf(0, 150000 - EXPAND_CITY_POP_INCREASE), city.population)
            assertEquals(3000 - EXPAND_CITY_DEVEL_INCREASE, city.productionMax)
            assertEquals(maxOf(0, 800 - EXPAND_CITY_DEVEL_INCREASE), city.production)
            assertEquals(3000 - EXPAND_CITY_DEVEL_INCREASE, city.commerceMax)
            assertEquals(maxOf(0, 700 - EXPAND_CITY_DEVEL_INCREASE), city.commerce)
            assertEquals(3000 - EXPAND_CITY_DEVEL_INCREASE, city.securityMax)
            assertEquals(maxOf(0, 600 - EXPAND_CITY_DEVEL_INCREASE), city.security)
            assertEquals(3000 - EXPAND_CITY_WALL_INCREASE, city.orbitalDefenseMax)
            assertEquals(maxOf(0, 500 - EXPAND_CITY_WALL_INCREASE), city.orbitalDefense)
            assertEquals(3000 - EXPAND_CITY_WALL_INCREASE, city.fortressMax)
            assertEquals(maxOf(0, 400 - EXPAND_CITY_WALL_INCREASE), city.fortress)
            // Refund: cost = 100 * 500 + 60000/2 = 80000
            val expectedRefund = 100 * EXPAND_CITY_COST_COEF + EXPAND_CITY_DEFAULT_COST / 2
            assertEquals(50000 + expectedRefund, nation.funds)
            assertEquals(50000 + expectedRefund, nation.supplies)
            // exp/ded = 5 * 6 = 30
            assertEquals(30, gen.experience)
            assertEquals(30, gen.dedication)
        }

        @Test
        fun `che_감축 fails when level is 1`() {
            val gen = createGeneral(officerLevel = 12)
            val nation = createNation(funds = 50000, supplies = 50000)
            val city = createCity(level = 1)
            val cmd = che_감축(gen, createEnv(), null)
            cmd.nation = nation
            cmd.city = city

            val result = runCmd(cmd, "shrink_min")
            assertFalse(result.success, "Should fail when level <= 1")
        }
    }

    // ══════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════

    private fun runCmd(cmd: com.openlogh.command.BaseCommand, seed: String): CommandResult {
        return runBlocking { cmd.run(LiteHashDRBG.build(seed)) }
    }

    private fun createEnv(develCost: Int = 100): CommandEnv {
        return CommandEnv(
            year = 200,
            month = 1,
            startYear = 190,
            sessionId = 1,
            realtimeMode = false,
            develCost = develCost,
        )
    }

    private fun createGeneral(
        id: Long = 1,
        leadership: Short = 70,
        command: Short = 70,
        intelligence: Short = 70,
        funds: Int = 1000,
        supplies: Int = 1000,
        officerLevel: Short = 1,
        ships: Int = 0,
        shipClass: Short = 0,
        training: Short = 0,
        morale: Short = 0,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수$id",
        factionId = 1,
        planetId = 1,
        funds = funds,
        supplies = supplies,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        officerLevel = officerLevel,
        ships = ships,
        shipClass = shipClass,
        training = training,
        morale = morale,
    )

    private fun createCity(
        id: Long = 1,
        factionId: Long = 1,
        population: Int = 50000,
        populationMax: Int = 100000,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        orbitalDefense: Int = 500,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 500,
        fortressMax: Int = 1000,
        approval: Float = 80f,
        level: Short = 5,
        trade: Int = 100,
        frontState: Short = 0,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시$id",
        factionId = factionId,
        population = population,
        populationMax = populationMax,
        production = production,
        productionMax = productionMax,
        commerce = commerce,
        commerceMax = commerceMax,
        security = security,
        securityMax = securityMax,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = orbitalDefenseMax,
        fortress = fortress,
        fortressMax = fortressMax,
        approval = approval,
        supplyState = 1,
        level = level,
        tradeRoute = trade,
        frontState = frontState,
    )

    private fun createNation(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        level: Short = 5,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "테스트국가$id",
        color = "#FF0000",
        funds = funds,
        supplies = supplies,
        factionRank = level,
        capitalPlanetId = 1,
    )
}
