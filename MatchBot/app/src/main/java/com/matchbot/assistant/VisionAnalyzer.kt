package com.matchbot.assistant

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * MatchBot v0.2 matcher.
 *
 * v0.1 could accidentally treat the black side bars around Match Factory as
 * visually identical objects. v0.2 first detects the actual game viewport,
 * ignores top/bottom UI, then compares compact local colour/texture descriptors.
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

    private data class HorizontalBounds(val left: Int, val right: Int)

    fun findTriple(bitmap: Bitmap, threshold: Float): MatchResult? {
        if (bitmap.width < 300 || bitmap.height < 500) return null

        val bounds = detectGameViewport(bitmap)
        val areaWidth = bounds.right - bounds.left
        if (areaWidth < bitmap.width * 0.30f) return null

        // Keep only the pile itself; exclude goal cards, timer, tray and boosters.
        val top = (bitmap.height * 0.18f).toInt()
        val bottom = (bitmap.height * 0.76f).toInt()

        val candidates = collectCandidates(
            bitmap = bitmap,
            left = bounds.left,
            top = top,
            right = bounds.right,
            bottom = bottom,
            areaWidth = areaWidth
        )

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

    /**
     * Detects the bright, continuous Match Factory game viewport and rejects the
     * black Android/tablet bars around it. This is intentionally based on many
     * vertical samples so the MatchBot overlay cannot create a fake viewport.
     */
    private fun detectGameViewport(bitmap: Bitmap): HorizontalBounds {
        val yStart = (bitmap.height * 0.08f).toInt()
        val yEnd = (bitmap.height * 0.90f).toInt()
        val xStep = max(3, bitmap.width / 260)
        val yStep = max(4, bitmap.height / 150)

        data class Segment(val start: Int, val end: Int)

        val segments = ArrayList<Segment>()
        var segmentStart = -1
        var x = 0

        while (x < bitmap.width) {
            var bright = 0
            var count = 0
            var y = yStart

            while (y < yEnd) {
                if (luminance(bitmap.getPixel(x, y)) > 20f) bright++
                count++
                y += yStep
            }

            val active = count > 0 && bright.toFloat() / count.toFloat() > 0.52f

            if (active && segmentStart < 0) {
                segmentStart = x
            } else if (!active && segmentStart >= 0) {
                segments.add(Segment(segmentStart, x))
                segmentStart = -1
            }

            x += xStep
        }

        if (segmentStart >= 0) segments.add(Segment(segmentStart, bitmap.width))

        val best = segments.maxByOrNull { it.end - it.start }
        if (best != null && best.end - best.start >= bitmap.width * 0.30f) {
            val width = best.end - best.start
            val padding = max(4, (width * 0.015f).toInt())
            return HorizontalBounds(
                left = (best.start + padding).coerceIn(0, bitmap.width - 1),
                right = (best.end - padding).coerceIn(1, bitmap.width)
            )
        }

        return HorizontalBounds(
            left = (bitmap.width * 0.04f).toInt(),
            right = (bitmap.width * 0.96f).toInt()
        )
    }

    private fun collectCandidates(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        areaWidth: Int
    ): List<Candidate> {
        val step = max(22, areaWidth / 18)
        val radius = max(20, areaWidth / 20)
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
        val minDistance = max(80f, areaWidth / 9.5f)
        val maxCandidates = 32

        for ((x, yy, saliency) in raw) {
            if (selected.size >= maxCandidates) break

            val tooClose = selected.any { c ->
                distance(c.x, c.y, x, yy) < minDistance
            }
            if (tooClose) continue

            selected.add(
                Candidate(
                    x = x,
                    y = yy,
                    saliency = saliency,
                    descriptor = buildDescriptor(bitmap, x, yy, areaWidth)
                )
            )
        }

        return selected
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
                val px = x.coerceIn(0, bitmap.width - 1)
                val py = y.coerceIn(0, bitmap.height - 1)
                val lum = luminance(bitmap.getPixel(px, py))

                sum += lum
                sumSq += lum * lum

                val x2 = min(bitmap.width - 1, px + stride)
                val y2 = min(bitmap.height - 1, py + stride)
                edges += abs(lum - luminance(bitmap.getPixel(x2, py)))
                edges += abs(lum - luminance(bitmap.getPixel(px, y2)))
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

    /**
     * Compact descriptor tuned on the user's Match Factory screenshot:
     * three relatively small RGB histograms + local mean/std colour statistics.
     *
     * Keeping the windows compact is important: the v0.1 wide windows included
     * neighbouring toys/background and made unrelated areas appear similar.
     */
    private fun buildDescriptor(
        bitmap: Bitmap,
        cx: Int,
        cy: Int,
        areaWidth: Int
    ): FloatArray {
        val scales = intArrayOf(
            max(16, areaWidth / 40),
            max(22, areaWidth / 30),
            max(30, areaWidth / 22)
        )

        val out = FloatArray(64 * scales.size + 6)
        var offset = 0

        for (radius in scales) {
            val hist = FloatArray(64)
            val stride = max(2, radius / 8)
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
                for (i in hist.indices) {
                    out[offset + i] = hist[i] / samples.toFloat()
                }
            }
            offset += 64
        }

        val statRadius = max(18, areaWidth / 26)
        val stride = max(2, statRadius / 8)

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var sumRR = 0f
        var sumGG = 0f
        var sumBB = 0f
        var count = 0

        var y = cy - statRadius
        while (y <= cy + statRadius) {
            var x = cx - statRadius
            while (x <= cx + statRadius) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val c = bitmap.getPixel(x, y)
                    val r = ((c shr 16) and 0xff) / 255f
                    val g = ((c shr 8) and 0xff) / 255f
                    val b = (c and 0xff) / 255f

                    sumR += r
                    sumG += g
                    sumB += b
                    sumRR += r * r
                    sumGG += g * g
                    sumBB += b * b
                    count++
                }
                x += stride
            }
            y += stride
        }

        if (count > 0) {
            val n = count.toFloat()
            val mr = sumR / n
            val mg = sumG / n
            val mb = sumB / n

            out[offset] = mr
            out[offset + 1] = mg
            out[offset + 2] = mb
            out[offset + 3] = sqrt(max(0f, sumRR / n - mr * mr))
            out[offset + 4] = sqrt(max(0f, sumGG / n - mg * mg))
            out[offset + 5] = sqrt(max(0f, sumBB / n - mb * mb))
        }

        normalize(out)
        return out
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
