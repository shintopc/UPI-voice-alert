package com.upialert.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import com.upialert.R
import com.upialert.data.AppDatabase
import com.upialert.data.TransactionEntity
import com.upialert.data.TransactionRepository
import com.upialert.databinding.ActivityReportsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private lateinit var repository: TransactionRepository
    
    // Filters
    private var startTime: Long = 0
    private var endTime: Long = 0
    private val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back) 
        supportActionBar?.title = "Reports & Analysis"

        // Init Repo
        val db = AppDatabase.getDatabase(applicationContext)
        repository = TransactionRepository(db.transactionDao())

        setupFilters()
        setupChart()

        // Default to This Week
        setRangeThisWeek()
        binding.tvIncomeTitle.text = "This Week"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chip_today -> {
                    setRangeToday()
                    binding.tvIncomeTitle.text = "TODAY'S INCOME"
                }
                R.id.chip_this_week -> {
                    setRangeThisWeek()
                    binding.tvIncomeTitle.text = "This Week"
                }
                R.id.chip_this_month -> {
                    setRangeThisMonth()
                    binding.tvIncomeTitle.text = "This Month"
                }
                R.id.chip_last_month -> {
                    setRangeLastMonth()
                    binding.tvIncomeTitle.text = "Last Month"
                }
                // chip_custom handled by OnClickListener
            }
        }
        
        binding.chipCustom.setOnClickListener {
            openDatePicker()
        }
        
        binding.cardTotalIncome.setOnClickListener {
            binding.chipToday.isChecked = true
        }
    }
    
    private fun setRangeToday() {
        val calendar = Calendar.getInstance()
        
        // Start of Today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        startTime = calendar.timeInMillis
        
        // End of Today
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        endTime = calendar.timeInMillis
        
        loadData()
    }

    private fun setRangeThisWeek() {
        val calendar = Calendar.getInstance()
        // End of today
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        endTime = calendar.timeInMillis

        // Start of 7 days ago (or start of week)
        // Let's do last 7 days for better trend view
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        startTime = calendar.timeInMillis
        
        loadData()
    }

    private fun setRangeThisMonth() {
        val calendar = Calendar.getInstance()
        
        // End of today (or end of month?) - Visualization for "This Month" usually means "So far"
        // But for query end range we usually want up to now or end of current month. 
        // Let's go to max of current month to be safe.
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        endTime = calendar.timeInMillis

        // Start of month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        startTime = calendar.timeInMillis

        loadData()
    }

    private fun setRangeLastMonth() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        
        // End of last month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        endTime = calendar.timeInMillis

        // Start of last month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        startTime = calendar.timeInMillis

        loadData()
    }

    private fun openDatePicker() {
        if (supportFragmentManager.findFragmentByTag("date_range") != null) return

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(Pair(MaterialDatePicker.thisMonthInUtcMilliseconds(), MaterialDatePicker.todayInUtcMilliseconds()))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            startTime = selection.first
            endTime = selection.second
            // Add padding to end time to include the full last day
            val cal = Calendar.getInstance()
            cal.timeInMillis = endTime
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            endTime = cal.timeInMillis
            
            // Re-check chip if it was cleared
            if (!binding.chipCustom.isChecked) {
                binding.chipCustom.isChecked = true
            }
            loadData()
        }
        picker.show(supportFragmentManager, "date_range")
    }

    private fun loadData() {
        // Update Range Text
        binding.tvDateRange.text = "${dateFormatter.format(Date(startTime))} - ${dateFormatter.format(Date(endTime))}"

        lifecycleScope.launch {
            repository.getTransactionsInRange(startTime, endTime).collectLatest { transactions ->
                updateSummary(transactions)
                updateChart(transactions)
            }
        }
    }

    private fun updateSummary(transactions: List<TransactionEntity>) {
        val total = transactions.sumOf { it.amount }
        val count = transactions.size
        
        binding.tvTotalIncome.text = "₹${String.format("%.2f", total)}"
        binding.tvTxnCount.text = count.toString()
    }

    private fun updateChart(transactions: List<TransactionEntity>) {
        val dailyMap = mutableMapOf<String, Double>()
        val cal = Calendar.getInstance()
        
        // Check Duration
        // If range is <= 26 hours (approx 1 day + wiggle room), use Hourly
        val durationMillis = endTime - startTime
        val isHourly = durationMillis <= (26 * 60 * 60 * 1000L)
        
        val displayFormatter = if (isHourly) {
             SimpleDateFormat("ha", Locale.getDefault()) // 10PM, 11AM
        } else {
             dateFormatter // dd MMM
        }
        
        // Pre-fill
        val tempCal = Calendar.getInstance()
        tempCal.timeInMillis = startTime
        // Round down start time for iteration loop
        if (isHourly) {
             tempCal.set(Calendar.MINUTE, 0)
             tempCal.set(Calendar.SECOND, 0)
        }
        
        while (tempCal.timeInMillis <= endTime) {
            val dateKey = displayFormatter.format(tempCal.time)
            if (!dailyMap.containsKey(dateKey)) {
                dailyMap[dateKey] = 0.0
            }
            if (isHourly) {
                tempCal.add(Calendar.HOUR_OF_DAY, 1)
            } else {
                tempCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Fill Data
        transactions.forEach { txn ->
            cal.timeInMillis = txn.timestamp
            val dateKey = displayFormatter.format(cal.time)
            // Note: If txn is from different year/month but formatter is limited, keys merge.
            // For this app, assume filtering handles range validity.
            // We use put/get to be safe against exact time mismatches if pre-fill missed any
             if (dailyMap.containsKey(dateKey)) {
                dailyMap[dateKey] = dailyMap[dateKey]!! + txn.amount
            } else if (isHourly) {
                 // For hourly, sometimes exact keys might differ if we didn't iterate perfectly, add safely
                 // But pre-fill loop + data formatter should match.
                 dailyMap[dateKey] = txn.amount
            }
        }

        // Create Entries
        val barEntries = ArrayList<BarEntry>()
        val lineEntries = ArrayList<Entry>()
        val labels = ArrayList<String>()
        var index = 0f
        
        // Ensure sorted order
        // Current map insertion order from pre-fill should be chronological
        
        dailyMap.forEach { (date, amount) ->
            barEntries.add(BarEntry(index, amount.toFloat()))
            lineEntries.add(Entry(index, amount.toFloat()))
            labels.add(date)
            index++
        }

        // --- Bar Chart Data ---
        val barDataSet = BarDataSet(barEntries, "Income")
        barDataSet.color = androidx.core.content.ContextCompat.getColor(this, R.color.green_money)
        barDataSet.valueTextSize = 10f
        
        val barData = BarData(barDataSet)
        binding.barChart.data = barData

        // --- Line Chart Data ---
        val lineDataSet = LineDataSet(lineEntries, "Income Wave")
        lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Wave effect
        lineDataSet.color = androidx.core.content.ContextCompat.getColor(this, R.color.purple_700)
        lineDataSet.setCircleColor(androidx.core.content.ContextCompat.getColor(this, R.color.purple_700))
        lineDataSet.lineWidth = 2f
        lineDataSet.circleRadius = 3f
        lineDataSet.setDrawFilled(true)
        lineDataSet.fillDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.gradient_chart)
        lineDataSet.valueTextSize = 10f
        lineDataSet.setDrawValues(false)
        
        val lineData = LineData(lineDataSet)
        binding.lineChart.data = lineData
        
        // --- Styling ---
        val textColorPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        val textColorSecondary = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        
        // Bar Chart
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = textColorSecondary
            axisLeft.textColor = textColorSecondary
            axisRight.isEnabled = false
            barDataSet.valueTextColor = textColorPrimary
            animateY(1000)
            invalidate()
        }
        
        // Line Chart
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = textColorSecondary
            axisLeft.textColor = textColorSecondary
            axisRight.isEnabled = false
            // lineDataSet.valueTextColor = textColorPrimary // values hidden
            animateY(1000)
            invalidate()
        }
    }
    
    // Setup initial empty chart
    private fun setupChart() {
        binding.barChart.setNoDataText("Select a range to view data")
        binding.lineChart.setNoDataText("Select a range to view data")
        
        binding.btnShareGraph.setOnClickListener {
            shareGraph()
        }
    }
    
    private fun shareGraph() {
        // Capture the whole card (Title + Graph + Background)
        val bitmap = getBitmapFromView(binding.cardWaveGraph)
        try {
            val cachePath = java.io.File(cacheDir, "images")
            cachePath.mkdirs()
            val stream = java.io.FileOutputStream("$cachePath/chart_share.png")
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            
            val newFile = java.io.File(cachePath, "chart_share.png")
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                newFile
            )
            
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, "image/png")
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                putExtra(android.content.Intent.EXTRA_TEXT, "Here is my earnings graph from UPI Alert!")
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Graph"))
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(this, "Failed to share graph", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getBitmapFromView(view: android.view.View): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(
            view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
        }
        view.draw(canvas)
        return bitmap
    }
}
