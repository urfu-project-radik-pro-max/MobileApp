package com.example.mobilereader.data.parser

import java.security.MessageDigest


internal fun resourceDigest(canonical: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))

    return buildString(32) {
        for (index in 0 until 16) append("%02x".format(bytes[index].toInt() and 0xff))
    }
}

internal fun resourceCacheFileName(
    prefix: String,
    canonical: String,
    displayName: String,
): String {
    val leaf = displayName.substringAfterLast('/')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .takeLast(80)
        .ifBlank { "resource" }
    return "${prefix}_${resourceDigest(canonical)}_$leaf"
}
