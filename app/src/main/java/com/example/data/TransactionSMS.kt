package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsUniqueId"], unique = true)]
)
data class TransactionSMS(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsUniqueId: String,
    val timestamp: Long,
    val amount: Double,
    val beneficiary: String,
    val type: String, // "Credit", "Debit", "EMI", "SIP"
    val category: String, // "Food & Drinks", "Rent", "Bills", "Other", "Unknown"
    val accountIdentifier: String,
    val remainingBalance: Double?,
    val rawSms: String
)
