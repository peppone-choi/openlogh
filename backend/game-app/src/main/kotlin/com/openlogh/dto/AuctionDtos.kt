package com.openlogh.dto

data class CreateAuctionRequest(
    val type: String,
    val sellerId: Long,
    val item: String,
    val amount: Int,
    val minPrice: Int,
    val finishBidAmount: Int? = null,
    val closeTurnCnt: Int? = null,
)

data class BidRequest(val bidderId: Long, val amount: Int)

data class MarketTradeRequest(
    val officerId: Long,
    val amount: Int,
)

data class CreateItemAuctionRequest(
    val officerId: Long,
    val itemType: String,
    val startPrice: Int,
)

data class CancelAuctionRequest(
    val officerId: Long,
)

data class OpenResourceAuctionRequest(
    val hostGeneralId: Long,
    val amount: Int,
    val closeTurnCnt: Int,
    val startBidAmount: Int,
    val finishBidAmount: Int,
)

data class BidResourceAuctionRequest(
    val bidderId: Long,
    val amount: Int,
)

data class AuctionBidResponse(val success: Boolean, val currentPrice: Int? = null, val error: String? = null)

data class AuctionActionResponse(val success: Boolean, val error: String? = null)

data class AuctionHistoryEntry(
    val id: Long, val type: String, val itemCode: String, val amount: Int,
    val finalPrice: Int, val sellerName: String, val buyerName: String?,
    val status: String, val createdAt: java.time.OffsetDateTime,
)

data class MarketPriceResponse(val goldPerRice: Double, val ricePerGold: Double)

data class MarketBuyRiceResponse(val success: Boolean, val amount: Int? = null, val cost: Int? = null, val error: String? = null)

data class MarketSellRiceResponse(val success: Boolean, val amount: Int? = null, val revenue: Int? = null, val error: String? = null)

data class ItemAuctionCreateResponse(val success: Boolean, val auctionId: Long? = null, val error: String? = null)
