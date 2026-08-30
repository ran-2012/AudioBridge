package dev.ran.audiobridge.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioBridgeServiceTest {
    @Test
    fun resolveConnectTarget_shouldUseExplicitTarget_WhenProvided() {
        val target = AudioBridgeService.resolveConnectTarget("192.168.1.5", 6000)

        assertEquals("192.168.1.5" to 6000, target)
    }

    @Test
    fun resolveConnectTarget_shouldFallBackToUsbReverse_WhenNoExplicitTarget() {
        assertEquals("127.0.0.1" to 5000, AudioBridgeService.resolveConnectTarget(null, -1))
        assertEquals("127.0.0.1" to 5000, AudioBridgeService.resolveConnectTarget("", 0))
        assertEquals("127.0.0.1" to 5000, AudioBridgeService.resolveConnectTarget("   ", 6000))
    }
}
