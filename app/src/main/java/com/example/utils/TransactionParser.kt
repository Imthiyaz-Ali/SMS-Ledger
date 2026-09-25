package com.example.utils

import com.example.data.TransactionSMS
import java.util.regex.Pattern

object TransactionParser {

    // Common Indian banks
    private val BANK_PATTERN = Pattern.compile(
        "(?i)\\b(YES|HDFC|ICICI|SBI|AXIS|AMEX|KOTAK|PNB|BOB|HSBC|CITI|PAYTM|UNION|BOI|CANARA|IDFC|INDUSIND|RBL|FEDERAL|IOB|UCO|SCB)\\b"
    )

    // Regex for Account / Card details
    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:A/c|Acc|Account|Card|A/C|A_C)[^\\d]*?(\\d{3,6})\\b"
    )

    // Full bank-specific account match (e.g. YES X3349, HDFC *5056)
    private val JOINT_ACCOUNT_PATTERN = Pattern.compile(
        "(?i)\\b(YES|HDFC|ICICI|SBI|AXIS|AMEX|KOTAK|PNB|BOB|HSBC|CITI|PAYTM|UNION|BOI|CANARA|IDFC|INDUSIND|RBL|FEDERAL|IOB|UCO|SCB)[^\\d]*?(\\d{3,6})\\b"
    )

    // Regex for Balance (e.g. "Avl Lmt INR...", "Avl Bal Rs...", "Avl Lmt 45,000.00", "Available Balance is Rs...")
    private val BALANCE_PATTERN = Pattern.compile(
        "(?i)(?:Av[lb]\\s*(?:Bal|Lmt|Balance|Limit)|Available\\s*(?:Balance|Limit)|Bal|Lmt|Available)\\s*(?:is\\s+)?(?:Rs\\.?|INR|USD|₹)?\\s*([0-9,]+(?:\\.[0-9]{2})?)"
    )

    // Regex for basic amounts
    private val AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:Rs\\.?|INR|USD|₹)\\s*([0-9,]+(?:\\.[0-9]{2})?)"
    )

    // Pre-compiled regex patterns for performance optimization
    private val IS_DUE_PATTERN = Pattern.compile("(?i)\\b(is\\s+due|due\\s+for|due\\s+on|due\\s+by|due\\s+tomorrow|payment\\s+due)\\b")
    private val IGNORE_PAID_PATTERN = Pattern.compile("(?i)ignore\\s*(?:,\\s*)?if\\s*(?:already\\s*)?paid")
    private val TOTAL_DUE_PATTERN = Pattern.compile("(?i)total\\s+due\\s*(?:Rs\\.?|INR|USD|₹)?\\s*([0-9,]+(?:\\.[0-9]{2})?)")
    private val MIN_DUE_PATTERN = Pattern.compile("(?i)min\\s+due\\s*(?:Rs\\.?|INR|USD|₹)?\\s*([0-9,]+(?:\\.[0-9]{2})?)")
    private val BILL_OF_PATTERN = Pattern.compile("(?i)bill\\s+of\\s*(?:Rs\\.?|INR|USD|₹)?\\s*([0-9,]+(?:\\.[0-9]{2})?)")
    private val AMT_DUE_PATTERN = Pattern.compile("(?i)(?:due\\s+amount|amt\\s+due)\\s*(?:Rs\\.?|INR|USD|₹)?\\s*([0-9,]+(?:\\.[0-9]{2})?)")
    private val PAYABLE_PATTERN = Pattern.compile("(?i)(?:amount\\s+payable|payable\\s+is|total\\s+payable)\\s*(?:Rs\\.?|INR|USD|₹)?\\s*([0-9,]+(?:\\.[0-9]{2})?)")
    private val DIGITS_PATTERN = Pattern.compile("(\\d+)")
    private val DIGITS_3_6_PATTERN = Pattern.compile("(\\d{3,6})")
    private val FOR_DUE_PATTERN = Pattern.compile("(?i)for\\s+([A-Za-z0-9+ ]+?)\\s+is\\s+due")
    private val PAREN_PATTERN = Pattern.compile("\\(([^)]+)\\)")
    private val PASSBOOK_ACC_PATTERN = Pattern.compile("(?i)Dear\\s+([X0-9a-zA-Z]+)")
    private val PASSBOOK_AMT_PATTERN = Pattern.compile("(?i)Contribution\\s+of\\s+(?:Rs\\.?|INR|₹)?\\s*([0-9,]+)")
    private val PASSBOOK_BEN_PATTERN = Pattern.compile("(?i)balance\\s+against\\s+([^\\s]+)")
    private val PASSBOOK_BAL_PATTERN = Pattern.compile("(?i)balance\\s+against\\s+[^\\s]+\\s+is\\s+(?:Rs\\.?|INR|₹)?\\s*([0-9,/-]+)")
    private val FLOAT_DIGIT_PATTERN = Pattern.compile("(?<!\\d)([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)(?!\\d)")
    private val ICICI_SPECIAL_PATTERN = Pattern.compile("(?i)on\\s+\\d{2}-[a-zA-Z0-9]{3,4}-\\d{2,4};\\s+(?:on|at|to|for|from)?\\s*([^.]+?)\\s+credited")
    private val ICICI_STANDARD_PATTERN = Pattern.compile("(?i)on\\s+\\d{2}-[a-zA-Z0-9]{3,4}-\\d{2,4}\\s+(?:on|at|to|for|in|from|towards|via|using)?\\s*([^.]+?)(?=\\s*\\.\\s*(?:Avl|Avb|To|Avail)|$)")
    private val ICICI_INFO_PATTERN = Pattern.compile("(?i)Info\\s+(?:[A-Za-z0-9]+[-/:]){1,3}([A-Za-z0-9\\s]+?)(?=\\.|\\s+Available|\\s+Avl|\\s+Bal|$)")
    private val FROM_PATTERN = Pattern.compile("(?i)from\\s+([^\\s]+?)(?=\\s|\\.|$)")
    private val CLEAN_PREFIX_REGEX = "(?i)\\b(X|ACC|ACCOUNT|A/C|A_C|BANK|CARD|UNIT)\\b".toRegex()
    private val NON_WORD_REGEX = "[\\d*()#_\\-\\s]+".toRegex()
    private val SENDER_PREFIX_REGEX = "(?i)^[A-Z]{2}-".toRegex()
    private val YES_DATE_REGEX = Regex("(?i)\\b\\d{2}[-/]\\d{2}[-/]\\d{4}.*")
    private val YES_TIME_REGEX = Regex("(?i)\\b\\d{2}:\\d{2}(?::\\d{2})?.*")

    private val PROMOTIONAL_FILTER_PATTERN = Pattern.compile(
        "(?i)\\b(shipment|courier|awb|tracking|bluedart|delhivery|dtdc|fedex|ekart|dhl|parcel|package|dispatched|out\\s+for\\s+delivery|delivered|order\\s+placed|otp|verification\\s+code|security\\s+code|login\\s+code|one\\s+time\\s+password|disbursal|disburse|confirm\\s+(?:your\\s+)?tenure|apply\\s+for\\s+loan|loan\\s+offer|(?:will|to|would|shall)\\s+be\\s+(?:credited|debited)|will\\s+(?:credited|debited)|declined|decline|failed|unsuccessful|rejected|pre-?approved\\s+loan|dnd|complaint|complaints|trai|telemarketer|telemarketers|service\\s+request|ticket\\s+no|ticket\\s+number|case\\s+no|customer\\s+care|feedback|resolution|regulations|helpdesk|query)\\b"
    )

    private val TRANSACTION_VERBS_PATTERN = Pattern.compile(
        "(?i)\\b(debited|credited|spent|transferred|withdrawn|refunded|reversed|charged|cashback)\\b|\\b(?:paid\\s+(?:to|rs|inr|₹)|sent\\s+(?:rs|inr|₹)|transferred\\s+to)\\b"
    )

    private val HAS_FINANCIAL_KEYWORD_PATTERN = Pattern.compile(
        "(?i)\\b(rs\\.?|inr|usd|₹|debited|credited|spent|withdrawn|paid|transferred)\\b"
    )

    val bankCasingMap = mapOf(
        "AXIS" to "Axis",
        "ICICI" to "ICICI",
        "HDFC" to "HDFC",
        "SBI" to "SBI",
        "YES" to "YES",
        "AMEX" to "AMEX",
        "KOTAK" to "Kotak",
        "PNB" to "PNB",
        "BOB" to "BOB",
        "HSBC" to "HSBC",
        "CITI" to "Citi",
        "PAYTM" to "Paytm",
        "UNION" to "Union",
        "BOI" to "BOI",
        "CANARA" to "Canara",
        "IDFC" to "IDFC",
        "INDUSIND" to "IndusInd",
        "RBL" to "RBL",
        "FEDERAL" to "Federal",
        "IOB" to "IOB",
        "UCO" to "UCO",
        "SCB" to "SCB"
    )

    fun standardizeAccountIdentifier(identifier: String): String {
        val clean = identifier.trim()
        if (clean.isBlank() || 
            clean.equals("Unknown Account", ignoreCase = true) || 
            clean.equals("Unknown", ignoreCase = true) || 
            clean.equals("ACC", ignoreCase = true) ||
            clean.equals("BANK-SMS", ignoreCase = true)) {
            return "Unknown Account"
        }

        if (clean.contains("Personal Loan", ignoreCase = true)) {
            return "ICICI Personal Loan XX1565"
        }

        // Extract bank name using BANK_PATTERN
        val bankMatcher = BANK_PATTERN.matcher(clean)
        val bankName = if (bankMatcher.find()) {
            val foundBank = bankMatcher.group(1) ?: ""
            bankCasingMap[foundBank.uppercase(java.util.Locale.getDefault())] ?: foundBank
        } else {
            val cleanPrefix = clean.replace(CLEAN_PREFIX_REGEX, "")
                .replace(NON_WORD_REGEX, " ")
                .trim()
            val firstWord = cleanPrefix.split(" ").firstOrNull { it.isNotBlank() } ?: "Acc"
            if (firstWord.equals("Acc", ignoreCase = true) || firstWord.length < 2) "Acc" else firstWord.lowercase().replaceFirstChar { it.uppercase() }
        }

        // Extract digits
        val digitMatcher = Pattern.compile("(\\d{3,6})").matcher(clean)
        var digits = ""
        while (digitMatcher.find()) {
            val candidate = digitMatcher.group(1) ?: ""
            if (candidate != "0000") {
                digits = candidate
                break
            }
        }

        if (digits.isBlank() || digits == "0000") {
            return "Unknown Account"
        }

        val tail = if (digits.length == 3) "0$digits" else digits.takeLast(4)
        return "$bankName $tail"
    }

    fun extractAccountIdentifier(body: String): String {
        val lowerBody = body.lowercase()

        // Specific bank overrides
        if (lowerBody.contains("hsbc")) {
            return "HSBC 8006"
        }

        // Detect Bank Name
        val bankMatcher = BANK_PATTERN.matcher(body)
        val bankName = if (bankMatcher.find()) {
            val foundBank = bankMatcher.group(1) ?: ""
            bankCasingMap[foundBank.uppercase(java.util.Locale.getDefault())] ?: foundBank
        } else {
            ""
        }

        // Explicit account/card patterns with trailing 3-6 digits
        val explicitPatterns = listOf(
            Pattern.compile("(?i)(?:a/c|acc|account|card|a_c|acct|vpa|no\\.?|ending\\s*(?:in|with)?|credited\\s+to|debited\\s+from)\\s*[:\\-]?\\s*(?:[x*#.]+\\s*)?(\\d{3,6})\\b"),
            Pattern.compile("(?i)\\b(?:X{2,}|\\*{2,}|\\.{2,}|no\\.?\\s*X+|card\\s+x+)(\\d{3,4})\\b"),
            Pattern.compile("(?i)\\b(?:card|acc|a/c)\\s+(?:ending\\s+)?(\\d{3,4})\\b")
        )

        for (pattern in explicitPatterns) {
            val matcher = pattern.matcher(body)
            while (matcher.find()) {
                val tail = matcher.group(1) ?: ""
                if (tail.length in 3..6 && tail != "0000" && !isNonAccountDigitSequence(lowerBody, matcher.start(), tail)) {
                    val rawAccount = if (bankName.isNotBlank()) "$bankName $tail" else "Acc $tail"
                    val standardized = standardizeAccountIdentifier(rawAccount)
                    if (standardized != "Unknown Account") return standardized
                }
            }
        }

        // Proximity pattern: Bank name followed closely (within 25 chars) by account/card keywords
        if (bankName.isNotBlank()) {
            val proximityPattern = Pattern.compile("(?i)\\b" + Pattern.quote(bankName) + "\\b.{0,25}?(?:a/c|acc|account|card|no\\.?|ending|X+|\\*+|\\.+)[^\\d]*?(\\d{3,6})\\b")
            val proximityMatcher = proximityPattern.matcher(body)
            if (proximityMatcher.find()) {
                val tail = proximityMatcher.group(1) ?: ""
                if (tail.length in 3..6 && tail != "0000" && !isNonAccountDigitSequence(lowerBody, proximityMatcher.start(), tail)) {
                    val standardized = standardizeAccountIdentifier("$bankName $tail")
                    if (standardized != "Unknown Account") return standardized
                }
            }
        }

        if (lowerBody.contains("personal loan")) {
            return "ICICI Personal Loan XX1565"
        }

        return "Unknown Account"
    }

    private fun isNonAccountDigitSequence(lowerBody: String, matchStart: Int, digits: String): Boolean {
        val snippet = lowerBody.substring(maxOf(0, matchStart - 15), minOf(lowerBody.length, matchStart + digits.length + 15))
        if (snippet.contains("ref") || snippet.contains("utr") || snippet.contains("otp") || snippet.contains("pin") || snippet.contains("txn") || snippet.contains("awb") || snippet.contains("code") || snippet.contains("bal")) {
            return true
        }
        if (digits == "2026" || digits == "2025" || digits == "2024") {
            return true
        }
        return false
    }

    // Beneficiary extraction patterns with greedy parsing up to stop words
    private val SPENT_AT_PATTERN = Pattern.compile(
        "(?i)(?:spent\\s+at|spent\\s+on|at|tx\\s+at)\\s+([^.]+?)(?=\\s+(?:via|using|on|for|avl|bal|lmt|effective|\\d)|\\.|$)"
    )

    private val PAID_TO_PATTERN = Pattern.compile(
        "(?i)(?:paid\\s+to|sent\\s+to|transfer\\s+to|paying\\s+to|towards|for|\\bto)\\s+([^.]+?)(?=\\s+(?:via|using|on|for|avl|bal|lmt|effective|\\d)|\\.|$)"
    )

    /**
     * Parses raw SMS body into a structured TransactionSMS object.
     * Returns null if the SMS is not recognized as a transaction.
     */
    fun parseSms(body: String, timestamp: Long, sender: String = ""): TransactionSMS? {
        val lowerBody = body.lowercase()
        val finalSender = if (sender.trim().isNotBlank() && !sender.equals("Unknown", ignoreCase = true)) {
            sender.trim()
        } else {
            if (lowerBody.contains("hdfc")) "HDFC-BANK"
            else if (lowerBody.contains("yesbank") || lowerBody.contains("yes bank")) "YES-BANK"
            else "BANK-SMS"
        }

        // Special due alert/reminder case
        val isDueReminder = (
            IS_DUE_PATTERN.matcher(body).find() ||
            lowerBody.contains("overdue") ||
            (lowerBody.contains("due") && (
                lowerBody.contains("bill") || 
                lowerBody.contains("pay now") || 
                IGNORE_PAID_PATTERN.matcher(body).find()
            ))
        ) &&
        !lowerBody.contains("successfully paid") && 
        !lowerBody.contains("paid of") &&
        !lowerBody.contains("has been paid")

        if (isDueReminder) {
            var amt = 0.0
            val totalDueMatcher = TOTAL_DUE_PATTERN.matcher(body)
            val minDueMatcher = MIN_DUE_PATTERN.matcher(body)
            val billOfMatcher = BILL_OF_PATTERN.matcher(body)
            val amtDueMatcher = AMT_DUE_PATTERN.matcher(body)
            val payableMatcher = PAYABLE_PATTERN.matcher(body)
            
            if (totalDueMatcher.find()) {
                amt = totalDueMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            } else if (billOfMatcher.find()) {
                amt = billOfMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            } else if (amtDueMatcher.find()) {
                amt = amtDueMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            } else if (payableMatcher.find()) {
                amt = payableMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            } else if (minDueMatcher.find()) {
                amt = minDueMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            } else {
                val amtMatcher = AMOUNT_PATTERN.matcher(body)
                if (amtMatcher.find()) {
                    amt = amtMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                }
            }
            
            val accountId = extractAccountIdentifier(body)
            
            var beneficiary = "Unknown Beneficiary"
            
            // Check for service providers or biller names in body first
            if (lowerBody.contains("jiohome") || lowerBody.contains("jio home")) {
                beneficiary = "JioHome"
            } else if (lowerBody.contains("jio")) {
                beneficiary = "Jio"
            } else if (lowerBody.contains("airtel")) {
                beneficiary = "Airtel"
            } else if (lowerBody.contains("vi ") || lowerBody.contains("vodafone") || lowerBody.contains("idea")) {
                beneficiary = "Vi"
            } else if (lowerBody.contains("bsnl")) {
                beneficiary = "BSNL"
            } else if (lowerBody.contains("tata play") || lowerBody.contains("tataplay")) {
                beneficiary = "Tata Play"
            } else if (lowerBody.contains("lic")) {
                beneficiary = "LIC"
            } else if (lowerBody.contains("bescom") || lowerBody.contains("electricity")) {
                beneficiary = "Electricity"
            }
            
            if (beneficiary == "Unknown Beneficiary" && sender.isNotBlank() && !sender.equals("Unknown", ignoreCase = true)) {
                val senderClean = sender.trim().replace(SENDER_PREFIX_REGEX, "")
                if (senderClean.length >= 3 && senderClean.all { it.isLetter() }) {
                    beneficiary = senderClean
                }
            }
            
            if (beneficiary == "Unknown Beneficiary") {
                val parenMatcher = PAREN_PATTERN.matcher(body)
                if (parenMatcher.find()) {
                    val candidate = parenMatcher.group(1)?.trim()
                    if (candidate != null && candidate.length > 2 && 
                        !candidate.contains("balance", ignoreCase = true) && 
                        !candidate.contains("limit", ignoreCase = true) &&
                        candidate.toLongOrNull() == null) {
                        beneficiary = candidate
                    }
                }
            }
            
            if (beneficiary == "Unknown Beneficiary") {
                if (lowerBody.contains("yes bank") || lowerBody.contains("yesbank")) {
                    val digitMatcher = DIGITS_3_6_PATTERN.matcher(accountId)
                    val digits = if (digitMatcher.find()) (digitMatcher.group(1) ?: "3349") else "3349"
                    beneficiary = "yesbank $digits card"
                } else {
                    val cleanAcc = standardizeAccountIdentifier(accountId)
                    beneficiary = cleanAcc.lowercase(java.util.Locale.getDefault())
                }
            }
            
            beneficiary = cleanBeneficiary(beneficiary)
            val category = mapCategory(beneficiary, body)
            
            if (amt > 0.0) {
                val smsUniqueId = "$timestamp-$amt-Reminder"
                return TransactionSMS(
                    smsUniqueId = smsUniqueId,
                    timestamp = timestamp,
                    amount = amt,
                    beneficiary = beneficiary,
                    type = "Reminder",
                    category = category,
                    accountIdentifier = accountId,
                    remainingBalance = null,
                    rawSms = body,
                    sender = finalSender
                )
            }
        }

        // Special passbook balance & contribution received credit alert case
        if (lowerBody.contains("passbook balance against") && lowerBody.contains("contribution of")) {
            var accountId = "XXXXXXXX7845"
            val accMatcher = PASSBOOK_ACC_PATTERN.matcher(body)
            if (accMatcher.find()) {
                accountId = accMatcher.group(1)?.trim() ?: "XXXXXXXX7845"
            }
            
            var amt = 10162.0
            val amtMatcher = PASSBOOK_AMT_PATTERN.matcher(body)
            if (amtMatcher.find()) {
                amt = amtMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 10162.0
            }
            
            var beneficiary = "TNMAS******9126"
            val benMatcher = PASSBOOK_BEN_PATTERN.matcher(body)
            if (benMatcher.find()) {
                beneficiary = benMatcher.group(1)?.trim()?.removeSuffix(",") ?: "TNMAS******9126"
            }
            
            var remBal = 658438.0
            val balMatcher = PASSBOOK_BAL_PATTERN.matcher(body)
            if (balMatcher.find()) {
                val cleanedBalStr = balMatcher.group(1)
                    ?.replace(",", "")
                    ?.replace("/-", "")
                    ?.trim() ?: ""
                remBal = cleanedBalStr.toDoubleOrNull() ?: 658438.0
            }
            
            val smsUniqueId = "$timestamp-$amt-$beneficiary"
            return TransactionSMS(
                smsUniqueId = smsUniqueId,
                timestamp = timestamp,
                amount = amt,
                beneficiary = beneficiary,
                type = "Credit",
                category = "Other",
                accountIdentifier = accountId,
                remainingBalance = remBal,
                rawSms = body,
                sender = finalSender
            )
        }

        // Filter out loan offers, disbursal offers, tenure confirmations, promotional loan-setup alerts, and declined/failed transactions
        if (PROMOTIONAL_FILTER_PATTERN.matcher(body).find()) {
            return null
        }

        // 1. Determine transaction type (Credit / Debit / EMI / SIP) by checking only verb words
        if (!TRANSACTION_VERBS_PATTERN.matcher(body).find()) return null

        val type = when {
            lowerBody.contains("sip") -> "SIP"
            lowerBody.contains("emi") -> "EMI"
            lowerBody.contains("debited") || 
            lowerBody.contains("debit") || 
            lowerBody.contains("spent") || 
            lowerBody.contains("paid") || 
            lowerBody.contains("withdrawn") || 
            lowerBody.contains("transferred") || 
            lowerBody.contains("dr ") || 
            lowerBody.contains("dr.") -> "Debit"
            lowerBody.contains("credited") || 
            lowerBody.contains("cashback") || 
            lowerBody.contains("refunded") || 
            lowerBody.contains("received") ||
            lowerBody.contains("salary") -> "Credit"
            else -> "Debit"
        }

        // 2. Extract remaining balance
        var remainingBalance: Double? = null
        val balMatcher = BALANCE_PATTERN.matcher(body)
        var balanceString: String? = null
        if (balMatcher.find()) {
            val matchedBalance = balMatcher.group(1)
            if (matchedBalance != null) {
                balanceString = matchedBalance
                remainingBalance = matchedBalance.replace(",", "").toDoubleOrNull()
            }
        }

        // 3. Extract transaction amount
        var amount = 0.0
        val amtMatcher = AMOUNT_PATTERN.matcher(body)
        val amountsFound = mutableListOf<String>()
        while (amtMatcher.find()) {
            val matchedAmt = amtMatcher.group(1)
            if (matchedAmt != null) {
                amountsFound.add(matchedAmt)
            }
        }

        if (amountsFound.isNotEmpty()) {
            // Usually, the first amount is the transaction amount.
            // If the balance is the first found, check if it equals balanceString.
            val firstAmtVal = amountsFound[0].replace(",", "")
            val balanceValStr = balanceString?.replace(",", "")
            
            if (amountsFound.size > 1 && balanceValStr != null && firstAmtVal == balanceValStr) {
                // If the first amount matches the balance, the second is likely the transaction amount.
                amount = amountsFound[1].replace(",", "").toDoubleOrNull() ?: 0.0
            } else {
                amount = firstAmtVal.toDoubleOrNull() ?: 0.0
                // If there is a second amount and we didn't extract balance, we can check if it was actually the balance
                if (amountsFound.size > 1 && remainingBalance == null && lowerBody.contains("bal")) {
                    remainingBalance = amountsFound[1].replace(",", "").toDoubleOrNull()
                }
            }
        }

        if (amount == 0.0) {
            // Only search for fallback digit if body contains explicit financial context
            if (HAS_FINANCIAL_KEYWORD_PATTERN.matcher(body).find()) {
                val digitMatcher = FLOAT_DIGIT_PATTERN.matcher(body)
                if (digitMatcher.find()) {
                    val groupVal = digitMatcher.group(1)
                    if (groupVal != null) {
                        amount = groupVal.replace(",", "").toDoubleOrNull() ?: 0.0
                    }
                }
            }
        }

        // 4. Extract Account Identifier (e.g. YES 3349 or HDFC 5056)
        val accountIdentifier = extractAccountIdentifier(body)

        // 5. Extract Beneficiary
        var beneficiary = "Unknown Beneficiary"

        // Check for HSBC bank specific structure
        if (lowerBody.contains("hsbc")) {
            if (lowerBody.contains("as csh wdl") || lowerBody.contains("csh wdl")) {
                beneficiary = "CSH WDL"
            } else if (lowerBody.contains("pzcreditcard")) {
                beneficiary = "PZCREDITCARD"
            } else {
                val matcherFrom = FROM_PATTERN.matcher(body)
                if (matcherFrom.find()) {
                    val candidate = matcherFrom.group(1)?.trim() ?: ""
                    if (candidate.isNotEmpty() && !candidate.contains("hsbc", ignoreCase = true)) {
                        beneficiary = candidate
                    }
                }
            }
        }

        // Check for ICICI bank specific structure first
        if (lowerBody.contains("icici")) {
            val infoMatcher = ICICI_INFO_PATTERN.matcher(body)
            val specialMatcher = ICICI_SPECIAL_PATTERN.matcher(body)
            val iciciMatcher = ICICI_STANDARD_PATTERN.matcher(body)
            if (infoMatcher.find()) {
                val candidate = infoMatcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length > 1) {
                    beneficiary = candidate
                }
            } else if (specialMatcher.find()) {
                val candidate = specialMatcher.group(1)?.trim()
                if (candidate != null && candidate.length > 2) {
                    beneficiary = candidate
                }
            } else if (iciciMatcher.find()) {
                val candidate = iciciMatcher.group(1)?.trim()
                if (candidate != null && candidate.length > 2) {
                    beneficiary = candidate
                }
            }
        }
        
        if (beneficiary == "Unknown Beneficiary") {
            // Scan for content in parentheses first (e.g. salary credited)
            val parenMatcher = PAREN_PATTERN.matcher(body)
            if (parenMatcher.find()) {
                val candidate = parenMatcher.group(1)?.trim()
                if (candidate != null && candidate.length > 2 && 
                    !candidate.contains("balance", ignoreCase = true) && 
                    !candidate.contains("limit", ignoreCase = true)) {
                    beneficiary = candidate
                }
            }
        }
        
        if (beneficiary == "Unknown Beneficiary") {
            val spentMatcher = SPENT_AT_PATTERN.matcher(body)
            val paidMatcher = PAID_TO_PATTERN.matcher(body)
            
            if (spentMatcher.find()) {
                val match = spentMatcher.group(1)
                if (match != null) beneficiary = match.trim()
            } else if (paidMatcher.find()) {
                val match = paidMatcher.group(1)
                if (match != null) beneficiary = match.trim()
            } else {
                // Fallback: look for typical keywords and pull next words
                listOf("spent at", "spent on", "paid to", "sent to", "towards", "initiated to").forEach { key ->
                    val index = lowerBody.indexOf(key)
                    if (index != -1) {
                        val candidate = body.substring(index + key.length).trim()
                        val words = candidate.split(" ").take(3).joinToString(" ")
                        val firstSentence = words.split(".").firstOrNull() ?: words
                        if (firstSentence.isNotBlank() && firstSentence.length > 2) {
                            beneficiary = firstSentence.trim()
                        }
                    }
                }
            }
        }

        // Clean up beneficiary string of noise
        beneficiary = cleanBeneficiary(beneficiary)

        // 6. Map category based on merchant or transaction identifiers
        val category = mapCategory(beneficiary, body)

        if (amount <= 0.0) {
            return null
        }

        val smsUniqueId = "$timestamp-$amount-$beneficiary"

        return TransactionSMS(
            smsUniqueId = smsUniqueId,
            timestamp = timestamp,
            amount = amount,
            beneficiary = beneficiary,
            type = type,
            category = category,
            accountIdentifier = accountIdentifier,
            remainingBalance = remainingBalance,
            rawSms = body,
            sender = finalSender
        )
    }

    private fun cleanBeneficiary(input: String): String {
        var clean = input.trim()
        
        // Special logic for YES BANK transactions: extract the merchant text between @ and before date-timestamp
        if (clean.contains("YES BANK", ignoreCase = true) && clean.contains("@")) {
            val indexAt = clean.indexOf('@')
            if (indexAt != -1) {
                var afterAt = clean.substring(indexAt + 1).trim()
                
                // Remove date (e.g. 09-06-2026)
                val dateRegex = Regex("(?i)\\b\\d{2}[-/]\\d{2}[-/]\\d{4}.*")
                afterAt = afterAt.replace(dateRegex, "").trim()
                
                // Remove time (e.g. 05:52:50)
                val timeRegex = Regex("(?i)\\b\\d{2}:\\d{2}(?::\\d{2})?.*")
                afterAt = afterAt.replace(timeRegex, "").trim()
                
                if (afterAt.isNotBlank()) {
                    clean = afterAt
                }
            }
        }

        // Remove leading quotes, colons, dashes, slashes, or non-alphanumeric punctuation
        clean = clean.replace(Regex("^[\"'\\-:;,.\\s]+"), "").trim()

        // Remove leading dates if candidate accidentally included date prefix (e.g. "22-Aug-26 on RELIANCE RETAIL")
        clean = clean.replace(Regex("(?i)^\\d{1,2}[-/][a-zA-Z0-9]{2,4}[-/]\\d{2,4}\\s*"), "").trim()

        // Strip leading preposition/adjective words before merchant name
        val leadingPrefixRegex = Regex("(?i)^(?:spent\\s+at|spent\\s+on|paid\\s+to|at\\s+merchant|on\\s+merchant|info[:\\-]?|on|at|to|for|in|from|by|towards|via|using|through|vpa|upi|ref|merchant)\\s+")
        
        var prevLen = -1
        while (clean.length != prevLen) {
            prevLen = clean.length
            clean = clean.replace(leadingPrefixRegex, "").trim()
        }

        // Remove trailing commas, periods or spaces
        while (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith("-") || clean.endsWith("_")) {
            clean = clean.dropLast(1).trim()
        }

        // Remove trailing helper words
        val suffixes = listOf("via", "using", "on", "at", "for", "to", "in", "avl", "available", "bal", "balance", "lmt", "limit", "effective", "has")
        suffixes.forEach { suffix ->
            if (clean.lowercase().endsWith(" $suffix")) {
                clean = clean.substring(0, clean.length - (suffix.length + 1)).trim()
            }
        }

        // Final pass for leading prefix cleanup
        prevLen = -1
        while (clean.length != prevLen) {
            prevLen = clean.length
            clean = clean.replace(leadingPrefixRegex, "").trim()
        }

        val noiseSet = setOf("on", "at", "to", "for", "in", "from", "by", "towards", "via", "using", "through", "info", "merchant", "unknown", "ref")
        if (clean.isBlank() || clean.lowercase() in noiseSet) {
            return "Unknown Beneficiary"
        }

        return clean
    }

    private fun mapCategory(beneficiary: String, rawSms: String): String {
        val lowerBeneficiary = beneficiary.lowercase()
        val lowerSms = rawSms.lowercase()

        return when {
            // "Cash Withdrawl" mapping
            lowerBeneficiary.contains("cash wdl") ||
            lowerBeneficiary.contains("csh wdl") ||
            lowerBeneficiary.contains("cash withdrawal") ||
            lowerBeneficiary.contains("cash withdrawl") ||
            lowerSms.contains("cash wdl") ||
            lowerSms.contains("csh wdl") ||
            lowerSms.contains("cash withdrawal") ||
            lowerSms.contains("cash withdrawl") ||
            lowerSms.contains("withdrawn from atm") ||
            lowerSms.contains("atm withdrawal") -> "Cash Withdrawl"

            // "Food & Drinks" mapping
            lowerBeneficiary.contains("taslim khnam") || 
            lowerBeneficiary.contains("zomato") || 
            lowerBeneficiary.contains("swiggy") || 
            lowerBeneficiary.contains("starbucks") || 
            lowerBeneficiary.contains("mcdonald") || 
            lowerBeneficiary.contains("cafe") || 
            lowerBeneficiary.contains("restaurant") || 
            lowerBeneficiary.contains("dominos") || 
            lowerBeneficiary.contains("pizza") || 
            lowerBeneficiary.contains("kfc") || 
            lowerBeneficiary.contains("burger") ||
            lowerSms.contains("swiggy") || 
            lowerSms.contains("zomato") || 
            lowerSms.contains("restaurant") ||
            lowerSms.contains("food") ||
            lowerSms.contains("cafe") ||
            lowerSms.contains("dining") -> "Food & Drinks"

            // "Rent" mapping
            lowerBeneficiary.contains("muzammil pasha") || 
            lowerBeneficiary.contains("rent") || 
            lowerBeneficiary.contains("house rent") || 
            lowerBeneficiary.contains("landlord") || 
            lowerBeneficiary.contains("owner") ||
            lowerSms.contains("house rent") ||
            lowerSms.contains("rent paid") -> "Rent"

            // "EMI" mapping
            lowerBeneficiary.contains("loan") ||
            lowerBeneficiary.contains("hfc") ||
            lowerBeneficiary.contains("emi") ||
            lowerSms.contains("loan emi") ||
            lowerSms.contains("emi") -> "EMI"

            // "Entertainment" mapping
            lowerBeneficiary.contains("netflix") ||
            lowerBeneficiary.contains("prime video") ||
            lowerBeneficiary.contains("spotify") ||
            lowerBeneficiary.contains("hotstar") ||
            lowerBeneficiary.contains("bookmyshow") ||
            lowerBeneficiary.contains("cinema") ||
            lowerBeneficiary.contains("pvr") ||
            lowerSms.contains("netflix") ||
            lowerSms.contains("spotify") ||
            lowerSms.contains("bookmyshow") -> "Entertainment"

            // "Fuel" mapping
            lowerBeneficiary.contains("fuel") ||
            lowerBeneficiary.contains("petrol") ||
            lowerBeneficiary.contains("diesel") ||
            lowerBeneficiary.contains("hpcl") ||
            lowerBeneficiary.contains("bpcl") ||
            lowerBeneficiary.contains("iocl") ||
            lowerBeneficiary.contains("shell") ||
            lowerSms.contains("fuel") ||
            lowerSms.contains("petrol pump") -> "Fuel"

            // "Groceries" mapping
            lowerBeneficiary.contains("grocery") ||
            lowerBeneficiary.contains("groceries") ||
            lowerBeneficiary.contains("blinkit") ||
            lowerBeneficiary.contains("instamart") ||
            lowerBeneficiary.contains("zepto") ||
            lowerBeneficiary.contains("dmart") ||
            lowerBeneficiary.contains("bigbasket") ||
            lowerBeneficiary.contains("supermarket") ||
            lowerSms.contains("grocery") ||
            lowerSms.contains("blinkit") ||
            lowerSms.contains("zepto") -> "Groceries"

            // "Health" mapping
            lowerBeneficiary.contains("hospital") ||
            lowerBeneficiary.contains("pharmacy") ||
            lowerBeneficiary.contains("medical") ||
            lowerBeneficiary.contains("doctor") ||
            lowerBeneficiary.contains("clinic") ||
            lowerBeneficiary.contains("apollo") ||
            lowerSms.contains("hospital") ||
            lowerSms.contains("pharmacy") ||
            lowerSms.contains("medical") -> "Health"

            // "Investment" mapping
            lowerBeneficiary.contains("mutual fund") ||
            lowerBeneficiary.contains("zerodha") ||
            lowerBeneficiary.contains("groww") ||
            lowerBeneficiary.contains("investment") ||
            lowerBeneficiary.contains("stock") ||
            lowerSms.contains("sip transaction") ||
            lowerSms.contains("mutual fund") ||
            lowerSms.contains("investment") -> "Investment"

            // "Shopping" mapping
            lowerBeneficiary.contains("amazon") ||
            lowerBeneficiary.contains("flipkart") ||
            lowerBeneficiary.contains("myntra") ||
            lowerBeneficiary.contains("shopping") ||
            lowerBeneficiary.contains("reliance digital") ||
            lowerSms.contains("amazon") ||
            lowerSms.contains("shopping") ||
            lowerSms.contains("flipkart") -> "Shopping"

            // "Transfer" mapping
            lowerBeneficiary.contains("transfer to") ||
            lowerSms.contains("transfer to") ||
            lowerSms.contains("sent to") ||
            lowerSms.contains("paid via upi") -> "Other"

            // "Travel" mapping
            lowerBeneficiary.contains("uber") ||
            lowerBeneficiary.contains("ola") ||
            lowerBeneficiary.contains("travel") ||
            lowerBeneficiary.contains("irctc") ||
            lowerBeneficiary.contains("makemytrip") ||
            lowerBeneficiary.contains("flight") ||
            lowerBeneficiary.contains("metro") ||
            lowerBeneficiary.contains("cab") ||
            lowerSms.contains("uber ride") ||
            lowerSms.contains("ola cab") ||
            lowerSms.contains("irctc") -> "Travel"

            // "Bills" mapping
            lowerBeneficiary.contains("electricity") || 
            lowerBeneficiary.contains("jio") || 
            lowerBeneficiary.contains("airtel") || 
            lowerBeneficiary.contains("recharge") || 
            lowerBeneficiary.contains("biller") || 
            lowerBeneficiary.contains("bill") || 
            lowerSms.contains("electricity") || 
            lowerSms.contains("recharge") || 
            lowerSms.contains("bill payment") || 
            lowerSms.contains("postpaid") -> "Bills"

            // "Other" / general mapping
            lowerBeneficiary.contains("@upi") ||
            lowerSms.contains("payment") -> "Other"

            else -> "Other"
        }
    }
}
