package dev.timelock

/** Pure timing rules. The caller supplies elapsed-realtime milliseconds. */
internal object IntervalTimer {
    data class Tick(
        val shouldFire: Boolean,
        val nextDeadlineMs: Long,
        val skippedPeriods: Long,
        val lateByMs: Long,
    )

    fun nextTick(nowMs: Long, deadlineMs: Long, intervalMs: Long): Tick {
        require(intervalMs > 0L) { "interval must be positive" }
        require(deadlineMs >= 0L) { "deadline must not be negative" }
        if (nowMs < deadlineMs) return Tick(false, deadlineMs, 0L, 0L)
        val lateByMs = nowMs - deadlineMs
        val skipped = lateByMs / intervalMs
        return Tick(
            shouldFire = true,
            nextDeadlineMs = deadlineMs + (skipped + 1L) * intervalMs,
            skippedPeriods = skipped,
            lateByMs = lateByMs,
        )
    }

    fun parseInterval(value: String, unit: IntervalUnit): Long? {
        if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
        val amount = value.toLongOrNull() ?: return null
        if (amount <= 0L) return null
        val multiplier = when (unit) {
            IntervalUnit.SECONDS -> 1_000L
            IntervalUnit.MINUTES -> 60_000L
        }
        if (amount > 86_400_000L / multiplier) return null
        return amount * multiplier
    }

    fun remaining(nowMs: Long, deadlineMs: Long): Long = (deadlineMs - nowMs).coerceAtLeast(0L)

    fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }
}

internal enum class IntervalUnit { SECONDS, MINUTES }
