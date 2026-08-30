package dev.ran.audiobridge.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanDiscoveryClientTest {
    private val client = LanDiscoveryClient()

    @Test
    fun parseAnnounce_shouldParseValidAnnounce() {
        val json = """{"t":"winAudioBridgeAnnounce","name":"DESKTOP-X","host":"192.168.1.5","port":6000,"ver":1}"""

        val server = client.parseAnnounce(json)

        assertEquals("DESKTOP-X", server?.name)
        assertEquals("192.168.1.5", server?.host)
        assertEquals(6000, server?.port)
    }

    @Test
    fun parseAnnounce_shouldUseDefaultPort_WhenPortMissing() {
        val json = """{"t":"winAudioBridgeAnnounce","name":"DESKTOP-X","host":"192.168.1.5"}"""

        val server = client.parseAnnounce(json)

        assertEquals("DESKTOP-X", server?.name)
        assertEquals(6000, server?.port)
    }

    @Test
    fun parseAnnounce_shouldReturnNull_ForWrongType() {
        val json = """{"t":"other","name":"DESKTOP-X","host":"192.168.1.5"}"""

        assertNull(client.parseAnnounce(json))
    }

    @Test
    fun parseAnnounce_shouldReturnNull_ForMissingHostOrName() {
        assertNull(client.parseAnnounce("""{"t":"winAudioBridgeAnnounce","name":"","host":"192.168.1.5"}"""))
        assertNull(client.parseAnnounce("""{"t":"winAudioBridgeAnnounce","name":"DESKTOP-X","host":""}"""))
        assertNull(client.parseAnnounce("not-json"))
    }
}
