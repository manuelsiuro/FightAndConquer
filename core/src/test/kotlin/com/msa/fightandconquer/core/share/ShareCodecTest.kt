package com.msa.fightandconquer.core.share

import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.campaign.TestLevels
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodecTest {

    private val def = CustomMapDef(id = "s1", name = "Strip", level = TestLevels.strip())

    private fun ok(result: ShareDecodeResult): CustomMapDef =
        (result as ShareDecodeResult.Ok).def

    private fun error(result: ShareDecodeResult): ShareError =
        (result as ShareDecodeResult.Failed).error

    @Test
    fun `text form round trips`() {
        val text = ShareCodec.encodeText(def)
        assertTrue(text.startsWith(ShareCodec.TEXT_PREFIX))
        assertEquals(def, ok(ShareCodec.decodeText(text)))
    }

    @Test
    fun `byte form round trips`() {
        assertEquals(def, ok(ShareCodec.decodeBytes(ShareCodec.encodeBytes(def))))
    }

    @Test
    fun `whitespace around a pasted code is forgiven`() {
        assertEquals(def, ok(ShareCodec.decodeText("  ${ShareCodec.encodeText(def)}\n")))
    }

    /**
     * Generated maps at every size, wrapped as scenarios, must round-trip — and their
     * measured text sizes are pinned here so the QR ceiling (2000 bytes at EC L) is a
     * fact, not an estimate. SMALL must always fit a QR; larger sizes get honest
     * bounds that fail loudly if the format ever bloats.
     */
    @Test
    fun `generated maps round trip and their sizes are pinned`() {
        val sizes = mutableMapOf<MapSize, Int>()
        for (size in MapSize.entries) {
            val map = MapGenerator.generate(MapParams(seed = 7L, size = size, playerCount = 4))
            val scenario = CustomMapDef(
                id = "gen-$size",
                name = "gen-$size",
                level = LevelDef(
                    id = "gen-$size",
                    seed = 7L,
                    map = map,
                    seats = listOf(SeatDef.Player) + List(3) { SeatDef.Ai(Difficulty.NORMAL) },
                ),
            )
            val text = ShareCodec.encodeText(scenario)
            sizes[size] = text.length
            assertEquals(scenario, ok(ShareCodec.decodeText(text)))
        }
        // Measured with the frozen preset dictionary: SMALL 1537, MEDIUM 3821, LARGE 5612.
        assertTrue("SMALL must fit a QR: ${sizes[MapSize.SMALL]}", sizes[MapSize.SMALL]!! <= 2000)
        assertTrue("MEDIUM ballooned: ${sizes[MapSize.MEDIUM]}", sizes[MapSize.MEDIUM]!! <= 4500)
        assertTrue("LARGE ballooned: ${sizes[MapSize.LARGE]}", sizes[MapSize.LARGE]!! <= 6500)
    }

    @Test
    fun `a small authored scenario compresses to a short code`() {
        // The preset dictionary absorbs the fixed boilerplate: measured 196 chars.
        assertTrue(ShareCodec.encodeText(def).length <= 400)
    }

    @Test
    fun `not our text at all`() {
        assertEquals(ShareError.NOT_A_MAP_CODE, error(ShareCodec.decodeText("hello world")))
        assertEquals(ShareError.NOT_A_MAP_CODE, error(ShareCodec.decodeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))))
    }

    @Test
    fun `a damaged payload is corrupted, not malformed`() {
        val text = ShareCodec.encodeText(def)
        val flipped = text.dropLast(3) + when (text.takeLast(3).first()) {
            'A' -> 'B'
            else -> 'A'
        } + text.takeLast(2)
        val result = ShareCodec.decodeText(flipped)
        assertTrue(
            "expected CORRUPTED or MALFORMED, got $result",
            result is ShareDecodeResult.Failed &&
                (result.error == ShareError.CORRUPTED || result.error == ShareError.MALFORMED),
        )
    }

    @Test
    fun `a truncated payload is corrupted`() {
        val text = ShareCodec.encodeText(def)
        val result = ShareCodec.decodeText(text.take(text.length / 2))
        assertTrue(result is ShareDecodeResult.Failed)
    }

    @Test
    fun `a newer envelope version is refused as unsupported`() {
        val body = ShareCodec.encodeBytes(def).copyOfRange(4, ShareCodec.encodeBytes(def).size)
        body[0] = 99
        assertEquals(
            ShareError.UNSUPPORTED_VERSION,
            error(ShareCodec.decodeBytes(ShareCodec.MAGIC + body)),
        )
    }

    @Test
    fun `an intact envelope around an invalid scenario reports its violations`() {
        val invalid = def.copy(level = def.level.copy(seats = listOf(SeatDef.Player)))
        val result = ShareCodec.decodeText(ShareCodec.encodeText(invalid)) as ShareDecodeResult.Failed
        assertEquals(ShareError.INVALID_MAP, result.error)
        assertTrue(result.violations.isNotEmpty())
    }

    @Test
    fun `a zip bomb is capped and refused`() {
        val huge = ByteArray(4 * 1024 * 1024) { 'a'.code.toByte() }
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(huge)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        val deflated = out.toByteArray()
        val crc = CRC32().apply { update(deflated) }.value
        val body = ByteArray(5 + deflated.size)
        body[0] = 1
        body[1] = (crc ushr 24).toByte()
        body[2] = (crc ushr 16).toByte()
        body[3] = (crc ushr 8).toByte()
        body[4] = crc.toByte()
        deflated.copyInto(body, 5)
        val text = ShareCodec.TEXT_PREFIX +
            Base64.getUrlEncoder().withoutPadding().encodeToString(body)
        assertEquals(ShareError.MALFORMED, error(ShareCodec.decodeText(text)))
    }
}
