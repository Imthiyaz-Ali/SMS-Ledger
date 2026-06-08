package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<TransactionSMS>> = transactionDao.getAllTransactions()
    
    val accountBalances: Flow<List<AccountBalance>> = transactionDao.getLatestAccountBalances()

    fun getMonthlySpendsByCategory(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySpend>> {
        return transactionDao.getMonthlySpendsByCategory(startOfMonth, endOfMonth)
    }

    suspend fun insert(transaction: TransactionSMS): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun insertAll(transactions: List<TransactionSMS>) {
        transactionDao.insertTransactions(transactions)
    }

    suspend fun updateTransactionCategory(id: Long, category: String) {
        transactionDao.updateTransactionCategory(id, category)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }
}
