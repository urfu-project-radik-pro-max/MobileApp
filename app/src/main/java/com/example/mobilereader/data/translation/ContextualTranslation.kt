package com.example.mobilereader.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ContextualTranslationRequest(
    val selectedText: String,
    val surroundingParagraph: String,
    val context: String,
    val sourceLanguage: String?,
    val targetLanguage: String,
    val instruction: String,
)

@Serializable
data class ContextualTranslationResponse(
    val translation: String,
    val explanation: String,
    val nuance: String? = null,
)

sealed interface ContextualTranslationResult {
    data object Demonstration : ContextualTranslationResult
    data class Complete(val response: ContextualTranslationResponse) : ContextualTranslationResult
}

enum class ContextualTranslationProblem {
    EMPTY_BOOK,
    INVALID_SELECTION,
    EMPTY_SELECTION,
    SELECTED_TEXT_TOO_LONG,
    PARAGRAPH_TOO_LONG,
    INVALID_SERVER_ADDRESS,
    UNENCRYPTED_CONNECTION,
    SERVER_RESPONSE,
    INVALID_RESPONSE,
    NETWORK,
}

class ContextualTranslationException(
    val problem: ContextualTranslationProblem,
    val statusCode: Int? = null,
) : Exception()

class ContextualTranslationGateway(
    private val allowUnencryptedConnection: Boolean,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun translate(
        request: ContextualTranslationRequest,
        serverAddress: String,
    ): ContextualTranslationResult {
        if (serverAddress.isBlank()) {
            return ContextualTranslationResult.Demonstration
        }

        return withContext(Dispatchers.IO) {
            val address = try {
                URL(serverAddress.trim())
            } catch (error: Exception) {
                throw ContextualTranslationException(ContextualTranslationProblem.INVALID_SERVER_ADDRESS)
            }
            if (address.protocol != "https" && address.protocol != "http") {
                throw ContextualTranslationException(ContextualTranslationProblem.INVALID_SERVER_ADDRESS)
            }
            if (address.protocol == "http" && !allowUnencryptedConnection) {
                throw ContextualTranslationException(ContextualTranslationProblem.UNENCRYPTED_CONNECTION)
            }
            val connection = try {
                address.openConnection() as HttpURLConnection
            } catch (error: Exception) {
                throw ContextualTranslationException(ContextualTranslationProblem.NETWORK)
            }
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 90_000
                connection.doOutput = true
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { output ->
                    output.write(json.encodeToString(request).toByteArray(Charsets.UTF_8))
                }
                if (connection.responseCode !in 200..299) {
                    throw ContextualTranslationException(
                        ContextualTranslationProblem.SERVER_RESPONSE,
                        connection.responseCode,
                    )
                }
                val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
                val response = try {
                    json.decodeFromString<ContextualTranslationResponse>(responseBody)
                } catch (error: Exception) {
                    throw ContextualTranslationException(ContextualTranslationProblem.INVALID_RESPONSE)
                }
                if (response.translation.isBlank() && response.explanation.isBlank()) {
                    throw ContextualTranslationException(ContextualTranslationProblem.INVALID_RESPONSE)
                }
                ContextualTranslationResult.Complete(response)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ContextualTranslationException) {
                throw error
            } catch (error: Exception) {
                throw ContextualTranslationException(ContextualTranslationProblem.NETWORK)
            } finally {
                connection.disconnect()
            }
        }
    }
}
