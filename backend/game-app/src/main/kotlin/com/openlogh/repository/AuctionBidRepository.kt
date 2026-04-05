package com.openlogh.repository

import com.openlogh.entity.AuctionBid
import org.springframework.data.jpa.repository.JpaRepository

interface AuctionBidRepository : JpaRepository<AuctionBid, Long> {
    fun findByAuctionId(auctionId: Long): List<AuctionBid>
    fun findByBidderOfficerId(bidderOfficerId: Long): List<AuctionBid>
    fun findByAuctionIdOrderByAmountDesc(auctionId: Long): List<AuctionBid>
    fun findTopByAuctionIdOrderByAmountDesc(auctionId: Long): AuctionBid?
}
