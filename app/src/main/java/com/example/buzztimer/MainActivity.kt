package com.example.buzztimer

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.buzztimer.activity.SettingsActivity
import com.example.buzztimer.adapter.TimerIntervalAdapter
import com.example.buzztimer.databinding.ActivityMainBinding
import com.example.buzztimer.databinding.DialogEditIntervalBinding
import com.example.buzztimer.model.TimerInterval
import com.example.buzztimer.service.ForegroundTimerService

class MainActivity : AppCompatActivity(), ForegroundTimerService.TimerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var intervalAdapter: TimerIntervalAdapter
    private var timerService: ForegroundTimerService? = null
    private var isBound: Boolean = false

    private var isPaused: Boolean = false
    
    // Connection to the foreground service
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundTimerService.LocalBinder
            timerService = binder.getService()
            timerService?.setTimerListener(this@MainActivity)
            isBound = true
            
            // Update UI based on current service state if needed
            if (timerService?.isTimerRunning() == true) {
                binding.btnStart.text = getString(R.string.pause)
                binding.btnStart.icon = getDrawable(R.drawable.ic_pause)
                binding.btnStop.isEnabled = true
                binding.fabAddInterval.hide()
                binding.switchCircular.isEnabled = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            timerService = null
        }
    }

    // Register the permissions callback
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, proceed with timer
            if (isPaused) {
                resumeTimer()
            } else {
                startTimer()
            }
        } else {
            Toast.makeText(
                this,
                R.string.request_notifications_permission,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        
        // Bind to the service
        bindTimerService()

        setupRecyclerView()
        setupButtons()
        updateEmptyState()
        setupProgressIndicator()
        
        // Initialize lap counter to hidden
        binding.tvLapCounter.visibility = android.view.View.GONE
    }
    
    private fun bindTimerService() {
        Intent(this, ForegroundTimerService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStart() {
        super.onStart()
        if (!isBound) {
            bindTimerService()
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Don't unbind - we want the service to continue running
        // We'll only unbind when explicitly stopping the timer
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupRecyclerView() {
        intervalAdapter = TimerIntervalAdapter(
            mutableListOf(),
            onEditClick = { position, interval -> showEditIntervalDialog(position, interval) },
            onDeleteClick = { position -> deleteInterval(position) },
            onDuplicateClick = { position, interval -> duplicateInterval(position, interval) }
        )

        binding.rvTimerSequence.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = intervalAdapter
            
            // Set up drag-to-reorder functionality
            intervalAdapter.attachToRecyclerView(this)
        }
    }
    
    private fun updateEmptyState() {
        val isEmpty = intervalAdapter.getIntervals().isEmpty()
        binding.emptyStateLayout.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        binding.contentLayout.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
    }
    
    private fun setupProgressIndicator() {
        // Initialize the progress indicator
        binding.timerProgressIndicator.progress = 0
        binding.timerProgressIndicator.max = 100
    }

    private fun setupButtons() {
        // Initialize button text
        binding.btnStart.text = getString(R.string.start)
        binding.btnStart.icon = getDrawable(R.drawable.ic_play)
        binding.btnStop.isEnabled = false
        
        // Setup FAB for adding intervals
        binding.fabAddInterval.setOnClickListener {
            showEditIntervalDialog()
        }

        // Setup main control buttons
        binding.btnStart.setOnClickListener {
            if (timerService?.isTimerRunning() == true) {
                pauseTimer()
            } else {
                if (isPaused) {
                    resumeTimer()
                } else {
                    startTimer()
                }
            }
        }

        binding.btnStop.setOnClickListener {
            stopAndResetTimer()
        }
    }

    private fun showEditIntervalDialog(position: Int = -1, interval: TimerInterval? = null) {
        val dialogBinding = DialogEditIntervalBinding.inflate(LayoutInflater.from(this))
        
        // Pre-fill with existing interval values if editing
        if (interval != null) {
            dialogBinding.etIntervalName.setText(interval.name ?: "")
            dialogBinding.etMinutes.setText(interval.minutes.toString())
            dialogBinding.etSeconds.setText(interval.seconds.toString())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (position == -1) R.string.add_interval else R.string.edit_interval)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etIntervalName.text.toString().trim()
                val minutes = dialogBinding.etMinutes.text.toString().toIntOrNull() ?: 0
                val seconds = dialogBinding.etSeconds.text.toString().toIntOrNull() ?: 0

                // Validate that time is not 0m 0s
                if (minutes == 0 && seconds == 0) {
                    Toast.makeText(this, R.string.invalid_zero_time, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Validate that time values are not negative
                if (minutes < 0 || seconds < 0) {
                    Toast.makeText(this, R.string.invalid_negative_time, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (seconds >= 60) {
                    Toast.makeText(this, R.string.invalid_time, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Use the name only if it's not empty
                val intervalName = if (name.isNotBlank()) name else null
                val newInterval = TimerInterval(minutes, seconds, intervalName)

                if (position == -1) {
                    intervalAdapter.addInterval(newInterval)
                } else {
                    intervalAdapter.updateInterval(position, newInterval)
                }
                
                // Update empty state after adding/editing
                updateEmptyState()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun deleteInterval(position: Int) {
        intervalAdapter.removeInterval(position)
        // Update empty state after deleting
        updateEmptyState()
    }

    private fun duplicateInterval(position: Int, interval: TimerInterval) {
        // Create a copy of the interval
        val duplicatedInterval = TimerInterval(
            interval.minutes,
            interval.seconds,
            interval.name?.let { "$it (Copy)" }
        )
        
        // Insert the duplicated interval right after the original
        intervalAdapter.insertInterval(position + 1, duplicatedInterval)
        
        // Update empty state after adding
        updateEmptyState()
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> true
                else -> {
                    // Show rationale dialog if needed
                    AlertDialog.Builder(this)
                        .setTitle(R.string.permission_required)
                        .setMessage(R.string.request_notifications_permission)
                        .setPositiveButton(R.string.grant_permission) { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                    false
                }
            }
        } else {
            // Before Android 13, notification permission was granted automatically
            true
        }
    }
    
    private fun startTimer() {
        // Check if service is bound
        if (!isBound || timerService == null) {
            bindTimerService()
            Toast.makeText(this, "Service initializing, please try again", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            !checkNotificationPermission()) {
            return
        }
        
        val intervals = intervalAdapter.getIntervals()
        if (intervals.isEmpty()) {
            Toast.makeText(this, R.string.no_intervals_added, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Start the service first to make it a foreground service
        val serviceIntent = Intent(this, ForegroundTimerService::class.java)
        startService(serviceIntent)
        
        // Set up and start timer
        timerService?.setIntervals(intervals)
        timerService?.setCircular(binding.switchCircular.isChecked)
        timerService?.startTimer()
        isPaused = false
        
        // Update UI for timer running state
        binding.btnStart.text = getString(R.string.pause)
        binding.btnStart.icon = getDrawable(R.drawable.ic_pause)
        binding.btnStop.isEnabled = true
        binding.fabAddInterval.hide() // Hide FAB when timer is running
        binding.switchCircular.isEnabled = false
    }

    private fun pauseTimer() {
        timerService?.pauseTimer()
        isPaused = true
        
        // Update UI for paused state
        binding.btnStart.text = getString(R.string.resume)
        binding.btnStart.icon = getDrawable(R.drawable.ic_play)
        binding.btnStop.isEnabled = true
    }

    private fun resumeTimer() {
        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            !checkNotificationPermission()) {
            return
        }
            
        timerService?.resumeTimer()
        isPaused = false
        
        // Update UI for resumed state
        binding.btnStart.text = getString(R.string.pause)
        binding.btnStart.icon = getDrawable(R.drawable.ic_pause)
    }

    private fun stopAndResetTimer() {
        timerService?.resetTimer()
        isPaused = false
        
        // Clear active interval highlighting
        intervalAdapter.clearActiveInterval()
        
        // Reset UI to initial state
        binding.tvCurrentTimer.text = "00:00"
        binding.timerProgressIndicator.progress = 0
        binding.btnStart.text = getString(R.string.start)
        binding.btnStart.icon = getDrawable(R.drawable.ic_play)
        binding.btnStop.isEnabled = false
        binding.fabAddInterval.show() // Show FAB when timer is stopped
        binding.switchCircular.isEnabled = true
        binding.tvLapCounter.visibility = android.view.View.GONE
        
        // Stop the service
        val serviceIntent = Intent(this, ForegroundTimerService::class.java)
        stopService(serviceIntent)
    }

    private fun stopTimer() {
        timerService?.stopTimer()
        isPaused = false
        
        // Reset UI to initial state
        binding.tvCurrentTimer.text = "00:00"
        binding.timerProgressIndicator.progress = 0
        binding.btnStart.isEnabled = true
        binding.btnStart.text = getString(R.string.start)
        binding.btnStart.icon = getDrawable(R.drawable.ic_play)
        binding.btnStop.isEnabled = false
        binding.fabAddInterval.show() // Show FAB when timer is stopped
        binding.switchCircular.isEnabled = true
        binding.tvLapCounter.visibility = android.view.View.GONE
        
        // Stop the service
        val serviceIntent = Intent(this, ForegroundTimerService::class.java)
        stopService(serviceIntent)
    }

    // TimerListener implementation
    override fun onTimerTick(millisUntilFinished: Long) {
        // Update UI on main thread
        runOnUiThread {
            // Update time display
            binding.tvCurrentTimer.text = timerService?.formatTimeForDisplay(millisUntilFinished) ?: "00:00"
            
            // Update progress indicator
            val currentInterval = timerService?.getCurrentInterval()
            val totalDuration = currentInterval?.getTotalTimeMillis() ?: 1L
            val progress = ((totalDuration - millisUntilFinished) * 100 / totalDuration).toInt()
            binding.timerProgressIndicator.progress = progress
        }
    }

    override fun onIntervalComplete(intervalIndex: Int) {
        // Update UI on main thread
        runOnUiThread {
            // Show visual flash effect for immediate feedback
            showIntervalCompleteFlash()
            
            // Reset progress for next interval
            binding.timerProgressIndicator.progress = 0
        }
    }
    
    override fun onLapCountChanged(lapCount: Int) {
        runOnUiThread {
            // Show/hide and update the lap counter
            if (lapCount > 0) {
                binding.tvLapCounter.visibility = android.view.View.VISIBLE
                binding.tvLapCounter.text = getString(R.string.lap_counter, lapCount)
            } else {
                binding.tvLapCounter.visibility = android.view.View.GONE
            }
        }
    }

    override fun onSequenceComplete() {
        // Update UI on main thread
        runOnUiThread {
            // Clear active interval highlighting
            intervalAdapter.clearActiveInterval()
            
            // Reset UI when sequence completes
            binding.tvCurrentTimer.text = "00:00"
            binding.timerProgressIndicator.progress = 0
            binding.btnStart.isEnabled = true
            binding.btnStart.text = getString(R.string.start)
            binding.btnStart.icon = getDrawable(R.drawable.ic_play)
            binding.btnStop.isEnabled = false
            binding.fabAddInterval.show() // Show FAB when timer is completed
            binding.switchCircular.isEnabled = true
            
            // Keep lap counter visible when circular is enabled, otherwise hide it
            if (!binding.switchCircular.isChecked) {
                binding.tvLapCounter.visibility = android.view.View.GONE
                Toast.makeText(this, R.string.timer_completed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onCurrentIntervalChanged(intervalIndex: Int) {
        // Update UI on main thread
        runOnUiThread {
            // Update the adapter to highlight the current interval
            intervalAdapter.setActiveInterval(intervalIndex)
        }
    }
    
    /**
     * Creates a full-screen flash effect when an interval completes
     * Overlays the entire screen with a bright flash for immediate visual feedback
     */
    private fun showIntervalCompleteFlash() {
        // Create a full-screen flash overlay
        val flashOverlay = android.view.View(this)
        flashOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
        flashOverlay.alpha = 0f
        
        // Add the overlay to the root layout
        val rootLayout = binding.root as androidx.coordinatorlayout.widget.CoordinatorLayout
        val layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(flashOverlay, layoutParams)
        
        // Create flash animation - fade in quickly, hold, then fade out
        val flashAnimator = ValueAnimator.ofFloat(0f, 0.8f, 0.8f, 0f)
        flashAnimator.duration = 400 // 400ms total flash duration
        flashAnimator.addUpdateListener { animator ->
            val alpha = animator.animatedValue as Float
            flashOverlay.alpha = alpha
        }
        
        // Remove the overlay when animation completes
        flashAnimator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {
                rootLayout.removeView(flashOverlay)
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                rootLayout.removeView(flashOverlay)
            }
        })
        
        // Start the flash animation
        flashAnimator.start()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_clear_all -> {
                showClearAllIntervalsDialog()
                true
            }
            R.id.menu_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showClearAllIntervalsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(R.string.clear_all_confirmation)
            .setPositiveButton(R.string.clear_all) { _, _ ->
                clearAllIntervals()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun clearAllIntervals() {
        // Stop timer if running
        if (timerService?.isTimerRunning() == true) {
            stopTimer()
        }
        
        // Clear all intervals
        val intervals = intervalAdapter.getIntervals().toMutableList()
        repeat(intervals.size) {
            intervalAdapter.removeInterval(0)
        }
        
        // Update empty state
        updateEmptyState()
        
        Toast.makeText(this, R.string.all_intervals_cleared, Toast.LENGTH_SHORT).show()
    }
    
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}
