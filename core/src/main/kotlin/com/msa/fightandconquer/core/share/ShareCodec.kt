package com.msa.fightandconquer.core.share

import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.core.editor.MapCodec
import com.msa.fightandconquer.core.map.MapViolation
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.text.Charsets.UTF_8

/** Why an import was refused; the app maps each to a translatable message. */
enum class ShareError {
    /** The input is not one of ours at all — wrong prefix or magic. */
    NOT_A_MAP_CODE,

    /** Made by a newer format than this build understands. */
    UNSUPPORTED_VERSION,

    /** The envelope is ours but the bytes were damaged in transit (CRC mismatch). */
    CORRUPTED,

    /** Intact bytes that do not decompress or parse into a scenario. */
    MALFORMED,

    /** A well-formed scenario that fails structural validation. */
    INVALID_MAP,
}

sealed interface ShareDecodeResult {
    data class Ok(val def: CustomMapDef) : ShareDecodeResult
    data class Failed(
        val error: ShareError,
        val violations: List<MapViolation> = emptyList(),
    ) : ShareDecodeResult
}

/**
 * The wire format every sharing channel speaks — text code, `.fcmap` file, QR payload
 * and the steganographic image all carry the same envelope:
 *
 * ```
 * body  = [format version: 1 byte][CRC32 of deflated JSON: 4 bytes BE][deflated JSON]
 * file  = "FCM1" + body
 * text  = "FCM1:" + base64url(body)          (no padding)
 * ```
 *
 * The JSON inside is [MapCodec]'s (`ignoreUnknownKeys` + `encodeDefaults`), so schema
 * evolution works by defaulted fields exactly like saves; the format-version byte is
 * reserved for a change of *envelope*, not of schema. Decoding checks outermost-first
 * so every failure mode maps to one precise [ShareError], and inflation is capped so a
 * hostile payload cannot balloon in memory.
 */
object ShareCodec {

    const val FORMAT_VERSION: Int = 1
    const val TEXT_PREFIX: String = "FCM1:"
    val MAGIC: ByteArray = byteArrayOf(0x46, 0x43, 0x4D, 0x31) // "FCM1"

    private const val HEADER_SIZE = 5
    private const val MAX_INFLATED_BYTES = 512 * 1024

    fun encodeText(def: CustomMapDef): String =
        TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(body(def))

    fun encodeBytes(def: CustomMapDef): ByteArray = MAGIC + body(def)

    fun decodeText(text: String): ShareDecodeResult {
        val trimmed = text.trim()
        if (!trimmed.startsWith(TEXT_PREFIX)) {
            return ShareDecodeResult.Failed(ShareError.NOT_A_MAP_CODE)
        }
        val body = try {
            Base64.getUrlDecoder().decode(trimmed.removePrefix(TEXT_PREFIX))
        } catch (_: IllegalArgumentException) {
            return ShareDecodeResult.Failed(ShareError.CORRUPTED)
        }
        return decodeBody(body)
    }

    fun decodeBytes(bytes: ByteArray): ShareDecodeResult {
        if (bytes.size < MAGIC.size || !MAGIC.contentEquals(bytes.copyOfRange(0, MAGIC.size))) {
            return ShareDecodeResult.Failed(ShareError.NOT_A_MAP_CODE)
        }
        return decodeBody(bytes.copyOfRange(MAGIC.size, bytes.size))
    }

    private fun body(def: CustomMapDef): ByteArray {
        val deflated = deflate(MapCodec.encode(def).toByteArray(UTF_8))
        val crc = CRC32().apply { update(deflated) }.value
        val out = ByteArray(HEADER_SIZE + deflated.size)
        out[0] = FORMAT_VERSION.toByte()
        out[1] = (crc ushr 24).toByte()
        out[2] = (crc ushr 16).toByte()
        out[3] = (crc ushr 8).toByte()
        out[4] = crc.toByte()
        deflated.copyInto(out, HEADER_SIZE)
        return out
    }

    private fun decodeBody(body: ByteArray): ShareDecodeResult {
        if (body.size <= HEADER_SIZE) return ShareDecodeResult.Failed(ShareError.CORRUPTED)
        val version = body[0].toInt() and 0xFF
        if (version > FORMAT_VERSION) {
            return ShareDecodeResult.Failed(ShareError.UNSUPPORTED_VERSION)
        }
        val stored = ((body[1].toLong() and 0xFF) shl 24) or
            ((body[2].toLong() and 0xFF) shl 16) or
            ((body[3].toLong() and 0xFF) shl 8) or
            (body[4].toLong() and 0xFF)
        val deflated = body.copyOfRange(HEADER_SIZE, body.size)
        if (CRC32().apply { update(deflated) }.value != stored) {
            return ShareDecodeResult.Failed(ShareError.CORRUPTED)
        }
        val json = try {
            inflate(deflated)
        } catch (_: DataFormatException) {
            return ShareDecodeResult.Failed(ShareError.MALFORMED)
        } catch (_: IllegalStateException) {
            return ShareDecodeResult.Failed(ShareError.MALFORMED)
        }
        val def = try {
            MapCodec.decode(json.toString(UTF_8))
        } catch (_: Exception) {
            return ShareDecodeResult.Failed(ShareError.MALFORMED)
        }
        val violations = CustomMapValidator.validate(def)
        if (violations.isNotEmpty()) {
            return ShareDecodeResult.Failed(ShareError.INVALID_MAP, violations)
        }
        return ShareDecodeResult.Ok(def)
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setDictionary(DICTIONARY)
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 4 + 64)
            val buffer = ByteArray(8 * 1024)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream(bytes.size * 4)
            val buffer = ByteArray(8 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && inflater.needsDictionary()) {
                    inflater.setDictionary(DICTIONARY)
                    continue
                }
                if (n == 0 && inflater.needsInput()) error("truncated deflate stream")
                out.write(buffer, 0, n)
                check(out.size() <= MAX_INFLATED_BYTES) { "inflated payload too large" }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /**
     * Preset deflate dictionary: the JSON of a canonical scenario touching every common
     * vocabulary item, so the ~1.3 KB of self-describing boilerplate (the full
     * `RuleConstants` snapshot above all) compresses to back-references instead of
     * riding along in every code. Measured effect: a 9-tile scenario's text code drops
     * from ~1 375 to a few hundred chars, and a SMALL generated map fits a QR.
     *
     * **FROZEN.** Format version 1 codes are compressed against exactly these bytes;
     * regenerating this string from a newer build would silently break every code in
     * the wild. Envelope changes go through [FORMAT_VERSION], never through this text.
     */
    private val DICTIONARY: ByteArray =
        """{"version":1,"id":"00000000-0000-0000-0000-000000000000","name":"name","author":"author","createdAt":0,"modifiedAt":0,"level":{"id":"00000000-0000-0000-0000-000000000000","seed":0,"map":{"version":1,"name":"name","generatorParams":null,"tiles":[{"hex":0,"owner":0,"building":"CAPITAL","flora":null,"deposit":null,"terrain":"LAND"},{"hex":65536,"owner":1,"building":"CAPITAL","flora":null,"deposit":null,"terrain":"LAND"},{"hex":131072,"owner":null,"building":null,"flora":{"type":"tree"},"deposit":null,"terrain":"LAND"},{"hex":196608,"owner":null,"building":null,"flora":{"type":"grave","createdRound":0},"deposit":null,"terrain":"LAND"},{"hex":262144,"owner":null,"building":null,"flora":null,"deposit":"GOLD_VEIN","terrain":"LAND"},{"hex":327680,"owner":null,"building":null,"flora":null,"deposit":"FERTILE","terrain":"LAND"},{"hex":393216,"owner":null,"building":null,"flora":null,"deposit":"FISH_SHOAL","terrain":"SEA"},{"hex":458752,"owner":null,"building":null,"flora":null,"deposit":null,"terrain":"SEA"},{"hex":524288,"owner":2,"building":"FARM","flora":null,"deposit":null,"terrain":"LAND"},{"hex":589824,"owner":3,"building":"TOWER","flora":null,"deposit":null,"terrain":"LAND"}],"capitals":[0,65536]},"seats":[{"type":"player"},{"type":"ai","difficulty":"EASY"},{"type":"ai","difficulty":"NORMAL"},{"type":"ai","difficulty":"HARD"},{"type":"ai","difficulty":"PASSIVE"}],"rules":{"unitCost":[10,20,30,40],"unitUpkeep":[2,6,18,54],"maxTier":4,"soldierMoveRanges":[3,4,5,6],"archerMoveRange":3,"hexIncome":1,"farmCostBase":12,"farmCostStep":2,"farmIncome":4,"towerCost":15,"towerDefense":2,"strongTowerCost":35,"strongTowerDefense":3,"capitalDefense":1,"capitalLootPercent":50,"startingTreasury":12,"startRegionSize":7,"treeClearBonus":3,"treeSpreadPercent":10,"initialTreePercent":8,"fogOfWar":false,"visionRadiusOwned":2,"visionRadiusUnit":3,"visionRadiusBuilding":4,"fertileHexBonus":1,"fertileFarmBonus":2,"goldVeinsPerPlayer":1,"goldVeinBandMin":3,"goldVeinBandMax":6,"goldVeinsNeutralPer150Hexes":1,"fertilePerPlayer":2,"fertileNeutralPercent":3,"mineCost":20,"mineIncome":6,"marketCost":25,"marketNeighborIncome":1,"marketNeighborCap":5,"lumberCampCost":15,"lumberCampTreeIncome":2,"lumberCampTreeCap":4,"watchtowerCost":8,"watchtowerVisionRadius":6,"specialUnitsEnabled":true,"archerCost":14,"archerUpkeep":4,"archerStrength":1,"archerAuraDefense":2,"catapultCost":30,"catapultUpkeep":10,"catapultStrength":2,"catapultMoveRange":2,"navalEnabled":true,"transportCost":15,"transportUpkeep":4,"transportMoveRange":3,"warshipCost":25,"warshipUpkeep":8,"warshipStrength":2,"warshipMoveRange":3,"portCost":20,"portIncome":2,"beachheadGraceTurns":3,"fisheryCost":18,"fisheryShoalIncome":3,"fisheryShoalCap":3,"bridgeCost":15,"fishShoalsPerPlayer":1,"fishShoalBandMin":2,"fishShoalBandMax":6,"fishShoalsNeutralPer150Hexes":1,"diplomacyEnabled":true,"pactMinDurationRounds":2,"pactMaxDurationRounds":10,"pactProposalTtlRounds":1,"pactProposalCooldownRounds":6,"pactBreakPenaltyPercent":25,"disabledBuildings":[],"scriptedEventsEnabled":false},"startingTreasury":[0,0,0,0,0],"startingUnits":[{"seat":0,"hex":0,"unitType":"SOLDIER","tier":1},{"seat":1,"hex":65536,"unitType":"WARSHIP","tier":1}],"objectives":[{"type":"conquerAll"},{"type":"captureHexes","hexes":[0]},{"type":"survive","rounds":0}],"failures":[{"type":"turnLimit","rounds":0}],"parRounds":null,"hints":[],"scripts":[],"aiSolvable":true}}""".toByteArray(UTF_8)
}
