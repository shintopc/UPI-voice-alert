package com.upialert.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.upialert.data.AppDatabase
import com.upialert.data.TransactionRepository

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TransactionRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao())
    }

    val recentTransactions = repository.recentTransactions.asLiveData()
    val todayTotal = repository.getTodayTotal().asLiveData()
    val monthTotal = repository.getMonthTotal().asLiveData()

    fun deleteTransaction(transaction: com.upialert.data.TransactionEntity) = viewModelScope.launch {
        repository.delete(transaction)
    }
}
