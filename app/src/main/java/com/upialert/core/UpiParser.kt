package com.upialert.core

import java.util.Locale
import java.util.regex.Pattern

object UpiParser {

    // Common UPI Apps Package Names
    const val PKG_GPAY = "com.google.android.apps.nbu.paisa.user"
    const val PKG_PHONEPE = "com.phonepe.app"
    const val PKG_PAYTM = "net.one97.paytm"
    const val PKG_BHIM = "in.org.npci.upiapp"
    // Add more as needed

    private val supportedPackages = setOf(PKG_GPAY, PKG_PHONEPE, PKG_PAYTM, PKG_BHIM)

    fun isSupportedPackage(packageName: String): Boolean {
        return supportedPackages.contains(packageName)
    }

    /**
     * Extracts the amount from the notification text.
     * Returns 0.0 if soundbox logic applies (payment received),
     * or a positive double if amount is found.
     * Returns null if it's not a relevant transaction (e.g. promotional, or debit).
     */
    fun parseAmount(title: String?, content: String?): Double? {
        val fullText = "${title ?: ""} ${content ?: ""}".lowercase(Locale.ROOT)

        // 1. Filter out irrelevant notifications
        if (fullText.contains("failed") || 
            fullText.contains("request") || 
            fullText.contains("declined") ||
            fullText.contains("refund") ||
            fullText.contains("cashback") || // Usually we don't announce cashback as user payments
            fullText.contains("otp")
        ) {
            return null
        }

        // 2. Ensure it is a CREDIT transaction
        // Keywords: "received", "credited", "sent you"
        // Anti-keywords: "paid", "debited", "sent to"
        val isCredit = fullText.contains("received") || 
                       fullText.contains("credited") || 
                       fullText.contains("sent you") ||
                       fullText.contains("paid you")

        if (!isCredit) {
            return null
        }

        // 3. Extract Amount using Regex
        // Matches: ₹100, Rs. 100, INR 100, 100.00, Rs 500
        // Handles comma: 1,000.00
        val regex = Pattern.compile("(?i)(?:rs\\.?|rup(?:ees?)?|inr|₹|INR)\\s*[:\\-]?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        val matcher = regex.matcher(fullText)

        return if (matcher.find()) {
            try {
                // Remove commas and parse
                val amountStr = matcher.group(1)?.replace(",", "") ?: return null
                amountStr.toDoubleOrNull()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
