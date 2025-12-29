package com.upialert.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.upialert.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: TransactionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val packageName = packageName
        val isEnabled = enabledListeners != null && enabledListeners.contains(packageName)

        if (!isEnabled) {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = TransactionsAdapter { transaction ->
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteTransaction(transaction)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupObservers() {
        viewModel.recentTransactions.observe(this) { transactions ->
            adapter.submitList(transactions)
        }

        viewModel.todayTotal.observe(this) { total ->
            binding.tvTotalAmount.text = String.format("₹%.2f", total ?: 0.0)
        }

        viewModel.monthTotal.observe(this) { total ->
            binding.tvMonthTotal.text = String.format("₹%.2f", total ?: 0.0)
        }
    }

    private fun setupListeners() {
        // Draggable FAB Logic
        var dY = 0f
        binding.fabSettings.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dY = view.y - event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val newY = event.rawY + dY
                    // Optional: Boundary checks to keep it within screen height
                    // For now, just restricting it to be within reasonable bounds if needed
                    // But raw translation is fine as requested "vertically moveable"
                    val parentHeight = (view.parent as android.view.View).height
                    val viewHeight = view.height
                    
                    // Simple boundary check
                    if (newY > 0 && newY < parentHeight - viewHeight) {
                        view.animate()
                            .y(newY)
                            .setDuration(0)
                            .start()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (event.eventTime - event.downTime < 200) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.btnViewReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        binding.btnReceiveQr.setOnClickListener {
            val qrFragment = QrDisplayFragment()
            qrFragment.show(supportFragmentManager, QrDisplayFragment.TAG)
        }
    }
}
