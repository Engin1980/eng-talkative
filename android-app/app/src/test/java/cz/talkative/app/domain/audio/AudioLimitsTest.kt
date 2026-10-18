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
