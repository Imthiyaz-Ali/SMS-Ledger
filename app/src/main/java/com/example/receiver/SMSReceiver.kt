package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.AppDatabase
import com.example.utils.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SMSReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.messageBody ?: continue
                val sender = message.originatingAddress ?: ""
                val timestamp = message.timestampMillis

                Log.d("SMSReceiver", "Received SMS from $sender: $body")

                // Only parse if it looks like transactional / bank message (alphanumeric sender usually, or contains bank keys)
                val parsed = TransactionParser.parseSms(body, timestamp)
                if (parsed != null) {
                    Log.i("SMSReceiver", "Successfully parsed SMS transaction: $parsed")
                    scope.launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            db.transactionDao().insertTransaction(parsed)
                        } catch (e: Exception) {
                            Log.e("SMSReceiver", "Failed to cache parsed transaction", e)
                        }
                    }
                }
            }
        }
    }
}
