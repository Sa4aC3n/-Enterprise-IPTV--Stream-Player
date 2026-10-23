package com.enterprise.iptv.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class EpgReminderTest {

    @Test
    fun testReminderSerializationAndDeserialization() {
        val now = System.currentTimeMillis()
        val futureStart = now + TimeUnit.HOURS.toMillis(2)
        val triggerTime = futureStart - TimeUnit.MINUTES.toMillis(5)

        val reminder = EpgReminder(
            programId = "prog_123",
            channelId = "ch_456",
            channelName = "قناة الكأس الرياضية",
            streamUrl = "http://example.com/alkass.m3u8",
            programTitle = "مباراة نهائي الكأس",
            startEpochMs = futureStart,
            scheduledTimeMs = triggerTime
        )

        assertTrue(reminder.isPending)

        val json = reminder.toJson()
        assertEquals("prog_123", json.getString("programId"))
        assertEquals("قناة الكأس الرياضية", json.getString("channelName"))
        assertEquals("مباراة نهائي الكأس", json.getString("programTitle"))
        assertEquals(futureStart, json.getLong("startEpochMs"))

        val restored = EpgReminder.fromJson(json)
        assertEquals(reminder.programId, restored.programId)
        assertEquals(reminder.channelId, restored.channelId)
        assertEquals(reminder.channelName, restored.channelName)
        assertEquals(reminder.streamUrl, restored.streamUrl)
        assertEquals(reminder.programTitle, restored.programTitle)
        assertEquals(reminder.startEpochMs, restored.startEpochMs)
        assertEquals(reminder.scheduledTimeMs, restored.scheduledTimeMs)
        assertTrue(restored.isPending)
    }

    @Test
    fun testPastReminderIsNotPending() {
        val pastStart = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        val reminder = EpgReminder(
            programId = "prog_past",
            channelId = "ch_past",
            channelName = "قناة قديمة",
            streamUrl = "http://example.com/old.m3u8",
            programTitle = "برنامج منتهي",
            startEpochMs = pastStart,
            scheduledTimeMs = pastStart - TimeUnit.MINUTES.toMillis(5)
        )

        assertFalse(reminder.isPending)
    }
}
