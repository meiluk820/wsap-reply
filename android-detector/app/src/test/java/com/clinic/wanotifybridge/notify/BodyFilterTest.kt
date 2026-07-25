package com.clinic.wanotifybridge.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyFilterTest {

    @Test
    fun `real messages pass`() {
        assertTrue(BodyFilter.isForwardable("Hi, can I book a scaling this Friday?"))
        assertTrue(BodyFilter.isForwardable("2 crowns please"))
    }

    @Test
    fun `blank and rollup bodies are dropped`() {
        assertFalse(BodyFilter.isForwardable(""))
        assertFalse(BodyFilter.isForwardable("   "))
        assertFalse(BodyFilter.isForwardable("3 new messages"))
        assertFalse(BodyFilter.isForwardable("12 messages from 4 chats"))
    }

    @Test
    fun `bare media and missed call labels are dropped`() {
        assertFalse(BodyFilter.isForwardable("Photo"))
        assertFalse(BodyFilter.isForwardable("📷 Photo"))
        assertFalse(BodyFilter.isForwardable("Missed voice call"))
        assertFalse(BodyFilter.isForwardable("voice message"))
    }

    @Test
    fun `media with a caption is a real message`() {
        assertTrue(BodyFilter.isForwardable("📷 Photo\nIs this tooth ok?"))
    }
}
