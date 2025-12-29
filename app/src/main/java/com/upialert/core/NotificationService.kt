package com.upialert.core

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.upialert.data.AppDatabase
import com.upialert.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        Log.d("NotificationService", "Service Created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notification ->
            val packageName = notification.packageName
            
            // Ignore own notifications (Foreground Service) to prevent overwriting debug logs
            if (packageName == applicationContext.packageName) {
                return
            }

            val extras = notification.notification.extras
            // DEBUG: Dump ALL extras to find where the text is hiding
            val sb = StringBuilder()
            val keys = extras.keySet()
            for (key in keys) {
                val value = extras.get(key)
                if (value != null) {
                    sb.append("$key: $value\n")
                }
            }
            val dump = sb.toString()

            getSharedPreferences("upi_debug", android.content.Context.MODE_PRIVATE).edit().apply {
                putString("last_pkg", packageName)
                putString("last_title", "See Dump")
                putString("last_text", "See Dump")
                putString("last_dump", dump) // New field
                putLong("last_time", System.currentTimeMillis())
                apply()
            }
            
            // Try to extract standard fields more robustly for logic
            val titleCS = extras.getCharSequence("android.title")
            val textCS = extras.getCharSequence("android.text")
            val bigTextCS = extras.getCharSequence("android.bigText")
            
            val title = titleCS?.toString()
            val text = textCS?.toString() ?: bigTextCS?.toString()
            
            if (!UpiParser.isSupportedPackage(packageName)) {
                return
            }

            Log.d("NotificationListener", "Target Notification: $packageName Title: $title Text: $text")

            val amount = UpiParser.parseAmount(title, text)
            
            if (amount != null && amount > 0) {
                // Determine Sender Name
                // Usually in title: "Received from John" or text "Credit from John"
                // Simple logic: Use Title as a proxy or just "Unknown"
                val senderName = if (title?.contains("paid you", ignoreCase = true) == true) {
                    title.substringBefore(" paid you").trim()
                } else if (title?.contains("received from", ignoreCase = true) == true) {
                    title.substringAfter("received from").trim()
                } else {
                    title ?: "Unknown"
                }

                val transaction = TransactionEntity(
                    amount = amount,
                    appPackage = packageName,
                    senderName = senderName,
                    timestamp = System.currentTimeMillis(),
                    rawMessage = "$title | $text"
                )

                scope.launch {
                    try {
                        database.transactionDao().insert(transaction)
                        Log.d("NotificationListener", "Transaction Saved: $amount")
                        
                        // Announce
                        SoundboxService.speakAmount(applicationContext, amount, senderName)
                    } catch (e: Exception) {
                        Log.e("NotificationListener", "Error saving transaction", e)
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationService", "Listener Connected")
        // Optionally start the foreground service to ensure we stick around
        SoundboxService.start(applicationContext)
    }
}
