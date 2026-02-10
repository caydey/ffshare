package com.caydey.ffshare

import android.net.Uri
import com.caydey.ffshare.service.EncodingState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncodingStateTest {

    @Test
    fun idle_state_isSingleton() {
        assertSame(EncodingState.Idle, EncodingState.Idle)
    }

    @Test
    fun cancelled_state_isSingleton() {
        assertSame(EncodingState.Cancelled, EncodingState.Cancelled)
    }

    @Test
    fun encoding_state_holdsCorrectValues() {
        val state = EncodingState.Encoding(
            currentFile = 1,
            totalFiles = 3,
            fileName = "test.mp4",
            progressPercent = 50.5f,
            processedTime = 30000,
            totalDuration = 60000,
            estimatedTimeRemaining = 30000L,
            outputSize = 1024000L
        )

        assertEquals(1, state.currentFile)
        assertEquals(3, state.totalFiles)
        assertEquals("test.mp4", state.fileName)
        assertEquals(50.5f, state.progressPercent, 0.01f)
        assertEquals(30000, state.processedTime)
        assertEquals(60000, state.totalDuration)
        assertEquals(30000L, state.estimatedTimeRemaining)
        assertEquals(1024000L, state.outputSize)
    }

    @Test
    fun encoding_state_equality() {
        val state1 = EncodingState.Encoding(
            currentFile = 1,
            totalFiles = 1,
            fileName = "test.mp4",
            progressPercent = 50f,
            processedTime = 30000,
            totalDuration = 60000,
            estimatedTimeRemaining = 30000L,
            outputSize = 1024000L
        )

        val state2 = EncodingState.Encoding(
            currentFile = 1,
            totalFiles = 1,
            fileName = "test.mp4",
            progressPercent = 50f,
            processedTime = 30000,
            totalDuration = 60000,
            estimatedTimeRemaining = 30000L,
            outputSize = 1024000L
        )

        assertEquals(state1, state2)
    }

    @Test
    fun completed_state_holdsCorrectValues() {
        val uris = ArrayList<Uri>()
        uris.add(Uri.parse("content://test/1"))
        uris.add(Uri.parse("content://test/2"))

        val state = EncodingState.Completed(
            outputUris = uris,
            totalInputSize = 2048000L,
            totalOutputSize = 1024000L
        )

        assertEquals(2, state.outputUris.size)
        assertEquals(2048000L, state.totalInputSize)
        assertEquals(1024000L, state.totalOutputSize)
    }

    @Test
    fun completed_state_compressionRatio() {
        val state = EncodingState.Completed(
            outputUris = ArrayList(),
            totalInputSize = 1000000L,
            totalOutputSize = 500000L
        )

        val compressionRatio = 1.0 - (state.totalOutputSize.toDouble() / state.totalInputSize)
        assertEquals(0.5, compressionRatio, 0.001)
    }

    @Test
    fun failed_state_holdsErrorMessage() {
        val state = EncodingState.Failed(error = "FFmpeg encoding failed")
        assertEquals("FFmpeg encoding failed", state.error)
    }

    @Test
    fun state_types_areDistinct() {
        val idle: EncodingState = EncodingState.Idle
        val encoding: EncodingState = EncodingState.Encoding(1, 1, "test", 0f, 0, 0, 0, 0)
        val completed: EncodingState = EncodingState.Completed(ArrayList(), 0, 0)
        val failed: EncodingState = EncodingState.Failed("error")
        val cancelled: EncodingState = EncodingState.Cancelled

        assertTrue(idle is EncodingState.Idle)
        assertTrue(encoding is EncodingState.Encoding)
        assertTrue(completed is EncodingState.Completed)
        assertTrue(failed is EncodingState.Failed)
        assertTrue(cancelled is EncodingState.Cancelled)
    }
}
