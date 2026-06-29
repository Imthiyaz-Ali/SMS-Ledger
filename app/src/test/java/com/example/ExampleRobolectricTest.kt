package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.example.data.TransactionSMS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: AppDatabase
  private lateinit var repository: TransactionRepository

  @Before
  fun createDb() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = TransactionRepository(db.transactionDao())
  }

  @After
  fun closeDb() {
    db.close()
  }

  @Test
  fun testReadStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SMS Ledger", appName)
  }

  @Test
  fun testDeduplicationKeepsLongerSms() = runBlocking {
    val t1 = TransactionSMS(
      smsUniqueId = "1780850000000-264.0-on AMAZON PAY IN G",
      timestamp = 1780850000000L,
      amount = 264.0,
      accountIdentifier = "ICICI X6008",
      beneficiary = "on AMAZON PAY IN G",
      type = "Debit",
      category = "Shopping",
      remainingBalance = null,
      rawSms = "INR 264.00 spent using ICICI Bank Card XX6008 on 23-Jun-26 on AMAZON PAY IN G. Avl Limit: INR 1,99,736.00. If not you, call 1800 2662/SMS BLOCK 6008 to 9"
    )

    val t2 = TransactionSMS(
      smsUniqueId = "1780850000010-264.0-on AMAZON PAY IN G",
      timestamp = 1780850000010L, // slightly different timestamp but within 1 hour
      amount = 264.0,
      accountIdentifier = "ICICI X6008",
      beneficiary = "on AMAZON PAY IN G",
      type = "Debit",
      category = "Shopping",
      remainingBalance = null,
      rawSms = "INR 264.00 spent using ICICI Bank Card XX6008 on 23-Jun-26 on AMAZON PAY IN G. Avl Limit: INR 1,99,736.00. If not you, call 1800 2662/SMS BLOCK 6008 to 9215676766."
    )

    // Insert shorter first
    repository.insert(t1)
    var txList = repository.allTransactions.first()
    assertEquals(1, txList.size)
    assertEquals(t1.rawSms, txList[0].rawSms)

    // Insert longer second - should replace shorter
    repository.insert(t2)
    txList = repository.allTransactions.first()
    assertEquals(1, txList.size)
    assertEquals(t2.rawSms, txList[0].rawSms)
  }

  @Test
  fun testDeduplicationIgnoresShorterSms() = runBlocking {
    val t1 = TransactionSMS(
      smsUniqueId = "1780850000000-264.0-on AMAZON PAY IN G",
      timestamp = 1780850000000L,
      amount = 264.0,
      accountIdentifier = "ICICI X6008",
      beneficiary = "on AMAZON PAY IN G",
      type = "Debit",
      category = "Shopping",
      remainingBalance = null,
      rawSms = "INR 264.00 spent using ICICI Bank Card XX6008 on 23-Jun-26 on AMAZON PAY IN G. Avl Limit: INR 1,99,736.00. If not you, call 1800 2662/SMS BLOCK 6008 to 9215676766."
    )

    val t2 = TransactionSMS(
      smsUniqueId = "1780850000010-264.0-on AMAZON PAY IN G",
      timestamp = 1780850000010L,
      amount = 264.0,
      accountIdentifier = "ICICI X6008",
      beneficiary = "on AMAZON PAY IN G",
      type = "Debit",
      category = "Shopping",
      remainingBalance = null,
      rawSms = "INR 264.00 spent using ICICI Bank Card XX6008 on 23-Jun-26 on AMAZON PAY IN G. Avl Limit: INR 1,99,736.00. If not you, call 1800 2662/SMS BLOCK 6008 to 9"
    )

    // Insert longer first
    repository.insert(t1)
    var txList = repository.allTransactions.first()
    assertEquals(1, txList.size)
    assertEquals(t1.rawSms, txList[0].rawSms)

    // Insert shorter second - should be ignored
    repository.insert(t2)
    txList = repository.allTransactions.first()
    assertEquals(1, txList.size)
    assertEquals(t1.rawSms, txList[0].rawSms) // still the longer one
  }
}
