package com.example.mobilereader.reader

import androidx.compose.ui.text.AnnotatedString
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.InlineImageSize
import com.example.mobilereader.data.model.BlockAlign
import com.example.mobilereader.data.model.BlockStyle
import com.example.mobilereader.data.model.TableCell
import com.example.mobilereader.data.model.TableRow
import com.example.mobilereader.data.parser.InlineTextBuilder
import com.example.mobilereader.ui.reader.BookPage
import com.example.mobilereader.ui.reader.PagePart
import com.example.mobilereader.ui.reader.PaginationCache
import com.example.mobilereader.ui.reader.ReaderItem
import com.example.mobilereader.ui.reader.TableGrid
import com.example.mobilereader.ui.reader.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PaginationCacheTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `changed text invalidates cached pages`() {
        val original = ContentElement.Paragraph(AnnotatedString("First text"))
        val items = listOf(ReaderItem(0, original))
        val pages = listOf(
            BookPage(
                parts = listOf(
                    PagePart(0, original, text = original.text, charStart = 0, charEnd = original.text.length),
                ),
                firstItemIndex = 0,
            ),
        )
        val file = temporaryFolder.newFile("pages.json")
        PaginationCache.save(file, "layout", items, pages)

        assertNotNull(PaginationCache.load(file, "layout", items))
        assertNull(
            PaginationCache.load(
                file,
                "layout",
                listOf(ReaderItem(0, ContentElement.Paragraph(AnnotatedString("Other text")))),
            ),
        )
    }

    @Test
    fun `table rows survive a cache round trip`() {
        val table = ContentElement.Table(
            rows = listOf(TableRow(cells = listOf(TableCell(AnnotatedString("Readable cell"))), isHeader = false)),
        )
        val layout = TableLayout(
            grid = TableGrid.build(table.rows),
            colWidthsPx = intArrayOf(180),
            rowHeightsPx = intArrayOf(42),
            fontScale = 1f,
        )
        val items = listOf(ReaderItem(0, table))
        val file = temporaryFolder.newFile("table.json")
        PaginationCache.save(
            file,
            "layout",
            items,
            listOf(
                BookPage(
                    parts = listOf(
                        PagePart(0, table, rowStart = 0, rowEnd = 1, tableLayout = layout),
                    ),
                    firstItemIndex = 0,
                ),
            ),
        )

        val restored = PaginationCache.load(file, "layout", items)!!.single().parts.single()
        assertEquals("Readable cell", (restored.element as ContentElement.Table).flatText())
        assertEquals(180, restored.tableLayout!!.colWidthsPx.single())
        assertEquals(42, restored.tableLayout.rowHeightsPx.single())
    }

    @Test
    fun `image size and author text style invalidate cached pages`() {
        val imageFile = temporaryFolder.newFile("plate.svg")
        imageFile.writeText("<svg viewBox='0 0 200 100'></svg>")
        val image = ContentElement.Image(imageFile.absolutePath, widthFrac = 0.25f)
        val paragraph = ContentElement.Paragraph(AnnotatedString("Readable text"),
            block = BlockStyle(fontScale = 1.5f, align = BlockAlign.CENTER))
        val items = listOf(ReaderItem(0, image), ReaderItem(0, paragraph))
        val pages = listOf(BookPage(
            parts = listOf(PagePart(0, image, imageHeightPx = 50, imageWidthPx = 100),
                PagePart(1, paragraph, text = paragraph.text, charStart = 0,
                    charEnd = paragraph.text.length)),
            firstItemIndex = 0,
        ))
        val file = temporaryFolder.newFile("styled-pages.json")
        PaginationCache.save(file, "layout", items, pages)

        val cached = PaginationCache.load(file, "layout", items)
        assertNotNull(cached)
        assertEquals(100, cached?.single()?.parts?.first()?.imageWidthPx)
        assertNull(PaginationCache.load(file, "layout", listOf(
            ReaderItem(0, image.copy(widthFrac = 0.5f)), ReaderItem(0, paragraph))))
        assertNull(PaginationCache.load(file, "layout", listOf(
            ReaderItem(0, image), ReaderItem(0, paragraph.copy(
                block = paragraph.block?.copy(fontScale = 2f))))))
        assertNull(PaginationCache.load(file, "layout", listOf(
            ReaderItem(0, image), ReaderItem(0, paragraph.copy(
                block = paragraph.block?.copy(align = BlockAlign.RIGHT))))))
    }

    @Test
    fun `inline image size changes the pagination signature`() {
        val image = temporaryFolder.newFile("inline.svg")
        image.writeText("<svg viewBox='0 0 200 100'></svg>")

        fun paragraph(widthEm: Float): ContentElement.Paragraph {
            val builder = InlineTextBuilder()
            builder.text("Before ")
            builder.inlineImage(
                ref = image.absolutePath,
                size = InlineImageSize(widthEm = widthEm),
            )
            builder.text(" after")
            return ContentElement.Paragraph(builder.build())
        }

        val narrow = listOf(ReaderItem(0, paragraph(1f)))
        val wide = listOf(ReaderItem(0, paragraph(3f)))
        val first = narrow.single().element as ContentElement.Paragraph
        val pages = listOf(
            BookPage(
                parts = listOf(
                    PagePart(
                        itemIndex = 0,
                        element = first,
                        text = first.text,
                        charStart = 0,
                        charEnd = first.text.length,
                    ),
                ),
                firstItemIndex = 0,
            ),
        )
        val file = temporaryFolder.newFile("inline-pages.json")
        PaginationCache.save(file, "layout", narrow, pages)

        assertNotEquals(
            PaginationCache.contentSignature(narrow),
            PaginationCache.contentSignature(wide),
        )
        assertNotNull(PaginationCache.load(file, "layout", narrow))
        assertNull(PaginationCache.load(file, "layout", wide))
    }
}
