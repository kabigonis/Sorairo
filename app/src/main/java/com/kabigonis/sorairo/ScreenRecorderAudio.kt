package com.kabigonis.sorairo

/** Mixes stereo device playback with mono microphone PCM into one stereo stream. */
internal fun mixStereoWithMono(
    desktopStereo: ShortArray?,
    microphoneMono: ShortArray?,
    frames: Int,
    desktopGain: Float = 0.85f,
    microphoneGain: Float = 0.70f
): ShortArray {
    val output = ShortArray(frames * 2)
    for (frame in 0 until frames) {
        val desktopLeft = desktopStereo?.getOrNull(frame * 2)?.toFloat() ?: 0f
        val desktopRight = desktopStereo?.getOrNull(frame * 2 + 1)?.toFloat() ?: 0f
        val microphone = microphoneMono?.getOrNull(frame)?.toFloat() ?: 0f
        output[frame * 2] = (desktopLeft * desktopGain + microphone * microphoneGain)
            .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        output[frame * 2 + 1] = (desktopRight * desktopGain + microphone * microphoneGain)
            .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return output
}
