package com.example.mobilereader.epub

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class EpubReadingLimits(
    val mostArchiveEntries: Int = 20_000,
    val mostDeclaredBytes: Long = 4L * 1024 * 1024 * 1024,
    val mostReadBytes: Long = 768L * 1024 * 1024,
    val mostCompressionRatio: Long = 2_000,
    val mostPackageBytes: Long = 16L * 1024 * 1024,
    val mostCoverBytes: Long = 32L * 1024 * 1024,
    val mostChapterBytes: Long = 32L * 1024 * 1024,
)

class EpubReadException(message: String) : IOException(message)

class EpubArchive(
    archiveFile: File,
    private val readingLimits: EpubReadingLimits = EpubReadingLimits(),
) : Closeable {
    private val archive = ZipFile(archiveFile)
    private val archiveEntries = archive.entries().asSequence().associateBy { it.name }
    private var bytesAlreadyRead = 0L

    init {
        try {
            if (archiveEntries.size > readingLimits.mostArchiveEntries) {
                throw EpubReadException("EPUB has too many archive entries")
            }
            var declaredBytes = 0L
            for (archiveEntry in archiveEntries.values) {
                if (archiveEntry.isDirectory) continue
                if (canonicalPath(archiveEntry.name) != archiveEntry.name) {
                    throw EpubReadException("EPUB has an unsafe archive path")
                }
                val entrySize = archiveEntry.size
                if (entrySize > 0) {
                    if (declaredBytes > readingLimits.mostDeclaredBytes - entrySize) {
                        throw EpubReadException("EPUB declares too much uncompressed data")
                    }
                    declaredBytes += entrySize
                }
                val compressedSize = archiveEntry.compressedSize
                if (entrySize >= 8L * 1024 * 1024 && compressedSize > 0 &&
                    entrySize / compressedSize > readingLimits.mostCompressionRatio
                ) {
                    throw EpubReadException("EPUB has an excessive compression ratio")
                }
            }
        } catch (error: Exception) {
            archive.close()
            throw error
        }
    }

    fun readRequired(resourcePath: String, largestBytes: Long): ByteArray {
        val safePath = canonicalPath(resourcePath)
            ?: throw EpubReadException("EPUB resource path is unsafe")
        val archiveEntry = archiveEntries[safePath]
            ?: throw EpubReadException("EPUB resource is missing: $safePath")
        return readEntry(archiveEntry, largestBytes)
    }

    fun readOptional(resourcePath: String, largestBytes: Long): ByteArray? {
        val safePath = canonicalPath(resourcePath) ?: return null
        val archiveEntry = archiveEntries[safePath] ?: return null
        return runCatching { readEntry(archiveEntry, largestBytes) }.getOrNull()
    }

    fun resolveFrom(baseDirectory: String, resourceLink: String): String? {
        val cleanLink = resourceLink.substringBefore('#').substringBefore('?')
        if (cleanLink.isBlank() || cleanLink.startsWith('/') || cleanLink.contains('\\')) return null
        val decodedLink = runCatching {
            URLDecoder.decode(cleanLink.replace("+", "%2B"), Charsets.UTF_8.name())
        }.getOrNull() ?: return null
        val fullPath = if (baseDirectory.isEmpty()) decodedLink else "$baseDirectory/$decodedLink"
        return canonicalPath(fullPath)
    }

    private fun readEntry(archiveEntry: ZipEntry, largestBytes: Long): ByteArray {
        if (archiveEntry.isDirectory || archiveEntry.size > largestBytes) {
            throw EpubReadException("EPUB resource is too large")
        }
        val resultBytes = ByteArrayOutputStream()
        val bufferBytes = ByteArray(16 * 1024)
        archive.getInputStream(archiveEntry).use { resourceStream ->
            while (true) {
                val readCount = resourceStream.read(bufferBytes)
                if (readCount < 0) break
                if (readCount == 0) continue
                if (resultBytes.size().toLong() > largestBytes - readCount ||
                    bytesAlreadyRead > readingLimits.mostReadBytes - readCount
                ) {
                    throw EpubReadException("EPUB resource exceeds reading limits")
                }
                resultBytes.write(bufferBytes, 0, readCount)
                bytesAlreadyRead += readCount
            }
        }
        return resultBytes.toByteArray()
    }

    private fun canonicalPath(inputPath: String): String? {
        if (inputPath.startsWith('/') || inputPath.contains('\\') || inputPath.contains('\u0000')) {
            return null
        }
        val pathParts = mutableListOf<String>()
        for (pathPart in inputPath.split('/')) {
            when (pathPart) {
                "", "." -> Unit
                ".." -> {
                    if (pathParts.isEmpty()) return null
                    pathParts.removeAt(pathParts.lastIndex)
                }
                else -> pathParts += pathPart
            }
        }
        return pathParts.joinToString("/").takeIf { it.isNotEmpty() }
    }

    override fun close() = archive.close()
}
