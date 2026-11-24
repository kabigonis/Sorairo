package com.kabigonis.sorairo

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ScreenRecorderAudioTest {
    @Test fun desktopAndMicrophoneAreMixedIntoStereo() {
        val result = mixStereoWithMono(
            desktopStereo = shortArrayOf(1000, 2000, 3000, 4000),
            microphoneMono = shortArrayOf(1000, 2000),
            frames = 2,
            desktopGain = 1f,
            microphoneGain = 1f
        )
        assertArrayEquals(shortArrayOf(2000, 3000, 5000, 6000), result)
    }

    @Test fun mixerSaturatesInsteadOfOverflowing() {
        val result = mixStereoWithMono(
            desktopStereo = shortArrayOf(30_000, 30_000),
            microphoneMono = shortArrayOf(20_000),
            frames = 1,
            desktopGain = 1f,
            microphoneGain = 1f
        )
        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE), result)
    }

    @Test fun missingSourceBecomesSilence() {
        assertArrayEquals(
            shortArrayOf(100, 100, 200, 200),
            mixStereoWithMono(null, shortArrayOf(100, 200), 2, 0f, 1f)
        )
    }
}
