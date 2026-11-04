package com.example.mobilereader.ui.reader

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.mobilereader.data.PageMargins
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.BookFont
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PaginationSpecKeyTest {

    @Test
    fun `side margins change measured page width and cache key`() {
        val density = Density(2f)
        val margins = listOf(PageMargins.DP_6, PageMargins.DP_20, PageMargins.DP_40)
        val widths = margins.map { margin ->
            with(density) { ReaderMetrics.contentWidth(400.dp, margin).roundToPx() }
        }
        assertEquals(listOf(776, 720, 640), widths)
        val keys = margins.mapIndexed { index, margin ->
            PaginationSpec(
                contentWidthPx = widths[index],
                contentHeightPx = 1_200,
                density = density,
                settings = ReaderSettings(pageMargins = margin),
                fontSize = 18f,
            ).key
        }
        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `touch positions select one of nine equal cells`() {
        assertEquals(0, pageMarginStepAt(0f, 90f))
        assertEquals(4, pageMarginStepAt(45f, 90f))
        assertEquals(8, pageMarginStepAt(89f, 90f))
    }

    @Test
    fun `reader font size changes the page format`() {
        fun spec(fontSize: Float) = PaginationSpec(
            contentWidthPx = 800,
            contentHeightPx = 1_200,
            density = Density(2f),
            settings = ReaderSettings(),
            fontSize = fontSize,
        )

        assertNotEquals(spec(18f).key, spec(24f).key)
    }

    @Test
    fun `app heading preference invalidates cached pagination`() {
        fun key(centered: Boolean) = PaginationSpec(
            contentWidthPx = 800, contentHeightPx = 1_200,
            density = Density(2f), settings = ReaderSettings(centerHeadings = centered), fontSize = 18f,
        ).key
        assertNotEquals(key(false), key(true))
    }

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `density and system font scale invalidate page layout`() {
        fun key(density: Float, fontScale: Float) = PaginationSpec(
            contentWidthPx = 800,
            contentHeightPx = 1_200,
            density = Density(density = density, fontScale = fontScale),
            settings = ReaderSettings(),
            fontSize = 18f,
        ).key

        val baseline = key(density = 2f, fontScale = 1f)
        assertNotEquals(baseline, key(density = 2.25f, fontScale = 1f))
        assertNotEquals(baseline, key(density = 2f, fontScale = 1.2f))
    }

    @Test
    fun `changing embedded font bytes changes signature and pagination key`() {
        val file = temp.newFile("publisher.ttf")
        val face = BookFont("publisher serif", file.absolutePath, bold = false, italic = false)
        file.writeBytes(byteArrayOf(1, 2, 3))
        val firstSignature = embeddedFontSignature(listOf(face))
        val firstKey = keyForEmbeddedFonts(firstSignature)

        file.writeBytes(byteArrayOf(1, 2, 4))
        val secondSignature = embeddedFontSignature(listOf(face))
        val secondKey = keyForEmbeddedFonts(secondSignature)

        assertNotEquals(firstSignature, secondSignature)
        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun `font signature is stable across extraction paths and list order`() {
        val first = temp.newFile("first.ttf").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val second = temp.newFile("second.ttf").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val bold = temp.newFile("bold.ttf").apply { writeBytes(byteArrayOf(6, 5, 4)) }

        val one = embeddedFontSignature(
            listOf(
                BookFont("publisher serif", first.absolutePath, bold = false, italic = false),
                BookFont("publisher serif", bold.absolutePath, bold = true, italic = false),
            ),
        )
        val two = embeddedFontSignature(
            listOf(
                BookFont("publisher serif", bold.absolutePath, bold = true, italic = false),
                BookFont("publisher serif", second.absolutePath, bold = false, italic = false),
            ),
        )

        assertEquals(one, two)
    }

    @Test
    fun `missing font state differs from a present empty font`() {
        val missing = temp.root.resolve("missing.ttf")
        val empty = temp.newFile("empty.ttf")

        assertNotEquals(
            embeddedFontSignature(
                listOf(BookFont("publisher serif", missing.absolutePath, false, false)),
            ),
            embeddedFontSignature(
                listOf(BookFont("publisher serif", empty.absolutePath, false, false)),
            ),
        )
    }

    private fun keyForEmbeddedFonts(signature: String): String = PaginationSpec(
        contentWidthPx = 800,
        contentHeightPx = 1_200,
        density = Density(density = 2f, fontScale = 1f),
        settings = ReaderSettings(),
        fontSize = 18f,
        embeddedFontSignature = signature,
    ).key
}
