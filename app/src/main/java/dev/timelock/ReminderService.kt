package dev.timelock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

internal data class TimerSnapshot(
    val state: String = STATE_IDLE,
    val intervalMs: Long = 30_000L,
    val deadlineMs: Long = 0L,
    val remainingMs: Long = 0L,
    val triggerCount: Int = 0,
    val sessionId: Long = 0L,
    val lastError: String = "",
) {
    companion object {
        const val STATE_IDLE = "idle"
        const val STATE_PREPARING = "preparing"
        const val STATE_RUNNING = "running"
        const val STATE_PAUSED = "paused"
    }
}

class ReminderService : Service() {
    inner class LocalBinder : Binder() {
        internal fun snapshot(): TimerSnapshot = currentSnapshot

        internal fun preview() {
            worker.post {
                if (!soundReady || currentSnapshot.state != TimerSnapshot.STATE_IDLE) return@post
                stopActiveSound("preview_replaced")
                updateSnapshot(currentSnapshot.copy(lastError = ""))
                playReminder(currentSnapshot.sessionId, generation, isPreview = true)
            }
        }

        internal fun stopPreview() {
            worker.post {
                if (currentSnapshot.state == TimerSnapshot.STATE_IDLE) stopActiveSound("preview_closed")
            }
        }
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    @Volatile private var currentSnapshot = TimerSnapshot()

    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var soundPool: SoundPool
    private var soundId = 0
    private var soundReady = false
    private var pendingIntervalMs: Long? = null
    private var sessionCounter = 0L
    private var generation = 0L
    private var activeStreamId = 0
    private var focusRequest: AudioFocusRequest? = null
    private var focusRelease: Runnable? = null
    private var dueRunnable: Runnable? = null
    private var pausedRemainingMs = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:interval-reminder")
            .apply { setReferenceCounted(false) }

        workerThread = HandlerThread("TimeLockTimer").apply { start() }
        worker = Handler(workerThread.looper)
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
        soundPool.setOnLoadCompleteListener { _, loadedId, status ->
            worker.post {
                if (loadedId != soundId) return@post
                if (status != 0) {
                    pendingIntervalMs = null
                    updateSnapshot(TimerSnapshot(lastError = "提示音加载失败，请重新打开应用"))
                    Log.e(TAG, "event=audio_load_failed status=$status")
                    return@post
                }
                soundReady = true
                val pending = pendingIntervalMs
                if (pending != null) startSession(pending)
            }
        }
        soundId = soundPool.load(this, R.raw.reminder, 1)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_START) {
            try {
                startAsForeground()
            } catch (exception: RuntimeException) {
                Log.e(TAG, "event=foreground_start_failed", exception)
                worker.post { updateSnapshot(currentSnapshot.copy(lastError = "后台提醒无法启动，请检查通知权限")) }
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, 30_000L) ?: 30_000L
        val expectedSession = intent?.getLongExtra(EXTRA_SESSION_ID, 0L) ?: 0L
        worker.post {
            when (action) {
                ACTION_START -> {
                    if (currentSnapshot.state == TimerSnapshot.STATE_RUNNING ||
                        currentSnapshot.state == TimerSnapshot.STATE_PAUSED ||
                        pendingIntervalMs != null
                    ) return@post
                    if (intervalMs <= 0L || intervalMs > MAX_INTERVAL_MS) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        updateSnapshot(currentSnapshot.copy(lastError = "间隔无效，请重新输入"))
                        stopSelf(startId)
                        return@post
                    }
                    updateSnapshot(currentSnapshot.copy(state = TimerSnapshot.STATE_PREPARING,
                        intervalMs = intervalMs, lastError = "", triggerCount = 0))
                    if (soundReady) startSession(intervalMs) else pendingIntervalMs = intervalMs
                }
                ACTION_PAUSE -> if (isCurrentSession(expectedSession)) pauseSession()
                ACTION_RESUME -> if (isCurrentSession(expectedSession)) resumeSession()
                ACTION_STOP -> if (expectedSession == 0L || isCurrentSession(expectedSession)) stopSession(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification(currentSnapshot.copy(state = TimerSnapshot.STATE_PREPARING))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startSession(intervalMs: Long) {
        pendingIntervalMs = null
        sessionCounter += 1L
        generation += 1L
        pausedRemainingMs = intervalMs
        val sessionId = sessionCounter
        try {
            wakeLock.acquire()
        } catch (exception: RuntimeException) {
            updateSnapshot(TimerSnapshot(lastError = "无法保持锁屏计时，请检查系统电池设置"))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.e(TAG, "event=wake_lock_failed", exception)
            return
        }
        val now = SystemClock.elapsedRealtime()
        Log.i(TAG, "event=session_started sessionId=$sessionId intervalMs=$intervalMs elapsedMs=$now")
        updateSnapshot(TimerSnapshot(
            state = TimerSnapshot.STATE_RUNNING,
            intervalMs = intervalMs,
            deadlineMs = now + intervalMs,
            remainingMs = intervalMs,
            sessionId = sessionId,
        ))
        updateNotification()
        scheduleDeadline(sessionId, generation)
    }

    private fun scheduleDeadline(sessionId: Long, expectedGeneration: Long) {
        dueRunnable?.let(worker::removeCallbacks)
        val deadline = currentSnapshot.deadlineMs
        val delay = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        dueRunnable = Runnable { onDeadline(sessionId, expectedGeneration, deadline) }
        worker.postDelayed(dueRunnable!!, delay)
    }

    private fun onDeadline(sessionId: Long, expectedGeneration: Long, deadline: Long) {
        if (!isCurrentSession(sessionId) || expectedGeneration != generation ||
            currentSnapshot.state != TimerSnapshot.STATE_RUNNING
        ) return
        val now = SystemClock.elapsedRealtime()
        val tick = IntervalTimer.nextTick(now, deadline, currentSnapshot.intervalMs)
        if (!tick.shouldFire) {
            worker.postDelayed(
                dueRunnable!!,
                (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L),
            )
            return
        }

        Log.i(TAG, "event=reminder_due sessionId=$sessionId generation=$expectedGeneration " +
            "scheduledElapsedMs=$deadline dispatchElapsedMs=$now lateMs=${tick.lateByMs} " +
            "skippedPeriods=${tick.skippedPeriods}")
        playReminder(sessionId, expectedGeneration)
        val nextSnapshot = currentSnapshot.copy(
            deadlineMs = tick.nextDeadlineMs,
            remainingMs = currentSnapshot.intervalMs,
        )
        updateSnapshot(nextSnapshot)
        updateNotification()
        scheduleDeadline(sessionId, expectedGeneration)
    }

    private fun playReminder(sessionId: Long, expectedGeneration: Long, isPreview: Boolean = false) {
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            updateSnapshot(currentSnapshot.copy(lastError = if (isPreview) "闹钟音量为零，试听失败"
                else "闹钟音量为零，已跳过提醒声"))
            Log.w(TAG, "event=audio_rejected sessionId=$sessionId reason=alarm_volume_zero preview=$isPreview")
            return
        }
        abandonAudioFocus()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    worker.post {
                        if (expectedGeneration == generation) stopActiveSound("focus_lost")
                    }
                }
            }, worker)
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            updateSnapshot(currentSnapshot.copy(lastError = if (isPreview) "系统暂不允许播放，试听失败"
                else "系统暂不允许播放提醒声"))
            Log.w(TAG, "event=audio_rejected sessionId=$sessionId reason=focus_denied preview=$isPreview")
            return
        }
        focusRequest = request
        val streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        if (streamId == 0) {
            updateSnapshot(currentSnapshot.copy(lastError = if (isPreview) "试听失败"
                else "提醒声播放失败"))
            Log.w(TAG, "event=audio_rejected sessionId=$sessionId reason=soundpool_rejected preview=$isPreview")
            abandonAudioFocus()
            return
        }
        activeStreamId = streamId
        if (!isPreview) updateSnapshot(currentSnapshot.copy(triggerCount = currentSnapshot.triggerCount + 1, lastError = ""))
        Log.i(TAG, "event=${if (isPreview) "preview" else "audio_request"}_accepted sessionId=$sessionId streamId=$streamId")

        val release = Runnable {
            if (expectedGeneration == generation && activeStreamId == streamId) stopActiveSound("finished")
        }
        focusRelease?.let(worker::removeCallbacks)
        focusRelease = release
        worker.postDelayed(release, SOUND_TAIL_MS)
    }

    private fun pauseSession() {
        if (currentSnapshot.state != TimerSnapshot.STATE_RUNNING) return
        val now = SystemClock.elapsedRealtime()
        pausedRemainingMs = IntervalTimer.remaining(now, currentSnapshot.deadlineMs)
        generation += 1L
        dueRunnable?.let(worker::removeCallbacks)
        dueRunnable = null
        stopActiveSound("paused")
        releaseWakeLock()
        updateSnapshot(currentSnapshot.copy(
            state = TimerSnapshot.STATE_PAUSED,
            remainingMs = pausedRemainingMs,
            lastError = "",
        ))
        Log.i(TAG, "event=session_paused sessionId=${currentSnapshot.sessionId} remainingMs=$pausedRemainingMs")
        updateNotification()
    }

    private fun resumeSession() {
        if (currentSnapshot.state != TimerSnapshot.STATE_PAUSED) return
        try {
            wakeLock.acquire()
        } catch (exception: RuntimeException) {
            updateSnapshot(currentSnapshot.copy(lastError = "无法继续锁屏计时，请检查系统电池设置"))
            Log.e(TAG, "event=wake_lock_failed_on_resume", exception)
            return
        }
        generation += 1L
        val now = SystemClock.elapsedRealtime()
        val deadline = now + pausedRemainingMs
        updateSnapshot(currentSnapshot.copy(
            state = TimerSnapshot.STATE_RUNNING,
            deadlineMs = deadline,
            remainingMs = pausedRemainingMs,
            lastError = "",
        ))
        Log.i(TAG, "event=session_resumed sessionId=${currentSnapshot.sessionId} remainingMs=$pausedRemainingMs")
        updateNotification()
        scheduleDeadline(currentSnapshot.sessionId, generation)
    }

    private fun stopSession(startId: Int) {
        val sessionId = currentSnapshot.sessionId
        generation += 1L
        dueRunnable?.let(worker::removeCallbacks)
        dueRunnable = null
        pendingIntervalMs = null
        stopActiveSound("stopped")
        releaseWakeLock()
        updateSnapshot(TimerSnapshot(sessionId = sessionId, intervalMs = currentSnapshot.intervalMs))
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "event=session_stopped sessionId=$sessionId")
        stopSelf(startId)
    }

    private fun stopActiveSound(reason: String) {
        focusRelease?.let(worker::removeCallbacks)
        focusRelease = null
        if (activeStreamId != 0) {
            soundPool.stop(activeStreamId)
            activeStreamId = 0
            Log.i(TAG, "event=audio_stopped reason=$reason")
        }
        abandonAudioFocus()
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun isCurrentSession(sessionId: Long): Boolean =
        sessionId != 0L && currentSnapshot.sessionId == sessionId

    private fun updateSnapshot(snapshot: TimerSnapshot) {
        currentSnapshot = snapshot
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        notificationManager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            "间隔提醒状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示正在运行或已暂停的间隔提醒"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
    }

    private fun updateNotification() {
        if (currentSnapshot.state == TimerSnapshot.STATE_IDLE) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification(currentSnapshot))
    }

    private fun buildNotification(snapshot: TimerSnapshot): Notification {
        val running = snapshot.state == TimerSnapshot.STATE_RUNNING
        val paused = snapshot.state == TimerSnapshot.STATE_PAUSED
        val title = when {
            running -> "间隔提醒运行中 · ${formatInterval(snapshot.intervalMs)}"
            paused -> "间隔提醒已暂停"
            else -> "间隔提醒正在准备"
        }
        val body = if (running) "已触发 ${snapshot.triggerCount} 次 · 下一次倒计时显示在通知中"
        else if (paused) "可在应用中继续或停止"
        else "正在加载提示音"
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setOnlyAlertOnce(true)
            .setOngoing(running || paused)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
        if (running) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + IntervalTimer.remaining(
                    SystemClock.elapsedRealtime(), snapshot.deadlineMs,
                ))
            builder.addAction(action(NOTIFICATION_PAUSE, "暂停", android.R.drawable.ic_media_pause, snapshot.sessionId))
        } else if (paused) {
            builder.addAction(action(NOTIFICATION_RESUME, "继续", android.R.drawable.ic_media_play, snapshot.sessionId))
        }
        if (running || paused) {
            builder.addAction(action(ACTION_STOP, "停止", android.R.drawable.ic_menu_close_clear_cancel, snapshot.sessionId))
        }
        return builder.build()
    }

    private fun action(action: String, title: String, icon: Int, sessionId: Long): Notification.Action =
        Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), title, PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, ReminderService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )).build()

    override fun onDestroy() {
        generation += 1L
        dueRunnable?.let(worker::removeCallbacks)
        focusRelease?.let(worker::removeCallbacks)
        if (::soundPool.isInitialized) {
            if (activeStreamId != 0) soundPool.stop(activeStreamId)
            soundPool.release()
        }
        abandonAudioFocus()
        releaseWakeLock()
        if (::workerThread.isInitialized) workerThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.timelock.action.START"
        const val ACTION_PAUSE = "dev.timelock.action.PAUSE"
        const val ACTION_RESUME = "dev.timelock.action.RESUME"
        const val ACTION_STOP = "dev.timelock.action.STOP"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_SESSION_ID = "session_id"
        const val NOTIFICATION_PAUSE = ACTION_PAUSE
        const val NOTIFICATION_RESUME = ACTION_RESUME

        private const val TAG = "TimeLock"
        private const val CHANNEL_ID = "interval_status"
        private const val NOTIFICATION_ID = 7101
        private const val MAX_INTERVAL_MS = 86_400_000L
        private const val SOUND_TAIL_MS = 650L

        fun start(context: Context, intervalMs: Long) {
            val intent = Intent(context, ReminderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_INTERVAL_MS, intervalMs)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun sendAction(context: Context, action: String, sessionId: Long) {
            context.startService(Intent(context, ReminderService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SESSION_ID, sessionId))
        }

        fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        private fun formatInterval(intervalMs: Long): String = if (intervalMs % 60_000L == 0L) {
            "${intervalMs / 60_000L} 分钟"
        } else {
            "${intervalMs / 1_000L} 秒"
        }
    }
}
