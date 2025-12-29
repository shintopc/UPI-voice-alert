package com.upialert.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val appPackage: String,
    val senderName: String,
    val timestamp: Long,
    val rawMessage: String
)
