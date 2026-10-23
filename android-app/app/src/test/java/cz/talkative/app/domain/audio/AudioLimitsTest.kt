package cz.talkative.app.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioLimitsTest {

    @Test
    fun shortMp3_isAccepted() {
        assertNull(AudioLimits.check("audio/mpeg", 3_000))
    }

    @Test
    fun mp3AtTheLimit_isAccepted_justOverIsTooLong() {
        assertNull(AudioLimits.check("audio/mpeg", AudioLimits.MAX_DURATION_MILLIS))
        assertEquals(AudioError.TOO_LONG, AudioLimits.check("audio/mpeg", AudioLimits.MAX_DURATION_MILLIS + 1))
    }

    @Test
    fun m4a_isAccepted_inBothMimeTypes_andLongerThanLimitIsTooLong() {
        assertNull(AudioLimits.check("audio/mp4", 3_000))
        assertNull(AudioLimits.check("audio/x-m4a", 3_000))
        assertEquals(AudioError.TOO_LONG, AudioLimits.check("audio/mp4", AudioLimits.MAX_DURATION_MILLIS + 1))
    }

    @Test
    fun fileExtension_followsTheFormat() {
        assertEquals("mp3", AudioLimits.fileExtension("audio/mpeg"))
        assertEquals("m4a", AudioLimits.fileExtension("audio/mp4"))
        assertEquals("m4a", AudioLimits.fileExtension("audio/x-m4a"))
    }

    @Test
    fun otherFormats_areUnsupported() {
        assertEquals(AudioError.UNSUPPORTED_FORMAT, AudioLimits.check("audio/ogg", 3_000))
        assertEquals(AudioError.UNSUPPORTED_FORMAT, AudioLimits.check("audio/x-wav", 3_000))
        assertEquals(AudioError.UNSUPPORTED_FORMAT, AudioLimits.check(null, 3_000))
    }

    @Test
    fun missingOrZeroDuration_isUnreadable() {
        assertEquals(AudioError.UNREADABLE, AudioLimits.check("audio/mpeg", null))
        assertEquals(AudioError.UNREADABLE, AudioLimits.check("audio/mpeg", 0))
    }

    @Test
    fun wrongFormatWinsOverLength() {
        assertEquals(AudioError.UNSUPPORTED_FORMAT, AudioLimits.check("audio/ogg", 10 * AudioLimits.MAX_DURATION_MILLIS))
    }
}
