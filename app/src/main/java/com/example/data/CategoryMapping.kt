package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_mappings")
data class CategoryMapping(
    @PrimaryKey val beneficiary: String, // Normalized lowercase beneficiary string
    val category: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
