package com.clinic.wanotifybridge.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduperTest {

    @Test
    fun `first sighting is new`() {
        val deduper = Deduper()
        assertTrue(deduper.markIfNew("a"))
    }

    @Test
    fun `repeat within ttl is suppressed`() {
        var now = 0L
        val deduper = Deduper(ttlMillis = 1_000L, clock = { now })
        assertTrue(deduper.markIfNew("a"))
        now = 500
        assertFalse(deduper.markIfNew("a"))
    }

    @Test
    fun `a repost keeps refreshing the suppression window`() {
        var now = 0L
        val deduper = Deduper(ttlMillis = 1_000L, clock = { now })
        deduper.markIfNew("a")
        // WhatsApp re-posts every 800ms; the key must stay suppressed rather than aging out
        // between re-posts.
        repeat(5) {
            now += 800
            assertFalse(deduper.markIfNew("a"))
        }
    }

    @Test
    fun `key is forwardable again once ttl elapses with no reposts`() {
        var now = 0L
        val deduper = Deduper(ttlMillis = 1_000L, clock = { now })
        deduper.markIfNew("a")
        now = 1_500
        assertTrue(deduper.markIfNew("a"))
    }

    @Test
    fun `distinct keys do not interfere`() {
        val deduper = Deduper()
        assertTrue(deduper.markIfNew("a"))
        assertTrue(deduper.markIfNew("b"))
    }

    @Test
    fun `map stays bounded under load`() {
        var now = 0L
        val deduper = Deduper(ttlMillis = 60_000L, maxEntries = 10, clock = { now })
        repeat(100) {
            now += 1
            deduper.markIfNew("key$it")
        }
        // The oldest keys were evicted, so an early key reads as new again — that is the
        // accepted trade-off for a bounded cache.
        assertTrue(deduper.markIfNew("key0"))
    }
}
