package com.openlogh.repository

import com.openlogh.entity.Auction
import org.springframework.data.jpa.repository.JpaRepository

interface AuctionRepository : JpaRepository<Auction, Long> {
    fun findBySessionId(sessionId: Long): List<Auction>
    fun findBySessionIdAndStatus(sessionId: Long, status: String): List<Auction>
    fun findBySessionIdAndStatusOrderByCreatedAtDesc(sessionId: Long, status: String): List<Auction>
    fun findBySessionIdAndStatusNotOrderByCreatedAtDesc(sessionId: Long, status: String): List<Auction>
    fun findBySellerOfficerId(sellerOfficerId: Long): List<Auction>
    fun findByStatusAndExpiresAtLessThanEqual(status: String, expiresAt: java.time.OffsetDateTime): List<Auction>
}
