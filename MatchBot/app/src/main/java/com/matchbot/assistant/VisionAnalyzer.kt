package com.matchbot.assistant

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight, dependency-free image matcher for the first prototype.
 * It finds visually busy candidate regions, builds rotation-tolerant color
 * histograms at multiple scales, then searches for a mutually similar triple.
 */
class VisionAnalyzer {

    data class MatchResult(
        val points: List<PointF>,
        val confidence: Float,
        val candidateCount: Int
    )

    private data class Candidate(
        val x: Int,
        val y: Int,
        val saliency: Float,
        val descriptor: FloatArray
    )

    fun findTriple(bitmap: Bitmap, threshold: Float): MatchResult? {
        if (bitmap.width < 300 || bitmap.height < 500) return null

        val left = (bitmap.width * 0.03f).toInt()
        val right = (bitmap.width * 0.97f).toInt()
        val top = (bitmap.height * 0.13f).toInt()
        val bottom = (bitmap.height * 0.76f).toInt()

        if (right <= left || bottom <= top) return null

        val candidates = collectCandidates(bitmap, left, top, right, bottom)
        if (candidates.size < 3) return null

        var best: List<Candidate>? = null
        var bestScore = -1f

        for (i in 0 until candidates.size - 2) {
            for (j in i + 1 until candidates.size - 1) {
                val s1 = similarity(candidates[i].descriptor, candidates[j].descriptor)
                if (s1 < threshold) continue

                for (k in j + 1 until candidates.size) {
                    val s2 = similarity(candidates[i].descriptor, candidates[k].descriptor)
                    if (s2 < threshold) continue
                    val s3 = similarity(candidates[j].descriptor, candidates[k].descriptor)
                    if (s3 < threshold) continue

                    val score = (s1 + s2 + s3) / 3f
                    if (score > bestScore) {
                        bestScore = score
                        best = listOf(candidates[i], candidates[j], candidates[k])
                    }
                }
            }
        }

        val selected = best ?: return null
        return MatchResult(
            points = selected.map { PointF(it.x.toFloat(), it.y.toFloat()) },
            confidence = bestScore,
            candidateCount = candidates.size
        )
    }

    private fun collectCandidates(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): List<Candidate> {
        val step = max(18, bitmap.width / 26)
        val radius = max(16, bitmap.width / 32)
        val raw = ArrayList<Triple<Int, Int, Float>>()

        var y = top + radius
        while (y < bottom - radius) {
            var x = left + radius
            while (x < right - radius) {
                val score = localSaliency(bitmap, x, y, radius)
                if (score > 22f) raw.add(Triple(x, y, score))
                x += step
            }
            y += step
        }

        raw.sortByDescending { it.third }

        val selected = ArrayList<Candidate>()
        val minDistance = max(36f, bitmap.width / 16f)
        val maxCandidates = 38

        for ((x, yy, saliency) in raw) {
            if (selected.size >= maxCandidates) break
            val tooClose = selected.any { c -> distance(c.x, c.y, x, yy) < minDistance }
            if (tooClose) continue

            val descriptor = buildDescriptor(bitmap, x, yy)
            selected.add(Candidate(x, yy, saliency, descriptor))
        }

        return selected.sortedByDescending { it.saliency }.take(maxCandidates)
    }

    private fun localSaliency(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        val stride = max(3, radius / 6)
        var sum = 0f
        var sumSq = 0f
        var edges = 0f
        var count = 0

        var y = cy - radius
        while (y <= cy + radius) {
            var x = cx - radius
            while (x <= cx + radius) {
                val c = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                val lum = luminance(c)
                sum += lum
                sumSq += lum * lum

                val x2 = min(bitmap.width - 1, x + stride)
                val y2 = min(bitmap.height - 1, y + stride)
                val cx2 = bitmap.getPixel(x2, y.coerceIn(0, bitmap.height - 1))
                val cy2 = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y2)
                edges += abs(lum - luminance(cx2)) + abs(lum - luminance(cy2))
                count++
                x += stride
            }
            y += stride
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = max(0f, sumSq / count - mean * mean)
        val std = sqrt(variance)
        val edgeMean = edges / (count * 2f)
        return std * 0.65f + edgeMean * 0.35f
    }

    private fun buildDescriptor(bitmap: Bitmap, cx: Int, cy: Int): FloatArray {
        val scales = intArrayOf(
            max(18, bitmap.width / 34),
            max(28, bitmap.width / 24),
            max(40, bitmap.width / 17)
        )
        val out = FloatArray(64 * scales.size + 6)
        var offset = 0

        for (radius in scales) {
            val hist = FloatArray(64)
            val stride = max(2, radius / 10)
            var samples = 0

            var y = cy - radius
            while (y <= cy + radius) {
                var x = cx - radius
                while (x <= cx + radius) {
                    if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                        val c = bitmap.getPixel(x, y)
                        val r = (c shr 16) and 0xff
                        val g = (c shr 8) and 0xff
                        val b = c and 0xff
                        val rb = r ushr 6
                        val gb = g ushr 6
                        val bb = b ushr 6
                        hist[(rb shl 4) or (gb shl 2) or bb] += 1f
                        samples++
                    }
                    x += stride
                }
                y += stride
            }

            if (samples > 0) {
                for (i in hist.indices) out[offset + i] = hist[i] / samples
            }
            offset += 64
        }

        val stats = colorStats(bitmap, cx, cy, max(20, bitmap.width / 28))
        for (i in stats.indices) out[offset + i] = stats[i]

        normalize(out)
        return out
    }

    private fun colorStats(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): FloatArray {
        val inner = radius * 0.45f
        val stride = max(2, radius / 8)
        var ir = 0f; var ig = 0f; var ib = 0f; var ic = 0
        var or = 0f; var og = 0f; var ob = 0f; var oc = 0

        var y = cy - radius
        while (y <= cy + radius) {
            var x = cx - radius
            while (x <= cx + radius) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val dx = (x - cx).toFloat()
                    val dy = (y - cy).toFloat()
                    val d = sqrt(dx * dx + dy * dy)
                    val c = bitmap.getPixel(x, y)
                    val r = ((c shr 16) and 0xff) / 255f
                    val g = ((c shr 8) and 0xff) / 255f
                    val b = (c and 0xff) / 255f
                    if (d <= inner) {
                        ir += r; ig += g; ib += b; ic++
                    } else if (d <= radius) {
                        or += r; og += g; ob += b; oc++
                    }
                }
                x += stride
            }
            y += stride
        }

        return floatArrayOf(
            if (ic > 0) ir / ic else 0f,
            if (ic > 0) ig / ic else 0f,
            if (ic > 0) ib / ic else 0f,
            if (oc > 0) or / oc else 0f,
            if (oc > 0) og / oc else 0f,
            if (oc > 0) ob / oc else 0f
        )
    }

    private fun similarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var aa = 0f
        var bb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        if (aa <= 1e-8f || bb <= 1e-8f) return 0f
        return (dot / sqrt(aa * bb)).coerceIn(0f, 1f)
    }

    private fun normalize(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm <= 1e-8f) return
        for (i in v.indices) v[i] /= norm
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = (x1 - x2).toFloat()
        val dy = (y1 - y2).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
}
