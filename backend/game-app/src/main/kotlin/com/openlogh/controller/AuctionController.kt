package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.entity.Auction
import com.openlogh.entity.AuctionBid
import com.openlogh.repository.AuctionBidRepository
import com.openlogh.repository.AuctionRepository
import com.openlogh.repository.OfficerRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api")
class AuctionController(
    private val auctionRepository: AuctionRepository,
    private val auctionBidRepository: AuctionBidRepository,
    private val officerRepository: OfficerRepository,
) {
    @GetMapping("/worlds/{worldId}/auctions")
    fun list(@PathVariable worldId: Long): ResponseEntity<List<Auction>> {
        val auctions = auctionRepository.findBySessionIdAndStatusNotOrderByCreatedAtDesc(worldId, "deleted")
        return ResponseEntity.ok(auctions)
    }

    @PostMapping("/worlds/{worldId}/auctions")
    fun create(
        @PathVariable worldId: Long,
        @RequestBody req: CreateAuctionRequest,
    ): ResponseEntity<Auction> {
        val seller = officerRepository.findById(req.sellerId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val auction = Auction(
            sessionId = worldId,
            sellerGeneralId = req.sellerId,
            type = req.type,
            itemCode = req.item,
            amount = req.amount,
            minPrice = req.minPrice,
            startBidAmount = req.minPrice,
            finishBidAmount = req.finishBidAmount ?: 0,
            currentPrice = req.minPrice,
            hostGeneralId = req.sellerId,
            hostName = seller.name,
            status = "open",
            createdAt = OffsetDateTime.now(),
            expiresAt = OffsetDateTime.now().plusDays((req.closeTurnCnt ?: 3).toLong()),
        )
        val saved = auctionRepository.save(auction)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PostMapping("/auctions/{auctionId}/bid")
    fun bid(
        @PathVariable auctionId: Long,
        @RequestBody req: BidRequest,
    ): ResponseEntity<AuctionBidResponse> {
        val auction = auctionRepository.findById(auctionId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (auction.status != "open") {
            return ResponseEntity.ok(AuctionBidResponse(success = false, error = "경매가 종료되었습니다."))
        }
        if (req.amount <= auction.currentPrice) {
            return ResponseEntity.ok(AuctionBidResponse(success = false, error = "현재가보다 높은 금액을 입력하세요."))
        }

        val bid = AuctionBid(
            sessionId = auction.sessionId,
            auctionId = auctionId,
            bidderGeneralId = req.bidderId,
            amount = req.amount,
        )
        auctionBidRepository.save(bid)

        auction.currentPrice = req.amount
        auction.buyerGeneralId = req.bidderId
        auctionRepository.save(auction)

        return ResponseEntity.ok(AuctionBidResponse(success = true, currentPrice = req.amount))
    }

    @PostMapping("/auctions/{auctionId}/cancel")
    fun cancel(
        @PathVariable auctionId: Long,
        @RequestBody req: CancelAuctionRequest,
    ): ResponseEntity<AuctionActionResponse> {
        val auction = auctionRepository.findById(auctionId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (auction.hostGeneralId != req.officerId) {
            return ResponseEntity.ok(AuctionActionResponse(success = false, error = "권한이 없습니다."))
        }
        if (auction.buyerGeneralId != null) {
            return ResponseEntity.ok(AuctionActionResponse(success = false, error = "이미 입찰이 있어 취소할 수 없습니다."))
        }
        auction.status = "cancelled"
        auctionRepository.save(auction)
        return ResponseEntity.ok(AuctionActionResponse(success = true))
    }

    @PostMapping("/auctions/{auctionId}/finalize")
    fun finalize(@PathVariable auctionId: Long): ResponseEntity<AuctionActionResponse> {
        val auction = auctionRepository.findById(auctionId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        auction.status = "completed"
        auctionRepository.save(auction)
        return ResponseEntity.ok(AuctionActionResponse(success = true))
    }

    @GetMapping("/worlds/{worldId}/auction-history")
    fun getHistory(@PathVariable worldId: Long): ResponseEntity<List<AuctionHistoryEntry>> {
        val completed = auctionRepository.findBySessionIdAndStatusOrderByCreatedAtDesc(worldId, "completed")
        val history = completed.map {
            AuctionHistoryEntry(
                id = it.id,
                type = it.type,
                itemCode = it.itemCode,
                amount = it.amount,
                finalPrice = it.currentPrice,
                sellerName = it.hostName,
                buyerName = null,
                status = it.status,
                createdAt = it.createdAt,
            )
        }
        return ResponseEntity.ok(history)
    }

    @GetMapping("/worlds/{worldId}/market-price")
    fun getMarketPrice(@PathVariable worldId: Long): ResponseEntity<MarketPriceResponse> {
        // TODO: implement dynamic market pricing
        return ResponseEntity.ok(MarketPriceResponse(goldPerRice = 1.0, ricePerGold = 1.0))
    }

    @PostMapping("/worlds/{worldId}/market/buy-rice")
    fun buyRice(
        @PathVariable worldId: Long,
        @RequestBody req: MarketTradeRequest,
    ): ResponseEntity<MarketBuyRiceResponse> {
        // TODO: implement market buy logic
        return ResponseEntity.ok(MarketBuyRiceResponse(success = true, amount = req.amount, cost = req.amount))
    }

    @PostMapping("/worlds/{worldId}/market/sell-rice")
    fun sellRice(
        @PathVariable worldId: Long,
        @RequestBody req: MarketTradeRequest,
    ): ResponseEntity<MarketSellRiceResponse> {
        // TODO: implement market sell logic
        return ResponseEntity.ok(MarketSellRiceResponse(success = true, amount = req.amount, revenue = req.amount))
    }

    @PostMapping("/worlds/{worldId}/item-auctions")
    fun createItemAuction(
        @PathVariable worldId: Long,
        @RequestBody req: CreateItemAuctionRequest,
    ): ResponseEntity<ItemAuctionCreateResponse> {
        // TODO: implement item auction creation
        return ResponseEntity.ok(ItemAuctionCreateResponse(success = true, auctionId = 0))
    }
}
