package com.photocompressorlite.myapp
import org.junit.Assert.*
import org.junit.Test
class CompressionMathTest {
    @Test fun savingsUsesActualBytes() {
        assertEquals(75.0, CompressionMath.savedPercent(1000, 250), .001)
    }
    @Test fun largerOutputReportsNegativeSavings() {
        assertEquals(-20.0, CompressionMath.savedPercent(1000, 1200), .001)
    }
    @Test fun emptyInputDoesNotDivideByZero() {
        assertEquals(0.0, CompressionMath.savedPercent(0, 100), .001)
    }
    @Test fun downsamplingBoundsMemory() {
        assertEquals(4, CompressionMath.sampleSize(8000, 6000))
        assertEquals(1, CompressionMath.sampleSize(1200, 900))
    }
    @Test fun dimensionReductionAlwaysMakesProgress() {
        assertEquals(800, CompressionMath.nextDimension(1000))
        assertEquals(1, CompressionMath.nextDimension(1))
        assertTrue(CompressionMath.nextDimension(64) < 64)
    }
}
