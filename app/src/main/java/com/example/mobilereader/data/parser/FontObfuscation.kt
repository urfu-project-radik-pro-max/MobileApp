package com.example.mobilereader.data.parser

import java.security.MessageDigest


object FontObfuscation {


    const val IDPF_ALGORITHM = "http://www.idpf.org/2008/embedding"


    const val ADOBE_ALGORITHM = "http://ns.adobe.com/pdf/enc#RC"

    const val IDPF_PREFIX = 1040
    const val ADOBE_PREFIX = 1024


    fun idpfKey(uniqueIdentifier: String): ByteArray {
        val cleaned = uniqueIdentifier.filterNot {
            it == ' ' || it == '\t' || it == '\r' || it == '\n'
        }
        return MessageDigest.getInstance("SHA-1").digest(cleaned.toByteArray(Charsets.UTF_8))
    }


    fun adobeKey(identifier: String): ByteArray? {
        var id = identifier.trim()
        val lower = id.lowercase()
        when {
            lower.startsWith("urn:uuid:") -> id = id.substring(9)
            lower.startsWith("uuid:") -> id = id.substring(5)
        }
        val hex = id.replace("-", "")
        if (hex.length != 32) return null
        val bytes = ByteArray(16)
        for (i in 0 until 16) {
            val high = hex[2 * i].digitToIntOrNull(16) ?: return null
            val low = hex[2 * i + 1].digitToIntOrNull(16) ?: return null
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }


    fun deobfuscate(bytes: ByteArray, key: ByteArray, count: Int): ByteArray {
        val out = bytes.copyOf()
        if (key.isEmpty()) return out
        val limit = minOf(count, out.size)
        for (i in 0 until limit) {
            out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return out
    }
}
