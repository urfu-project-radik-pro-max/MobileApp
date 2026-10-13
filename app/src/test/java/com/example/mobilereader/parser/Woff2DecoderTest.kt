package com.example.mobilereader.parser

import com.example.mobilereader.data.parser.Woff2Decoder
import com.example.mobilereader.data.parser.looksLikeFont
import org.brotli.dec.BrotliInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class Woff2DecoderTest {

    @Test
    fun `brotli helper round-trips through the real decoder`() {

        val data = ByteArray(200_000) { (it * 31 and 0xFF).toByte() }
        val stream = Woff2TestFonts.brotliUncompressed(data)
        val decoded = BrotliInputStream(ByteArrayInputStream(stream)).use { it.readBytes() }
        assertArrayEquals(data, decoded)
    }

    @Test
    fun `null-transform woff2 round-trips every table byte for byte`() {
        val tables = Woff2TestFonts.minimalTtfTables()
        val woff2 = Woff2TestFonts.buildWoff2(
            tables.map { (tag, data) -> Woff2TestFonts.Spec(tag, data) },
        )
        assertTrue(Woff2Decoder.isWoff2(woff2))

        val sfnt = Woff2Decoder.decode(woff2)
        assertTrue(sfnt != null && looksLikeFont(sfnt))
        val parsed = Woff2TestFonts.parseSfnt(sfnt!!)
        assertEquals(tables.size, parsed.size)
        for ((tag, data) in tables) {
            assertArrayEquals("table $tag", data, parsed[tag])
        }

        assertEquals(parsed.keys.toList(), parsed.keys.sorted())
    }

    private fun transformedFont(): ByteArray {
        val glyf = Woff2TestFonts.transformedGlyf(Woff2TestFonts.TRIANGLE)
        return Woff2TestFonts.buildWoff2(
            listOf(
                Woff2TestFonts.Spec("glyf", glyf, origLength = 64, transformed = true),
                Woff2TestFonts.Spec("loca", ByteArray(0), origLength = 6, transformed = true),
                Woff2TestFonts.Spec("head", Woff2TestFonts.headTable(indexToLocFormat = 1)),
                Woff2TestFonts.Spec("hhea", Woff2TestFonts.hheaTable(numHMetrics = 2)),
                Woff2TestFonts.Spec("maxp", Woff2TestFonts.maxpTable(numGlyphs = 2)),
                Woff2TestFonts.Spec("hmtx", Woff2TestFonts.hmtxTable()),
            ),
        )
    }

    @Test
    fun `transformed glyf reconstructs points loca and bbox`() {
        val sfnt = Woff2Decoder.decode(transformedFont())
        assertTrue(sfnt != null && looksLikeFont(sfnt))
        val parsed = Woff2TestFonts.parseSfnt(sfnt!!)


        val glyf = parsed.getValue("glyf")
        val loca = parsed.getValue("loca")
        assertEquals(6, loca.size)
        assertEquals(0, u16(loca, 0))
        assertEquals(0, u16(loca, 2))
        assertEquals(glyf.size / 2, u16(loca, 4))


        val head = parsed.getValue("head")
        assertEquals(0, u16(head, 50))


        assertEquals(1, i16(glyf, 0))
        assertEquals(50, i16(glyf, 2))
        assertEquals(0, i16(glyf, 4))
        assertEquals(1050, i16(glyf, 6))
        assertEquals(900, i16(glyf, 8))
        assertEquals(2, u16(glyf, 10))
        assertEquals(0, u16(glyf, 12))
        val points = parseSimpleGlyphPoints(glyf, pointCount = 3)
        assertEquals(
            Woff2TestFonts.TRIANGLE,
            points,
        )
    }

    @Test
    fun `transformed hmtx reconstructs omitted lsbs from glyf xmin`() {
        val glyf = Woff2TestFonts.transformedGlyf(Woff2TestFonts.TRIANGLE)
        val woff2 = Woff2TestFonts.buildWoff2(
            listOf(
                Woff2TestFonts.Spec("glyf", glyf, origLength = 64, transformed = true),
                Woff2TestFonts.Spec("loca", ByteArray(0), origLength = 6, transformed = true),
                Woff2TestFonts.Spec("head", Woff2TestFonts.headTable(indexToLocFormat = 0)),
                Woff2TestFonts.Spec("hhea", Woff2TestFonts.hheaTable(numHMetrics = 2)),
                Woff2TestFonts.Spec("maxp", Woff2TestFonts.maxpTable(numGlyphs = 2)),
                Woff2TestFonts.Spec(
                    "hmtx",
                    Woff2TestFonts.transformedHmtx(intArrayOf(500, 600)),
                    origLength = 8,
                    transformed = true,
                ),
            ),
        )
        val sfnt = Woff2Decoder.decode(woff2)
        assertTrue(sfnt != null)
        val hmtx = Woff2TestFonts.parseSfnt(sfnt!!).getValue("hmtx")
        assertEquals(8, hmtx.size)
        assertEquals(500, u16(hmtx, 0))
        assertEquals(0, i16(hmtx, 2))
        assertEquals(600, u16(hmtx, 4))
        assertEquals(50, i16(hmtx, 6))
    }

    @Test
    fun `collections and damage degrade to null`() {

        val ttc = Woff2TestFonts.buildWoff2(
            Woff2TestFonts.minimalTtfTables().map { (tag, data) -> Woff2TestFonts.Spec(tag, data) },
            flavor = 0x74746366L,
        )
        assertNull(Woff2Decoder.decode(ttc))


        val good = Woff2TestFonts.buildWoff2(
            Woff2TestFonts.minimalTtfTables().map { (tag, data) -> Woff2TestFonts.Spec(tag, data) },
        )
        assertNull(Woff2Decoder.decode(good.copyOf(good.size / 2)))
        assertNull(Woff2Decoder.decode(good.copyOf(30)))


        val badVersion = Woff2TestFonts.buildWoff2(
            Woff2TestFonts.minimalTtfTables().map { (tag, data) ->
                Woff2TestFonts.Spec(tag, data, transformVersion = if (tag == "glyf") 1 else -1)
            },
        )
        assertNull(Woff2Decoder.decode(badVersion))


        assertNull(Woff2Decoder.decode(ByteArray(100) { 7 }))
    }

    @Test
    fun `decoded output budget rejects brotli table expansion before allocation`() {
        val table = ByteArray(4_000) { (it and 7).toByte() }
        val expanding = Woff2TestFonts.buildWoff2(
            listOf(Woff2TestFonts.Spec("name", table)),
        )
        assertNull(Woff2Decoder.decode(expanding, maxDecodedBytes = 1_024))
        assertTrue(Woff2Decoder.decode(expanding, maxDecodedBytes = 8_192) != null)
    }

    @Test
    fun `directory cannot understate Brotli output and hide trailing decoded bytes`() {
        val valid = Woff2TestFonts.buildWoff2(
            listOf(Woff2TestFonts.Spec("name", ByteArray(64) { it.toByte() })),
        )


        assertEquals(64, valid[49].toInt() and 0xFF)
        val understated = valid.copyOf()
        understated[49] = 63
        assertNull(Woff2Decoder.decode(understated))
    }


    private fun u16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun i16(bytes: ByteArray, at: Int): Int = u16(bytes, at).toShort().toInt()


    private fun parseSimpleGlyphPoints(
        glyf: ByteArray,
        pointCount: Int,
    ): List<Triple<Int, Int, Boolean>> {
        var pos = 14
        val flags = IntArray(pointCount)
        for (i in 0 until pointCount) {
            flags[i] = glyf[pos++].toInt() and 0xFF
            assertEquals("no repeat flags expected", 0, flags[i] and 0x08)
        }
        val xs = IntArray(pointCount)
        var x = 0
        for (i in 0 until pointCount) {
            val flag = flags[i]
            x += when {
                flag and 0x02 != 0 -> {
                    val magnitude = glyf[pos++].toInt() and 0xFF
                    if (flag and 0x10 != 0) magnitude else -magnitude
                }
                flag and 0x10 != 0 -> 0
                else -> i16(glyf, pos).also { pos += 2 }
            }
            xs[i] = x
        }
        val ys = IntArray(pointCount)
        var y = 0
        for (i in 0 until pointCount) {
            val flag = flags[i]
            y += when {
                flag and 0x04 != 0 -> {
                    val magnitude = glyf[pos++].toInt() and 0xFF
                    if (flag and 0x20 != 0) magnitude else -magnitude
                }
                flag and 0x20 != 0 -> 0
                else -> i16(glyf, pos).also { pos += 2 }
            }
            ys[i] = y
        }
        return (0 until pointCount).map { Triple(xs[it], ys[it], flags[it] and 0x01 != 0) }
    }
}
