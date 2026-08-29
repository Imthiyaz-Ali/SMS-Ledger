package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
