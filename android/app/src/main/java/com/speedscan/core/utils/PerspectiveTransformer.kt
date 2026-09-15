package com.speedscan.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF

object PerspectiveTransformer {

    fun transform(
        bitmap: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomLeft: PointF,
        bottomRight: PointF
    ): Bitmap {
        val width = maxOf(
            distance(topLeft, topRight),
            distance(bottomLeft, bottomRight)
        ).toInt()
        
        val height = maxOf(
            distance(topLeft, bottomLeft),
            distance(topRight, bottomRight)
        ).toInt()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val src = floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
        
        val dst = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        
        return result
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return kotlin.math.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
    }
}
