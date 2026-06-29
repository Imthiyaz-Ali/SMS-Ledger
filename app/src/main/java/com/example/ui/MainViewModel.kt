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
    private val sharedPrefs = application.getSharedPreferences("account_approvals", Context.MODE_PRIVATE)

    private val _approvedAccounts = MutableStateFlow<Set<String>>(emptySet())
    val approvedAccounts: StateFlow<Set<String>> = _approvedAccounts.asStateFlow()

    private val _rejectedAccounts = MutableStateFlow<Set<String>>(emptySet())
    val rejectedAccounts: StateFlow<Set<String>> = _rejectedAccounts.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).transactionDao()
        repository = TransactionRepository(dao)

        _approvedAccounts.value = sharedPrefs.getStringSet("approved", emptySet()) ?: emptySet()
        _rejectedAccounts.value = sharedPrefs.getStringSet("rejected", emptySet()) ?: emptySet()

        healTransactions()
    }

    fun approveAccount(accountIdentifier: String) {
        viewModelScope.launch {
            val updated = _approvedAccounts.value.toMutableSet().apply { add(accountIdentifier) }
            val updatedRejected = _rejectedAccounts.value.toMutableSet().apply { remove(accountIdentifier) }
            sharedPrefs.edit().apply {
                putStringSet("approved", updated)
                putStringSet("rejected", updatedRejected)
                apply()
            }
            _approvedAccounts.value = updated
            _rejectedAccounts.value = updatedRejected
        }
    }

    fun rejectAccount(accountIdentifier: String) {
        viewModelScope.launch {
            val updated = _approvedAccounts.value.toMutableSet().apply { remove(accountIdentifier) }
            val updatedRejected = _rejectedAccounts.value.toMutableSet().apply { add(accountIdentifier) }
            sharedPrefs.edit().apply {
                putStringSet("approved", updated)
                putStringSet("rejected", updatedRejected)
                apply()
            }
            _approvedAccounts.value = updated
            _rejectedAccounts.value = updatedRejected
        }
    }

    fun resetAccountStatus(accountIdentifier: String) {
        viewModelScope.launch {
            val updated = _approvedAccounts.value.toMutableSet().apply { remove(accountIdentifier) }
            val updatedRejected = _rejectedAccounts.value.toMutableSet().apply { remove(accountIdentifier) }
            sharedPrefs.edit().apply {
                putStringSet("approved", updated)
                putStringSet("rejected", updatedRejected)
                apply()
            }
            _approvedAccounts.value = updated
            _rejectedAccounts.value = updatedRejected
        }
    }

    fun resetAllAccountStatuses() {
        viewModelScope.launch {
            sharedPrefs.edit().apply {
                putStringSet("approved", emptySet())
                putStringSet("rejected", emptySet())
                apply()
            }
            _approvedAccounts.value = emptySet()
            _rejectedAccounts.value = emptySet()
        }
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
            sharedPrefs.edit().clear().apply()
            _approvedAccounts.value = emptySet()
            _rejectedAccounts.value = emptySet()
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
     * Updates the type of a specific transaction.
     */
    fun updateTransactionType(id: Long, type: String) {
        viewModelScope.launch {
            repository.updateTransactionType(id, type)
        }
    }

    /**
     * Updates category for all past transactions with the same case-insensitive beneficiary.
     */
    fun updatePastTransactionsCategory(beneficiary: String, timestamp: Long, category: String) {
        viewModelScope.launch {
            repository.updatePastTransactionsCategory(beneficiary, timestamp, category)
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
                Pair("ICICI Bank Acct XX555 debited for Rs 442.25 on 23-Jun-26; IRCTC credited. UPI:111153842973. Call 18002662 for dispute. SMS BLOCK 555 to 9215676766.", 1 * oneDayMs),
                Pair("ICICI Bank Acct XX555 debited for Rs 800.00 on 24-Jun-26; KOSHISH EDUCATI credited. UPI:617531301365. Call 18002662 for dispute. SMS BLOCK 555 to 9215676766.", 0 * oneDayMs),
                Pair("Notification: Spent Rs 250.00 on Zomato food order via HDFC *5056. Avl Bal Rs 12,095.67.", 0 * oneDayMs),
                Pair("Rs. 2500.00 debited from HDFC Bank A/c *5056 on 07-Jun-2026 to MUZAMMIL PASHA. Avl Bal Rs 12,345.67.", 1 * oneDayMs),
                Pair("Alert: Your YES Bank Acc X3349 has been debited by INR 1,500.00 for TASLIM KHNAM. Avl Lmt INR 45,000.00.", 2 * oneDayMs),
                Pair("ICICI Bank Acc XX555 debited Rs. 48,663.00 on 05-Jun-26 InfoBIL*Personal .Avl Bal Rs. 15,365.82.To dispute call 18002662 or SMS BLOCK 555 to 9215676766", 4 * oneDayMs),
                Pair("Your ICICI Bank Acc XX555 has been credited with INR 35,000.00 (salary credited) on 01-Jun-2026. Avl Bal INR 50,365.82.", 4 * oneDayMs),
                Pair("Your YES X3349 has been debited by INR 3,500.00 for EMI repayment. Avl Lmt INR 40,000.00.", 4 * oneDayMs),
                Pair("SIP transaction of Rs. 5000.00 initiated towards Mutual Fund via HDFC *5056.", 5 * oneDayMs),
                Pair("Your HDFC Bank *5056 has been credited with INR 50,000.00 (salary credited) on 01-Jun-2026. Avl Bal INR 65,000.00.", 6 * oneDayMs),
                
                // Month - 1 (May - Month 1)
                Pair("INR 9628.17 is paid from HSBC account XXXXXX8006 to PZCREDITCARD on 30-May-26 with ref 864872852180. If this is not done by you, call 18002673456 to report.", 13 * oneDayMs),
                Pair("HSBC:Dear Customer, your HSBC A/c 074-422***-006 has been debited with INR 9,000.00 on 28MAY as CSH WDL. Your Avl Bal is INR 399.73 . To report fraud call 18002673456 (Local) or +914061268007 (International).", 15 * oneDayMs),
                Pair("HSBC:Dear Customer, your HSBC A/c 074-422***-006 has been debited with INR 10,000.00 on 28MAY as CSH WDL. Your Avl Bal is INR 9,399.73 . To report fraud call 18002673456 (Local) or +914061268007 (International).", 16 * oneDayMs),
                Pair("Your HSBC Acc XXXXXX8006 is credited for INR 16000.00 on 28-May-26 from 9885900594@axl. UPI Ref No 424240082164", 17 * oneDayMs),
                Pair("Alert: Your YES Bank Acc X3349 has been debited by INR 8,000.00 for Amazon shopping. Avl Lmt INR 38,000.00.", 25 * oneDayMs),
                Pair("ICICI Bank Acc XX555 debited Rs. 4,900.00 on 12-May-26 NFS*CASH WDL*. Avb Bal Rs. 26,507.01. To dispute Call 18002662 or SMS BLOCK 555 to 9215676766 .", 28 * oneDayMs),
                Pair("EMI of Rs 48663 for ICICI Bank Personal Loan XX1565 is due on 05-May-26. Please maintain sufficient funds in your linked Account XX4555 to avoid 5% per annum penal charges and Rs 500 bounce charges. EMI will be debited on holidays too. Access your loan related services on iMobile at icici.co/ICICIT/k/DUvOd7lne0G", 30 * oneDayMs),
                Pair("Rs. 3000.00 debited from HDFC Bank A/c *5056 to Landlord. Avl Bal Rs 15,000.00.", 32 * oneDayMs),
                Pair("Credited with Rs 48,000.00 (salary credited) into YES X3349. Avl Bal Rs 55,000.00.", 36 * oneDayMs),
                
                // Month - 2 (April - Month 2)
                Pair("Spent Rs 12,000.00 at Reliance Digital via HDFC *5056. Avl Bal Rs 18,000.00.", 55 * oneDayMs),
                Pair("Spent Rs 4,500.00 for Electricity Bill on HDFC *5056.", 62 * oneDayMs),
                Pair("Dear XXXXXXXX7845, your passbook balance against TNMAS******9126 is Rs. 6,58,438/-. Contribution of Rs. 10,162/- for due month Apr-26 has been received.", 65 * oneDayMs),
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

    fun healTransactions() {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(getApplication()).transactionDao()
                val list = dao.getAllTransactionsList()
                for (tx in list) {
                    val lowerBody = tx.rawSms.lowercase()
                    if (lowerBody.contains("x3349") && lowerBody.contains("is due") && (lowerBody.contains("5672") || lowerBody.contains("total due"))) {
                        if (tx.type != "Reminder" || tx.amount != 5672.0 || tx.beneficiary != "yesbank 3349 card" || tx.accountIdentifier != "YES 3349") {
                            val updated = tx.copy(
                                type = "Reminder",
                                category = "EMI",
                                amount = 5672.0,
                                beneficiary = "yesbank 3349 card",
                                accountIdentifier = "YES 3349"
                            )
                            dao.deleteTransaction(tx)
                            dao.insertTransaction(updated)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
