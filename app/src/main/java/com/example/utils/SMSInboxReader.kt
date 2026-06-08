package com.example.utils

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.example.data.TransactionSMS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SMSInboxReader {

    /**
     * Reads the SMS inbox and maps matchable transactions to a list of TransactionSMS.
     */
    suspend fun queryInboxTransactions(context: Context): List<TransactionSMS> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<TransactionSMS>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.DATE
        )

        try {
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC"
            )

            cursor?.use { c ->
                val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
                val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)

                while (c.moveToNext()) {
                    val body = c.getString(bodyIndex) ?: continue
                    val date = c.getLong(dateIndex)

                    val parsed = TransactionParser.parseSms(body, date)
                    if (parsed != null) {
                        transactions.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SMSInboxReader", "Error querying SMS inbox", e)
        }

        transactions
    }
}
