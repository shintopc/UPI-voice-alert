package com.upialert.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.upialert.R
import com.upialert.core.SoundboxService
import com.upialert.databinding.ActivityDiagnosticBinding
import java.util.Locale

class DiagnosticActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        runInitialChecks()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnTestNotification.setOnClickListener {
            testNotificationListener()
        }

        binding.btnTestService.setOnClickListener {
            testForegroundService()
        }

        binding.btnTestAudio.setOnClickListener {
            testAudio()
        }

        binding.btnTestWakelock.setOnClickListener {
            testWakeLock()
        }

        binding.btnOpenBatterySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun runInitialChecks() {
        checkBatteryOptimization()
        checkOem()
    }

    // 1. Notification Listener Test
    private fun testNotificationListener() {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = flat != null && flat.contains(packageName)

        if (isEnabled) {
            updateStatus(binding.tvStatusListener, "Listener Enabled: YES", true)
            binding.imgStatusListener.setImageResource(android.R.drawable.checkbox_on_background)
            binding.imgStatusListener.setColorFilter(getColor(R.color.green_money))
            
            // Simulate a fake notification to self (requires channel)
            simulateMockNotification()
        } else {
            updateStatus(binding.tvStatusListener, "Listener Enabled: NO. Please enable it.", false)
            binding.imgStatusListener.setImageResource(android.R.drawable.ic_delete)
            binding.imgStatusListener.setColorFilter(getColor(R.color.red_danger))
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun simulateMockNotification() {
        val channelId = "test_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Diagnostic Test", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Test: Received from AutoTester")
            .setContentText("Credited: ₹1.00") // This SHOULD trigger the listener if logic allows self, but we blocked self.
            // Actually, we blocked 'packageName' in NotificationService. 
            // So this WON'T trigger the production path (which is good for safety).
            // We just verify we can Post.
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
            
        nm.notify(999, notification)
        Toast.makeText(this, "Posted Test Notification. Check if erased/logged.", Toast.LENGTH_SHORT).show()
        
        // Auto cancel
        handler.postDelayed({ nm.cancel(999) }, 3000)
    }

    // 2. Foreground Service Test
    private fun testForegroundService() {
        try {
            SoundboxService.start(this)
            updateStatus(binding.tvStatusService, "Service Command Sent. Check Notification Shade.", true)
            binding.imgStatusService.setImageResource(android.R.drawable.checkbox_on_background)
            binding.imgStatusService.setColorFilter(getColor(R.color.green_money))
            
            // Auto stop after 5s
            handler.postDelayed({
                // SoundboxService doesn't have a stop method exposed easily besides modifying intent.
                // We rely on system handling or just let it run.
            }, 5000)
        } catch (e: Exception) {
            updateStatus(binding.tvStatusService, "Error: ${e.message}", false)
            binding.imgStatusService.setColorFilter(getColor(R.color.red_danger))
        }
    }

    // 3. Audio Test
    private fun testAudio() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            SoundboxService.speak(this, "This is a test voice alert running at maximum volume.")
            
            binding.tvStatusSystem.text = "Audio: Playing at Max Vol ($maxVol)"
            
            // Restore volume after 3s
            handler.postDelayed({
                am.setStreamVolume(AudioManager.STREAM_ALARM, originalVol, 0)
                binding.tvStatusSystem.append("\nVolume Restored.")
            }, 4000)
        } catch (e: Exception) {
            binding.tvStatusSystem.text = "Audio Error: ${e.message}"
        }
    }

    // 4. WakeLock Test
    private fun testWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UPIalert:DiagnosticTest")
        
        try {
            wakeLock.acquire(5000) // Auto release 5s
            binding.tvStatusSystem.text = "WakeLock: ACQUIRED for 5 seconds."
            
            if (wakeLock.isHeld) {
                binding.tvStatusSystem.append("\nLock is Active: YES")
            }
        } catch (e: Exception) {
            binding.tvStatusSystem.text = "WakeLock Error: ${e.message}"
        }
    }

    // 5. Battery & OEM
    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        if (isIgnored) {
            binding.tvBatteryStatus.text = "Battery Optimization: DISABLED (Good)"
            binding.tvBatteryStatus.setTextColor(getColor(R.color.green_money))
            binding.btnOpenBatterySettings.visibility = android.view.View.GONE
        } else {
            binding.tvBatteryStatus.text = "Battery Optimization: ENABLED (May delay alerts)"
            binding.tvBatteryStatus.setTextColor(getColor(R.color.red_danger))
            binding.btnOpenBatterySettings.visibility = android.view.View.VISIBLE
        }
    }

    private fun checkOem() {
        val manufacturer = Build.MANUFACTURER.uppercase(Locale.getDefault())
        binding.tvOemInfo.text = "Manufacturer: $manufacturer"
        
        val guidance = when {
            manufacturer.contains("XIAOMI") -> "Xiaomi/Redmi: Enable 'Autostart' in Security App > Permissions."
            manufacturer.contains("VIVO") -> "Vivo: Enable 'High Background Power Consumption' in Settings > Battery."
            manufacturer.contains("OPPO") || manufacturer.contains("REALME") -> "Oppo/Realme: Lock app in recent tasks (Split Screen menu)."
            manufacturer.contains("SAMSUNG") -> "Samsung: Add to 'Never Sleeping Apps' in Device Care."
            else -> "Ensure 'Background Restriction' is OFF and 'Battery Saver' is OFF."
        }
        binding.tvOemGuidance.text = guidance
    }

    private fun updateStatus(textView: android.widget.TextView, text: String, isSuccess: Boolean) {
        textView.text = text
        textView.setTextColor(if (isSuccess) getColor(R.color.black) else getColor(R.color.red_danger))
    }
}
