package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryMappingDao: CategoryMappingDao? = null,
    private val customCategoryDao: CustomCategoryDao? = null
) {

    val allTransactions: Flow<List<TransactionSMS>> = transactionDao.getAllTransactions()
    
    val accountBalances: Flow<List<AccountBalance>> = transactionDao.getLatestAccountBalances()

    val customCategories: Flow<List<String>> = customCategoryDao?.getAllCustomCategoriesFlow() 
        ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getMonthlySpendsByCategory(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySpend>> {
        return transactionDao.getMonthlySpendsByCategory(startOfMonth, endOfMonth)
    }

    suspend fun saveCustomCategory(category: String) {
        val clean = category.trim()
        if (clean.isNotBlank()) {
            customCategoryDao?.insertCustomCategory(CustomCategory(clean))
        }
    }

    suspend fun applyCategoryMapping(transaction: TransactionSMS): TransactionSMS {
        if (categoryMappingDao == null || transaction.beneficiary.isBlank() || transaction.beneficiary.equals("Unknown Beneficiary", ignoreCase = true)) {
            return transaction
        }
        val mappings = categoryMappingDao.getAllMappings()
        if (mappings.isEmpty()) return transaction

        val cleanBen = transaction.beneficiary.trim().lowercase()

        // 1. Exact match
        val exactMatch = mappings.find { it.beneficiary.trim().lowercase() == cleanBen }
        if (exactMatch != null) {
            return transaction.copy(category = exactMatch.category)
        }

        // 2. Partial match (e.g. if mapped rule is "zomato" and new tx is "zomato india")
        val partialMatch = mappings.find { mapping ->
            val mapped = mapping.beneficiary.trim().lowercase()
            mapped.isNotBlank() && (cleanBen.contains(mapped) || mapped.contains(cleanBen))
        }
        if (partialMatch != null) {
            return transaction.copy(category = partialMatch.category)
        }

        return transaction
    }

    suspend fun saveCategoryMapping(beneficiary: String, category: String) {
        val cleanCat = category.trim()
        if (categoryMappingDao != null && beneficiary.isNotBlank() && !beneficiary.equals("Unknown Beneficiary", ignoreCase = true)) {
            categoryMappingDao.insertOrUpdateMapping(
                CategoryMapping(
                    beneficiary = beneficiary.trim().lowercase(),
                    category = cleanCat
                )
            )
        }
        saveCustomCategory(cleanCat)
    }

    suspend fun insert(transaction: TransactionSMS): Long {
        var mappedTx = applyCategoryMapping(transaction)
        // Check for duplicates within 1 hour
        val duplicates = transactionDao.findDuplicateTransactions(
            amount = mappedTx.amount,
            accountIdentifier = mappedTx.accountIdentifier,
            timestamp = mappedTx.timestamp,
            timeWindow = 3600000L // 1 hour window
        )
        
        var shouldInsert = true
        var resultId = 0L

        for (existing in duplicates) {
            val isSimilarBeneficiary = existing.beneficiary.equals(mappedTx.beneficiary, ignoreCase = true) ||
                    existing.beneficiary.lowercase().contains(mappedTx.beneficiary.lowercase()) ||
                    mappedTx.beneficiary.lowercase().contains(existing.beneficiary.lowercase()) ||
                    existing.beneficiary.equals("Unknown", ignoreCase = true) ||
                    mappedTx.beneficiary.equals("Unknown", ignoreCase = true)
                    
            if (isSimilarBeneficiary) {
                // Preserve user's explicit custom category/type if present on existing
                val preservedCategory = if (existing.category.isNotBlank() && existing.category != "Other") existing.category else mappedTx.category
                val preservedType = if (existing.type.isNotBlank() && existing.type != "Debit" && existing.type != "Credit") existing.type else mappedTx.type
                val preservedCompleted = existing.isCompleted || mappedTx.isCompleted

                mappedTx = mappedTx.copy(
                    category = preservedCategory,
                    type = preservedType,
                    isCompleted = preservedCompleted
                )

                if (existing.rawSms.length >= mappedTx.rawSms.length) {
                    // Existing is better or same, update existing if category was merged
                    if (existing.category != mappedTx.category || existing.type != mappedTx.type || existing.isCompleted != mappedTx.isCompleted) {
                        transactionDao.updateTransactionCategory(existing.id, mappedTx.category)
                        transactionDao.updateTransactionType(existing.id, mappedTx.type)
                        transactionDao.updateTransactionCompleted(existing.id, mappedTx.isCompleted)
                    }
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

        val resolvedTx = resolveRemainingBalance(mappedTx)
        val id = transactionDao.insertTransaction(resolvedTx)
        reconcileCreditCardPayments()
        return id
    }

    suspend fun insertAll(transactions: List<TransactionSMS>) {
        if (transactions.isEmpty()) return

        // 0. Apply user category mappings to all incoming transactions
        val mappedTransactions = transactions.map { applyCategoryMapping(it) }

        // 1. Fetch all existing transactions once to do in-memory duplicate checks
        val existingList = transactionDao.getAllTransactionsList()
        val toInsert = mutableListOf<TransactionSMS>()
        val toDelete = mutableListOf<TransactionSMS>()
        
        // Keep track of what we decided to insert in this batch to avoid batch-internal duplicate insertions
        val batchInserted = mutableListOf<TransactionSMS>()
        
        // 2. Sort transactions chronologically (ascending) for running balance calculation
        val chronological = mappedTransactions.sortedBy { it.timestamp }
        
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
            var currentTx = tx

            for (existing in duplicates) {
                val isSimilarBeneficiary = existing.beneficiary.equals(currentTx.beneficiary, ignoreCase = true) ||
                        existing.beneficiary.lowercase().contains(currentTx.beneficiary.lowercase()) ||
                        currentTx.beneficiary.lowercase().contains(existing.beneficiary.lowercase()) ||
                        existing.beneficiary.equals("Unknown", ignoreCase = true) ||
                        currentTx.beneficiary.equals("Unknown", ignoreCase = true)
                        
                if (isSimilarBeneficiary) {
                    // Preserve user's custom category, type, and completion status
                    val preservedCategory = if (existing.category.isNotBlank() && existing.category != "Other") existing.category else currentTx.category
                    val preservedType = if (existing.type.isNotBlank() && existing.type != "Debit" && existing.type != "Credit") existing.type else currentTx.type
                    val preservedCompleted = existing.isCompleted || currentTx.isCompleted

                    currentTx = currentTx.copy(
                        category = preservedCategory,
                        type = preservedType,
                        isCompleted = preservedCompleted
                    )

                    if (existing.rawSms.length >= currentTx.rawSms.length) {
                        if (existing.id != 0L && (existing.category != currentTx.category || existing.type != currentTx.type || existing.isCompleted != currentTx.isCompleted)) {
                            transactionDao.updateTransactionCategory(existing.id, currentTx.category)
                            transactionDao.updateTransactionType(existing.id, currentTx.type)
                            transactionDao.updateTransactionCompleted(existing.id, currentTx.isCompleted)
                        }
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
                val resolvedTx = if (currentTx.remainingBalance != null || currentTx.type == "Reminder") {
                    if (currentTx.remainingBalance != null) {
                        lastKnownBalances[currentTx.accountIdentifier] = currentTx.remainingBalance
                    }
                    currentTx
                } else {
                    var lastBal = lastKnownBalances[currentTx.accountIdentifier]
                    if (lastBal == null) {
                        lastBal = transactionDao.getLastAvailableBalance(currentTx.accountIdentifier)
                    }
                    
                    if (lastBal != null) {
                        val newBal = if (currentTx.type == "Credit") {
                            lastBal + currentTx.amount
                        } else {
                            lastBal - currentTx.amount
                        }
                        lastKnownBalances[currentTx.accountIdentifier] = newBal
                        currentTx.copy(remainingBalance = newBal)
                    } else {
                        currentTx
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
        reconcileCreditCardPayments()
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

    suspend fun updateTransactionCategory(id: Long, category: String, beneficiary: String? = null) {
        transactionDao.updateTransactionCategory(id, category)
        if (beneficiary != null) {
            saveCategoryMapping(beneficiary, category)
        } else {
            val tx = transactionDao.getAllTransactionsList().find { it.id == id }
            if (tx != null) {
                saveCategoryMapping(tx.beneficiary, category)
            }
        }
    }

    suspend fun updateTransactionType(id: Long, type: String) {
        transactionDao.updateTransactionType(id, type)
    }

    suspend fun updateTransactionCompleted(id: Long, isCompleted: Boolean) {
        transactionDao.updateTransactionCompleted(id, isCompleted)
    }

    suspend fun updatePastTransactionsCategory(beneficiary: String, timestamp: Long, category: String) {
        transactionDao.updatePastTransactionsCategory(beneficiary, timestamp, category)
        saveCategoryMapping(beneficiary, category)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }

    suspend fun reconcileCreditCardPayments() {
        try {
            val list = transactionDao.getAllTransactionsList()
            val reminders = list.filter { tx ->
                tx.type == "Reminder" && !tx.isCompleted && (
                    tx.rawSms.lowercase().contains("card") ||
                    tx.rawSms.lowercase().contains("credit") ||
                    tx.rawSms.lowercase().contains("cc") ||
                    tx.beneficiary.lowercase().contains("card") ||
                    tx.beneficiary.lowercase().contains("credit") ||
                    tx.beneficiary.lowercase().contains("cc")
                )
            }
            val debits = list.filter { tx -> tx.type == "Debit" }
            for (reminder in reminders) {
                val matchingDebit = debits.find { debit -> debit.amount == reminder.amount }
                if (matchingDebit != null) {
                    // Update debit to "Credit Card Payment"
                    val updatedDebit = matchingDebit.copy(type = "Credit Card Payment")
                    transactionDao.deleteTransaction(matchingDebit)
                    transactionDao.insertTransaction(updatedDebit)
                    
                    // Update reminder to isCompleted = true
                    val updatedReminder = reminder.copy(isCompleted = true)
                    transactionDao.deleteTransaction(reminder)
                    transactionDao.insertTransaction(updatedReminder)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
