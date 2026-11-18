package com.example

import com.example.data.model.AiModelCatalog
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.example.data.repository.AiProviderRepository
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResilienceTest {

    @Test
    fun `sanitizeChatHistory drops orphaned trailing user turns and leading model turns`() {
        val raw = listOf(
            ChatMessage(id = "0", senderRole = SenderRole.model.name, content = "stray greeting"),
            ChatMessage(id = "1", senderRole = SenderRole.user.name, content = "Hello"),
            ChatMessage(id = "2", senderRole = SenderRole.model.name, content = "Hi there"),
            ChatMessage(id = "3", senderRole = SenderRole.user.name, content = "Failed question 1"),
            ChatMessage(id = "4", senderRole = SenderRole.user.name, content = "Failed question 2")
        )

        val cleaned = AiProviderRepository.sanitizeChatHistory(raw)

        assertEquals(2, cleaned.size)
        assertEquals(SenderRole.user, cleaned[0].resolvedSenderRole())
        assertEquals("Hello", cleaned[0].content)
        assertEquals(SenderRole.model, cleaned[1].resolvedSenderRole())
        assertEquals("Hi there", cleaned[1].content)
    }

    @Test
    fun `sanitizeChatHistory merges consecutive same-role messages inside valid turns`() {
        val raw = listOf(
            ChatMessage(id = "1", senderRole = SenderRole.user.name, content = "Part 1"),
            ChatMessage(id = "2", senderRole = SenderRole.user.name, content = "Part 2"),
            ChatMessage(id = "3", senderRole = SenderRole.model.name, content = "Answer 1"),
            ChatMessage(id = "4", senderRole = SenderRole.model.name, content = "Answer 2")
        )

        val cleaned = AiProviderRepository.sanitizeChatHistory(raw)

        assertEquals(2, cleaned.size)
        assertEquals(SenderRole.user, cleaned[0].resolvedSenderRole())
        assertEquals("Part 1\n\nPart 2", cleaned[0].content)
        assertEquals(SenderRole.model, cleaned[1].resolvedSenderRole())
        assertEquals("Answer 1\n\nAnswer 2", cleaned[1].content)
    }

    @Test
    fun `text and image fallback chains preserve requested primary and add valid 3x fallbacks`() {
        val proChain = AiProviderRepository.textModelFallbackChain(AiModelCatalog.PRO_REASONING_MODEL.id)
        assertEquals(
            listOf(
                AiModelCatalog.PRO_REASONING_MODEL.id,
                AiModelCatalog.DEFAULT_TEXT_MODEL.id,
                AiModelCatalog.FAST_TEXT_MODEL.id
            ),
            proChain
        )

        val defaultChain = AiProviderRepository.textModelFallbackChain(AiModelCatalog.DEFAULT_TEXT_MODEL.id)
        assertEquals(
            listOf(AiModelCatalog.DEFAULT_TEXT_MODEL.id, AiModelCatalog.FAST_TEXT_MODEL.id),
            defaultChain
        )

        val imageChain = AiProviderRepository.imageModelFallbackChain(AiModelCatalog.LITE_IMAGE_MODEL.id)
        assertEquals(AiModelCatalog.LITE_IMAGE_MODEL.id, imageChain.first())
        assertEquals(3, imageChain.size)
    }

    @Test
    fun `formatUserFacingError condenses noisy quota dumps into clear guidance`() {
        val noisyQuotaError = IllegalStateException(
            "You exceeded your current quota, please check your plan and billing details.\n" +
                "* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0"
        )

        val imgMsg = AiProviderRepository.formatUserFacingError(noisyQuotaError, isImageStudio = true)
        assertTrue(imgMsg.contains("Blaze"))
        assertTrue(imgMsg.length < 260)

        val chatMsg = AiProviderRepository.formatUserFacingError(noisyQuotaError, isImageStudio = false)
        assertTrue(chatMsg.contains("Flash-Lite"))
        assertTrue(chatMsg.length < 200)

        val appCheckMsg = AiProviderRepository.formatUserFacingError(
            IllegalStateException("HTTP 403 App Check token rejected")
        )
        assertTrue(appCheckMsg.contains("App Check"))
    }

    @Test
    fun `cerevix system instruction enforces brand identity and multilingual reply policy`() {
        val sys = AiProviderRepository.CEREVIX_SYSTEM_INSTRUCTION
        assertTrue(sys.contains("CEREVIX AI"))
        assertTrue(sys.contains("English"))
        assertTrue(sys.contains("same language"))
        assertTrue(sys.contains("trained by Google"))
    }

    @Test
    fun `attachment mime classification handles text code pdf and images safely`() {
        assertTrue(AiProviderRepository.isTextLikeMime("text/plain"))
        assertTrue(AiProviderRepository.isTextLikeMime("text/x-kotlin"))
        assertTrue(AiProviderRepository.isTextLikeMime("application/json"))
        assertTrue(AiProviderRepository.isBinaryInlineSupportedMime("application/pdf"))
        assertTrue(AiProviderRepository.isBinaryInlineSupportedMime("image/png"))
    }
}
