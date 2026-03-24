package com.openlogh.service

import com.openlogh.entity.Auction
import com.openlogh.entity.SessionState
import com.openlogh.repository.AuctionBidRepository
import com.openlogh.repository.AuctionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * 경매 시스템.
 *
 * gin7:
 * - 특수 기함, 장비 등을 경매로 거래
 * - 정기적으로 자동 경매 생성 (희귀 아이템)
 * - 입찰, 낙찰, 만료 처리
 * - 만료 시 연장 가능 (최대 3회)
 *
 * 자동 등록: 매 게임 3개월마다 희귀 아이템 경매 자동 생성
 */
@Service
class AuctionService(
    private val auctionRepository: AuctionRepository,
    private val auctionBidRepository: AuctionBidRepository,
    private val officerRepository: OfficerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(AuctionService::class.java)

        /** 자동 경매 주기 (게임 월) */
        const val AUTO_AUCTION_INTERVAL = 3

        /** 경매 기본 기간 (실시간 시간) */
        const val DEFAULT_AUCTION_HOURS = 48L

        /** 최대 연장 횟수 */
        const val MAX_EXTENSIONS = 3

        /** 연장 시간 (시간) */
        const val EXTENSION_HOURS = 12L

        /** 자동 경매 아이템 풀 */
        val AUTO_AUCTION_ITEMS = listOf(
            AutoAuctionItem("flagship_brunhilde", "기함: 브륀힐트", "flagship", 5000),
            AutoAuctionItem("flagship_hyperion", "기함: 히페리온", "flagship", 5000),
            AutoAuctionItem("flagship_barbarossa", "기함: 바르바로사", "flagship", 4000),
            AutoAuctionItem("flagship_ulysses", "기함: 율리시즈", "flagship", 4000),
            AutoAuctionItem("flagship_patro", "기함: 파트로클로스", "flagship", 3500),
            AutoAuctionItem("equip_zephyr_particle", "제피르 입자포", "equipment", 3000),
            AutoAuctionItem("equip_rail_cannon", "레일캐논 장비", "equipment", 2500),
            AutoAuctionItem("engine_high_output", "고출력 엔진", "engine", 2000),
            AutoAuctionItem("accessory_command_module", "고성능 지휘 모듈", "accessory", 1500),
        )
    }

    data class AutoAuctionItem(
        val code: String,
        val name: String,
        val type: String,
        val basePrice: Int,
    )

    /**
     * 매 턴 호출: 경매 처리 (만료 체크 + 자동 생성).
     */
    fun processAuctions(world: SessionState) {
        val sessionId = world.id.toLong()

        // 1. 만료된 경매 처리
        processExpiredAuctions(sessionId)

        // 2. 자동 경매 생성 (3개월마다)
        val currentMonth = world.currentMonth.toInt()
        if (currentMonth % AUTO_AUCTION_INTERVAL == 1) {
            autoRegisterAuctions(world)
        }
    }

    /**
     * 만료 경매 처리: 낙찰 또는 유찰.
     */
    private fun processExpiredAuctions(sessionId: Long) {
        val now = OffsetDateTime.now()
        val auctions = auctionRepository.findBySessionId(sessionId)
        val expired = auctions.filter { it.status == "open" && it.expiresAt.isBefore(now) }

        for (auction in expired) {
            if (auction.buyerGeneralId != null && auction.currentPrice > 0) {
                // 낙찰
                auction.status = "sold"
                awardAuctionItem(auction)
                log.info("Auction {} sold: {} to officer {} for {}",
                    auction.id, auction.itemCode, auction.buyerGeneralId, auction.currentPrice)
            } else if (auction.closeDateExtensionCount > 0) {
                // 연장
                auction.closeDateExtensionCount--
                auction.expiresAt = now.plusHours(EXTENSION_HOURS)
                log.debug("Auction {} extended (remaining: {})", auction.id, auction.closeDateExtensionCount)
            } else {
                // 유찰
                auction.status = "expired"
                log.info("Auction {} expired unsold: {}", auction.id, auction.itemCode)
            }
            auctionRepository.save(auction)
        }
    }

    /**
     * 낙찰 아이템 지급.
     */
    private fun awardAuctionItem(auction: Auction) {
        val buyerId = auction.buyerGeneralId ?: return
        val officer = officerRepository.findById(buyerId).orElse(null) ?: return

        when (auction.type) {
            "flagship" -> {
                officer.meta["flagship"] = auction.itemCode
                officer.meta["flagshipName"] = auction.meta["displayName"] ?: auction.itemCode
            }
            "equipment" -> officer.equipCode = auction.itemCode
            "engine" -> officer.engineCode = auction.itemCode
            "accessory" -> officer.accessoryCode = auction.itemCode
        }

        // 구매 대금 차감
        officer.funds -= auction.currentPrice
        officerRepository.save(officer)
    }

    /**
     * 자동 경매 등록: 랜덤으로 1~2개 아이템 경매 생성.
     */
    private fun autoRegisterAuctions(world: SessionState) {
        val sessionId = world.id.toLong()
        val rng = kotlin.random.Random(world.currentYear * 100L + world.currentMonth)

        // 현재 진행 중인 자동 경매 확인
        val active = auctionRepository.findBySessionId(sessionId)
            .filter { it.status == "open" && it.hostGeneralId == 0L }
        if (active.size >= 3) return // 이미 자동 경매 3개 이상 진행 중

        val count = rng.nextInt(1, 3) // 1~2개
        val available = AUTO_AUCTION_ITEMS.shuffled(rng).take(count)

        for (item in available) {
            // 이미 같은 아이템이 경매 중이면 스킵
            if (active.any { it.itemCode == item.code }) continue

            val auction = Auction(
                sessionId = sessionId,
                sellerGeneralId = 0, // 시스템 판매
                type = item.type,
                itemCode = item.code,
                amount = 1,
                minPrice = item.basePrice,
                startBidAmount = item.basePrice,
                finishBidAmount = 0,
                currentPrice = 0,
                hostGeneralId = 0, // 시스템
                hostName = "은하 시장",
                status = "open",
                expiresAt = OffsetDateTime.now().plusHours(DEFAULT_AUCTION_HOURS),
                meta = mutableMapOf("displayName" to item.name, "autoGenerated" to true),
            )
            auctionRepository.save(auction)
            log.info("Auto-auction created: {} (base price: {})", item.name, item.basePrice)
        }
    }
}
