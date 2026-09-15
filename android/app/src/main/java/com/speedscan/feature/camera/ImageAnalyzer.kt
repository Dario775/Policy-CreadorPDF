package com.speedscan.feature.camera

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.abs

fun processImageProxy(imageProxy: ImageProxy, onDetection: (Rect?) -> Unit) {
    try {
        val plane = imageProxy.planes.firstOrNull() ?: run {
            onDetection(null)
            return
        }
        onDetection(
            detectDocumentEdges(
                buffer = plane.buffer,
                width = imageProxy.width,
                height = imageProxy.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                rotation = imageProxy.imageInfo.rotationDegrees
            )
        )
    } catch (_: Exception) {
        onDetection(null)
    } finally {
        imageProxy.close()
    }
}

private fun detectDocumentEdges(
    buffer: java.nio.ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    rotation: Int
): Rect? {
    if (width < 80 || height < 80) return null

    fun luminance(x: Int, y: Int): Int =
        buffer.get(y * rowStride + x * pixelStride).toInt() and 0xff

    val sampleStep = 12
    fun verticalScore(x: Int): Int {
        var total = 0
        var samples = 0
        for (y in height / 10 until height * 9 / 10 step sampleStep) {
            total += abs(luminance(x - 3, y) - luminance(x + 3, y))
            samples++
        }
        return if (samples == 0) 0 else total / samples
    }

    fun horizontalScore(y: Int): Int {
        var total = 0
        var samples = 0
        for (x in width / 10 until width * 9 / 10 step sampleStep) {
            total += abs(luminance(x, y - 3) - luminance(x, y + 3))
            samples++
        }
        return if (samples == 0) 0 else total / samples
    }

    fun strongest(start: Int, end: Int, score: (Int) -> Int): Pair<Int, Int> {
        var bestPosition = start
        var bestScore = 0
        for (position in start until end step 4) {
            val current = score(position)
            if (current > bestScore) {
                bestPosition = position
                bestScore = current
            }
        }
        return bestPosition to bestScore
    }

    val (left, leftScore) = strongest(width / 20, width * 9 / 20, ::verticalScore)
    val (right, rightScore) = strongest(width * 11 / 20, width * 19 / 20, ::verticalScore)
    val (top, topScore) = strongest(height / 20, height * 9 / 20, ::horizontalScore)
    val (bottom, bottomScore) = strongest(height * 11 / 20, height * 19 / 20, ::horizontalScore)

    if (minOf(leftScore, rightScore, topScore, bottomScore) < 10) return null

    fun percent(value: Float): Int = (value * 100f).toInt().coerceIn(0, 100)
    val rawLeft = left.toFloat() / width
    val rawRight = right.toFloat() / width
    val rawTop = top.toFloat() / height
    val rawBottom = bottom.toFloat() / height

    return when (rotation) {
        90 -> Rect(
            percent(1f - rawBottom), percent(rawLeft),
            percent(1f - rawTop), percent(rawRight)
        )
        180 -> Rect(
            percent(1f - rawRight), percent(1f - rawBottom),
            percent(1f - rawLeft), percent(1f - rawTop)
        )
        270 -> Rect(
            percent(rawTop), percent(1f - rawRight),
            percent(rawBottom), percent(1f - rawLeft)
        )
        else -> Rect(
            percent(rawLeft), percent(rawTop),
            percent(rawRight), percent(rawBottom)
        )
    }
}
