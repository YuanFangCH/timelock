package dev.timelock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalTimerTest {
    @Test fun beforeFirstDeadlineWaitsForOneFullInterval() {
        val tick = IntervalTimer.nextTick(0L, 30_000L, 30_000L)
        assertEquals(false, tick.shouldFire)
        assertEquals(30_000L, tick.nextDeadlineMs)
    }

    @Test fun onTimeTickMovesByOneFixedInterval() {
        val tick = IntervalTimer.nextTick(30_000L, 30_000L, 30_000L)
        assertEquals(true, tick.shouldFire)
        assertEquals(60_000L, tick.nextDeadlineMs)
    }

    @Test fun delayedCallbackSkipsMissedBeatsWithoutBursting() {
        val tick = IntervalTimer.nextTick(nowMs = 95_000L, deadlineMs = 30_000L, intervalMs = 30_000L)
        assertEquals(true, tick.shouldFire)
        assertEquals(2L, tick.skippedPeriods)
        assertEquals(120_000L, tick.nextDeadlineMs)
    }

    @Test fun earlyCallbackDoesNotReportLateAndStillAdvancesFromDeadline() {
        val tick = IntervalTimer.nextTick(nowMs = 29_999L, deadlineMs = 30_000L, intervalMs = 30_000L)
        assertEquals(false, tick.shouldFire)
        assertEquals(0L, tick.lateByMs)
        assertEquals(30_000L, tick.nextDeadlineMs)
    }

    @Test fun remainingTimeIsClampedAtZero() {
        assertEquals(17_000L, IntervalTimer.remaining(13_000L, 30_000L))
        assertEquals(0L, IntervalTimer.remaining(31_000L, 30_000L))
    }

    @Test fun secondsAndMinutesConvertWithoutOverflow() {
        assertEquals(30_000L, IntervalTimer.parseInterval("30", IntervalUnit.SECONDS))
        assertEquals(120_000L, IntervalTimer.parseInterval("2", IntervalUnit.MINUTES))
        assertEquals(86_400_000L, IntervalTimer.parseInterval("1440", IntervalUnit.MINUTES))
        assertNull(IntervalTimer.parseInterval("1441", IntervalUnit.MINUTES))
    }

    @Test fun invalidInputIsRejected() {
        listOf("", "0", "-1", "1.5", " 1", "99999999999999999999999999999999999")
            .forEach { assertNull("Should reject: $it", IntervalTimer.parseInterval(it, IntervalUnit.SECONDS)) }
    }

    @Test fun countdownFormattingSwitchesToHoursAndRoundsUp() {
        assertEquals("00:01", IntervalTimer.formatRemaining(1L))
        assertEquals("01:00", IntervalTimer.formatRemaining(60_000L))
        assertEquals("01:00:00", IntervalTimer.formatRemaining(3_600_000L))
    }
}
