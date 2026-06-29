package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<TransactionSMS>> = transactionDao.getAllTransactions()
    
    val accountBalances: Flow<List<AccountBalance>> = transactionDao.getLatestAccountBalances()

    fun getMonthlySpendsByCategory(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySpend>> {
        return transactionDao.getMonthlySpendsByCategory(startOfMonth, endOfMonth)
    }

    suspend fun insert(transaction: TransactionSMS): Long {
        // Check for duplicates within 1 hour
        val duplicates = transactionDao.findDuplicateTransactions(
            amount = transaction.amount,
            accountIdentifier = transaction.accountIdentifier,
            timestamp = transaction.timestamp,
            timeWindow = 3600000L // 1 hour window
        )
        
        var shouldInsert = true
        var resultId = 0L

        for (existing in duplicates) {
            val isSimilarBeneficiary = existing.beneficiary.equals(transaction.beneficiary, ignoreCase = true) ||
                    existing.beneficiary.lowercase().contains(transaction.beneficiary.lowercase()) ||
                    transaction.beneficiary.lowercase().contains(existing.beneficiary.lowercase()) ||
                    existing.beneficiary.equals("Unknown", ignoreCase = true) ||
                    transaction.beneficiary.equals("Unknown", ignoreCase = true)
                    
            if (isSimilarBeneficiary) {
                if (existing.rawSms.length >= transaction.rawSms.length) {
                    // Existing is better or same, do not insert new one
                    shouldInsert = false
                    resultId = existing.id
                    break
                } else {
                    // New is longer (more complete), delete old one and proceed to insert new one
                    transactionDao.deleteTransaction(existing)
                }
            }
        }

        if (!shouldInsert) {
            return resultId
        }

        val resolvedTx = resolveRemainingBalance(transaction)
        return transactionDao.insertTransaction(resolvedTx)
    }

    suspend fun insertAll(transactions: List<TransactionSMS>) {
        if (transactions.isEmpty()) return

        // 1. Fetch all existing transactions once to do in-memory duplicate checks
        val existingList = transactionDao.getAllTransactionsList()
        val toInsert = mutableListOf<TransactionSMS>()
        val toDelete = mutableListOf<TransactionSMS>()
        
        // Keep track of what we decided to insert in this batch to avoid batch-internal duplicate insertions
        val batchInserted = mutableListOf<TransactionSMS>()
        
        // 2. Sort transactions chronologically (ascending) for running balance calculation
        val chronological = transactions.sortedBy { it.timestamp }
        
        val lastKnownBalances = mutableMapOf<String, Double>()
        
        for (tx in chronological) {
            val allCheckList = existingList + batchInserted
            
            // Filter duplicates within 1 hour
            val duplicates = allCheckList.filter { existing ->
                existing.amount == tx.amount &&
                existing.accountIdentifier == tx.accountIdentifier &&
                kotlin.math.abs(existing.timestamp - tx.timestamp) <= 3600000L
            }
            
            var shouldInsert = true
            for (existing in duplicates) {
                val isSimilarBeneficiary = existing.beneficiary.equals(tx.beneficiary, ignoreCase = true) ||
                        existing.beneficiary.lowercase().contains(tx.beneficiary.lowercase()) ||
                        tx.beneficiary.lowercase().contains(existing.beneficiary.lowercase()) ||
                        existing.beneficiary.equals("Unknown", ignoreCase = true) ||
                        tx.beneficiary.equals("Unknown", ignoreCase = true)
                        
                if (isSimilarBeneficiary) {
                    if (existing.rawSms.length >= tx.rawSms.length) {
                        shouldInsert = false
                        break
                    } else {
                        // The new one is more complete. Mark for deletion if it exists in DB
                        if (existing.id != 0L) {
                            toDelete.add(existing)
                        } else {
                            batchInserted.remove(existing)
                            toInsert.remove(existing)
                        }
                    }
                }
            }
            
            if (shouldInsert) {
                // Resolve balance for this tx
                val resolvedTx = if (tx.remainingBalance != null || tx.type == "Reminder") {
                    if (tx.remainingBalance != null) {
                        lastKnownBalances[tx.accountIdentifier] = tx.remainingBalance
                    }
                    tx
                } else {
                    var lastBal = lastKnownBalances[tx.accountIdentifier]
                    if (lastBal == null) {
                        lastBal = transactionDao.getLastAvailableBalance(tx.accountIdentifier)
                    }
                    
                    if (lastBal != null) {
                        val newBal = if (tx.type == "Credit") {
                            lastBal + tx.amount
                        } else {
                            lastBal - tx.amount
                        }
                        lastKnownBalances[tx.accountIdentifier] = newBal
                        tx.copy(remainingBalance = newBal)
                    } else {
                        tx
                    }
                }
                
                toInsert.add(resolvedTx)
                batchInserted.add(resolvedTx)
            }
        }
        
        // 3. Perform bulk DB operations
        if (toDelete.isNotEmpty()) {
            for (del in toDelete) {
                transactionDao.deleteTransaction(del)
            }
        }
        
        if (toInsert.isNotEmpty()) {
            transactionDao.insertTransactions(toInsert)
        }
    }

    private suspend fun resolveRemainingBalance(transaction: TransactionSMS): TransactionSMS {
        if (transaction.remainingBalance != null || transaction.type == "Reminder") {
            return transaction
        }
        val lastBal = transactionDao.getLastAvailableBalance(transaction.accountIdentifier)
        if (lastBal != null) {
            val newBal = if (transaction.type == "Credit") {
                lastBal + transaction.amount
            } else {
                lastBal - transaction.amount
            }
            return transaction.copy(remainingBalance = newBal)
        }
        return transaction
    }

    suspend fun updateTransactionCategory(id: Long, category: String) {
        transactionDao.updateTransactionCategory(id, category)
    }

    suspend fun updateTransactionType(id: Long, type: String) {
        transactionDao.updateTransactionType(id, type)
    }

    suspend fun updatePastTransactionsCategory(beneficiary: String, timestamp: Long, category: String) {
        transactionDao.updatePastTransactionsCategory(beneficiary, timestamp, category)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }
}
