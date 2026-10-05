package com.example.data

import com.example.utils.TransactionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryMappingDao: CategoryMappingDao? = null,
    private val customCategoryDao: CustomCategoryDao? = null
) {

    val allTransactions: Flow<List<TransactionSMS>> = transactionDao.getAllTransactions()
    
    val accountBalances: Flow<List<AccountBalance>> = transactionDao.getLatestAccountBalances().map { list ->
        list.mapNotNull { acc ->
            val cleanIdentifier = TransactionParser.standardizeAccountIdentifier(acc.accountIdentifier)
            if (cleanIdentifier.isBlank() || 
                cleanIdentifier.equals("Unknown Account", ignoreCase = true) || 
                cleanIdentifier.endsWith("0000")) {
                null
            } else {
                acc.copy(accountIdentifier = cleanIdentifier)
            }
        }
        .groupBy { it.accountIdentifier }
        .map { (_, accList) ->
            accList.maxByOrNull { it.lastUpdated } ?: accList.first()
        }
        .sortedBy { it.accountIdentifier }
    }

    suspend fun sanitizeDatabaseAccounts() {
        try {
            val allTx = transactionDao.getAllTransactionsList()
            for (tx in allTx) {
                val stdAccount = TransactionParser.standardizeAccountIdentifier(tx.accountIdentifier)
                if (stdAccount != tx.accountIdentifier) {
                    transactionDao.updateTransactionAccount(tx.id, stdAccount)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Error sanitizing database accounts", e)
        }
    }

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
        
        // Build an indexed lookup map: (accountIdentifier, amount) -> list of transactions
        // to avoid quadratic O(N^2) list scanning on large transaction sets
        val candidateMap = mutableMapOf<Pair<String, Double>, MutableList<TransactionSMS>>()
        for (tx in existingList) {
            val key = Pair(tx.accountIdentifier, tx.amount)
            candidateMap.getOrPut(key) { mutableListOf() }.add(tx)
        }

        // Keep track of what we decided to insert in this batch
        val batchInserted = mutableListOf<TransactionSMS>()
        
        // 2. Sort transactions chronologically (ascending) for running balance calculation
        val chronological = mappedTransactions.sortedBy { it.timestamp }
        val lastKnownBalances = mutableMapOf<String, Double>()
        
        for (tx in chronological) {
            val key = Pair(tx.accountIdentifier, tx.amount)
            val candidates = candidateMap[key] ?: emptyList()
            
            // Filter candidates within 1 hour
            val duplicates = candidates.filter { existing ->
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
                candidateMap.getOrPut(key) { mutableListOf() }.add(resolvedTx)
            }
        }
        
        // 3. Perform bulk DB operations
        if (toDelete.isNotEmpty()) {
            for (del in toDelete.distinctBy { it.id }) {
                if (del.id != 0L) {
                    transactionDao.deleteTransaction(del)
                }
            }
        }
        
        if (toInsert.isNotEmpty()) {
            val distinctToInsert = toInsert.distinctBy { it.smsUniqueId }
            transactionDao.insertTransactions(distinctToInsert)
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
            val tx = transactionDao.getTransactionById(id)
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
            reconcileSelfTransfers()
            if (transactionDao.getUncompletedReminderCount() == 0) return
            val list = transactionDao.getAllTransactionsList()
            val reminders = list.filter { tx ->
                tx.type == "Reminder" && !tx.isCompleted && (
                    tx.rawSms.contains("card", ignoreCase = true) ||
                    tx.rawSms.contains("credit", ignoreCase = true) ||
                    tx.rawSms.contains("cc", ignoreCase = true) ||
                    tx.beneficiary.contains("card", ignoreCase = true) ||
                    tx.beneficiary.contains("credit", ignoreCase = true) ||
                    tx.beneficiary.contains("cc", ignoreCase = true)
                )
            }
            if (reminders.isEmpty()) return
            val debits = list.filter { tx -> tx.type == "Debit" }.toMutableList()
            for (reminder in reminders) {
                val matchingDebit = debits.find { debit -> debit.amount == reminder.amount }
                if (matchingDebit != null) {
                    transactionDao.updateTransactionType(matchingDebit.id, "Credit Card Payment")
                    transactionDao.updateTransactionCompleted(reminder.id, true)
                    debits.remove(matchingDebit)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun reconcileSelfTransfers() {
        try {
            val list = transactionDao.getAllTransactionsList()
            if (list.isEmpty()) return

            val knownAccounts = list.map { it.accountIdentifier.lowercase().trim() }
                .filter { it.isNotBlank() && !it.contains("unknown") }
                .toSet()

            val debits = list.filter { it.type == "Debit" }
            val credits = list.filter { it.type == "Credit" }

            val toUpdateToTransfer = mutableSetOf<TransactionSMS>()

            // 1. Single transaction pattern check (beneficiary or SMS mentions another user account or self transfer)
            for (tx in list) {
                if (tx.category == "Transfer") continue

                val lowerBen = tx.beneficiary.lowercase().trim()
                val lowerSms = tx.rawSms.lowercase()

                val isSelfTransferKeyword = lowerBen.contains("self") || 
                        lowerBen.contains("own account") || 
                        lowerBen.contains("own a/c") ||
                        lowerSms.contains("self transfer") ||
                        lowerSms.contains("transfer to own") ||
                        lowerSms.contains("transfer to self") ||
                        lowerSms.contains("own account") ||
                        lowerSms.contains("own a/c") ||
                        lowerSms.contains("trf to own") ||
                        lowerSms.contains("trf to self") ||
                        lowerSms.contains("internal transfer") ||
                        lowerSms.contains("linked account") ||
                        lowerSms.contains("linked acct")

                val matchesAnotherAccount = knownAccounts.any { acc ->
                    acc != tx.accountIdentifier.lowercase().trim() && 
                    (lowerBen.contains(acc) || lowerSms.contains(acc))
                }

                if (isSelfTransferKeyword || matchesAnotherAccount) {
                    toUpdateToTransfer.add(tx.copy(category = "Transfer"))
                }
            }

            // 2. Pair matching between Debit on Account A and Credit on Account B within 15 mins
            for (debit in debits) {
                val matchingCredit = credits.find { credit ->
                    credit.accountIdentifier != debit.accountIdentifier &&
                    kotlin.math.abs(credit.amount - debit.amount) < 0.01 &&
                    kotlin.math.abs(credit.timestamp - debit.timestamp) <= 15 * 60 * 1000L
                }
                if (matchingCredit != null) {
                    if (debit.category != "Transfer") toUpdateToTransfer.add(debit.copy(category = "Transfer"))
                    if (matchingCredit.category != "Transfer") toUpdateToTransfer.add(matchingCredit.copy(category = "Transfer"))
                }
            }

            for (tx in toUpdateToTransfer) {
                transactionDao.updateTransactionCategory(tx.id, "Transfer")
            }
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Error reconciling self transfers", e)
        }
    }
}
