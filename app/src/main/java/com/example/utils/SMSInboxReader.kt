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
     * Optionally filters messages newer than [sinceTimestamp] for fast incremental scanning.
     */
    suspend fun queryInboxTransactions(context: Context, sinceTimestamp: Long = 0L): List<TransactionSMS> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<TransactionSMS>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.DATE,
            Telephony.Sms.Inbox.ADDRESS
        )

        val selection = if (sinceTimestamp > 0L) "${Telephony.Sms.Inbox.DATE} >= ?" else null
        val selectionArgs = if (sinceTimestamp > 0L) arrayOf(sinceTimestamp.toString()) else null

        try {
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.Inbox.DATE} DESC"
            )

            cursor?.use { c ->
                val bodyIndex = c.getColumnIndex(Telephony.Sms.Inbox.BODY)
                val dateIndex = c.getColumnIndex(Telephony.Sms.Inbox.DATE)
                val addressIndex = c.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)

                if (bodyIndex >= 0 && dateIndex >= 0) {
                    while (c.moveToNext()) {
                        try {
                            val body = c.getString(bodyIndex) ?: continue
                            val date = c.getLong(dateIndex)
                            val sender = if (addressIndex >= 0) c.getString(addressIndex) ?: "Unknown" else "Unknown"

                            val parsed = TransactionParser.parseSms(body, date, sender)
                            if (parsed != null) {
                                transactions.add(parsed)
                            }
                        } catch (e: Exception) {
                            Log.e("SMSInboxReader", "Error parsing row from SMS inbox", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SMSInboxReader", "Error querying SMS inbox", e)
        }

        transactions
    }
}
