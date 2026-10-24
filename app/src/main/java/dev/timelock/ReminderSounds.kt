package dev.timelock

internal object ReminderSounds {
    const val BRIGHT = 0
    const val SOFT = 1
    const val HIGH_LOW = 2
    const val TRIPLE = 3
    const val SYSTEM = 4
    const val BUILTIN_COUNT = 4

    val names = listOf("清脆双响", "柔和双响", "高低提示", "三声短响", "手机系统铃声")
    val resourceIds = intArrayOf(
        R.raw.reminder_bright,
        R.raw.reminder_soft,
        R.raw.reminder_high_low,
        R.raw.reminder_triple,
    )

    fun normalized(index: Int, ringtoneUri: String?): Int =
        when {
            index in 0 until BUILTIN_COUNT -> index
            index == SYSTEM && !ringtoneUri.isNullOrBlank() -> SYSTEM
            else -> BRIGHT
        }
}
