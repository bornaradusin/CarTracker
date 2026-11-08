package io.github.bornaradusin.cartracker.ui.share

import java.io.OutputStream

/**
 * Writes a looping animated GIF.
 *
 * All frames share one palette of up to 255 colours: the ones the caller says must be exact (route and
 * text colours) plus the most used colours of a sample picture. Each frame stores only the pixels that
 * changed since the frame before, so a line growing over a still map stays small.
 */
class GifWriter(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    exact: IntArray,
    sample: IntArray,
) {
    private val palette: IntArray = GifPalette.build(exact, sample, MAX_COLORS)
    private val nearest = IntArray(1 shl 18) { -1 }
    private var current = ByteArray(width * height)
    private var previous: ByteArray? = null
    private var pending: Frame? = null
    private val lzw = Lzw()

    private class Frame(val x: Int, val y: Int, val w: Int, val h: Int, val pixels: ByteArray, var delayCs: Int)

    init {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width)
        short(height)
        out.write(0xF7) // global colour table of 256 entries
        out.write(0) // background colour
        out.write(0) // square pixels
        for (i in 0 until 256) {
            val color = if (i < palette.size) palette[i] else 0
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }
        // Loop forever
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    /** Adds a frame of [width] × [height] ARGB pixels, shown for [delayCs] hundredths of a second. */
    fun frame(argb: IntArray, delayCs: Int) {
        require(argb.size == width * height)
        val indexed = current
        for (i in argb.indices) indexed[i] = index(argb[i]).toByte()

        val before = previous
        if (before == null) {
            pending = Frame(0, 0, width, height, indexed.copyOf(), delayCs)
        } else {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    if (indexed[row + x] != before[row + x]) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        maxY = y
                    }
                }
            }
            if (maxX < 0) {
                // Nothing changed: the last frame just stays up longer
                pending?.let { it.delayCs += delayCs }
                return
            }
            val w = maxX - minX + 1
            val h = maxY - minY + 1
            val pixels = ByteArray(w * h)
            for (y in 0 until h) {
                val src = (minY + y) * width + minX
                for (x in 0 until w) {
                    val now = indexed[src + x]
                    pixels[y * w + x] = if (now == before[src + x]) TRANSPARENT.toByte() else now
                }
            }
            flush()
            pending = Frame(minX, minY, w, h, pixels, delayCs)
        }
        // What's on screen now is what the next frame is compared with
        current = before ?: ByteArray(width * height)
        previous = indexed
    }

    /** Writes the last frame, held for an extra [holdCs], and ends the file. */
    fun finish(holdCs: Int = 0) {
        pending?.let { it.delayCs += holdCs }
        flush()
        out.write(0x3B)
        out.flush()
    }

    private fun flush() {
        val f = pending ?: return
        pending = null
        // Graphic control: leave the frame in place, index 255 is see-through
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x05))
        short(f.delayCs.coerceIn(2, 65_535))
        out.write(TRANSPARENT)
        out.write(0)
        // Image
        out.write(0x2C)
        short(f.x)
        short(f.y)
        short(f.w)
        short(f.h)
        out.write(0)
        lzw.encode(f.pixels, out)
    }

    private fun index(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val key = ((r shr 2) shl 12) or ((g shr 2) shl 6) or (b shr 2)
        val known = nearest[key]
        if (known >= 0) return known
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in palette.indices) {
            val c = palette[i]
            val dr = r - ((c shr 16) and 0xFF)
            val dg = g - ((c shr 8) and 0xFF)
            val db = b - (c and 0xFF)
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        nearest[key] = best
        return best
    }

    private fun short(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    companion object {
        const val MAX_COLORS = 255
        const val TRANSPARENT = 255
    }
}

/** Picks the palette: the exact colours first, then the sample's colours by median cut. */
internal object GifPalette {

    fun build(exact: IntArray, sample: IntArray, max: Int): IntArray {
        val result = LinkedHashSet<Int>()
        exact.forEach { if (result.size < max) result += it and 0xFFFFFF }
        val room = max - result.size
        if (room > 0) cut(sample, room).forEach { if (result.size < max) result += it }
        return result.toIntArray()
    }

    /** Median cut on 5 bits per channel: splits the busiest, widest box until there are [count] boxes. */
    private fun cut(sample: IntArray, count: Int): List<Int> {
        val histogram = IntArray(1 shl 15)
        for (c in sample) {
            if ((c ushr 24) < 128) continue
            histogram[((c shr 9) and 0x7C00) or ((c shr 6) and 0x03E0) or ((c shr 3) and 0x001F)]++
        }
        val colors = histogram.indices.filter { histogram[it] > 0 }.toIntArray()
        if (colors.isEmpty()) return emptyList()

        class Box(val items: IntArray) {
            val weight = items.sumOf { histogram[it].toLong() }
            val channel: Int
            val range: Int

            init {
                var best = 0
                var bestRange = -1
                for (ch in 0..2) {
                    var lo = 31
                    var hi = 0
                    for (c in items) {
                        val v = (c shr (10 - ch * 5)) and 31
                        if (v < lo) lo = v
                        if (v > hi) hi = v
                    }
                    if (hi - lo > bestRange) {
                        bestRange = hi - lo
                        best = ch
                    }
                }
                channel = best
                range = bestRange
            }

            fun score(): Double = if (items.size < 2 || range == 0) -1.0 else range.toDouble() * Math.sqrt(weight.toDouble())

            fun split(): Pair<Box, Box> {
                val shift = 10 - channel * 5
                val sorted = items.sortedBy { (it shr shift) and 31 }.toIntArray()
                var running = 0L
                var at = 1
                for (i in sorted.indices) {
                    running += histogram[sorted[i]]
                    if (running * 2 >= weight) {
                        at = (i + 1).coerceIn(1, sorted.size - 1)
                        break
                    }
                }
                return Box(sorted.copyOfRange(0, at)) to Box(sorted.copyOfRange(at, sorted.size))
            }

            fun average(): Int {
                var r = 0L
                var g = 0L
                var b = 0L
                for (c in items) {
                    val n = histogram[c]
                    r += ((c shr 10) and 31) * n
                    g += ((c shr 5) and 31) * n
                    b += (c and 31) * n
                }
                fun channel(sum: Long) = ((sum.toDouble() / weight) * 255 / 31).toInt().coerceIn(0, 255)
                return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            }
        }

        val boxes = mutableListOf(Box(colors))
        while (boxes.size < count) {
            val widest = boxes.maxByOrNull { it.score() } ?: break
            if (widest.score() < 0) break
            boxes.remove(widest)
            val (a, b) = widest.split()
            boxes += a
            boxes += b
        }
        return boxes.sortedByDescending { it.weight }.map { it.average() }
    }
}

/** GIF's LZW compression, 8-bit codes, with the usual hashed string table. */
private class Lzw {
    private val keys = IntArray(HSIZE)
    private val codes = IntArray(HSIZE)
    private val block = ByteArray(255)
    private var blockSize = 0
    private var bits = 0L
    private var bitCount = 0
    private var codeSize = 0

    fun encode(pixels: ByteArray, out: OutputStream) {
        out.write(MIN_CODE_SIZE)
        blockSize = 0
        bits = 0L
        bitCount = 0
        codeSize = MIN_CODE_SIZE + 1
        keys.fill(-1)
        var next = EOI + 1

        emit(CLEAR, out)
        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val k = pixels[i].toInt() and 0xFF
            val key = (k shl 12) or prefix
            var h = (k shl 4) xor prefix
            var found = -1
            if (keys[h] == key) {
                found = codes[h]
            } else if (keys[h] >= 0) {
                val step = if (h == 0) 1 else HSIZE - h
                while (true) {
                    h -= step
                    if (h < 0) h += HSIZE
                    if (keys[h] == key) {
                        found = codes[h]
                        break
                    }
                    if (keys[h] < 0) break
                }
            }
            if (found >= 0) {
                prefix = found
                continue
            }
            emit(prefix, out)
            if (next < MAX_CODES) {
                codes[h] = next++
                keys[h] = key
                // The reader widens its codes one code later than the table grows
                if (next > (1 shl codeSize) && codeSize < MAX_BITS) codeSize++
            } else {
                emit(CLEAR, out)
                keys.fill(-1)
                next = EOI + 1
                codeSize = MIN_CODE_SIZE + 1
            }
            prefix = k
        }
        emit(prefix, out)
        emit(EOI, out)
        if (bitCount > 0) byte((bits and 0xFF).toInt(), out)
        if (blockSize > 0) writeBlock(out)
        out.write(0)
    }

    private fun emit(code: Int, out: OutputStream) {
        bits = bits or (code.toLong() shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            byte((bits and 0xFF).toInt(), out)
            bits = bits ushr 8
            bitCount -= 8
        }
    }

    private fun byte(value: Int, out: OutputStream) {
        block[blockSize++] = value.toByte()
        if (blockSize == 255) writeBlock(out)
    }

    private fun writeBlock(out: OutputStream) {
        out.write(blockSize)
        out.write(block, 0, blockSize)
        blockSize = 0
    }

    companion object {
        const val HSIZE = 5003
        const val MIN_CODE_SIZE = 8
        const val CLEAR = 256
        const val EOI = 257
        const val MAX_BITS = 12
        const val MAX_CODES = 4096
    }
}
