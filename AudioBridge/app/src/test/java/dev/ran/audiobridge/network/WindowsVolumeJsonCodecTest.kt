package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.WindowsAppVolumeSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsVolumeJsonCodecTest {
    @Test
    fun buildCatalogRequest_shouldAlwaysEnableInlineIcons() {
        val payload = WindowsVolumeJsonCodec.buildCatalogRequest(7u)
        val json = JSONObject(payload)

        assertEquals(7L, json.getLong("requestId"))
        assertTrue(json.getBoolean("includeIconsInline"))
    }

    @Test
    fun buildSetMasterRequest_shouldClampVolumeAndEmitMuteFlag() {
        val payload = WindowsVolumeJsonCodec.buildSetMasterRequest(9u, volume = 1.5f, isMuted = true)
        val json = JSONObject(payload)

        assertEquals(9L, json.getLong("requestId"))
        assertEquals(1.0, json.getDouble("volume"), 0.0001)
        assertTrue(json.getBoolean("hasMute"))
        assertTrue(json.getBoolean("mute"))
    }

    @Test
    fun parseCatalogSnapshot_shouldSupportWrappedCatalogAndNormalizeBlankIcon() {
        val payload = """
            {
              "requestId": 15,
              "catalog": {
                "capturedAtMillis": 123456,
                "masterVolume": {
                  "deviceId": "default",
                  "deviceName": "扬声器",
                  "volume": 1.2,
                  "isMuted": true
                },
                "sessions": [
                  {
                    "sessionId": "session-1",
                    "processId": 100,
                    "processName": "browser.exe",
                    "displayName": "浏览器",
                    "state": "Active",
                    "volume": -1,
                    "muted": false,
                    "iconKey": "icon-1",
                    "iconHash": "hash-1",
                    "iconBase64": ""
                  }
                ]
              }
            }
        """.trimIndent()

        val catalog = WindowsVolumeJsonCodec.parseCatalogSnapshot(payload)

        assertEquals(15u, catalog.requestId)
        assertEquals(123456L, catalog.capturedAtMillis)
        assertEquals("扬声器", catalog.masterVolume.deviceName)
        assertEquals(1.0f, catalog.masterVolume.volume)
        assertTrue(catalog.masterVolume.isMuted)
        assertEquals(1, catalog.sessions.size)
        val session = catalog.sessions.single()
        assertEquals("session-1", session.sessionId)
        assertEquals(0f, session.volume)
        assertNull(session.iconBase64)
    }

    @Test
    fun parseCommandAck_shouldPreferMessageAndParseSession() {
        val payload = """
            {
              "requestId": 21,
              "success": true,
              "errorCode": 0,
              "message": "ok",
              "session": {
                "sessionId": "session-2",
                "displayName": "播放器",
                "processName": "player.exe",
                "volume": 0.45,
                "isMuted": false,
                "iconBase64": "Zm9v"
              }
            }
        """.trimIndent()

        val ack = WindowsVolumeJsonCodec.parseCommandAck(payload)

        assertTrue(ack.success)
        assertEquals(21u, ack.requestId)
        assertEquals("ok", ack.message)
        assertNotNull(ack.session)
        assertEquals("session-2", ack.session?.sessionId)
        assertEquals("Zm9v", ack.session?.iconBase64)
    }

    @Test
    fun parseDelta_shouldReturnRemovedSessionIdWhenPresent() {
        val payload = """
            {
              "deltaType": "removed",
              "removedSessionId": "session-9",
              "masterVolume": {
                "deviceName": "扬声器",
                "volume": 0.25,
                "muted": false
              }
            }
        """.trimIndent()

        val delta = WindowsVolumeJsonCodec.parseDelta(payload)
        val removedSessionId = WindowsVolumeJsonCodec.parseRemovedSessionId(payload)

        assertEquals("removed", delta.first)
        assertEquals(0.25f, delta.second?.volume)
        assertNull(delta.third)
        assertEquals("session-9", removedSessionId)
    }
}
