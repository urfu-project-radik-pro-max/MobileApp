package com.example.mobilereader.parser

import java.io.ByteArrayOutputStream


object Woff2TestFonts {


    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var current = 0
        private var bitCount = 0


        fun writeBits(value: Int, bits: Int) {
            for (i in 0 until bits) {
                current = current or (((value ushr i) and 1) shl bitCount)
                bitCount++
                if (bitCount == 8) {
                    out.write(current)
                    current = 0
                    bitCount = 0
                }
            }
        }

        fun alignByte() {
            if (bitCount > 0) {
                out.write(current)
                current = 0
                bitCount = 0
            }
        }

        fun writeBytes(bytes: ByteArray, off: Int, len: Int) {
            check(bitCount == 0) { "not byte-aligned" }
            out.write(bytes, off, len)
        }

        fun finish(): ByteArray {
            alignByte()
            return out.toByteArray()
        }
    }


    fun brotliUncompressed(data: ByteArray): ByteArray {
        val writer = BitWriter()
        writer.writeBits(0, 1)
        var off = 0
        while (off < data.size) {
            val chunk = minOf(65536, data.size - off)
            writer.writeBits(0, 1)
            writer.writeBits(0, 2)
            writer.writeBits(chunk - 1, 16)
            writer.writeBits(1, 1)
            writer.alignByte()
            writer.writeBytes(data, off, chunk)
            off += chunk
        }
        writer.writeBits(1, 1)
        writer.writeBits(1, 1)
        return writer.finish()
    }


    private val KNOWN_TAGS = arrayOf(
        "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post",
        "cvt ", "fpgm", "glyf", "loca", "prep", "CFF ", "VORG", "EBDT",
        "EBLC", "gasp", "hdmx", "kern", "LTSH", "PCLT", "VDMX", "vhea",
        "vmtx", "BASE", "GDEF", "GPOS", "GSUB", "EBSC", "JSTF", "MATH",
        "CBDT", "CBLC", "COLR", "CPAL", "SVG ", "sbix", "acnt", "avar",
        "bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx", "fvar",
        "gvar", "hsty", "just", "lcar", "mort", "morx", "opbd", "prop",
        "trak", "Zapf", "Silf", "Glat", "Gloc", "Feat", "Sill",
    )

    class Spec(
        val tag: String,

        val data: ByteArray,
        val origLength: Int = data.size,

        val transformed: Boolean = false,

        val transformVersion: Int = -1,
    )

    fun buildWoff2(tables: List<Spec>, flavor: Long = 0x00010000L): ByteArray {
        val payload = ByteArrayOutputStream()
        for (table in tables) payload.write(table.data)
        val compressed = brotliUncompressed(payload.toByteArray())

        val directory = ByteArrayOutputStream()
        for (table in tables) {
            val index = KNOWN_TAGS.indexOf(table.tag)
            val version = when {
                table.transformVersion >= 0 -> table.transformVersion
                table.transformed -> if (table.tag == "hmtx") 1 else 0
                table.tag == "glyf" || table.tag == "loca" -> 3
                else -> 0
            }
            directory.write((if (index >= 0) index else 63) or (version shl 6))
            if (index < 0) directory.write(table.tag.toByteArray(Charsets.ISO_8859_1))
            directory.write(uintBase128(table.origLength))
            if (table.transformed) directory.write(uintBase128(table.data.size))
        }
        val directoryBytes = directory.toByteArray()

        val out = ByteArrayOutputStream()
        out.write("wOF2".toByteArray())
        out.u32be(flavor)
        out.u32be((48 + directoryBytes.size + compressed.size).toLong())
        out.u16be(tables.size)
        out.u16be(0)
        out.u32be(0L)
        out.u32be(compressed.size.toLong())
        out.u16be(1)
        out.u16be(0)
        repeat(5) { out.u32be(0L) }
        out.write(directoryBytes)
        out.write(compressed)
        return out.toByteArray()
    }

    private fun uintBase128(value: Int): ByteArray {
        var v = value
        val groups = ArrayList<Int>()
        do {
            groups += v and 0x7F
            v = v ushr 7
        } while (v != 0)
        groups.reverse()
        val bytes = ByteArray(groups.size)
        for (i in groups.indices) {
            bytes[i] = (groups[i] or (if (i < groups.size - 1) 0x80 else 0)).toByte()
        }
        return bytes
    }


    val TRIANGLE = listOf(
        Triple(50, 0, true),
        Triple(550, 900, true),
        Triple(1050, 0, false),
    )


    fun minimalTtfTables(): List<Pair<String, ByteArray>> {
        val glyf = ByteArrayOutputStream()
        glyf.i16be(1)
        glyf.i16be(50); glyf.i16be(0); glyf.i16be(1050); glyf.i16be(900)
        glyf.u16be(2)
        glyf.u16be(0)
        glyf.write(0x01); glyf.write(0x01); glyf.write(0x00)
        glyf.i16be(50); glyf.i16be(500); glyf.i16be(500)
        glyf.i16be(0); glyf.i16be(900); glyf.i16be(-900)
        if (glyf.size() % 2 != 0) glyf.write(0)
        val glyfBytes = glyf.toByteArray()

        val loca = ByteArrayOutputStream()
        loca.u16be(0); loca.u16be(0); loca.u16be(glyfBytes.size / 2)

        return listOf(
            "glyf" to glyfBytes,
            "head" to headTable(indexToLocFormat = 0),
            "hhea" to hheaTable(numHMetrics = 2),
            "maxp" to maxpTable(numGlyphs = 2),
            "hmtx" to hmtxTable(),
            "loca" to loca.toByteArray(),
            "name" to byteArrayOf(0, 0, 0, 0, 0, 6),
            "cmap" to byteArrayOf(0, 0, 0, 1, 0, 3, 0, 1),
        )
    }

    fun headTable(indexToLocFormat: Int): ByteArray {
        val head = ByteArrayOutputStream()
        head.u32be(0x00010000L)
        head.u32be(0x00010000L)
        head.u32be(0L)
        head.u32be(0x5F0F3CF5L)
        head.u16be(0x0003)
        head.u16be(1000)
        repeat(4) { head.u32be(0L) }
        head.i16be(50); head.i16be(0); head.i16be(1050); head.i16be(900)
        head.u16be(0)
        head.u16be(8)
        head.i16be(2)
        head.i16be(indexToLocFormat)
        head.i16be(0)
        return head.toByteArray()
    }

    fun hheaTable(numHMetrics: Int): ByteArray {
        val hhea = ByteArrayOutputStream()
        hhea.u32be(0x00010000L)
        hhea.i16be(800); hhea.i16be(-200); hhea.i16be(0)
        hhea.u16be(600)
        hhea.i16be(0); hhea.i16be(0); hhea.i16be(1050)
        hhea.i16be(1); hhea.i16be(0); hhea.i16be(0)
        repeat(4) { hhea.i16be(0) }
        hhea.i16be(0)
        hhea.u16be(numHMetrics)
        return hhea.toByteArray()
    }

    fun maxpTable(numGlyphs: Int): ByteArray {
        val maxp = ByteArrayOutputStream()
        maxp.u32be(0x00010000L)
        maxp.u16be(numGlyphs)
        repeat(13) { maxp.u16be(0) }
        return maxp.toByteArray()
    }

    fun hmtxTable(): ByteArray {
        val hmtx = ByteArrayOutputStream()
        hmtx.u16be(500); hmtx.i16be(0)
        hmtx.u16be(600); hmtx.i16be(50)
        return hmtx.toByteArray()
    }


    fun transformedGlyf(points: List<Triple<Int, Int, Boolean>>): ByteArray {
        val nContour = ByteArrayOutputStream()
        nContour.i16be(0)
        nContour.i16be(1)

        val nPoints = ByteArrayOutputStream()
        nPoints.write(points.size)

        val flags = ByteArrayOutputStream()
        val glyphs = ByteArrayOutputStream()
        var px = 0
        var py = 0
        for ((x, y, onCurve) in points) {
            val dx = x - px
            val dy = y - py
            px = x
            py = y
            var flag = 124
            if (dx >= 0) flag = flag or 1
            if (dy >= 0) flag = flag or 2
            flags.write(flag or (if (onCurve) 0 else 0x80))
            val ax = if (dx >= 0) dx else -dx
            val ay = if (dy >= 0) dy else -dy
            glyphs.write(ax shr 8); glyphs.write(ax and 0xFF)
            glyphs.write(ay shr 8); glyphs.write(ay and 0xFF)
        }
        glyphs.write(0)

        val bbox = ByteArray(4)

        val out = ByteArrayOutputStream()
        out.u32be(0L)
        out.u16be(2)
        out.u16be(0)
        out.u32be(nContour.size().toLong())
        out.u32be(nPoints.size().toLong())
        out.u32be(flags.size().toLong())
        out.u32be(glyphs.size().toLong())
        out.u32be(0L)
        out.u32be(bbox.size.toLong())
        out.u32be(0L)
        out.write(nContour.toByteArray())
        out.write(nPoints.toByteArray())
        out.write(flags.toByteArray())
        out.write(glyphs.toByteArray())
        out.write(bbox)
        return out.toByteArray()
    }


    fun transformedHmtx(advances: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x03)
        for (advance in advances) out.u16be(advance)
        return out.toByteArray()
    }


    fun parseSfnt(sfnt: ByteArray): Map<String, ByteArray> {
        val numTables = ((sfnt[4].toInt() and 0xFF) shl 8) or (sfnt[5].toInt() and 0xFF)
        val tables = LinkedHashMap<String, ByteArray>()
        for (i in 0 until numTables) {
            val at = 12 + i * 16
            val tag = String(sfnt, at, 4, Charsets.ISO_8859_1)
            val offset = readU32(sfnt, at + 8).toInt()
            val length = readU32(sfnt, at + 12).toInt()
            tables[tag] = sfnt.copyOfRange(offset, offset + length)
        }
        return tables
    }

    private fun readU32(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toInt() and 0xFF).toLong() shl 24) or
            ((bytes[at + 1].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[at + 2].toInt() and 0xFF).toLong() shl 8) or
            (bytes[at + 3].toInt() and 0xFF).toLong()


    private fun ByteArrayOutputStream.u16be(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.i16be(value: Int) = u16be(value and 0xFFFF)

    private fun ByteArrayOutputStream.u32be(value: Long) {
        u16be(((value ushr 16) and 0xFFFF).toInt())
        u16be((value and 0xFFFF).toInt())
    }
}
