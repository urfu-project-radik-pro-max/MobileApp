package com.example.mobilereader.ui.reader

import com.example.mobilereader.data.model.BookFont
import java.io.File
import java.security.MessageDigest


internal fun embeddedFontSignature(fonts: List<BookFont>): String {
    val records = fonts.map { font ->
        EmbeddedFontRecord(
            family = font.family,
            bold = font.bold,
            italic = font.italic,
            fileState = fontFileState(File(font.path)),
        )
    }.sortedWith(
        compareBy<EmbeddedFontRecord>(
            { it.family },
            { it.bold },
            { it.italic },
            { it.fileState },
        ),
    )

    val digest = MessageDigest.getInstance("SHA-256")
    records.forEach { record ->
        digest.updateFramed(record.family)
        digest.update(if (record.bold) 1 else 0)
        digest.update(if (record.italic) 1 else 0)
        digest.updateFramed(record.fileState)
    }
    return digest.digest().toLowerHex()
}

private data class EmbeddedFontRecord(
    val family: String,
    val bold: Boolean,
    val italic: Boolean,
    val fileState: String,
)

private fun fontFileState(file: File): String {
    if (!file.isFile) return "missing"
    return runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        "bytes:${digest.digest().toLowerHex()}"
    }.getOrElse {
        "unreadable"
    }
}


private fun MessageDigest.updateFramed(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    update(byteArrayOf(
        (bytes.size ushr 24).toByte(),
        (bytes.size ushr 16).toByte(),
        (bytes.size ushr 8).toByte(),
        bytes.size.toByte(),
    ))
    update(bytes)
}

private fun ByteArray.toLowerHex(): String {
    val alphabet = "0123456789abcdef"
    return buildString(size * 2) {
        for (byte in this@toLowerHex) {
            val value = byte.toInt() and 0xff
            append(alphabet[value ushr 4])
            append(alphabet[value and 0x0f])
        }
    }
}
