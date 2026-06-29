package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra("notification_id", -1)

        // Dismiss the notification upon action click
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        val amountStr = intent.getStringExtra("amount") ?: "transaction"
        val merchant = intent.getStringExtra("merchant") ?: "merchant"
        val account = intent.getStringExtra("account") ?: "account"

        when (action) {
            "com.example.ACTION_SPLIT" -> {
                Toast.makeText(
                    context,
                    "✓ Initiating Split for ₹$amountStr at $merchant!",
                    Toast.LENGTH_LONG
                ).show()
            }
            "com.example.ACTION_STATS" -> {
                Toast.makeText(
                    context,
                    "Opening stats and category analytics for $merchant...",
                    Toast.LENGTH_LONG
                ).show()
                // Launch MainActivity
                val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("tab_index", 1) // Open analysis/detailed tab
                }
                if (mainIntent != null) {
                    context.startActivity(mainIntent)
                }
            }
            "com.example.ACTION_SHOW_SMS" -> {
                val rawSms = intent.getStringExtra("raw_sms") ?: "SMS content unavailable."
                Toast.makeText(
                    context,
                    "SMS: $rawSms",
                    Toast.LENGTH_LONG
                ).show()
            }
            "com.example.ACTION_MARK_AS_PAID" -> {
                Toast.makeText(
                    context,
                    "✓ EMI of ₹$amountStr for $account marked as PAID!",
                    Toast.LENGTH_LONG
                ).show()
            }
            "com.example.ACTION_SHARE_AXIO" -> {
                Toast.makeText(
                    context,
                    "✓ Sharing transaction details securely via axio...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
