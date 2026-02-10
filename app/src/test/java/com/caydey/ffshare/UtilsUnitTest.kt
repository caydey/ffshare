package com.caydey.ffshare

import com.caydey.ffshare.utils.Utils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UtilsUnitTest {

    private val context = RuntimeEnvironment.getApplication()
    private val utils = Utils(context)

    @Test
    fun bytesToHuman_bytes() {
        assertEquals("0 B", utils.bytesToHuman(0))
        assertEquals("512 B", utils.bytesToHuman(512))
        assertEquals("1023 B", utils.bytesToHuman(1023))
    }

    @Test
    fun bytesToHuman_kilobytes() {
        assertEquals("1.0 KiB", utils.bytesToHuman(1024))
        assertEquals("1.5 KiB", utils.bytesToHuman(1536))
        assertEquals("10.0 KiB", utils.bytesToHuman(10240))
    }

    @Test
    fun bytesToHuman_megabytes() {
        assertEquals("1.0 MiB", utils.bytesToHuman(1048576))
        assertEquals("5.5 MiB", utils.bytesToHuman(5767168))
    }

    @Test
    fun bytesToHuman_gigabytes() {
        assertEquals("1.0 GiB", utils.bytesToHuman(1073741824))
        assertEquals("2.5 GiB", utils.bytesToHuman(2684354560))
    }

    @Test
    fun millisToMicrowaveTime_seconds() {
        assertEquals("00:00", utils.millisToMicrowaveTime(0))
        assertEquals("00:01", utils.millisToMicrowaveTime(1000))
        assertEquals("00:30", utils.millisToMicrowaveTime(30000))
        assertEquals("00:59", utils.millisToMicrowaveTime(59000))
    }

    @Test
    fun millisToMicrowaveTime_minutes() {
        assertEquals("01:00", utils.millisToMicrowaveTime(60000))
        assertEquals("01:30", utils.millisToMicrowaveTime(90000))
        assertEquals("05:45", utils.millisToMicrowaveTime(345000))
        assertEquals("59:59", utils.millisToMicrowaveTime(3599000))
    }

    @Test
    fun millisToMicrowaveTime_hours() {
        assertEquals("01:00:00", utils.millisToMicrowaveTime(3600000))
        assertEquals("02:30:45", utils.millisToMicrowaveTime(9045000))
    }

    @Test
    fun isImage_returnsTrue_forImageTypes() {
        assertTrue(utils.isImage(Utils.MediaType.JPEG))
        assertTrue(utils.isImage(Utils.MediaType.PNG))
        assertTrue(utils.isImage(Utils.MediaType.GIF))
        assertTrue(utils.isImage(Utils.MediaType.WEBP))
    }

    @Test
    fun isImage_returnsFalse_forNonImageTypes() {
        assertFalse(utils.isImage(Utils.MediaType.MP4))
        assertFalse(utils.isImage(Utils.MediaType.MP3))
        assertFalse(utils.isImage(Utils.MediaType.UNKNOWN))
    }

    @Test
    fun isVideo_returnsTrue_forVideoTypes() {
        assertTrue(utils.isVideo(Utils.MediaType.MP4))
        assertTrue(utils.isVideo(Utils.MediaType.MKV))
        assertTrue(utils.isVideo(Utils.MediaType.WEBM))
        assertTrue(utils.isVideo(Utils.MediaType.AVI))
    }

    @Test
    fun isVideo_returnsFalse_forNonVideoTypes() {
        assertFalse(utils.isVideo(Utils.MediaType.JPEG))
        assertFalse(utils.isVideo(Utils.MediaType.MP3))
        assertFalse(utils.isVideo(Utils.MediaType.UNKNOWN))
    }

    @Test
    fun isAudio_returnsTrue_forAudioTypes() {
        assertTrue(utils.isAudio(Utils.MediaType.MP3))
        assertTrue(utils.isAudio(Utils.MediaType.OGG))
        assertTrue(utils.isAudio(Utils.MediaType.AAC))
        assertTrue(utils.isAudio(Utils.MediaType.WAV))
        assertTrue(utils.isAudio(Utils.MediaType.OPUS))
    }

    @Test
    fun isAudio_returnsFalse_forNonAudioTypes() {
        assertFalse(utils.isAudio(Utils.MediaType.JPEG))
        assertFalse(utils.isAudio(Utils.MediaType.MP4))
        assertFalse(utils.isAudio(Utils.MediaType.UNKNOWN))
    }

    @Test
    fun isSupportedMediaType_returnsFalse_forUnknown() {
        assertFalse(utils.isSupportedMediaType(Utils.MediaType.UNKNOWN))
    }

    @Test
    fun isSupportedMediaType_returnsTrue_forVideoAndImage() {
        assertTrue(utils.isSupportedMediaType(Utils.MediaType.MP4))
        assertTrue(utils.isSupportedMediaType(Utils.MediaType.JPEG))
    }
}
