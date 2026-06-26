package com.yvesds.vt5.features.telling.controller

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests para Controllers.
 * Verifica state management y event emission.
 */
class SpeechInputControllerTest {

    @Test
    fun testSpeechEventCreation() {
        val hypotheses = listOf(
            Pair("sparrow", 0.95f),
            Pair("sparrow", 0.92f)
        )
        val partials = listOf("spar", "sparse")

        val event = SpeechInputController.SpeechEvent.HypothesesReceived(
            utteranceId = "utt-001",
            hypotheses = hypotheses,
            partials = partials
        )

        assertNotNull(event)
        assertEquals("utt-001", event.utteranceId)
        assertEquals(2, event.hypotheses.size)
        assertEquals(2, event.partials.size)
        assertEquals("sparrow", event.hypotheses[0].first)
    }

    @Test
    fun testSpeechEventError() {
        val exception = Exception("Test error")
        val event = SpeechInputController.SpeechEvent.Error("Speech failed", exception)

        assertNotNull(event)
        assertEquals("Speech failed", event.message)
        assertEquals(exception, event.exception)
    }

    @Test
    fun testBirdNetEventCreation() {
        val event = BirdNetController.BirdNetEvent.Connected
        assertNotNull(event)
        assertTrue(event is BirdNetController.BirdNetEvent.Connected)
    }

    @Test
    fun testUploadStateTransitions() {
        var state: UploadController.UploadState = UploadController.UploadState.Idle

        // Transition to Uploading
        state = UploadController.UploadState.Uploading
        assertTrue(state is UploadController.UploadState.Uploading)

        // Transition to Success
        state = UploadController.UploadState.Success("online-123")
        assertTrue(state is UploadController.UploadState.Success)
        assertEquals("online-123", (state as UploadController.UploadState.Success).onlineId)

        // Transition to Error
        state = UploadController.UploadState.Error("Failed to upload", null)
        assertTrue(state is UploadController.UploadState.Error)
        assertEquals("Failed to upload", (state as UploadController.UploadState.Error).message)
    }
}

