package com.m0h31h31.bambucolor.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BambuTagDecoder {

    private val MASTER_KEY = byteArrayOf(
        0x9a.toByte(), 0x75.toByte(), 0x9c.toByte(), 0xf2.toByte(),
        0xc4.toByte(), 0xf7.toByte(), 0xca.toByte(), 0xff.toByte(),
        0x22.toByte(), 0x2c.toByte(), 0xb9.toByte(), 0x76.toByte(),
        0x9b.toByte(), 0x41.toByte(), 0xbc.toByte(), 0x96.toByte()
    )

    private val HKDF_CONTEXT = "RFID-A\u0000".toByteArray(Charsets.UTF_8)
    private const val SECTOR_COUNT = 16
    private const val KEY_BYTES = 6

    fun decode(tag: Tag): TagDataParsed? {
        val uid = tag.id
        if (uid.isEmpty()) return null

        val keys = deriveKeys(uid)
        val mfc = MifareClassic.get(tag) ?: return null

        try {
            mfc.connect()
            val blocks = readAllSectors(mfc, keys)
            return if (blocks.size >= 4) parseBlocks(blocks) else null
        } catch (_: Exception) {
            return null
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    private fun deriveKeys(uid: ByteArray): List<ByteArray> {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(uid, MASTER_KEY, HKDF_CONTEXT))
        val output = ByteArray(SECTOR_COUNT * KEY_BYTES)
        hkdf.generateBytes(output, 0, output.size)
        return (0 until SECTOR_COUNT).map { sector ->
            output.copyOfRange(sector * KEY_BYTES, (sector + 1) * KEY_BYTES)
        }
    }

    private fun readAllSectors(mfc: MifareClassic, keys: List<ByteArray>): MutableMap<Int, ByteArray> {
        val blocks = mutableMapOf<Int, ByteArray>()
        for (sector in 0 until SECTOR_COUNT) {
            val keyA = keys[sector]
            if (!mfc.authenticateSectorWithKeyA(sector, keyA)) {
                continue
            }
            val firstBlock = mfc.sectorToBlock(sector)
            for (blockInSector in 0 until 3) {
                val blockIndex = firstBlock + blockInSector
                if (blockIndex >= 64) break
                try {
                    blocks[blockIndex] = mfc.readBlock(blockIndex)
                } catch (_: Exception) {
                    // skip unreadable blocks
                }
            }
        }
        return blocks
    }

    private fun parseBlocks(blocks: Map<Int, ByteArray>): TagDataParsed {
        val block1 = blocks[1]
        val materialId = block1?.let { bytesToString(it, 8, 8) } ?: ""
        val variantId = block1?.let { bytesToString(it, 0, 8) } ?: ""

        val filamentType = blocks[2]?.let { bytesToString(it, 0, 16) } ?: ""
        val detailedFilamentType = blocks[4]?.let { bytesToString(it, 0, 16) } ?: ""

        var colorArgb = 0xFF888888.toInt()
        var spoolWeightG = 0
        var filamentDiameterMm = 0f
        blocks[5]?.let { b ->
            colorArgb = rgbaToArgb(b, 0)
            spoolWeightG = readUInt16LE(b, 4)
            filamentDiameterMm = readFloat32LE(b, 8)
        }

        var dryingTempC = 0
        var dryingTimeH = 0
        var bedTempC = 0
        var minHotendC = 0
        var maxHotendC = 0
        blocks[6]?.let { b ->
            dryingTempC = readUInt16LE(b, 0)
            dryingTimeH = readUInt16LE(b, 2)
            bedTempC = readUInt16LE(b, 6)
            minHotendC = readUInt16LE(b, 10)
            maxHotendC = readUInt16LE(b, 8)
        }

        var nozzleDiameterMm = 0f
        blocks[8]?.let { b ->
            nozzleDiameterMm = readFloat32LE(b, 12)
        }

        val trayUid = blocks[9]?.let { bytesToString(it, 0, 16) } ?: ""

        var spoolWidthMm = 0f
        blocks[10]?.let { b ->
            spoolWidthMm = readUInt16LE(b, 4) / 100f
        }

        val productionDate = blocks[12]?.let { bytesToString(it, 0, 16) } ?: ""

        var filamentLengthM = 0
        blocks[14]?.let { b ->
            filamentLengthM = readUInt16LE(b, 4)
        }

        var colorCount = 1
        var colorArgbSecond: Int? = null
        blocks[16]?.let { b ->
            val formatId = readUInt16LE(b, 0)
            if (formatId == 2) {
                colorCount = readUInt16LE(b, 2)
                if (colorCount >= 2) {
                    colorArgbSecond = abgrToArgb(b, 4)
                }
            }
        }

        return TagDataParsed(
            uidHex = blocks[0]?.let { bytesToHex(it, 0, 4) } ?: "",
            materialId = materialId,
            variantId = variantId,
            filamentType = filamentType,
            detailedFilamentType = detailedFilamentType,
            colorArgb = colorArgb,
            colorArgbSecond = colorArgbSecond,
            colorCount = colorCount,
            spoolWeightG = spoolWeightG,
            filamentDiameterMm = filamentDiameterMm,
            filamentLengthM = filamentLengthM,
            spoolWidthMm = spoolWidthMm,
            nozzleDiameterMm = nozzleDiameterMm,
            dryingTempC = dryingTempC,
            dryingTimeH = dryingTimeH,
            bedTempC = bedTempC,
            minHotendC = minHotendC,
            maxHotendC = maxHotendC,
            productionDate = productionDate,
            trayUid = trayUid,
        )
    }

    private fun bytesToString(data: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, data.size)
        val sb = StringBuilder()
        for (i in offset until end) {
            val b = data[i]
            if (b == 0.toByte()) break
            if (b in 0x20..0x7E) sb.append(b.toInt().toChar())
        }
        return sb.toString().trim()
    }

    private fun bytesToHex(data: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, data.size)
        return (offset until end).joinToString("") { "%02X".format(data[it]) }
    }

    private fun rgbaToArgb(data: ByteArray, offset: Int): Int {
        val r = (data[offset].toInt() and 0xFF)
        val g = (data[offset + 1].toInt() and 0xFF)
        val b = (data[offset + 2].toInt() and 0xFF)
        val a = (data[offset + 3].toInt() and 0xFF)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun abgrToArgb(data: ByteArray, offset: Int): Int {
        val a = (data[offset].toInt() and 0xFF)
        val b = (data[offset + 1].toInt() and 0xFF)
        val g = (data[offset + 2].toInt() and 0xFF)
        val r = (data[offset + 3].toInt() and 0xFF)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        val lo = (data[offset].toInt() and 0xFF)
        val hi = (data[offset + 1].toInt() and 0xFF)
        return (hi shl 8) or lo
    }

    private fun readFloat32LE(data: ByteArray, offset: Int): Float {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float
    }
}
