package com.example.mobilereader.parser

import com.example.mobilereader.data.model.BookTextDirection
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.FOOTNOTE_TAG
import com.example.mobilereader.data.model.LINK_TAG
import com.example.mobilereader.data.parser.EpubParser
import com.example.mobilereader.ui.reader.chapterReaderItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubSemanticBaselineTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private data class Scenario(
        val name: String,
        val document: String,
        val orderedText: List<String>,
        val selectableText: String?,
        val expectedHeadings: List<String> = emptyList(),
        val stylesheet: String? = null,
        val mediaType: String = "application/xhtml+xml",
        val extraResources: Map<String, String> = emptyMap(),
        val expectsImage: Boolean = false,
        val expectsTable: Boolean = false,
        val expectsRightToLeft: Boolean = false,
        val expectsLinkAndNote: Boolean = false,
        val fixedLayout: Boolean = false,
    )

    @Test
    fun `synthetic epub chapters preserve semantic reading order`() {
        val scenarios = listOf(
            Scenario(
                name = "ordinary-text",
                document = xhtml("<p>First sentence.</p><p>Second sentence.</p>"),
                orderedText = listOf("First sentence.", "Second sentence."),
                selectableText = "First sentence.",
            ),
            Scenario(
                name = "float-and-box-css",
                document = xhtml("<div class='frame'><p class='floating'>Float label.</p><p>Main text.</p></div>"),
                stylesheet = ".frame { padding: 1em; border: 2px solid navy; background: ivory; } " +
                    ".floating { float: left; width: 30%; margin-right: 1em; }",
                orderedText = listOf("Float label.", "Main text."),
                selectableText = "Main text.",
            ),
            Scenario(
                name = "mathml",
                document = xhtml("<p>Formula <math xmlns='http://www.w3.org/1998/Math/MathML'>" +
                    "<mi>a</mi><mo>+</mo><mi>b</mi></math> follows.</p>"),
                orderedText = listOf("Formula", "a + b", "follows."),
                selectableText = "Formula",
            ),
            Scenario(
                name = "dtbook",
                document = "<dtbook xmlns='http://www.daisy.org/z3986/2005/dtbook/'><book><bodymatter>" +
                    "<level1><levelhd>DTBook heading</levelhd><p>DTBook paragraph.</p></level1>" +
                    "</bodymatter></book></dtbook>",
                mediaType = "application/x-dtbook+xml",
                orderedText = listOf("DTBook heading", "DTBook paragraph."),
                selectableText = "DTBook paragraph.",
                expectedHeadings = listOf("DTBook heading"),
            ),
            Scenario(
                name = "right-to-left",
                document = xhtml("<p dir='rtl'>مرحبا بالعالم</p><p>Following text.</p>"),
                orderedText = listOf("مرحبا بالعالم", "Following text."),
                selectableText = "مرحبا بالعالم",
                expectsRightToLeft = true,
            ),
            Scenario(
                name = "link-and-footnote",
                document = xhtml("<p><a href='#destination'>Open destination</a> " +
                    "<a epub:type='noteref' href='#note'>1</a></p>" +
                    "<p id='destination'>Destination text.</p>" +
                    "<aside epub:type='footnote'><p id='note'>Note text.</p></aside>"),
                orderedText = listOf("Open destination", "Destination text."),
                selectableText = "Open destination",
                expectsLinkAndNote = true,
            ),
            Scenario(
                name = "image",
                document = xhtml("<p>Image introduction.</p><img src='plate.svg' alt='Blue plate'/>"),
                extraResources = mapOf("plate.svg" to "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'><rect width='10' height='10' fill='blue'/></svg>"),
                orderedText = listOf("Image introduction."),
                selectableText = "Image introduction.",
                expectsImage = true,
            ),
            Scenario(
                name = "table",
                document = xhtml("<p>Table introduction.</p><table><tr><th>Label</th><th>Value</th></tr>" +
                    "<tr><td>Alpha</td><td>One</td></tr></table>"),
                orderedText = listOf("Table introduction.", "Label", "Value", "Alpha", "One"),
                selectableText = "Table introduction.",
                expectsTable = true,
            ),
            Scenario(
                name = "fixed-with-text",
                document = xhtml("<p>Fixed page text.</p>"),
                orderedText = listOf("Fixed page text."),
                selectableText = "Fixed page text.",
                fixedLayout = true,
            ),
            Scenario(
                name = "fixed-without-text",
                document = xhtml("<img src='plate.svg' alt='Picture only'/>"),
                extraResources = mapOf("plate.svg" to "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'><circle cx='5' cy='5' r='3'/></svg>"),
                orderedText = emptyList(),
                selectableText = null,
                expectsImage = true,
                fixedLayout = true,
            ),
        )

        for (scenario in scenarios) {
            val epub = createEpub(scenario)
            val content = EpubParser.parseContent(epub, temporaryFolder.newFolder("${scenario.name}-resources"))
            val chapter = content.chapters.singleOrNull()
                ?: error("${scenario.name}: expected one readable chapter")
            val readingItems = chapterReaderItems(0, chapter.elements)
            assertEquals("${scenario.name}: changed reading order size", chapter.elements.size, readingItems.size)
            assertTrue("${scenario.name}: changed reading order", readingItems.indices.all { index ->
                readingItems[index].element === chapter.elements[index]
            })
            val semanticText = chapter.elements.mapNotNull { element ->
                when (element) {
                    is ContentElement.Paragraph -> element.text.text
                    is ContentElement.Heading -> element.text
                    is ContentElement.Table -> element.flatText()
                    else -> null
                }
            }.joinToString("\n")

            val normalizedText = semanticText.replace(Regex("\\s+"), " ").trim()
            assertEquals(scenario.expectedHeadings,
                chapter.elements.filterIsInstance<ContentElement.Heading>().map { it.text })
            var previousPosition = -1
            for (expectedText in scenario.orderedText) {
                val position = normalizedText.indexOf(expectedText, previousPosition + 1)
                assertTrue("${scenario.name}: missing or reordered '$expectedText'", position > previousPosition)
                assertEquals("${scenario.name}: duplicated '$expectedText'", 1,
                    Regex(Regex.escape(expectedText)).findAll(normalizedText).count())
                previousPosition = position
            }

            val paragraphs = chapter.elements.filterIsInstance<ContentElement.Paragraph>()
            if (scenario.selectableText == null) {
                assertTrue("${scenario.name}: unexpected selectable paragraph", paragraphs.isEmpty())
            } else {
                assertTrue("${scenario.name}: expected selectable paragraph", paragraphs.any {
                    it.text.text.contains(scenario.selectableText)
                })
            }
            if (scenario.expectsImage) {
                assertTrue("${scenario.name}: image is unavailable", chapter.elements.any {
                    it is ContentElement.Image && File(it.path).isFile
                })
            }
            if (scenario.expectsTable) {
                assertTrue("${scenario.name}: table is unavailable", chapter.elements.any {
                    it is ContentElement.Table && it.flatText().contains("Alpha")
                })
            }
            if (scenario.expectsRightToLeft) {
                assertEquals(BookTextDirection.RTL, paragraphs.first().block?.direction)
            }
            if (scenario.expectsLinkAndNote) {
                val linkedParagraph = paragraphs.first()
                val link = linkedParagraph.text.getStringAnnotations(
                    LINK_TAG, 0, linkedParagraph.text.length,
                ).single()
                val note = linkedParagraph.text.getStringAnnotations(
                    FOOTNOTE_TAG, 0, linkedParagraph.text.length,
                ).single()
                assertTrue(link.item in content.linkTargets)
                assertEquals("Note text.", content.notes.getValue(note.item).text)
            }

        }
    }

    private fun xhtml(body: String): String =
        "<html xmlns='http://www.w3.org/1999/xhtml' xmlns:epub='http://www.idpf.org/2007/ops'>" +
            "<head><link rel='stylesheet' href='style.css'/></head><body>$body</body></html>"

    private fun createEpub(scenario: Scenario): File {
        val documentName = if (scenario.mediaType == "application/x-dtbook+xml") "chapter.xml" else "chapter.xhtml"
        val stylesheetItem = if (scenario.stylesheet == null) "" else
            "<item id='style' href='style.css' media-type='text/css'/>"
        val resourceItems = scenario.extraResources.keys.mapIndexed { index, resourceName ->
            "<item id='resource$index' href='$resourceName' media-type='image/svg+xml'/>"
        }.joinToString("")
        val rendition = if (scenario.fixedLayout) {
            "<meta property='rendition:layout'>pre-paginated</meta>"
        } else {
            ""
        }
        val packageDocument = "<package xmlns='http://www.idpf.org/2007/opf' " +
            "xmlns:dc='http://purl.org/dc/elements/1.1/' version='3.0'>" +
            "<metadata><dc:title>${scenario.name}</dc:title>$rendition</metadata>" +
            "<manifest><item id='chapter' href='$documentName' media-type='${scenario.mediaType}'/>" +
            "$stylesheetItem$resourceItems</manifest><spine><itemref idref='chapter'/></spine></package>"
        val file = temporaryFolder.newFile("${scenario.name}.epub")
        ZipOutputStream(file.outputStream()).use { archive ->
            fun writeEntry(path: String, content: String) {
                archive.putNextEntry(ZipEntry(path))
                archive.write(content.toByteArray())
                archive.closeEntry()
            }

            writeEntry("mimetype", "application/epub+zip")
            writeEntry("META-INF/container.xml",
                "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'>" +
                    "<rootfiles><rootfile full-path='OPS/package.opf' " +
                    "media-type='application/oebps-package+xml'/></rootfiles></container>")
            writeEntry("OPS/package.opf", packageDocument)
            writeEntry("OPS/$documentName", scenario.document)
            scenario.stylesheet?.let { writeEntry("OPS/style.css", it) }
            scenario.extraResources.forEach { (path, content) -> writeEntry("OPS/$path", content) }
        }
        return file
    }
}
