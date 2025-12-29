package com.upialert.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    suspend fun insert(transaction: TransactionEntity) {
        transactionDao.insert(transaction)
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.delete(transaction)
    }

    suspend fun clearAllData() {
        transactionDao.deleteAll()
    }

    val recentTransactions: Flow<List<TransactionEntity>> = transactionDao.getRecentTransactions()

    fun getTodayTotal(): Flow<Double?> {
        val zoneId = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val startTime = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endTime = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        
        return transactionDao.getTotalAmountInRange(startTime, endTime)
    }

    fun getMonthTotal(): Flow<Double?> {
        val zoneId = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val startTime = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endTime = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        
        return transactionDao.getTotalAmountInRange(startTime, endTime)
    }

    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsInRange(startTime, endTime)
    }

    fun getTotalAmountInRange(startTime: Long, endTime: Long): Flow<Double?> {
        return transactionDao.getTotalAmountInRange(startTime, endTime)
    }

    fun getCountInRange(startTime: Long, endTime: Long): Flow<Int> {
        return transactionDao.getCountInRange(startTime, endTime)
    }
}
