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

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<TransactionSMS>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionSMS): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionSMS>)

    @Query("SELECT * FROM transactions WHERE amount = :amount AND accountIdentifier = :accountIdentifier AND ABS(timestamp - :timestamp) <= :timeWindow")
    suspend fun findDuplicateTransactions(amount: Double, accountIdentifier: String, timestamp: Long, timeWindow: Long): List<TransactionSMS>

    @Delete
    suspend fun deleteTransaction(transaction: TransactionSMS)

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateTransactionCategory(id: Long, category: String)

    @Query("UPDATE transactions SET type = :type WHERE id = :id")
    suspend fun updateTransactionType(id: Long, type: String)

    @Query("UPDATE transactions SET category = :category WHERE LOWER(beneficiary) = LOWER(:beneficiary) AND timestamp < :timestamp")
    suspend fun updatePastTransactionsCategory(beneficiary: String, timestamp: Long, category: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("""
        SELECT category, SUM(amount) as totalSpend, COUNT(id) as count 
        FROM transactions 
        WHERE timestamp >= :startOfMonth AND timestamp <= :endOfMonth AND type != 'Credit' AND type != 'Reminder' AND LOWER(category) != 'transfer'
        GROUP BY category
    """)
    fun getMonthlySpendsByCategory(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySpend>>

    @Query("SELECT COUNT(id) FROM transactions WHERE LOWER(beneficiary) = LOWER(:beneficiary)")
    suspend fun getTransactionCountForBeneficiary(beneficiary: String): Int

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :startOfMonth AND timestamp <= :endOfMonth AND type != 'Credit' AND type != 'Reminder' AND LOWER(category) != 'transfer'")
    suspend fun getTotalSpendsForMonth(startOfMonth: Long, endOfMonth: Long): Double?

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

    @Query("""
        SELECT remainingBalance 
        FROM transactions 
        WHERE accountIdentifier = :accountIdentifier AND remainingBalance IS NOT NULL 
        ORDER BY timestamp DESC, id DESC LIMIT 1
    """)
    suspend fun getLastAvailableBalance(accountIdentifier: String): Double?
}
