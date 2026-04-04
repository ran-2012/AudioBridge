package dev.ran.audiobridge.audio

object PlaybackCacheConfig {
    const val MIN_MILLISECONDS = 40
    const val MAX_MILLISECONDS = 400
    const val STEP_MILLISECONDS = 20
    const val DEFAULT_MILLISECONDS = 120

    fun normalize(milliseconds: Int): Int {
        val clamped = milliseconds.coerceIn(MIN_MILLISECONDS, MAX_MILLISECONDS)
        val offset = clamped - MIN_MILLISECONDS
        val steps = (offset + STEP_MILLISECONDS / 2) / STEP_MILLISECONDS
        return MIN_MILLISECONDS + steps * STEP_MILLISECONDS
    }

    fun sliderSteps(): Int = (MAX_MILLISECONDS - MIN_MILLISECONDS) / STEP_MILLISECONDS - 1
}