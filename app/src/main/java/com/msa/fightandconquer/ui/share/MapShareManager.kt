package com.msa.fightandconquer.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.share.LsbStego
import com.msa.fightandconquer.core.share.ShareCodec
import com.msa.fightandconquer.core.share.ShareDecodeResult
import com.msa.fightandconquer.core.share.ShareError
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/**
 * Every way a map leaves or enters the device. All channels carry the same
 * [ShareCodec] envelope: text code (clipboard/QR) and bytes (`.fcmap` file, stego
 * PNG). Plain class, Context-injected; failures come back as [ShareDecodeResult]
 * so the UI maps them through `ShareError.toUiText`.
 */
class MapShareManager(private val context: Context) {

    // ----- text -----

    fun copyCode(def: CustomMapDef) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(def.name, ShareCodec.encodeText(def)))
    }

    fun pasteCode(): ShareDecodeResult {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            ?: return ShareDecodeResult.Failed(ShareError.NOT_A_MAP_CODE)
        return ShareCodec.decodeText(text)
    }

    // ----- file -----

    fun shareFile(def: CustomMapDef) {
        val file = exportDir().resolve("${sanitize(def.name)}.fcmap")
        file.writeBytes(ShareCodec.encodeBytes(def))
        shareUri(uriFor(file), MIME_FCMAP)
    }

    fun importBytes(bytes: ByteArray): ShareDecodeResult = ShareCodec.decodeBytes(bytes)

    fun importFile(uri: Uri): ShareDecodeResult {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readCapped(input)
            }
        }.getOrNull() ?: return ShareDecodeResult.Failed(ShareError.MALFORMED)
        return importBytes(bytes)
    }

    // ----- QR -----

    /** Whether this map's code is small enough to scan reliably. */
    fun qrFits(def: CustomMapDef): Boolean =
        ShareCodec.encodeText(def).length <= QR_MAX_TEXT_BYTES

    /** The QR bitmap for the map's code, or null when it exceeds the guard. */
    fun qrBitmap(def: CustomMapDef, sizePx: Int = 720): Bitmap? {
        val text = ShareCodec.encodeText(def)
        if (text.length > QR_MAX_TEXT_BYTES) return null
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 2,
        )
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) BLACK else WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    // ----- stego image -----

    fun shareStegoImage(def: CustomMapDef) {
        val bitmap = MinimapRenderer.render(def)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        LsbStego.embed(pixels, ShareCodec.encodeBytes(def))
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val file = exportDir().resolve("${sanitize(def.name)}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        shareUri(uriFor(file), MIME_PNG)
    }

    /**
     * Imports from a picked image: the hidden stego payload first, then a photographed
     * or screenshotted QR. Hardware bitmaps cannot expose pixels and any scaling
     * destroys LSBs, so decoding is forced software and unscaled.
     */
    fun importImage(uri: Uri): ShareDecodeResult {
        val bitmap = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        }.getOrNull() ?: return ShareDecodeResult.Failed(ShareError.MALFORMED)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        LsbStego.extract(pixels)?.let { payload ->
            return importBytes(payload)
        }
        decodeQr(pixels, bitmap.width, bitmap.height)?.let { text ->
            return ShareCodec.decodeText(text)
        }
        return ShareDecodeResult.Failed(ShareError.NOT_A_MAP_CODE)
    }

    private fun decodeQr(pixels: IntArray, width: Int, height: Int): String? = runCatching {
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = com.google.zxing.BinaryBitmap(HybridBinarizer(source))
        QRCodeReader().decode(binary).text
    }.getOrNull()

    // ----- shared plumbing -----

    private fun exportDir(): File =
        File(context.cacheDir, "shared_maps").apply { mkdirs() }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun shareUri(uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }

    private fun readCapped(input: java.io.InputStream): ByteArray {
        val bytes = input.readNBytes(MAX_IMPORT_BYTES + 1)
        check(bytes.size <= MAX_IMPORT_BYTES) { "import too large" }
        return bytes
    }

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "map" }.replace(' ', '_')

    companion object {
        /**
         * Byte-mode EC-L version 40 tops out at 2 953; stopping at 2 000 keeps the
         * module density scannable on a phone screen. Pinned against measured sizes
         * in :core's ShareCodecTest (SMALL generated ~1 537).
         */
        const val QR_MAX_TEXT_BYTES = 2000
        private const val MAX_IMPORT_BYTES = 1024 * 1024
        private const val MIME_FCMAP = "application/octet-stream"
        private const val MIME_PNG = "image/png"
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
