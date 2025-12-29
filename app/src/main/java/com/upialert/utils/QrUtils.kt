package com.upialert.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QrUtils {
    
    fun generateQrCode(content: String, width: Int = 512, height: Int = 512): Bitmap? {
        if (content.isEmpty()) return null
        
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.MARGIN] = 1 // Min margin
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            
            val w = bitMatrix.width
            val h = bitMatrix.height
            val pixels = IntArray(w * h)
            
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * w + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getUpiString(payeeVpa: String, payeeName: String, amount: String? = null): String {
        // Basic format: upi://pay?pa=...&pn=...&cu=INR
        val validVpa = payeeVpa.trim()
        val validName = android.net.Uri.encode(payeeName.trim())
        
        var uri = "upi://pay?pa=$validVpa&pn=$validName&cu=INR"
        if (!amount.isNullOrEmpty()) {
            uri += "&am=$amount"
        }
        return uri
    }
}
