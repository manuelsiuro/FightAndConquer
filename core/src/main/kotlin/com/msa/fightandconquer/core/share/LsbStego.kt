package com.msa.fightandconquer.core.share

/**
 * Least-significant-bit steganography over ARGB pixels: the payload hides in the low
 * bit of each red, green and blue channel (3 bits per pixel, row-major), alpha left
 * untouched so premultiplication can never smear the data. Pure array math — the app
 * layer owns the `Bitmap`s and hands over its `IntArray`.
 *
 * Layout: `"FCSG"` magic (4 bytes) + payload length (4 bytes BE) + payload. A PNG
 * round-trip preserves every bit; lossy recompression (a messaging app "optimizing"
 * the photo) destroys the magic and [extract] returns null — the caller's cue for a
 * clean "no map data found" message. The payload itself is a full [ShareCodec]
 * envelope, so even a bit-lucky forgery still has a CRC to get past.
 */
object LsbStego {

    private val MAGIC = byteArrayOf(0x46, 0x43, 0x53, 0x47) // "FCSG"
    private const val LENGTH_BYTES = 4
    private const val HEADER_SIZE = 4 + LENGTH_BYTES
    private const val BITS_PER_PIXEL = 3

    /** Payload capacity of an image with [pixelCount] pixels, after the header. */
    fun capacityBytes(pixelCount: Int): Int =
        (pixelCount * BITS_PER_PIXEL / 8 - HEADER_SIZE).coerceAtLeast(0)

    /** Embeds [payload] into [pixels] in place. Throws if it cannot fit. */
    fun embed(pixels: IntArray, payload: ByteArray) {
        require(payload.size <= capacityBytes(pixels.size)) {
            "payload of ${payload.size} bytes exceeds capacity of ${capacityBytes(pixels.size)}"
        }
        val data = ByteArray(HEADER_SIZE + payload.size)
        MAGIC.copyInto(data)
        data[4] = (payload.size ushr 24).toByte()
        data[5] = (payload.size ushr 16).toByte()
        data[6] = (payload.size ushr 8).toByte()
        data[7] = payload.size.toByte()
        payload.copyInto(data, HEADER_SIZE)

        var bit = 0
        for (byte in data) {
            for (shift in 7 downTo 0) {
                val value = (byte.toInt() ushr shift) and 1
                val pixel = bit / BITS_PER_PIXEL
                val channelShift = CHANNEL_SHIFTS[bit % BITS_PER_PIXEL]
                pixels[pixel] = (pixels[pixel] and (1 shl channelShift).inv()) or
                    (value shl channelShift)
                bit++
            }
        }
    }

    /** Recovers a payload, or null if [pixels] carry no intact header. */
    fun extract(pixels: IntArray): ByteArray? {
        if (capacityBytes(pixels.size) < 0) return null
        val header = readBytes(pixels, 0, HEADER_SIZE) ?: return null
        if (!MAGIC.contentEquals(header.copyOfRange(0, 4))) return null
        val length = ((header[4].toInt() and 0xFF) shl 24) or
            ((header[5].toInt() and 0xFF) shl 16) or
            ((header[6].toInt() and 0xFF) shl 8) or
            (header[7].toInt() and 0xFF)
        if (length < 0 || length > capacityBytes(pixels.size)) return null
        return readBytes(pixels, HEADER_SIZE, length)
    }

    // Red, green, blue LSB positions inside an ARGB int.
    private val CHANNEL_SHIFTS = intArrayOf(16, 8, 0)

    private fun readBytes(pixels: IntArray, byteOffset: Int, count: Int): ByteArray? {
        val lastBit = (byteOffset + count) * 8 - 1
        if (lastBit / BITS_PER_PIXEL >= pixels.size) return null
        val out = ByteArray(count)
        var bit = byteOffset * 8
        for (i in 0 until count) {
            var value = 0
            repeat(8) {
                val pixel = bit / BITS_PER_PIXEL
                val channelShift = CHANNEL_SHIFTS[bit % BITS_PER_PIXEL]
                value = (value shl 1) or ((pixels[pixel] ushr channelShift) and 1)
                bit++
            }
            out[i] = value.toByte()
        }
        return out
    }
}
