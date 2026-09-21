package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import com.example.R
import com.example.data.AppDatabase
import com.example.data.TransactionSMS
import com.example.receiver.NotificationActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NotificationHelper {
    private const val CHANNEL_ID = "transaction_alerts"
    private const val CHANNEL_NAME = "Transaction Alerts"
    private const val CHANNEL_DESC = "Notifications when a new transaction is processed or a payment is due."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a notification when a new transaction is processed.
     */
    fun showTransactionNotification(context: Context, tx: TransactionSMS) {
        createNotificationChannel(context)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            
            // Get total spending in current month
            val startOfMonth = getStartOfMonthTimestamp()
            val endOfMonth = getEndOfMonthTimestamp()
            val totalMonthSpends = db.transactionDao().getTotalSpendsForMonth(startOfMonth, endOfMonth) ?: 0.0

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                postNativeTransactionNotification(context, tx, totalMonthSpends)
            }
        }
    }

    /**
     * Shows a due reminder notification (e.g., for ICICI Bank Personal Loan)
     */
    fun showDueReminderNotification(context: Context, tx: TransactionSMS, daysRemaining: Int = 2) {
        createNotificationChannel(context)
        val notificationId = (tx.id xor 987654).toInt()

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val markPaidIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_MARK_AS_PAID"
            putExtra("notification_id", notificationId)
            putExtra("amount", String.format(Locale.US, "%,.0f", tx.amount))
            putExtra("account", tx.accountIdentifier.ifEmpty { "Personal Loan" })
        }
        val pMarkPaid = PendingIntent.getBroadcast(context, notificationId * 10 + 1, markPaidIntent, pendingIntentFlags)

        val shareIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_SHARE_AXIO"
            putExtra("notification_id", notificationId)
        }
        val pShare = PendingIntent.getBroadcast(context, notificationId * 10 + 2, shareIntent, pendingIntentFlags)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pContent = PendingIntent.getActivity(context, notificationId, launchIntent, pendingIntentFlags)

        val cleanSubtext = formatAccountDisplayForHeader(tx.accountIdentifier, tx.type)

        // Amount colored in RED for due payment reminders
        val formattedAmount = String.format(Locale.US, "%,.2f", tx.amount)
        val titleHtml = "$cleanSubtext <font color=\"#D32F2F\"><b>₹$formattedAmount</b></font>"
        val titleCharSequence = HtmlCompat.fromHtml(titleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val notificationText = "Due in $daysRemaining days"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle(titleCharSequence)
            .setContentText(notificationText)
            .setSubText(cleanSubtext)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pContent)
            .addAction(0, "Mark as Paid", pMarkPaid)
            .addAction(0, "Share axio", pShare)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun postNativeTransactionNotification(
        context: Context,
        tx: TransactionSMS,
        totalMonthSpends: Double
    ) {
        val notificationId = (tx.id xor 123456).toInt()

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val splitIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_SPLIT"
            putExtra("notification_id", notificationId)
            putExtra("amount", String.format(Locale.US, "%,.2f", tx.amount))
            putExtra("merchant", tx.beneficiary)
        }
        val pSplit = PendingIntent.getBroadcast(context, notificationId * 10 + 3, splitIntent, pendingIntentFlags)

        val statsIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_STATS"
            putExtra("notification_id", notificationId)
            putExtra("merchant", tx.beneficiary)
        }
        val pStats = PendingIntent.getBroadcast(context, notificationId * 10 + 4, statsIntent, pendingIntentFlags)

        val showSmsIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_SMS"
            putExtra("notification_id", notificationId)
            putExtra("raw_sms", tx.rawSms)
        }
        val pShowSms = PendingIntent.getBroadcast(context, notificationId * 10 + 5, showSmsIntent, pendingIntentFlags)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pContent = PendingIntent.getActivity(context, notificationId, launchIntent, pendingIntentFlags)

        val bankHeader = formatAccountDisplayForHeader(tx.accountIdentifier, tx.type)
        val monthLabel = SimpleDateFormat("MMMM", Locale.US).format(Date(tx.timestamp))

        val isCredit = tx.type == "Credit"
        // Debit -> Vivid Red (#D32F2F), Credit -> Vivid Green (#2E7D32)
        val amountColorHex = if (isCredit) "#2E7D32" else "#D32F2F"
        val formattedAmount = String.format(Locale.US, "%,.2f", tx.amount)

        val titleHtml = "<font color=\"$amountColorHex\"><b>₹$formattedAmount</b></font> at ${tx.beneficiary}"
        val titleCharSequence = HtmlCompat.fromHtml(titleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

        val line2 = "Total ₹${String.format(Locale.US, "%,.2f", totalMonthSpends)} spent in $monthLabel"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle(titleCharSequence)
            .setContentText(line2)
            .setSubText(bankHeader)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line2))
            .addAction(0, "Split", pSplit)
            .addAction(0, "Stats", pStats)
            .addAction(0, "Show SMS", pShowSms)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun formatAccountDisplayForHeader(accountIdentifier: String, type: String): String {
        val lowerAcc = accountIdentifier.lowercase()
        val suffix = if (type == "Credit") "credit" else "debit"
        
        // Extract last 4 numbers
        val numberPattern = java.util.regex.Pattern.compile("(\\d{4,})")
        val matcher = numberPattern.matcher(accountIdentifier)
        val digits = if (matcher.find()) {
            val fullMatch = matcher.group(1)
            fullMatch?.takeLast(4) ?: "XXXX"
        } else {
            "XXXX"
        }

        return when {
            lowerAcc.contains("hdfc") -> "HDFC $suffix ($digits)"
            lowerAcc.contains("yes") -> "YesBank $suffix ($digits)"
            lowerAcc.contains("icici") -> "ICICI $suffix ($digits)"
            else -> {
                val cleanName = accountIdentifier.substringBefore("XX").trim().ifEmpty { "Bank" }
                "$cleanName $suffix ($digits)"
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
