package dev.timelock

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.os.SystemClock

class MainActivity : android.app.Activity() {
    private enum class UnitChoice { SECONDS, MINUTES }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var intervalInput: EditText
    private lateinit var secondsButton: Button
    private lateinit var minutesButton: Button
    private lateinit var countdown: TextView
    private lateinit var stateText: TextView
    private lateinit var errorText: TextView
    private lateinit var notificationNotice: TextView
    private lateinit var batteryNotice: TextView
    private lateinit var startPauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var previewButton: Button
    private lateinit var soundSpinner: Spinner
    private lateinit var selectedSoundText: TextView
    private lateinit var choosePhoneSoundButton: Button
    private lateinit var soundSettingsButton: Button
    private lateinit var batterySettingsButton: Button
    private var unit = UnitChoice.SECONDS
    private var soundIndex = ReminderSounds.BRIGHT
    private var ringtoneUri: String? = null
    private var ringtoneTitle: String? = null
    private var timerService: ReminderService.LocalBinder? = null
    private var isBound = false
    private var pendingStartMs: Long? = null

    private val refresh = object : Runnable {
        override fun run() {
            render()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            timerService = service as? ReminderService.LocalBinder
            render()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            timerService = null
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        restoreSettings()
        setupSoundChoices()
        bindToTimerService()
        render()
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) bindToTimerService()
        mainHandler.post(refresh)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onStop() {
        mainHandler.removeCallbacks(refresh)
        timerService?.stopPreview()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            timerService = null
        }
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            val intervalMs = pendingStartMs
            pendingStartMs = null
            if (intervalMs != null) {
                if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "通知未授权；锁屏时可能看不到通知栏控制按钮", Toast.LENGTH_LONG).show()
                }
                startInterval(intervalMs)
            }
            render()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RINGTONE || resultCode != RESULT_OK) return
        val picked = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (picked == null) {
            renderError("不能选择无声铃声，请挑选有声音的铃声")
            soundSpinner.setSelection(soundIndex)
            return
        }
        val selected = try {
            RingtoneManager.getRingtone(this, picked)
        } catch (_: RuntimeException) {
            null
        }
        if (selected == null) {
            renderError("无法打开这个手机铃声，请选择其他声音")
            soundSpinner.setSelection(soundIndex)
            return
        }
        ringtoneUri = picked.toString()
        ringtoneTitle = try { selected.getTitle(this) } catch (_: RuntimeException) { "手机铃声" }
        soundIndex = ReminderSounds.SYSTEM
        soundSpinner.setSelection(soundIndex)
        saveSoundSettings()
        updateSoundChoiceLabel()
        renderError("")
    }

    private fun bindViews() {
        intervalInput = findViewById(R.id.interval_input)
        secondsButton = findViewById(R.id.unit_seconds)
        minutesButton = findViewById(R.id.unit_minutes)
        countdown = findViewById(R.id.countdown)
        stateText = findViewById(R.id.state_text)
        errorText = findViewById(R.id.error_text)
        notificationNotice = findViewById(R.id.notification_notice)
        batteryNotice = findViewById(R.id.battery_notice)
        startPauseButton = findViewById(R.id.start_pause)
        stopButton = findViewById(R.id.stop)
        previewButton = findViewById(R.id.preview)
        soundSpinner = findViewById(R.id.sound_options)
        selectedSoundText = findViewById(R.id.selected_sound)
        choosePhoneSoundButton = findViewById(R.id.choose_phone_sound)
        soundSettingsButton = findViewById(R.id.sound_settings)
        batterySettingsButton = findViewById(R.id.battery_settings)

        secondsButton.setOnClickListener { setUnit(UnitChoice.SECONDS) }
        minutesButton.setOnClickListener { setUnit(UnitChoice.MINUTES) }
        findViewById<Button>(R.id.quick_30).setOnClickListener { setPreset(30L, UnitChoice.SECONDS) }
        findViewById<Button>(R.id.quick_50).setOnClickListener { setPreset(50L, UnitChoice.SECONDS) }
        findViewById<Button>(R.id.quick_120).setOnClickListener { setPreset(2L, UnitChoice.MINUTES) }

        startPauseButton.setOnClickListener { onMainAction() }
        stopButton.setOnClickListener {
            val snapshot = timerService?.snapshot()
            if (snapshot == null) {
                renderError("提醒服务尚未连接")
            } else {
                pendingStartMs = null
                ReminderService.sendAction(this, ReminderService.ACTION_STOP, snapshot.sessionId)
                hideKeyboard()
            }
        }
        previewButton.setOnClickListener {
            val binder = timerService
            if (binder == null) renderError("提示音服务尚未连接")
            else {
                binder.preview(soundIndex, ringtoneUri)
                Toast.makeText(this, "正在试听当前提示音", Toast.LENGTH_SHORT).show()
            }
        }
        choosePhoneSoundButton.setOnClickListener { openSystemTonePicker() }
        soundSettingsButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            } catch (_: RuntimeException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        batterySettingsButton.setOnClickListener {
            try {
                startActivity(ReminderService.batterySettingsIntent())
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
    }

    private fun setupSoundChoices() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ReminderSounds.names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        soundSpinner.adapter = adapter
        soundSpinner.setSelection(soundIndex)
        soundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (currentState() != TimerSnapshot.STATE_IDLE) return
                if (position == ReminderSounds.SYSTEM && ringtoneUri == null) {
                    soundSpinner.setSelection(soundIndex)
                    openSystemTonePicker()
                    return
                }
                if (soundIndex != position) {
                    soundIndex = position
                    saveSoundSettings()
                }
                updateSoundChoiceLabel()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        updateSoundChoiceLabel()
    }

    private fun openSystemTonePicker() {
        if (currentState() != TimerSnapshot.STATE_IDLE) return
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择间隔提醒铃声")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                ringtoneUri?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
        try {
            startActivityForResult(intent, REQUEST_RINGTONE)
        } catch (_: RuntimeException) {
            renderError("这部手机没有可用的系统铃声选择界面")
        }
    }

    private fun updateSoundChoiceLabel() {
        selectedSoundText.text = if (soundIndex == ReminderSounds.SYSTEM) {
            "当前：手机铃声 · ${ringtoneTitle ?: "已选择"}"
        } else {
            "当前：${ReminderSounds.names[soundIndex]}"
        }
        choosePhoneSoundButton.text = if (ringtoneUri == null) "从手机铃声库选择" else "更换手机铃声"
    }

    private fun onMainAction() {
        val snapshot = timerService?.snapshot() ?: TimerSnapshot()
        when (snapshot.state) {
            TimerSnapshot.STATE_RUNNING -> ReminderService.sendAction(this, ReminderService.ACTION_PAUSE, snapshot.sessionId)
            TimerSnapshot.STATE_PAUSED -> ReminderService.sendAction(this, ReminderService.ACTION_RESUME, snapshot.sessionId)
            TimerSnapshot.STATE_PREPARING -> Unit
            else -> {
                val intervalMs = IntervalTimer.parseInterval(intervalInput.text.toString(), unit.toIntervalUnit())
                if (intervalMs == null) {
                    renderError("请输入 1 秒至 24 小时的正整数间隔")
                    intervalInput.requestFocus()
                    return
                }
                saveSettings()
                pendingStartMs = intervalMs
                hideKeyboard()
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                } else {
                    pendingStartMs = null
                    startInterval(intervalMs)
                }
            }
        }
        render()
    }

    private fun startInterval(intervalMs: Long) {
        try {
            ReminderService.start(this, intervalMs, soundIndex, ringtoneUri)
            renderError("")
        } catch (_: RuntimeException) {
            renderError("后台提醒启动失败，请保持应用在前台后重试")
        }
    }

    private fun setUnit(choice: UnitChoice) {
        if (currentState() != TimerSnapshot.STATE_IDLE) return
        unit = choice
        intervalInput.text.clear()
        render()
    }

    private fun setPreset(value: Long, choice: UnitChoice) {
        if (currentState() != TimerSnapshot.STATE_IDLE) return
        unit = choice
        intervalInput.setText(value.toString())
        intervalInput.setSelection(intervalInput.text.length)
        render()
    }

    private fun currentState(): String = timerService?.snapshot()?.state ?: TimerSnapshot.STATE_IDLE

    private fun bindToTimerService() {
        if (isBound) return
        isBound = bindService(Intent(this, ReminderService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun restoreSettings() {
        val prefs = getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
        val savedUnit = prefs.getString(KEY_UNIT, UnitChoice.SECONDS.name)
        unit = runCatching { UnitChoice.valueOf(savedUnit ?: UnitChoice.SECONDS.name) }
            .getOrDefault(UnitChoice.SECONDS)
        intervalInput.setText(prefs.getString(KEY_VALUE, "30"))
        ringtoneUri = prefs.getString(KEY_RINGTONE_URI, null)
        ringtoneTitle = prefs.getString(KEY_RINGTONE_TITLE, null)
        soundIndex = ReminderSounds.normalized(
            prefs.getInt(KEY_SOUND_INDEX, ReminderSounds.BRIGHT), ringtoneUri)
    }

    private fun saveSettings() {
        getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_UNIT, unit.name)
            .putString(KEY_VALUE, intervalInput.text.toString())
            .putInt(KEY_SOUND_INDEX, soundIndex)
            .putString(KEY_RINGTONE_URI, ringtoneUri)
            .putString(KEY_RINGTONE_TITLE, ringtoneTitle)
            .apply()
    }

    private fun saveSoundSettings() {
        getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SOUND_INDEX, soundIndex)
            .putString(KEY_RINGTONE_URI, ringtoneUri)
            .putString(KEY_RINGTONE_TITLE, ringtoneTitle)
            .apply()
    }

    private fun render() {
        if (!::countdown.isInitialized) return
        val snapshot = timerService?.snapshot() ?: TimerSnapshot()
        when (snapshot.state) {
            TimerSnapshot.STATE_RUNNING -> {
                countdown.text = IntervalTimer.formatRemaining(
                    IntervalTimer.remaining(SystemClock.elapsedRealtime(), snapshot.deadlineMs),
                )
                stateText.text = "正在提醒 · 已触发 ${snapshot.triggerCount} 次 · 间隔 ${formatInterval(snapshot.intervalMs)}"
                startPauseButton.text = "暂停"
            }
            TimerSnapshot.STATE_PAUSED -> {
                countdown.text = IntervalTimer.formatRemaining(snapshot.remainingMs)
                stateText.text = "已暂停 · 已触发 ${snapshot.triggerCount} 次"
                startPauseButton.text = "继续"
            }
            TimerSnapshot.STATE_PREPARING -> {
                countdown.text = IntervalTimer.formatRemaining(snapshot.intervalMs)
                stateText.text = "正在准备提示音…"
                startPauseButton.text = "准备中"
            }
            else -> {
                countdown.text = IntervalTimer.formatRemaining(IntervalTimer.parseInterval(
                    intervalInput.text.toString(), unit.toIntervalUnit(),
                ) ?: 0L)
                stateText.text = "尚未开始"
                startPauseButton.text = "开始提醒"
            }
        }
        val active = snapshot.state != TimerSnapshot.STATE_IDLE
        intervalInput.isEnabled = !active
        secondsButton.isEnabled = !active
        minutesButton.isEnabled = !active
        findViewById<Button>(R.id.quick_30).isEnabled = !active
        findViewById<Button>(R.id.quick_50).isEnabled = !active
        findViewById<Button>(R.id.quick_120).isEnabled = !active
        stopButton.isEnabled = active
        previewButton.isEnabled = !active
        soundSpinner.isEnabled = !active
        choosePhoneSoundButton.isEnabled = !active
        updateUnitButtons()
        updateSystemNotices()
        if (snapshot.lastError.isNotBlank()) renderError(snapshot.lastError)
        else if (errorText.text.isNotBlank() && snapshot.state != TimerSnapshot.STATE_PREPARING) renderError("")
    }

    private fun updateSystemNotices() {
        val notificationsEnabled = if (Build.VERSION.SDK_INT >= 24) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()
        } else true
        notificationNotice.text = if (notificationsEnabled) {
            "通知已开启，锁屏时可从通知栏暂停或停止。提醒声使用系统闹钟音量。"
        } else {
            "通知尚未开启。计时仍可运行，但锁屏时可能看不到控制按钮。"
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= 23) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true
        batteryNotice.text = if (ignoringBatteryOptimizations) {
            "电池优化已豁免，锁屏提醒条件较好；手机厂商仍可能限制后台运行。"
        } else {
            "系统电池优化可能延迟锁屏提醒。可在下方设置中允许本应用后台运行。"
        }
    }

    private fun updateUnitButtons() {
        val running = currentState() != TimerSnapshot.STATE_IDLE
        secondsButton.setTextColor(if (unit == UnitChoice.SECONDS) Color.WHITE else Color.DKGRAY)
        minutesButton.setTextColor(if (unit == UnitChoice.MINUTES) Color.WHITE else Color.DKGRAY)
        secondsButton.alpha = if (unit == UnitChoice.SECONDS) 1f else 0.78f
        minutesButton.alpha = if (unit == UnitChoice.MINUTES) 1f else 0.78f
        if (running) {
            secondsButton.alpha = 0.5f
            minutesButton.alpha = 0.5f
        }
    }

    private fun renderError(message: String) {
        errorText.text = message
        errorText.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(intervalInput.windowToken, 0)
    }

    private fun formatInterval(intervalMs: Long): String = if (intervalMs % 60_000L == 0L) {
        "${intervalMs / 60_000L} 分钟"
    } else "${intervalMs / 1_000L} 秒"

    private fun UnitChoice.toIntervalUnit() = when (this) {
        UnitChoice.SECONDS -> IntervalUnit.SECONDS
        UnitChoice.MINUTES -> IntervalUnit.MINUTES
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 250L
        private const val REQUEST_NOTIFICATIONS = 3001
        private const val REQUEST_RINGTONE = 3002
        private const val SETTINGS_NAME = "reminder_settings"
        private const val KEY_VALUE = "interval_value"
        private const val KEY_UNIT = "interval_unit"
        private const val KEY_SOUND_INDEX = "sound_index"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_RINGTONE_TITLE = "ringtone_title"
    }
}
