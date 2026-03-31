package com.opensam.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.HexFormat

private const val LITE_DRBG_BLOCK_SIZE = LiteHashDRBG.BUFFER_BYTE_SIZE

private val TEST_VECTOR_BLOCKS = listOf(
    "24d9ccd648556255fd0ee9f5b29918de90617341958b3b354d572167e4dee02b757816a2bbe0b502c52413ffd384381a9d7b4e193df6f4345d6a95e111d661c4",
    "2e9264512f6f4b080cf1376b74fab6878ecf4a6e185942d2e5b22cf923885b9952d40601a414225d6901417fd4ce9368ac77e4a63d3fc9b58ab952bb8c33f165",
    "8e2ebf5af6283a1b18f4c044c86c20d02be3890613c4cc8b7c6b7b35581263b972a82630df69a9289988422d7c3a9be5edf78d5de16fabd01e5dd4e458068d8a",
    "398596047ba547bfe371ec863a3e019ab0dbc4bb3b27e9077685aae4283ff6bbccfd981d92f9358f7efffbb72a940414802d98466d132e2ad0a16a12946d5f47",
    "b3606fe9b18c4aa7315e78bb9e47cb51cc4e203fcc2e631f0405c1b872c8e1cb5b6415ea74bbb77fffaaadb002b47cb4f4628dc0709634365b187667f5c708cb",
)

private val TEST_VECTOR = HexFormat.of().parseHex(TEST_VECTOR_BLOCKS.joinToString(""))

private fun fillLiteBlock(src: ByteArray, filler: ByteArray, length: Int = LITE_DRBG_BLOCK_SIZE): ByteArray {
    require(filler.isNotEmpty()) { "filler must have length" }

    val result = ByteArray(length)
    val srcLen = minOf(src.size, length)
    src.copyInto(result, destinationOffset = 0, startIndex = 0, endIndex = srcLen)

    var offset = srcLen
    while (offset < length) {
        val copyLen = minOf(filler.size, length - offset)
        filler.copyInto(result, destinationOffset = offset, startIndex = 0, endIndex = copyLen)
        offset += copyLen
    }

    return result
}

private class LiteHashDummyBlockRng(
    private val repeatBlocks: List<ByteArray>,
    initialStateIdx: Int = 0,
) : LiteHashDRBG(seed = "", stateIdx = 0) {
    private val repeatBlockCnt: Int

    init {
        require(repeatBlocks.isNotEmpty()) { "repeatBlocks is empty" }
        repeatBlocks.forEach { block ->
            require(block.size == LITE_DRBG_BLOCK_SIZE) {
                "Invalid repeat block ${block.size} != $LITE_DRBG_BLOCK_SIZE"
            }
        }

        repeatBlockCnt = repeatBlocks.size
        stateIdx = ((initialStateIdx % repeatBlockCnt) + repeatBlockCnt) % repeatBlockCnt
        genNextBlock()
    }

    override fun genNextBlock() {
        buffer = repeatBlocks[stateIdx].copyOf()
        bufferIdx = 0
        stateIdx = (stateIdx + 1) % repeatBlockCnt
    }
}

class LiteHashDRBGTest {
    @Test
    fun `sha512 block sequence matches php vector`() {
        val rng = LiteHashDRBG.build("HelloWorld")
        val actual = ByteArray(LITE_DRBG_BLOCK_SIZE * TEST_VECTOR_BLOCKS.size)

        for (idx in TEST_VECTOR_BLOCKS.indices) {
            val block = rng.nextBytes(LITE_DRBG_BLOCK_SIZE)
            block.copyInto(actual, destinationOffset = idx * LITE_DRBG_BLOCK_SIZE)
        }

        assertArrayEquals(TEST_VECTOR, actual)
    }

    @Test
    fun `nextBytes matches php offset reads`() {
        val rng = LiteHashDRBG.build("HelloWorld")
        val reads = listOf(10, 32, 1, 64, 5, 16)
        var offset = 0

        for (size in reads) {
            val expected = TEST_VECTOR.copyOfRange(offset, offset + size)
            val actual = rng.nextBytes(size)
            assertArrayEquals(expected, actual)
            offset += size
        }
    }

    @Test
    fun `100 sequential draws match golden values for parity-test-seed`() {
        val expected = longArrayOf(
            767, 870, 80, 249, 645, 27, 946, 368, 383, 751,
            9, 551, 391, 747, 61, 159, 407, 911, 390, 749,
            239, 278, 971, 960, 388, 662, 10, 680, 853, 855,
            296, 118, 0, 318, 358, 971, 60, 722, 859, 829,
            115, 798, 254, 893, 514, 543, 357, 845, 880, 428,
            711, 820, 225, 263, 914, 423, 997, 311, 410, 956,
            209, 528, 715, 60, 358, 572, 161, 856, 785, 934,
            593, 829, 39, 201, 834, 825, 525, 749, 687, 489,
            778, 486, 442, 705, 533, 420, 657, 696, 498, 710,
            333, 924, 455, 813, 739, 436, 623, 221, 318, 112,
        )

        val rng = LiteHashDRBG.build("parity-test-seed")
        val actual = (1..100).map { rng.nextLegacyInt(999) }.toLongArray()
        assertArrayEquals(expected, actual, "100 sequential draws must match golden values")
    }

    @Test
    fun `edge case seeds produce valid non-crashing sequences`() {
        // Zero seed
        val rng0 = LiteHashDRBG.build("0")
        val draws0 = (1..20).map { rng0.nextLegacyInt(999) }
        assertTrue(draws0.all { it in 0..999 }, "All draws from seed '0' must be in [0, 999]")

        // Empty string seed
        val rngEmpty = LiteHashDRBG.build("")
        val drawsEmpty = (1..20).map { rngEmpty.nextLegacyInt(999) }
        assertTrue(drawsEmpty.all { it in 0..999 }, "All draws from seed '' must be in [0, 999]")

        // Different seeds must produce different sequences
        val rng0b = LiteHashDRBG.build("0")
        val rngEmptyB = LiteHashDRBG.build("")
        val seq0 = (1..10).map { rng0b.nextLegacyInt(10000) }
        val seqEmpty = (1..10).map { rngEmptyB.nextLegacyInt(10000) }
        assertTrue(seq0 != seqEmpty, "Seeds '0' and '' must produce different sequences")
    }

    @Test
    fun `Long MAX_VALUE seed produces golden values`() {
        val expected = longArrayOf(187, 27, 352, 644, 374, 228, 663, 352, 122, 240)

        val rng = LiteHashDRBG.build("9223372036854775807")
        val actual = (1..10).map { rng.nextLegacyInt(999) }.toLongArray()
        assertArrayEquals(expected, actual, "Long.MAX_VALUE seed must match golden values")
    }

    @Test
    fun `100 sequential nextFloat1 draws are in valid range`() {
        val rng = LiteHashDRBG.build("float-range-test")
        val draws = (1..100).map { rng.nextFloat1() }
        assertTrue(draws.all { it >= 0.0 && it < 1.0 }, "All nextFloat1() draws must be in [0.0, 1.0)")
    }

    @Test
    fun `mixed nextLegacyInt and nextFloat1 draws are deterministic`() {
        val rng1 = LiteHashDRBG.build("mixed-draw-seed")
        val rng2 = LiteHashDRBG.build("mixed-draw-seed")

        val results1 = (1..50).map { i ->
            if (i % 2 == 0) rng1.nextLegacyInt(1000).toDouble() else rng1.nextFloat1()
        }
        val results2 = (1..50).map { i ->
            if (i % 2 == 0) rng2.nextLegacyInt(1000).toDouble() else rng2.nextFloat1()
        }
        assertEquals(results1, results2, "Mixed draw sequences must be identical for same seed")
    }

    @Test
    fun `nextLegacyInt and nextFloat1 match php dummy block vectors`() {
        val pattern = byteArrayOf(
            0x00,
            0x11,
            0x22,
            0x33,
            0x44,
            0x55,
            0x66,
            0x77,
            0x88.toByte(),
            0x99.toByte(),
            0xaa.toByte(),
            0xbb.toByte(),
            0xcc.toByte(),
            0xdd.toByte(),
            0xee.toByte(),
            0xff.toByte(),
        )
        val block = fillLiteBlock(byteArrayOf(), pattern)

        val intRng = LiteHashDummyBlockRng(listOf(block))
        intRng.nextBytes(7)
        assertEquals(0x77L, intRng.nextLegacyInt(0xff))

        val floatRng = LiteHashDummyBlockRng(listOf(block))
        floatRng.nextBytes(11)

        val floatMax = (1L shl LiteHashDRBG.MAX_RNG_SUPPORT_BIT).toDouble()
        val expectedA = 0x1100ffeeddccbbL / floatMax
        val expectedB = 0x08776655443322L / floatMax

        val actualA = floatRng.nextFloat1()
        val actualB = floatRng.nextFloat1()

        assertEquals(expectedA, actualA)
        assertTrue(actualA < 0.5313720384)
        assertTrue(actualA > 0.5313720383)
        assertEquals(expectedB, actualB)
    }
}
