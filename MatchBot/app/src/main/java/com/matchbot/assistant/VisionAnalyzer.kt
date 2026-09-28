package com.matchbot.assistant

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * MatchBot v0.2.1
 *
 * Tuned for Match Factory on tablets/phones:
 * - detects the real game viewport and ignores black side bars
 * - analyzes only the central object pile, not goals/tray/boosters
 * - uses a denser candidate grid so small/overlapping objects are not skipped
 * - compares multi-scale colour + radial texture descriptors
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

        val viewport = detectGameViewport(bitmap)
        val viewportWidth = viewport.right - viewport.left
        if (viewportWidth < bitmap.width * 0.30f) return null

        val boardLeft = (viewport.left + viewportWidth * 0.17f).toInt()
        val boardRight = (viewport.right - viewportWidth * 0.17f).toInt()
        val boardTop = (bitmap.height * 0.17f).toInt()
        val boardBottom = (bitmap.height * 0.77f).toInt()

        val candidates = collectCandidates(
            bitmap = bitmap,
            left = boardLeft,
            top = boardTop,
            right = boardRight,
            bottom = boardBottom,
            viewportWidth = viewportWidth
        )

        if (candidates.size < 3) return null

        val n = candidates.size
        val sim = Array(n) { FloatArray(n) }

        for (i in 0 until n) {
            sim[i][i] = 1f
            for (j in i + 1 until n) {
                val s = similarity(
                    candidates[i].descriptor,
                    candidates[j].descriptor
                )
                sim[i][j] = s
                sim[j][i] = s
            }
        }

        var bestIndices: IntArray? = null
        var bestScore = -1f

        for (i in 0 until n - 2) {
            for (j in i + 1 until n - 1) {
                val s1 = sim[i][j]
                if (s1 < threshold) continue

                for (k in j + 1 until n) {
                    val s2 = sim[i][k]
                    val s3 = sim[j][k]
                    val minPair = min(s1, min(s2, s3))
                    if (minPair < threshold) continue

                    val average = (s1 + s2 + s3) / 3f
                    val score = average * 0.72f + minPair * 0.28f

                    if (score > bestScore) {
                        bestScore = score
                        bestIndices = intArrayOf(i, j, k)
                    }
                }
            }
        }

        val ids = bestIndices ?: return null

        return MatchResult(
            points = ids.map { idx ->
                PointF(
                    candidates[idx].x.toFloat(),
                    candidates[idx].y.toFloat()
                )
            },
            confidence = bestScore,
            candidateCount = candidates.size
        )
    }

    private fun detectGameViewport(bitmap: Bitmap): HorizontalBounds {
        val yStart = (bitmap.height * 0.08f).toInt()
        val yEnd = (bitmap.height * 0.92f).toInt()
        val xStep = max(3, bitmap.width / 280)
        val yStep = max(4, bitmap.height / 160)

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

            val active =
                count > 0 &&
                    bright.toFloat() / count.toFloat() > 0.46f

            if (active && segmentStart < 0) {
                segmentStart = x
            } else if (!active && segmentStart >= 0) {
                segments.add(Segment(segmentStart, x))
                segmentStart = -1
            }

            x += xStep
        }

        if (segmentStart >= 0) {
            segments.add(Segment(segmentStart, bitmap.width))
        }

        val best = segments.maxByOrNull { it.end - it.start }

        if (
            best != null &&
            best.end - best.start >= bitmap.width * 0.30f
        ) {
            val width = best.end - best.start
            val pad = max(3, (width * 0.008f).toInt())

            return HorizontalBounds(
                left =
                    (best.start + pad).coerceIn(
                        0,
                        bitmap.width - 1
                    ),
                right =
                    (best.end - pad).coerceIn(
                        1,
                        bitmap.width
                    )
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
        viewportWidth: Int
    ): List<Candidate> {
        val step = max(18, viewportWidth / 34)
        val saliencyRadius = max(18, viewportWidth / 34)
        val raw = ArrayList<Triple<Int, Int, Float>>()

        var y = top + saliencyRadius

        while (y < bottom - saliencyRadius) {
            var x = left + saliencyRadius

            while (x < right - saliencyRadius) {
                val score =
                    localSaliency(
                        bitmap,
                        x,
                        y,
                        saliencyRadius
                    )

                if (score > 20f) {
                    raw.add(Triple(x, y, score))
                }

                x += step
            }

            y += step
        }

        raw.sortByDescending { it.third }

        val selected = ArrayList<Candidate>()
        val minDistance = max(34f, viewportWidth / 19f)
        val maxCandidates = 58

        for ((x, y, saliency) in raw) {
            if (selected.size >= maxCandidates) break

            if (
                selected.any { c ->
                    distance(c.x, c.y, x, y) < minDistance
                }
            ) {
                continue
            }

            selected.add(
                Candidate(
                    x = x,
                    y = y,
                    saliency = saliency,
                    descriptor =
                        buildDescriptor(
                            bitmap,
                            x,
                            y,
                            viewportWidth
                        )
                )
            )
        }

        return selected
    }

    private fun localSaliency(
        bitmap: Bitmap,
        cx: Int,
        cy: Int,
        radius: Int
    ): Float {
        val stride = max(3, radius / 6)
        var sumLum = 0f
        var sumLumSq = 0f
        var edgeTotal = 0f
        var saturationTotal = 0f
        var count = 0

        var y = cy - radius

        while (y <= cy + radius) {
            var x = cx - radius

            while (x <= cx + radius) {
                val px =
                    x.coerceIn(
                        0,
                        bitmap.width - 1
                    )
                val py =
                    y.coerceIn(
                        0,
                        bitmap.height - 1
                    )

                val color = bitmap.getPixel(px, py)
                val lum = luminance(color)

                sumLum += lum
                sumLumSq += lum * lum
                saturationTotal += saturation(color)

                val x2 =
                    min(
                        bitmap.width - 1,
                        px + stride
                    )
                val y2 =
                    min(
                        bitmap.height - 1,
                        py + stride
                    )

                edgeTotal +=
                    abs(
                        lum -
                            luminance(
                                bitmap.getPixel(
                                    x2,
                                    py
                                )
                            )
                    )

                edgeTotal +=
                    abs(
                        lum -
                            luminance(
                                bitmap.getPixel(
                                    px,
                                    y2
                                )
                            )
                    )

                count++
                x += stride
            }

            y += stride
        }

        if (count == 0) return 0f

        val mean = sumLum / count
        val variance =
            max(
                0f,
                sumLumSq / count -
                    mean * mean
            )
        val std = sqrt(variance)
        val edgeMean =
            edgeTotal /
                (count * 2f)
        val satMean =
            saturationTotal /
                count

        return
            std * 0.52f +
                edgeMean * 0.33f +
                satMean * 18f
    }

    private fun buildDescriptor(
        bitmap: Bitmap,
        cx: Int,
        cy: Int,
        viewportWidth: Int
    ): FloatArray {
        val scales =
            intArrayOf(
                max(15, viewportWidth / 48),
                max(22, viewportWidth / 35),
                max(30, viewportWidth / 26)
            )

        val out =
            FloatArray(
                64 * scales.size + 24
            )

        var offset = 0

        for (radius in scales) {
            val hist = FloatArray(64)
            val stride = max(2, radius / 8)
            var samples = 0

            var y = cy - radius

            while (y <= cy + radius) {
                var x = cx - radius

                while (x <= cx + radius) {
                    if (
                        x in 0 until bitmap.width &&
                        y in 0 until bitmap.height
                    ) {
                        val c =
                            bitmap.getPixel(
                                x,
                                y
                            )

                        val r =
                            (c shr 16) and 0xff
                        val g =
                            (c shr 8) and 0xff
                        val b =
                            c and 0xff

                        val rb = r ushr 6
                        val gb = g ushr 6
                        val bb = b ushr 6

                        hist[
                            (rb shl 4) or
                                (gb shl 2) or
                                bb
                        ] += 1f

                        samples++
                    }

                    x += stride
                }

                y += stride
            }

            if (samples > 0) {
                for (i in hist.indices) {
                    out[offset + i] =
                        hist[i] /
                            samples.toFloat()
                }
            }

            offset += 64
        }

        val radialRadius =
            max(
                28,
                viewportWidth / 27
            )
        val radialStride =
            max(
                2,
                radialRadius / 10
            )

        val ringCount = IntArray(3)
        val sumR = FloatArray(3)
        val sumG = FloatArray(3)
        val sumB = FloatArray(3)
        val sumSat = FloatArray(3)
        val sumLum = FloatArray(3)
        val sumLumSq = FloatArray(3)
        val sumEdge = FloatArray(3)
        val darkCount = IntArray(3)

        var y = cy - radialRadius

        while (y <= cy + radialRadius) {
            var x = cx - radialRadius

            while (x <= cx + radialRadius) {
                if (
                    x in 0 until bitmap.width &&
                    y in 0 until bitmap.height
                ) {
                    val dx =
                        (x - cx).toFloat()
                    val dy =
                        (y - cy).toFloat()
                    val d =
                        sqrt(
                            dx * dx +
                                dy * dy
                        )

                    if (d <= radialRadius) {
                        val norm =
                            d /
                                radialRadius

                        val ring =
                            when {
                                norm < 0.34f -> 0
                                norm < 0.68f -> 1
                                else -> 2
                            }

                        val c =
                            bitmap.getPixel(
                                x,
                                y
                            )

                        val r =
                            ((c shr 16) and 0xff) /
                                255f
                        val g =
                            ((c shr 8) and 0xff) /
                                255f
                        val b =
                            (c and 0xff) /
                                255f
                        val lum =
                            luminance(c) /
                                255f

                        sumR[ring] += r
                        sumG[ring] += g
                        sumB[ring] += b
                        sumSat[ring] +=
                            saturation(c)
                        sumLum[ring] += lum
                        sumLumSq[ring] +=
                            lum * lum

                        if (lum < 0.20f) {
                            darkCount[ring]++
                        }

                        val x2 =
                            min(
                                bitmap.width - 1,
                                x + radialStride
                            )
                        val y2 =
                            min(
                                bitmap.height - 1,
                                y + radialStride
                            )

                        val edge =
                            abs(
                                luminance(c) -
                                    luminance(
                                        bitmap.getPixel(
                                            x2,
                                            y
                                        )
                                    )
                            ) +
                                abs(
                                    luminance(c) -
                                        luminance(
                                            bitmap.getPixel(
                                                x,
                                                y2
                                            )
                                        )
                                )

                        sumEdge[ring] +=
                            edge / 510f
                        ringCount[ring]++
                    }
                }

                x += radialStride
            }

            y += radialStride
        }

        for (ring in 0..2) {
            val n =
                ringCount[ring]
                    .coerceAtLeast(1)
                    .toFloat()

            val meanLum =
                sumLum[ring] / n

            val base =
                offset +
                    ring * 8

            out[base] =
                sumR[ring] / n
            out[base + 1] =
                sumG[ring] / n
            out[base + 2] =
                sumB[ring] / n
            out[base + 3] =
                sumSat[ring] / n
            out[base + 4] =
                meanLum
            out[base + 5] =
                sqrt(
                    max(
                        0f,
                        sumLumSq[ring] /
                            n -
                            meanLum *
                                meanLum
                    )
                )
            out[base + 6] =
                sumEdge[ring] / n
            out[base + 7] =
                darkCount[ring] / n
        }

        normalize(out)
        return out
    }

    private fun similarity(
        a: FloatArray,
        b: FloatArray
    ): Float {
        var dot = 0f
        var aa = 0f
        var bb = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }

        if (
            aa <= 1e-8f ||
            bb <= 1e-8f
        ) {
            return 0f
        }

        return
            (
                dot /
                    sqrt(aa * bb)
                ).coerceIn(
                0f,
                1f
            )
    }

    private fun normalize(v: FloatArray) {
        var sum = 0f

        for (value in v) {
            sum +=
                value * value
        }

        val norm = sqrt(sum)

        if (norm <= 1e-8f) return

        for (i in v.indices) {
            v[i] /= norm
        }
    }

    private fun luminance(color: Int): Float {
        val r =
            (color shr 16) and 0xff
        val g =
            (color shr 8) and 0xff
        val b =
            color and 0xff

        return
            0.2126f * r +
                0.7152f * g +
                0.0722f * b
    }

    private fun saturation(color: Int): Float {
        val r =
            ((color shr 16) and 0xff) /
                255f
        val g =
            ((color shr 8) and 0xff) /
                255f
        val b =
            (color and 0xff) /
                255f

        val hi =
            max(
                r,
                max(g, b)
            )

        val lo =
            min(
                r,
                min(g, b)
            )

        return
            if (hi <= 1e-6f) {
                0f
            } else {
                (hi - lo) / hi
            }
    }

    private fun distance(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int
    ): Float {
        val dx =
            (x1 - x2).toFloat()
        val dy =
            (y1 - y2).toFloat()

        return
            sqrt(
                dx * dx +
                    dy * dy
            )
    }
}
