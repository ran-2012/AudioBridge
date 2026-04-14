package dev.ran.audiobridge.data

import dev.ran.audiobridge.model.HiddenWindowsApp
import dev.ran.audiobridge.model.HiddenWindowsAppSupport
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenWindowsAppPreferencesCodecTest {
    @Test
    fun defaultHiddenValues_shouldContainAudioBridge() {
        assertEquals(setOf("AudioBridge"), HiddenWindowsAppPreferencesCodec.defaultHiddenProcessNames())
        assertEquals("AudioBridge", HiddenWindowsAppPreferencesCodec.defaultHiddenWindowsApps().single().processName)
    }

    @Test
    fun encodeThenDecode_shouldPreserveMergedHiddenApps() {
        val encoded = HiddenWindowsAppPreferencesCodec.encodeHiddenWindowsApps(
            listOf(
                HiddenWindowsApp(processName = "player.exe", displayName = "播放器", iconBase64 = null),
                HiddenWindowsApp(processName = "player.exe", displayName = "播放器", iconBase64 = "Zm9v"),
                HiddenWindowsApp(processName = "bridge.exe", displayName = "", iconBase64 = null),
            ),
        )

        val decoded = HiddenWindowsAppPreferencesCodec.decodeHiddenWindowsApps(encoded)

        assertEquals(2, decoded.size)
        assertEquals("bridge.exe", decoded[0].processName)
        assertEquals("bridge.exe", decoded[0].displayName)
        assertEquals("player.exe", decoded[1].processName)
        assertEquals("Zm9v", decoded[1].iconBase64)
    }

    @Test
    fun buildHiddenWindowsApp_shouldPreferRicherSessionMetadata() {
        val existing = HiddenWindowsApp(
            processName = "AudioBridge",
            displayName = "AudioBridge",
            iconBase64 = null,
        )
        val session = WindowsAppVolumeSession(
            sessionId = "session-1",
            processName = "AudioBridge",
            displayName = "AudioBridge Receiver",
            iconBase64 = "Zm9v",
        )

        val hiddenApp = HiddenWindowsAppSupport.buildHiddenWindowsApp(session, existing)

        assertEquals("AudioBridge", hiddenApp?.processName)
        assertEquals("AudioBridge Receiver", hiddenApp?.displayName)
        assertEquals("Zm9v", hiddenApp?.iconBase64)
    }

    @Test
    fun filterVisibleSessions_shouldHideOnlyExactProcessNameMatches() {
        val sessions = listOf(
            WindowsAppVolumeSession(sessionId = "1", processName = "AudioBridge", displayName = "AudioBridge"),
            WindowsAppVolumeSession(sessionId = "2", processName = "AudioBridgeHelper", displayName = "Helper"),
        )

        val visible = HiddenWindowsAppSupport.filterVisibleSessions(sessions, setOf("AudioBridge"))

        assertEquals(1, visible.size)
        assertEquals("AudioBridgeHelper", visible.single().processName)
    }

    @Test
    fun distinctSessionsByProcessName_shouldKeepBestSessionPerProcess() {
        val sessions = listOf(
            WindowsAppVolumeSession(
                sessionId = "1",
                processName = "player.exe",
                displayName = "",
                iconBase64 = null,
                state = "Inactive",
            ),
            WindowsAppVolumeSession(
                sessionId = "2",
                processName = "player.exe",
                displayName = "播放器",
                iconBase64 = "Zm9v",
                state = "Active",
            ),
            WindowsAppVolumeSession(
                sessionId = "3",
                processName = "browser.exe",
                displayName = "浏览器",
            ),
        )

        val distinct = HiddenWindowsAppSupport.distinctSessionsByProcessName(sessions)

        assertEquals(2, distinct.size)
        assertEquals("2", distinct.first { it.processName == "player.exe" }.sessionId)
        assertEquals("3", distinct.first { it.processName == "browser.exe" }.sessionId)
    }

    @Test
    fun decodeHiddenWindowsApps_shouldReturnEmptyListForBlankInput() {
        assertTrue(HiddenWindowsAppPreferencesCodec.decodeHiddenWindowsApps(null).isEmpty())
        assertTrue(HiddenWindowsAppPreferencesCodec.decodeHiddenWindowsApps("").isEmpty())
    }

    @Test
    fun buildHiddenWindowsApp_shouldIgnoreBlankProcessName() {
        val session = WindowsAppVolumeSession(sessionId = "1", processName = " ", displayName = "测试")

        val hiddenApp = HiddenWindowsAppSupport.buildHiddenWindowsApp(session)

        assertNull(hiddenApp)
    }
}