package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.SMSInboxReader
import com.example.utils.TransactionParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository

    init {
        val dao = AppDatabase.getDatabase(application).transactionDao()
        repository = TransactionRepository(dao)
    }

    // All structured transactions
    val transactions: StateFlow<List<TransactionSMS>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Latest balances grouped by bank account
    val accountBalances: StateFlow<List<AccountBalance>> = repository.accountBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category distribution/totals for standard analytics display
    private val _startOfMonth = MutableStateFlow(getStartOfMonthTimestamp())
    private val _endOfMonth = MutableStateFlow(getEndOfMonthTimestamp())

    val monthlySpendsByCategory: StateFlow<List<CategorySpend>> = combine(
        _startOfMonth,
        _endOfMonth
    ) { start, end ->
        Pair(start, end)
    }.flatMapLatest { (start, end) ->
        repository.getMonthlySpendsByCategory(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Total monthly debit spend for progress bars or alerts
    val totalMonthlySpend: StateFlow<Double> = monthlySpendsByCategory
        .map { spends -> spends.sumOf { it.totalSpend } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun refreshTimeBounds() {
        _startOfMonth.value = getStartOfMonthTimestamp()
        _endOfMonth.value = getEndOfMonthTimestamp()
    }

    /**
     * Reads inbox SMS and updates database cache.
     */
    fun scanDeviceInbox(context: Context, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val inboxList = SMSInboxReader.queryInboxTransactions(context)
            if (inboxList.isNotEmpty()) {
                repository.insertAll(inboxList)
            }
            onComplete(inboxList.size)
        }
    }

    /**
     * Clear all cached database transactions.
     */
    fun clearCache() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    /**
     * Updates the category of a specific transaction.
     */
    fun updateTransactionCategory(id: Long, category: String) {
        viewModelScope.launch {
            repository.updateTransactionCategory(id, category)
        }
    }

    /**
     * Seeds sample transaction SMS to DB for presentation.
     */
    fun seedSampleSms() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oneDayMs = 24L * 60L * 60L * 1000L
            
            val seedData = listOf(
                // Current Month (June 2026 - Month 0)
                Pair("Notification: Spent Rs 250.00 on Zomato food order via HDFC *5056. Avl Bal Rs 12,095.67.", 0 * oneDayMs),
                Pair("Rs. 2500.00 debited from HDFC Bank A/c *5056 on 07-Jun-2026 to MUZAMMIL PASHA. Avl Bal Rs 12,345.67.", 1 * oneDayMs),
                Pair("Alert: Your YES Bank Acc X3349 has been debited by INR 1,500.00 for TASLIM KHNAM. Avl Lmt INR 45,000.00.", 2 * oneDayMs),
                Pair("Your YES X3349 has been debited by INR 3,500.00 for EMI repayment. Avl Lmt INR 40,000.00.", 4 * oneDayMs),
                Pair("SIP transaction of Rs. 5000.00 initiated towards Mutual Fund via HDFC *5056.", 5 * oneDayMs),
                Pair("Your HDFC Bank *5056 has been credited with INR 50,000.00 (salary credited) on 01-Jun-2026. Avl Bal INR 65,000.00.", 6 * oneDayMs),
                
                // Month - 1 (May - Month 1)
                Pair("Alert: Your YES Bank Acc X3349 has been debited by INR 8,000.00 for Amazon shopping. Avl Lmt INR 38,000.00.", 25 * oneDayMs),
                Pair("Rs. 3000.00 debited from HDFC Bank A/c *5056 to Landlord. Avl Bal Rs 15,000.00.", 32 * oneDayMs),
                Pair("Credited with Rs 48,000.00 (salary credited) into YES X3349. Avl Bal Rs 55,000.00.", 36 * oneDayMs),
                
                // Month - 2 (April - Month 2)
                Pair("Spent Rs 12,000.00 at Reliance Digital via HDFC *5056. Avl Bal Rs 18,000.00.", 55 * oneDayMs),
                Pair("Spent Rs 4,500.00 for Electricity Bill on HDFC *5056.", 62 * oneDayMs),
                Pair("Credited with Rs 48,000.00 (salary credited) into HDFC *5056. Avl Bal Rs 34,500.00.", 66 * oneDayMs),
                
                // Month - 3 (March - Month 3)
                Pair("Spent Rs 6,000.00 on Groceries via HDFC *5056.", 85 * oneDayMs),
                Pair("Salary credited Rs 45,000.00 into YES X3349 on 01-Mar-2026.", 95 * oneDayMs),
                
                // Month - 4 (February - Month 4)
                Pair("Your YES Bank Acc X3349 has been debited by INR 9,000.00 for rent repayment.", 115 * oneDayMs),
                Pair("Salary credited INR 45,000.00 into YES X3349 on 01-Feb-2026.", 125 * oneDayMs),
                
                // Month - 5 (January - Month 5)
                Pair("Spent Rs 15,000.00 on Travel booking ticket at MakeMyTrip on YES X3349.", 145 * oneDayMs),
                Pair("Salary credited INR 45,000.00 into YES X3349 on 01-Jan-2026.", 155 * oneDayMs)
            )
            
            val parsedSamples = mutableListOf<TransactionSMS>()
            
            seedData.forEach { (body, offset) ->
                val timestamp = now - offset
                TransactionParser.parseSms(body, timestamp)?.let {
                    parsedSamples.add(it)
                }
            }

            repository.insertAll(parsedSamples)
        }
    }

    private fun getStartOfMonthTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfMonthTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
