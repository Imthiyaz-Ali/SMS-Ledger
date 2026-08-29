package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryMappingDao {

    @Query("SELECT * FROM category_mappings")
    suspend fun getAllMappings(): List<CategoryMapping>

    @Query("SELECT * FROM category_mappings")
    fun getAllMappingsFlow(): Flow<List<CategoryMapping>>

    @Query("SELECT category FROM category_mappings WHERE LOWER(beneficiary) = LOWER(:beneficiary) LIMIT 1")
    suspend fun getCategoryForBeneficiary(beneficiary: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMapping(mapping: CategoryMapping)

    @Query("DELETE FROM category_mappings WHERE LOWER(beneficiary) = LOWER(:beneficiary)")
    suspend fun deleteMapping(beneficiary: String)

    @Query("DELETE FROM category_mappings")
    suspend fun deleteAllMappings()
}
