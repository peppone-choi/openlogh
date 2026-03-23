package com.openlogh.controller

import com.openlogh.dto.CastVoteRequest
import com.openlogh.dto.CreateVoteCommentRequest
import com.openlogh.dto.CreateVoteRequest
import com.openlogh.dto.VoteCommentResponse
import com.openlogh.entity.Vote
import com.openlogh.entity.VoteCast
import com.openlogh.repository.VoteCastRepository
import com.openlogh.repository.VoteRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api")
class VoteController(
    private val voteRepository: VoteRepository,
    private val voteCastRepository: VoteCastRepository,
) {
    @GetMapping("/worlds/{worldId}/votes")
    fun list(@PathVariable worldId: Long): ResponseEntity<List<Vote>> {
        return ResponseEntity.ok(voteRepository.findBySessionId(worldId))
    }

    @PostMapping("/worlds/{worldId}/votes")
    fun create(
        @PathVariable worldId: Long,
        @RequestBody req: CreateVoteRequest,
    ): ResponseEntity<Vote> {
        val optionsMap = mutableMapOf<String, Any>()
        req.options.forEachIndexed { idx, option ->
            optionsMap[idx.toString()] = mapOf("label" to option, "count" to 0)
        }
        val vote = Vote(
            sessionId = worldId,
            title = req.title,
            options = optionsMap,
            status = "open",
            createdAt = OffsetDateTime.now(),
            expiresAt = OffsetDateTime.now().plusDays(7),
        )
        val saved = voteRepository.save(vote)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PostMapping("/votes/{voteId}/cast")
    fun cast(
        @PathVariable voteId: Long,
        @RequestBody req: CastVoteRequest,
    ): ResponseEntity<*> {
        val vote = voteRepository.findById(voteId).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        if (vote.status != "open") {
            return ResponseEntity.badRequest().body(mapOf("error" to "투표가 마감되었습니다."))
        }
        val existing = voteCastRepository.findByVoteIdAndOfficerId(voteId, req.voterId)
        if (existing != null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "이미 투표하셨습니다."))
        }

        val cast = VoteCast(
            sessionId = vote.sessionId,
            voteId = voteId,
            officerId = req.voterId,
            optionIdx = req.optionIndex.toShort(),
        )
        voteCastRepository.save(cast)

        @Suppress("UNCHECKED_CAST")
        val optionEntry = vote.options[req.optionIndex.toString()] as? MutableMap<String, Any>
        if (optionEntry != null) {
            val count = (optionEntry["count"] as? Number)?.toInt() ?: 0
            optionEntry["count"] = count + 1
            voteRepository.save(vote)
        }

        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/votes/{voteId}/close")
    fun close(@PathVariable voteId: Long): ResponseEntity<*> {
        val vote = voteRepository.findById(voteId).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        vote.status = "closed"
        voteRepository.save(vote)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/votes/{voteId}/comments")
    fun listComments(@PathVariable voteId: Long): ResponseEntity<List<VoteCommentResponse>> {
        // Vote comments stored in vote.options meta or a separate mechanism
        // Stub: return empty list
        return ResponseEntity.ok(emptyList())
    }

    @PostMapping("/votes/{voteId}/comments")
    fun createComment(
        @PathVariable voteId: Long,
        @RequestBody req: CreateVoteCommentRequest,
    ): ResponseEntity<VoteCommentResponse> {
        val vote = voteRepository.findById(voteId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val comment = VoteCommentResponse(
            id = System.currentTimeMillis(),
            authorGeneralId = req.authorGeneralId,
            content = req.content,
            createdAt = OffsetDateTime.now(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @DeleteMapping("/votes/{voteId}/comments/{commentId}")
    fun deleteComment(
        @PathVariable voteId: Long,
        @PathVariable commentId: Long,
        @RequestParam generalId: Long,
    ): ResponseEntity<Void> {
        // Stub: comment deletion
        return ResponseEntity.ok().build()
    }
}
