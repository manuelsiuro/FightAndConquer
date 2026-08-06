package com.msa.fightandconquer.ui.share

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ZXing is pure Java, so the QR pipeline runs on the host: encode at the exact
 * guard size, rasterize to pixels, decode back. Pins that EC-L byte mode really
 * carries [MapShareManager.QR_MAX_TEXT_BYTES] through a lossless raster — the
 * on-device path adds only Bitmap plumbing around the same calls.
 */
class QrRoundTripTest {

    @Test
    fun `a guard-size payload survives encode-rasterize-decode`() {
        val payload = "FCM1:" + "A".repeat(MapShareManager.QR_MAX_TEXT_BYTES - 5)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 2,
        )
        val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 720, 720, hints)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        assertEquals(payload, decoded)
    }
}
