package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {

    @Query("SELECT name FROM custom_categories ORDER BY createdAt ASC")
    fun getAllCustomCategoriesFlow(): Flow<List<String>>

    @Query("SELECT name FROM custom_categories ORDER BY createdAt ASC")
    suspend fun getAllCustomCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomCategory(category: CustomCategory)

    @Query("DELETE FROM custom_categories WHERE LOWER(name) = LOWER(:name)")
    suspend fun deleteCustomCategory(name: String)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAllCustomCategories()
}
