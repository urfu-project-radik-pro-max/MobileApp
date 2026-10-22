package com.example.mobilereader.data.translation

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class ContextualTranslationGatewayTest {
    @Test
    fun demonstrationModeDoesNotRequireServer() = runBlocking {
        val result = ContextualTranslationGateway(allowUnencryptedConnection = false)
            .translate(request(), serverAddress = "")

        assertEquals(ContextualTranslationResult.Demonstration, result)
    }

    @Test
    fun sendsContextAndReadsStructuredResponse() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var receivedBody = ""
        server.createContext("/translate") { exchange ->
            receivedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            val response = """{"translation":"перевод","explanation":"объяснение","nuance":"оттенок"}"""
            val responseBytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { output -> output.write(responseBytes) }
        }
        server.start()

        try {
            val result = ContextualTranslationGateway(allowUnencryptedConnection = true)
                .translate(request(), "http://127.0.0.1:${server.address.port}/translate")

            assertTrue(result is ContextualTranslationResult.Complete)
            assertEquals("перевод", (result as ContextualTranslationResult.Complete).response.translation)
            val requestBody = Json.parseToJsonElement(receivedBody).jsonObject
            assertEquals("selected phrase", requestBody["selectedText"]?.jsonPrimitive?.content)
            assertEquals("whole paragraph", requestBody["surroundingParagraph"]?.jsonPrimitive?.content)
            assertEquals("previous page\n\nwhole paragraph", requestBody["context"]?.jsonPrimitive?.content)
            assertEquals("explain the nuance", requestBody["instruction"]?.jsonPrimitive?.content)
        } finally {
            server.stop(0)
        }
    }

    private fun request() = ContextualTranslationRequest(
        selectedText = "selected phrase",
        surroundingParagraph = "whole paragraph",
        context = "previous page\n\nwhole paragraph",
        sourceLanguage = "en",
        targetLanguage = "ru",
        instruction = "explain the nuance",
    )
}
