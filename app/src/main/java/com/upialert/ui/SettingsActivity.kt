package com.upialert.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.upialert.core.SoundboxService
import com.upialert.data.AppDatabase
import com.upialert.data.TransactionEntity
import com.upialert.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    
    // Default Schedule: 10 PM to 6 AM (Mute window)
    private var startHour = 22
    private var startMinute = 0
    private var endHour = 6
    private var endMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTestVoice.setOnClickListener {
            SoundboxService.speakAmount(this, 123.45)
        }





        // Initialize Battery UI updates in onResume

        
        binding.btnDiagnostic.setOnClickListener {
            startActivity(Intent(this, DiagnosticActivity::class.java))
        }

        binding.btnClearData.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Factory Reset")
                .setMessage("Are you sure you want to clear ALL data? This includes transaction history and settings. This cannot be undone.")
                .setPositiveButton("Clear Data") { _, _ ->
                    lifecycleScope.launch {
                        // Clear Database
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.transactionDao().deleteAll()
                        
                        // Clear Settings
                        getSharedPreferences("upi_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        getSharedPreferences("upi_debug", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        
                        android.widget.Toast.makeText(this@SettingsActivity, "All data cleared", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // Reset spinner to default
                        binding.spinnerLanguage.setSelection(0)
                        
                        // Optional: Navigate back to Main
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupLanguageSpinner()
        setupVoiceSettings()
        setupPaymentSettings()
    }

    override fun onResume() {
        super.onResume()
        checkBatteryOptimizationStatus()
    }

    private fun setupVoiceSettings() {
        val prefs = getSharedPreferences("upi_settings", android.content.Context.MODE_PRIVATE)
        
        // Load Toggles
        binding.switchVoiceEnabled.isChecked = prefs.getBoolean("voice_enabled", true)
        val isScheduleEnabled = prefs.getBoolean("schedule_enabled", false)
        binding.switchScheduleEnabled.isChecked = isScheduleEnabled
        binding.layoutSchedule.visibility = if (isScheduleEnabled) android.view.View.VISIBLE else android.view.View.GONE
        
        // Load Time
        startHour = prefs.getInt("mute_start_hour", 22)
        startMinute = prefs.getInt("mute_start_minute", 0)
        endHour = prefs.getInt("mute_end_hour", 6)
        endMinute = prefs.getInt("mute_end_minute", 0)
        
        updateTimeTexts()
        
        // Listeners
        binding.switchVoiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("voice_enabled", isChecked).apply()
        }
        
        binding.switchScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("schedule_enabled", isChecked).apply()
            binding.layoutSchedule.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        binding.etTimeStart.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                .setHour(startHour)
                .setMinute(startMinute)
                .setTitleText("Select Mute Start Time")
                .build()
                
            picker.addOnPositiveButtonClickListener {
                startHour = picker.hour
                startMinute = picker.minute
                saveSchedule(prefs)
                updateTimeTexts()
            }
            picker.show(supportFragmentManager, "start_time")
        }
        
        binding.etTimeEnd.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                .setHour(endHour)
                .setMinute(endMinute)
                .setTitleText("Select Mute End Time")
                .build()
                
            picker.addOnPositiveButtonClickListener {
                endHour = picker.hour
                endMinute = picker.minute
                saveSchedule(prefs)
                updateTimeTexts()
            }
            picker.show(supportFragmentManager, "end_time")
        }
    }
    
    private fun saveSchedule(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .putInt("mute_start_hour", startHour)
            .putInt("mute_start_minute", startMinute)
            .putInt("mute_end_hour", endHour)
            .putInt("mute_end_minute", endMinute)
            .apply()
    }
    
    private fun updateTimeTexts() {
        binding.etTimeStart.setText(String.format("%02d:%02d", startHour, startMinute))
        binding.etTimeEnd.setText(String.format("%02d:%02d", endHour, endMinute))
    }

    private fun setupPaymentSettings() {
        val prefs = getSharedPreferences("upi_settings", android.content.Context.MODE_PRIVATE)
        
        // Load existing
        binding.etUpiId.setText(prefs.getString("vpa", ""))
        binding.etPayeeName.setText(prefs.getString("payee_name", ""))
        
        binding.btnSaveUpiDetails.setOnClickListener {
            val vpa = binding.etUpiId.text.toString().trim()
            val name = binding.etPayeeName.text.toString().trim()
            
            if (vpa.isEmpty() || name.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter both UPI ID and Name", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Basic validation for VPA (contains @)
            if (!vpa.contains("@")) {
                 android.widget.Toast.makeText(this, "Invalid UPI ID format (must contain @)", android.widget.Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            }
            
            prefs.edit()
                .putString("vpa", vpa)
                .putString("payee_name", name)
                .apply()
                
            android.widget.Toast.makeText(this, "Payment details saved!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }



    private fun setupLanguageSpinner() {
        val languages = listOf("English", "Hindi", "Malayalam", "Bengali", "Gujarati", "Kannada", "Marathi", "Tamil", "Telugu", "Urdu")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        val prefs = getSharedPreferences("upi_settings", android.content.Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "English")
        val position = languages.indexOf(currentLang)
        if (position >= 0) {
            binding.spinnerLanguage.setSelection(position)
        }

        binding.spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedLang = languages[position]
                prefs.edit().putString("language", selectedLang).apply()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Voice Speed
        val currentSpeed = prefs.getFloat("voice_speed", 1.0f)
        binding.sliderSpeed.value = currentSpeed
        binding.tvSpeedLabel.text = "Voice Speed: ${String.format("%.1f", currentSpeed)}x"
        
        binding.sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvSpeedLabel.text = "Voice Speed: ${String.format("%.1f", value)}x"
                prefs.edit().putFloat("voice_speed", value).apply()
            }
        }
    }



    private fun checkBatteryOptimizationStatus() {
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        
        if (isIgnoring) {
            binding.tvBatteryStatus.text = "Battery Optimization: DISABLED ✅"
            binding.tvBatteryStatus.setTextColor(getColor(com.upialert.R.color.green_money))
            binding.btnFixBattery.visibility = android.view.View.GONE
        } else {
            binding.tvBatteryStatus.text = "Battery Optimization: ENABLED ❌"
            binding.tvBatteryStatus.setTextColor(getColor(com.upialert.R.color.red_danger)) 
            binding.btnFixBattery.visibility = android.view.View.VISIBLE
            
            binding.btnFixBattery.setOnClickListener {
                showBatteryExplanationDialog()
            }
        }
    }

    private fun showBatteryExplanationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Allow Background Activity")
            .setMessage("To ensure voice alerts work reliably when your phone is locked or sleeping, please allow this app to ignore battery optimizations.\n\nSelect 'Allow' in the next screen.")
            .setPositiveButton("Allow") { _, _ ->
                requestBatteryFix()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestBatteryFix() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                android.widget.Toast.makeText(this, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
