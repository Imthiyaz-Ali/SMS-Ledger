package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CategorySpend(
    val category: String,
    val totalSpend: Double,
    val count: Int
)

data class AccountBalance(
    val accountIdentifier: String,
    val remainingBalance: Double,
    val lastUpdated: Long
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionSMS>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionSMS): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionSMS>)

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateTransactionCategory(id: Long, category: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("""
        SELECT category, SUM(amount) as totalSpend, COUNT(id) as count 
        FROM transactions 
        WHERE timestamp >= :startOfMonth AND timestamp <= :endOfMonth AND type != 'Credit'
        GROUP BY category
    """)
    fun getMonthlySpendsByCategory(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySpend>>

    @Query("""
        SELECT t.accountIdentifier, t.remainingBalance, t.timestamp as lastUpdated
        FROM transactions t
        INNER JOIN (
            SELECT accountIdentifier, MAX(id) as maxId
            FROM transactions
            WHERE remainingBalance IS NOT NULL AND accountIdentifier != ''
            GROUP BY accountIdentifier
        ) sub ON t.id = sub.maxId
    """)
    fun getLatestAccountBalances(): Flow<List<AccountBalance>>
}
