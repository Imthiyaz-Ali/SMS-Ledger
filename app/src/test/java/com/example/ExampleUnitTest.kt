package com.example

import com.example.utils.TransactionParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

  private fun assertParsedEquals(field: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
      System.err.println("--- MISMATCH DETECTED --- File: ExampleUnitTest.kt, Field: '$field', Expected: <$expected>, Actual: <$actual>")
      fail("Field '$field' mismatch: expected <$expected> but was <$actual>")
    }
  }

  @Test
  fun testDebitTransactionWithMerchantAndBalance() {
    val sms = "Alert: Your YES Bank Acc X3349 has been debited by INR 1,500.00 for TASLIM KHNAM. Avl Lmt INR 45,000.00."
    val result = TransactionParser.parseSms(sms, 1780850000000L)
    
    assertNotNull("Result should not be null", result)
    result?.let {
      assertParsedEquals("amount", 1500.00, it.amount)
      assertParsedEquals("type", "Debit", it.type)
      assertParsedEquals("accountIdentifier", "YES X3349", it.accountIdentifier)
      assertParsedEquals("beneficiary", "TASLIM KHNAM", it.beneficiary)
      assertParsedEquals("category", "Food & Drinks", it.category)
      assertParsedEquals("remainingBalance", 45000.00, it.remainingBalance)
    }
  }

  @Test
  fun testHdfcDebitWithMuzammilPasha() {
    val sms = "Rs. 2500.00 debited from HDFC Bank A/c *5056 on 07-Jun-2026 to MUZAMMIL PASHA. Avl Bal Rs 12,345.67."
    val result = TransactionParser.parseSms(sms, 1780850000000L)
    
    assertNotNull("Result should not be null", result)
    result?.let {
      assertParsedEquals("amount", 2500.00, it.amount)
      assertParsedEquals("type", "Debit", it.type)
      assertParsedEquals("accountIdentifier", "HDFC X5056", it.accountIdentifier)
      assertParsedEquals("beneficiary", "MUZAMMIL PASHA", it.beneficiary)
      assertParsedEquals("category", "Rent", it.category)
      assertParsedEquals("remainingBalance", 12345.67, it.remainingBalance)
    }
  }

  @Test
  fun testSalaryCreditTransaction() {
    val sms = "Your HDFC Bank *5056 has been credited with INR 50,000.00 (salary credited) on 01-Jun-2026. Avl Bal INR 65,000.00."
    val result = TransactionParser.parseSms(sms, 1780850000000L)
    
    assertNotNull("Result should not be null", result)
    result?.let {
      assertParsedEquals("amount", 50000.00, it.amount)
      assertParsedEquals("type", "Credit", it.type)
      assertParsedEquals("accountIdentifier", "HDFC X5056", it.accountIdentifier)
      assertParsedEquals("beneficiary", "salary credited", it.beneficiary)
      assertParsedEquals("category", "Unknown", it.category)
      assertParsedEquals("remainingBalance", 65000.00, it.remainingBalance)
    }
  }

  @Test
  fun testEmiDebitTransaction() {
    val sms = "Your YES X3349 has been debited by INR 3,500.00 for EMI repayment. Avl Lmt INR 40,000.00."
    val result = TransactionParser.parseSms(sms, 1780850000000L)
    
    assertNotNull("Result should not be null", result)
    result?.let {
      assertParsedEquals("amount", 3500.00, it.amount)
      assertParsedEquals("type", "EMI", it.type)
      assertParsedEquals("accountIdentifier", "YES X3349", it.accountIdentifier)
      assertParsedEquals("beneficiary", "EMI repayment", it.beneficiary)
      assertParsedEquals("category", "Bills", it.category)
      assertParsedEquals("remainingBalance", 40000.00, it.remainingBalance)
    }
  }

  @Test
  fun testSipTransaction() {
    val sms = "SIP transaction of Rs. 5000.00 initiated towards Mutual Fund via HDFC *5056."
    val result = TransactionParser.parseSms(sms, 1780850000000L)
    
    assertNotNull("Result should not be null", result)
    result?.let {
      assertParsedEquals("amount", 5000.00, it.amount)
      assertParsedEquals("type", "SIP", it.type)
      assertParsedEquals("accountIdentifier", "HDFC X5056", it.accountIdentifier)
      assertParsedEquals("beneficiary", "Mutual Fund", it.beneficiary)
      assertParsedEquals("category", "Other", it.category)
    }
  }

  @Test
  fun testNonTransactionMessage() {
    val sms = "Hey, are we still meeting today at 5 PM for coffee?"
    val result = TransactionParser.parseSms(sms, 1780850000000L)
    
    assertNull(result)
  }
}


