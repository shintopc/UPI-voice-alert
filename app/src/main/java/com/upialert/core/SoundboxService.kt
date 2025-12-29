package com.upialert.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.upialert.R
import com.upialert.ui.MainActivity

class SoundboxService : Service() {

    private lateinit var ttsManager: TtsManager
    
    companion object {
        const val CHANNEL_ID = "UPI_SOUNDBOX_CHANNEL"
        const val ACTION_SPEAK = "com.upialert.action.SPEAK"
        const val EXTRA_MESSAGE = "com.upialert.extra.MESSAGE"
        
        const val EXTRA_SENDER = "EXTRA_SENDER"
        
        fun start(context: Context) {
            val intent = Intent(context, SoundboxService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun speakAmount(context: Context, amount: Double, senderName: String = "") {
            val intent = Intent(context, SoundboxService::class.java).apply {
                action = ACTION_SPEAK
                putExtra("EXTRA_AMOUNT", amount)
                putExtra(EXTRA_SENDER, senderName)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun speak(context: Context, message: String, onComplete: (() -> Unit)? = null) {
            val intent = Intent(context, SoundboxService::class.java).apply {
                action = ACTION_SPEAK
                putExtra(EXTRA_MESSAGE, message)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var originalVolume: Int = -1
    private lateinit var audioManager: android.media.AudioManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ttsManager = TtsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        // Acquire WakeLock immediately
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "UPIAlert::SoundboxWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes timeout*/)

        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SPEAK) {
            val amount = intent.getDoubleExtra("EXTRA_AMOUNT", -1.0)
            val senderName = intent.getStringExtra(EXTRA_SENDER) ?: ""
            
            if (amount > 0) {
                 val prefs = getSharedPreferences("upi_settings", android.content.Context.MODE_PRIVATE)
                 
                 // 1. Check Master Switch
                 if (!prefs.getBoolean("voice_enabled", true)) {
                     finishWork()
                     return START_NOT_STICKY
                 }
                 
                 // 2. Check Schedule
                 if (prefs.getBoolean("schedule_enabled", false)) {
                     val startH = prefs.getInt("mute_start_hour", 22)
                     val startM = prefs.getInt("mute_start_minute", 0)
                     val endH = prefs.getInt("mute_end_hour", 6)
                     val endM = prefs.getInt("mute_end_minute", 0)
                     
                     val now = java.util.Calendar.getInstance()
                     val currentH = now.get(java.util.Calendar.HOUR_OF_DAY)
                     val currentM = now.get(java.util.Calendar.MINUTE)
                     
                     val currentTimeMinutes = currentH * 60 + currentM
                     val startTimeMinutes = startH * 60 + startM
                     val endTimeMinutes = endH * 60 + endM
                     
                     var isMuted = false
                     if (startTimeMinutes > endTimeMinutes) {
                         // Overnight (e.g. 22:00 to 06:00)
                         // Muted if Current >= Start OR Current < End
                         if (currentTimeMinutes >= startTimeMinutes || currentTimeMinutes < endTimeMinutes) {
                             isMuted = true
                         }
                     } else {
                         // Same day (e.g. 13:00 to 15:00)
                         // Muted if Current >= Start AND Current < End
                         if (currentTimeMinutes >= startTimeMinutes && currentTimeMinutes < endTimeMinutes) {
                             isMuted = true
                         }
                     }
                     
                     if (isMuted) {
                         android.util.Log.d("SoundboxService", "Muted due to schedule")
                         finishWork()
                         return START_NOT_STICKY
                     }
                 }
                 
                 val lang = prefs.getString("language", "English")
                 
                 val formattedAmount = if (amount % 1 == 0.0) {
                     amount.toInt().toString()
                 } else {
                     amount.toString()
                 }

                 val (text, locale, rate) = when (lang) {
                     "Hindi" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") " $senderName se ₹$formattedAmount रुपये प्राप्त हुए" else " ₹$formattedAmount रुपये प्राप्त हुए"
                         Triple(msg, java.util.Locale("hi", "IN"), 1.0f)
                     }
                     "Malayalam" -> {
                         // Remove space between amount and 'രൂപ' to force Malayalam number pronunciation
                         // Format: "Name Amount... payment cheythirikkunnu" (User preferred format)
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName ${formattedAmount}രൂപ പേയ്‌മെന്റ് ചെയ്തിരിക്കുന്നു" else "${formattedAmount}രൂപ പേയ്‌മെന്റ് ചെയ്തിരിക്കുന്നു"
                         Triple(msg, java.util.Locale("ml", "IN"), 0.85f)
                     }
                     "Bengali" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName ের থেকে ₹$formattedAmount প্রাপ্ত হয়েছে" else "₹$formattedAmount প্রাপ্ত হয়েছে"
                         Triple(msg, java.util.Locale("bn", "IN"), 1.0f)
                     }
                     "Gujarati" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName તરફથી ₹$formattedAmount મળ્યા છે" else "₹$formattedAmount મળ્યા છે"
                         Triple(msg, java.util.Locale("gu", "IN"), 1.0f)
                     }
                     "Kannada" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName ರವರಿಂದ ₹$formattedAmount ಸ್ವೀಕರಿಸಲಾಗಿದೆ" else "₹$formattedAmount ಸ್ವೀಕರಿಸಲಾಗಿದೆ"
                         Triple(msg, java.util.Locale("kn", "IN"), 1.0f)
                     }
                     "Marathi" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName कडून ₹$formattedAmount प्राप्त झाले" else "₹$formattedAmount प्राप्त झाले"
                         Triple(msg, java.util.Locale("mr", "IN"), 1.0f)
                     }
                     "Tamil" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName இடமிருந்து ₹$formattedAmount பெறப்பட்டது" else "₹$formattedAmount பெறப்பட்டது"
                         Triple(msg, java.util.Locale("ta", "IN"), 1.0f)
                     }
                     "Telugu" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName నుండి ₹$formattedAmount స్వీకరించబడింది" else "₹$formattedAmount స్వీకరించబడింది"
                         Triple(msg, java.util.Locale("te", "IN"), 1.0f)
                     }
                     "Urdu" -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "$senderName سے ₹$formattedAmount وصول ہوئے" else "₹$formattedAmount وصول ہوئے"
                         Triple(msg, java.util.Locale("ur", "IN"), 1.0f)
                     }
                     else -> {
                         val msg = if (senderName.isNotEmpty() && senderName != "Unknown") "Received ₹$formattedAmount from $senderName" else "Received ₹$formattedAmount"
                         Triple(msg, java.util.Locale("en", "IN"), 1.0f)
                     }
                 }
                 


                 val userSpeed = prefs.getFloat("voice_speed", 1.0f)
                 
                 ttsManager.setLanguage(locale)
                 ttsManager.setRate(rate * userSpeed)
                 
                 // Maximize Volume for Alarm Stream
                 originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                 val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                 audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                 
                 ttsManager.speak(text) {
                     // On Complete
                     finishWork()
                 }
            } else {
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                if (message != null) {
                    ttsManager.speak(message) {
                        finishWork()
                    }
                } else {
                    finishWork()
                }
            }
        } else {
            // Not a speak action? Stop.
             finishWork()
        }
        
        return START_NOT_STICKY
    }

    private fun finishWork() {
        try {
            if (originalVolume != -1) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, originalVolume, 0)
            }
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ttsManager.shutdown()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "UPI Soundbox Service"
            val descriptionText = "Keeps the app active to announce payments"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UPI Soundbox Active")
            .setContentText("Listening for payments...")
            .setSmallIcon(R.mipmap.ic_launcher) // Use default launcher icon for now
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
