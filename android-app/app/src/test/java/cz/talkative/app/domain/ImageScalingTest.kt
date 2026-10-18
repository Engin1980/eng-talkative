package cz.talkative.app.domain

import cz.talkative.app.domain.image.ImageScaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageScalingTest {

    @Test
    fun largeLandscape_isScaledToLongestSide_keepingAspect() {
        assertEquals(512 to 256, ImageScaling.scaledSize(2000, 1000))
    }

    @Test
    fun largePortrait_isScaledByHeight() {
        assertEquals(256 to 512, ImageScaling.scaledSize(1000, 2000))
    }

    @Test
    fun smallImage_isNeverEnlarged() {
        assertEquals(100 to 50, ImageScaling.scaledSize(100, 50))
        assertEquals(512 to 512, ImageScaling.scaledSize(512, 512))
    }

    @Test
    fun extremeAspect_keepsAtLeastOnePixel() {
        assertEquals(512 to 1, ImageScaling.scaledSize(100_000, 10))
    }

    @Test
    fun invalidSize_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImageScaling.scaledSize(0, 10) }
    }

    @Test
    fun sampleSize_isPowerOfTwo_andNeverShrinksBelowTarget() {
        assertEquals(1, ImageScaling.sampleSize(400, 300))
        assertEquals(1, ImageScaling.sampleSize(1000, 1000)) // 1000/2 = 500 < 512
        assertEquals(2, ImageScaling.sampleSize(1024, 768))
        assertEquals(8, ImageScaling.sampleSize(4096, 3072))
        for (longest in listOf(600, 1024, 2000, 4000, 8000, 12000)) {
            val sample = ImageScaling.sampleSize(longest, longest / 2)
            assertEquals(true, longest / sample >= 512)
        }
    }
}
