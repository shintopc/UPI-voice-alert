package com.upialert.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @androidx.room.Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 50")
    fun getRecentTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :startTime AND timestamp < :endTime")
    fun getTotalAmountInRange(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp ASC")
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startTime AND timestamp < :endTime")
    fun getCountInRange(startTime: Long, endTime: Long): Flow<Int>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
