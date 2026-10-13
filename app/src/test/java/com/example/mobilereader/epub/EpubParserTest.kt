package com.example.mobilereader.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun readsMetadataAndEpubTwoCover() {
        val bookFile = temporaryFolder.newFile("first.epub")
        writeBook(
            bookFile,
            """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>Quiet River</dc:title>
                    <dc:creator opf:role="aut">Mira Lane</dc:creator>
                    <dc:language>en</dc:language>
                    <meta name="cover" content="cover-id"/>
                  </metadata>
                  <manifest><item id="cover-id" href="images/cover.png" media-type="image/png"/></manifest>
                </package>
            """.trimIndent(),
            mapOf("OPS/images/cover.png" to byteArrayOf(1, 2, 3)),
        )

        val bookMetadata = EpubParser.parseMetadata(bookFile)

        assertEquals("Quiet River", bookMetadata.title)
        assertEquals("Mira Lane", bookMetadata.author)
        assertEquals("en", bookMetadata.language)
        assertArrayEquals(byteArrayOf(1, 2, 3), bookMetadata.coverBytes)
    }

    @Test
    fun readsEpubThreeAuthorRoleAndSeries() {
        val bookFile = temporaryFolder.newFile("second.epub")
        writeBook(
            bookFile,
            """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>North Wind</dc:title>
                    <dc:creator id="main-author">Alex Doe</dc:creator>
                    <meta refines="#main-author" property="role">aut</meta>
                    <dc:contributor id="translator">Nina Doe</dc:contributor>
                    <meta refines="#translator" property="role">trl</meta>
                    <meta id="series" property="belongs-to-collection">Wind Books</meta>
                    <meta refines="#series" property="group-position">2</meta>
                  </metadata>
                </package>
            """.trimIndent(),
        )

        val bookMetadata = EpubParser.parseMetadata(bookFile)

        assertEquals("Alex Doe", bookMetadata.author)
        assertEquals(listOf("Nina Doe"), bookMetadata.translators)
        assertEquals("Wind Books", bookMetadata.series)
        assertEquals(2f, bookMetadata.seriesNumber)
    }

    @Test
    fun refusesPackageOutsideArchiveRoot() {
        val bookFile = temporaryFolder.newFile("unsafe.epub")
        writeBook(bookFile, "<package/>", packagePath = "../outside.opf")

        assertThrows(EpubReadException::class.java) {
            EpubParser.parseMetadata(bookFile)
        }
    }

    private fun writeBook(
        bookFile: File,
        packageXml: String,
        extraEntries: Map<String, ByteArray> = emptyMap(),
        packagePath: String = "OPS/package.opf",
    ) {
        ZipOutputStream(bookFile.outputStream()).use { archiveOutput ->
            fun writeEntry(resourcePath: String, resourceBytes: ByteArray) {
                archiveOutput.putNextEntry(ZipEntry(resourcePath))
                archiveOutput.write(resourceBytes)
                archiveOutput.closeEntry()
            }
            writeEntry(
                "META-INF/container.xml",
                "<container><rootfiles><rootfile full-path=\"$packagePath\"/></rootfiles></container>"
                    .toByteArray(),
            )
            writeEntry("OPS/package.opf", packageXml.toByteArray())
            extraEntries.forEach { (resourcePath, resourceBytes) ->
                writeEntry(resourcePath, resourceBytes)
            }
        }
    }
}
