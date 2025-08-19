package com.surajiyer.buzztimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.surajiyer.buzztimer.MainActivity
import com.surajiyer.buzztimer.R
import com.surajiyer.buzztimer.model.TimerInterval
import java.util.concurrent.TimeUnit

/**
 * ForegroundTimerService handles the timer functionality in the background
 * using a foreground service to ensure reliable operation even when the app
 * is in the background or the screen is off.
 */
class ForegroundTimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "buzztimer_channel"
        private const val CHANNEL_NAME = "BuzzTimer Notifications"
        private const val UPDATE_INTERVAL = 100L // 100ms update interval for smoother countdown
        private const val NOTIFICATION_UPDATE_INTERVAL = 1000L // Update notification every 1 second
        private const val WAKE_LOCK_TAG = "BuzzTimer:WakeLock"
        
        // Notification action constants
        const val ACTION_PAUSE = "com.surajiyer.buzztimer.ACTION_PAUSE"
        const val ACTION_RESUME = "com.surajiyer.buzztimer.ACTION_RESUME"
        const val ACTION_STOP = "com.surajiyer.buzztimer.ACTION_STOP"
    }

    interface TimerListener {
        fun onTimerTick(millisUntilFinished: Long)
        fun onIntervalComplete(intervalIndex: Int)
        fun onSequenceComplete()
        fun onLapCountChanged(lapCount: Int)
        fun onCurrentIntervalChanged(intervalIndex: Int)
        fun onTimerPaused()
        fun onTimerResumed()
        fun onTimerStopped()
    }

    // Binder given to clients
    private val binder = LocalBinder()
    private var timerListener: TimerListener? = null

    // Timer state variables
    private var intervals: List<TimerInterval> = emptyList()
    private var isCircular: Boolean = false
    private var currentIntervalIndex: Int = 0
    private var isRunning: Boolean = false
    private var isPaused: Boolean = false
    private var remainingTimeMillis: Long = 0
    private var targetEndTime: Long = 0
    private var lapCount: Int = 0
    private var lastNotificationUpdate: Long = 0
    
    // For handling vibration
    private lateinit var vibrator: Vibrator
    
    // Wake lock to keep CPU running during timer
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        
        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Create notification channel for Android O and higher
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle notification actions
        when (intent?.action) {
            ACTION_PAUSE -> {
                pauseTimer()
            }
            ACTION_RESUME -> {
                resumeTimer()
            }
            ACTION_STOP -> {
                resetTimer()
            }
        }
        
        // Return sticky to ensure service restarts if killed
        return START_STICKY
    }
    
    /**
     * Class for clients to access this service
     */
    inner class LocalBinder : Binder() {
        fun getService(): ForegroundTimerService = this@ForegroundTimerService
    }
    
    /**
     * Set the listener to receive timer updates
     */
    fun setTimerListener(listener: TimerListener) {
        timerListener = listener
    }

    /**
     * Set the intervals for the timer sequence
     */
    fun setIntervals(intervals: List<TimerInterval>) {
        this.intervals = intervals
    }

    /**
     * Set whether the timer should repeat in a circular fashion
     */
    fun setCircular(isCircular: Boolean) {
        this.isCircular = isCircular
    }
    
    /**
     * Get the currently active interval
     */
    fun getCurrentInterval(): TimerInterval? {
        return if (intervals.isNotEmpty() && currentIntervalIndex >= 0 && currentIntervalIndex < intervals.size) {
            intervals[currentIntervalIndex]
        } else {
            null
        }
    }
    
    /**
     * Get all intervals in the sequence
     */
    fun getIntervals(): List<TimerInterval> {
        return intervals.toList() // Return a copy to prevent external modification
    }
    
    /**
     * Get the current lap count
     */
    fun getLapCount(): Int {
        return lapCount
    }
    
    /**
     * Check if the timer is currently running
     */
    fun isTimerRunning() = isRunning
    
    /**
     * Start the timer sequence from the beginning
     */
    fun startTimer() {
        if (intervals.isEmpty()) return
        
        // Start service as foreground with appropriate type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(silent = false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(silent = false))
        }
        
        // Acquire wake lock to keep CPU active
        acquireWakeLock()
        
        isRunning = true
        isPaused = false
        // Reset lap count when starting a new timer
        lapCount = 0
        timerListener?.onLapCountChanged(lapCount)
        startInterval(0)
    }
    
    /**
     * Pause the running timer
     */
    fun pauseTimer() {
        if (isRunning) {
            removeTimerCallbacks()
            isRunning = false
            isPaused = true
            forceUpdateNotification()
            timerListener?.onTimerPaused()
        }
    }
    
    /**
     * Resume a paused timer
     */
    fun resumeTimer() {
        if (isPaused && remainingTimeMillis > 0) {
            targetEndTime = SystemClock.elapsedRealtime() + remainingTimeMillis
            isRunning = true
            isPaused = false
            scheduleNextTick()
            forceUpdateNotification()
            timerListener?.onTimerResumed()
        }
    }
    
    /**
     * Stop the timer completely
     */
    fun stopTimer() {
        removeTimerCallbacks()
        isRunning = false
        isPaused = false
        remainingTimeMillis = 0
        
        // Stop foreground service and release wake lock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        releaseWakeLock()
        timerListener?.onTimerStopped()
    }
    
    /**
     * Reset the timer state
     */
    fun resetTimer() {
        stopTimer()
        remainingTimeMillis = 0
        currentIntervalIndex = 0
        lapCount = 0
        timerListener?.onLapCountChanged(lapCount)
        timerListener?.onCurrentIntervalChanged(-1) // No active interval when reset
    }
    
    /**
     * Start a specific interval in the sequence
     */
    private fun startInterval(index: Int) {
        if (index >= intervals.size) {
            timerListener?.onSequenceComplete()
            stopTimer()
            return
        }

        currentIntervalIndex = index
        val interval = intervals[index]
        val totalMillis = interval.getTotalTimeMillis()
        
        // Notify listener about the current interval change
        timerListener?.onCurrentIntervalChanged(currentIntervalIndex)
        
        // Set up timing parameters
        remainingTimeMillis = totalMillis
        targetEndTime = SystemClock.elapsedRealtime() + totalMillis
        
        // Update notification and start timer
        forceUpdateNotification()
        removeTimerCallbacks()
        scheduleNextTick()
    }
    
    // Handler for timer callbacks on the main thread
    private val handler = android.os.Handler(Looper.getMainLooper())
    
    /**
     * Runnable that handles the timer tick logic
     */
    private val timerRunnable = object : Runnable {
        override fun run() {
            val currentTime = SystemClock.elapsedRealtime()
            remainingTimeMillis = targetEndTime - currentTime
            
            if (remainingTimeMillis <= 0) {
                // Timer completed for current interval
                remainingTimeMillis = 0
                triggerVibration()
                timerListener?.onIntervalComplete(currentIntervalIndex)
                
                // Move to next interval or complete sequence
                val nextIndex = currentIntervalIndex + 1
                if (nextIndex < intervals.size) {
                    startInterval(nextIndex)
                } else if (isCircular) {
                    // Start from the beginning if circular is enabled
                    // Increment lap count since one complete sequence is finished
                    lapCount++
                    timerListener?.onLapCountChanged(lapCount)
                    startInterval(0)
                } else {
                    timerListener?.onSequenceComplete()
                    stopTimer()
                }
            } else {
                // Update listener on every tick for smooth UI
                timerListener?.onTimerTick(remainingTimeMillis)
                
                // Update notification only every second to avoid throttling
                val now = SystemClock.elapsedRealtime()
                if (now - lastNotificationUpdate >= NOTIFICATION_UPDATE_INTERVAL) {
                    updateNotification()
                    lastNotificationUpdate = now
                }
                
                scheduleNextTick()
            }
        }
    }
    
    /**
     * Schedule the next timer tick
     */
    private fun scheduleNextTick() {
        handler.postDelayed(timerRunnable, UPDATE_INTERVAL)
    }
    
    /**
     * Cancel all scheduled timer ticks
     */
    private fun removeTimerCallbacks() {
        handler.removeCallbacks(timerRunnable)
    }
    
    /**
     * Create the notification channel for Android O and higher
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Timer notifications"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Build the notification for the foreground service
     * @param silent If true, the notification will not make sound or vibrate
     */
    private fun buildNotification(silent: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val currentInterval = getCurrentInterval()
        val title = if (isPaused) 
            getString(R.string.timer_paused) 
        else 
            currentInterval?.name ?: getString(R.string.app_name)
            
        val contentText = if (isPaused) {
            formatTimeForDisplay(remainingTimeMillis)
        } else {
            getString(
                R.string.notification_timer_running, 
                formatTimeForDisplay(remainingTimeMillis)
            )
        }
        
        // Create notification builder
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_empty_timer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        // Make notification silent for regular updates to avoid constant vibration/sound
        if (silent) {
            notificationBuilder.setSound(null)
                .setVibrate(null)
                .setOnlyAlertOnce(true)
        }
        
        // Add action buttons based on current state
        if (isPaused) {
            // When paused, show Resume and Stop buttons
            val resumeIntent = Intent(this, ForegroundTimerService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder.addAction(
                R.drawable.ic_play, 
                getString(R.string.resume), 
                resumePendingIntent
            )
        } else {
            // When running, show Pause button
            val pauseIntent = Intent(this, ForegroundTimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder.addAction(
                R.drawable.ic_pause, 
                getString(R.string.pause), 
                pausePendingIntent
            )
        }
        
        // Always show Stop button
        val stopIntent = Intent(this, ForegroundTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationBuilder.addAction(
            R.drawable.ic_stop, 
            getString(R.string.stop), 
            stopPendingIntent
        )
        
        return notificationBuilder.build()
    }
    
    /**
     * Update the notification with current timer state (silent update)
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(silent = true))
    }
    
    /**
     * Force update the notification immediately (used for state changes)
     */
    private fun forceUpdateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(silent = false))
        lastNotificationUpdate = SystemClock.elapsedRealtime()
    }
    
    /**
     * Acquire a wake lock to keep the CPU active during timer operation
     */
    private fun acquireWakeLock() {
        // Release any existing wake lock first to prevent leaks
        releaseWakeLock()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            // Set a timeout to ensure wake lock is released even if there's an error
            acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }
    
    /**
     * Release the wake lock when timer is stopped
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wakeLock = null
        }
    }
    
    /**
     * Trigger vibration when an interval completes
     */
    private fun triggerVibration() {
        // Acquire a temporary wake lock to ensure vibration works even when screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val vibrationWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$WAKE_LOCK_TAG:vibration"
        )
        
        try {
            // Acquire wake lock for a short duration to ensure vibration
            vibrationWakeLock.acquire(5000L) // 5 seconds should be enough
            
            // Create a simpler vibration pattern with only a single pulse
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // For Android 8.0+, use a vibration effect with stronger amplitude
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 800),  // Timing pattern: wait, vibrate (single vibration)
                            intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE),  // Amplitude pattern
                            -1  // Don't repeat
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 800), -1)  // Pattern for older devices (single vibration)
                }
            }
        } finally {
            // Release the temporary wake lock
            if (vibrationWakeLock.isHeld) {
                vibrationWakeLock.release()
            }
        }
    }
    
    /**
     * Format time for display
     */
    fun formatTimeForDisplay(millisUntilFinished: Long): String {
        val roundedMillis = millisUntilFinished + 500
        val minutes = TimeUnit.MILLISECONDS.toMinutes(roundedMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(roundedMillis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    override fun onDestroy() {
        // Clean up all resources
        removeTimerCallbacks()
        releaseWakeLock()
        timerListener = null
        super.onDestroy()
    }
}
