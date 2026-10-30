package com.example.mobilereader.parser

import com.example.mobilereader.data.requireSelectableEpubText
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.parser.EpubParser
import com.example.mobilereader.data.parser.ReaderResourceLimits
import com.example.mobilereader.data.parser.ResourceLimitException
import com.example.mobilereader.data.parser.ResourceLimitKind
import com.example.mobilereader.ui.reader.chapterReaderItems
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubImportSafetyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing required chapter reports its path`() {
        val file = epub(listOf(ChapterFile("missing.xhtml", "", includeEntry = false)))

        val problem = assertThrows(IllegalArgumentException::class.java) {
            EpubParser.parseContent(file, temporaryFolder.newFolder("missing-images"))
        }

        assertTrue(problem.message.orEmpty().contains("missing.xhtml"))
        assertTrue(problem.message.orEmpty().contains("missing"))
    }

    @Test
    fun `unreadable required chapter reports why it failed`() {
        val document = "<html><head><title>Service title</title><script>Hidden text</script></head></html>"
        val file = epub(listOf(ChapterFile("empty.xhtml", document)))

        val problem = assertThrows(IllegalArgumentException::class.java) {
            EpubParser.parseContent(file, temporaryFolder.newFolder("empty-images"))
        }

        assertTrue(problem.message.orEmpty().contains("empty.xhtml"))
        assertTrue(problem.message.orEmpty().contains("readable"))
    }

    @Test
    fun `missing DTBook book recovers heading paragraph list math and link`() {
        val document = """
            <dtbook xmlns="http://www.daisy.org/z3986/2005/dtbook/">
              <head><title>Service title</title><script>Hidden script</script></head>
              <bodymatter>
                <level1><levelhd>Recovered chapter</levelhd>
                  <p>Open <a href="#target">the link</a>.</p>
                  <list><li>First item</li></list>
                  <p id="target">Math <math xmlns="http://www.w3.org/1998/Math/MathML"><mi>x</mi><mo>+</mo><mn>1</mn></math>.</p>
                </level1>
              </bodymatter>
            </dtbook>
        """.trimIndent()
        val file = epub(listOf(ChapterFile("chapter.xml", document, "application/x-dtbook+xml")))

        val content = EpubParser.parseContent(file, temporaryFolder.newFolder("dtbook-images"))
        val text = content.chapters.single().elements.joinToString(" ") { element ->
            when (element) {
                is ContentElement.Paragraph -> element.text.text
                is ContentElement.Heading -> element.text
                else -> ""
            }
        }

        assertTrue(text.contains("Recovered chapter"))
        assertTrue(text.contains("the link"))
        assertTrue(text.contains("First item"))
        assertTrue(text.contains("x + 1"))
        assertFalse(text.contains("Service title"))
        assertFalse(text.contains("Hidden script"))
        requireSelectableEpubText(content)
    }

    @Test
    fun `XHTML with missing explicit body retains readable content`() {
        val document = """
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>Service title</title></head>
              <main><h1>Recovered heading</h1><p>Recovered paragraph.</p></main>
            </html>
        """.trimIndent()
        val file = epub(listOf(ChapterFile("chapter.xhtml", document)))

        val content = EpubParser.parseContent(file, temporaryFolder.newFolder("xhtml-images"))
        val text = content.chapters.single().elements.joinToString(" ") { element ->
            when (element) {
                is ContentElement.Paragraph -> element.text.text
                is ContentElement.Heading -> element.text
                else -> ""
            }
        }

        assertTrue(text.contains("Recovered heading"))
        assertTrue(text.contains("Recovered paragraph."))
        assertFalse(text.contains("Service title"))
    }

    @Test
    fun `image page survives beside text but image-only book cannot be imported`() {
        val imageDocument = "<html><body><img src='page.png' alt='Scanned page'/></body></html>"
        val picture = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val imageOnly = epub(
            listOf(ChapterFile("image.xhtml", imageDocument)),
            mapOf("page.png" to picture),
        )
        val imageOnlyContent = EpubParser.parseContent(
            imageOnly,
            temporaryFolder.newFolder("image-only-images"),
        )
        assertTrue(imageOnlyContent.chapters.single().elements.any { it is ContentElement.Image })
        val problem = assertThrows(IllegalArgumentException::class.java) {
            requireSelectableEpubText(imageOnlyContent)
        }
        assertTrue(problem.message.orEmpty().contains("no selectable text"))

        val mixed = epub(
            listOf(
                ChapterFile("text.xhtml", "<html><body><p>Selectable chapter.</p></body></html>"),
                ChapterFile("image.xhtml", imageDocument),
            ),
            mapOf("page.png" to picture),
        )
        val mixedContent = EpubParser.parseContent(mixed, temporaryFolder.newFolder("mixed-images"))
        assertEquals(2, mixedContent.chapters.size)
        val readingItems = mixedContent.chapters.flatMapIndexed { chapterIndex, chapter ->
            chapterReaderItems(chapterIndex, chapter.elements)
        }
        assertEquals(mixedContent.chapters.sumOf { it.elements.size }, readingItems.size)
        assertEquals(listOf(0, 1), readingItems.map { it.chapterIndex }.distinct())
        assertTrue(readingItems.first().element is ContentElement.Paragraph)
        assertTrue(readingItems.last().element is ContentElement.Image)
        assertTrue(mixedContent.chapters.last().elements.any { it is ContentElement.Image })
        requireSelectableEpubText(mixedContent)
    }

    @Test
    fun `resource limits and damaged archives are not hidden by recovery`() {
        val document = "<dtbook><bodymatter><p>${"text".repeat(400)}</p></bodymatter></dtbook>"
        val file = epub(listOf(ChapterFile("large.xml", document, "application/x-dtbook+xml")))
        val limits = ReaderResourceLimits.DEFAULT.copy(maxChapterBytes = 512)
        val limitProblem = assertThrows(ResourceLimitException::class.java) {
            EpubParser.parseContent(file, temporaryFolder.newFolder("limit-images"), limits)
        }
        assertEquals(ResourceLimitKind.ENTRY_SIZE, limitProblem.kind)

        val damaged = temporaryFolder.newFile("damaged.epub")
        damaged.writeText("not a ZIP archive")
        assertThrows(Exception::class.java) {
            EpubParser.parseContent(damaged, temporaryFolder.newFolder("damaged-images"))
        }
    }

    private data class ChapterFile(
        val path: String,
        val document: String,
        val mediaType: String = "application/xhtml+xml",
        val includeEntry: Boolean = true,
    )

    private fun epub(
        chapters: List<ChapterFile>,
        resources: Map<String, ByteArray> = emptyMap(),
    ): File {
        val manifest = chapters.mapIndexed { index, chapter ->
            "<item id='chapter$index' href='${chapter.path}' media-type='${chapter.mediaType}'/>"
        }.joinToString("")
        val spine = chapters.indices.joinToString("") { index ->
            "<itemref idref='chapter$index'/>"
        }
        val packageDocument = """
            <package xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
              <metadata><dc:title>Safety fixture</dc:title><dc:language>en</dc:language></metadata>
              <manifest>$manifest</manifest><spine>$spine</spine>
            </package>
        """.trimIndent()
        val entries = linkedMapOf(
            "META-INF/container.xml" to
                "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>".toByteArray(),
            "book.opf" to packageDocument.toByteArray(),
        )
        chapters.filter { it.includeEntry }.forEach { chapter ->
            entries[chapter.path] = chapter.document.toByteArray()
        }
        entries.putAll(resources)
        val file = temporaryFolder.newFile("fixture-${System.nanoTime()}.epub")
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, bytes) ->
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }
}
