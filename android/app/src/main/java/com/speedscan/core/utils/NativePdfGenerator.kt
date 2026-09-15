package com.speedscan.core.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.speedscan.core.domain.model.FilterType
import com.speedscan.core.domain.model.SignatureHorizontal
import com.speedscan.core.domain.model.SignaturePlacement
import com.speedscan.core.domain.model.SignatureVertical
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PdfPageSize(val width: Int, val height: Int) {
    A4(1190, 1684),
    LETTER(1224, 1584),
    LEGAL(1224, 2016)
}

class NativePdfGenerator(private val context: Context) {

    suspend fun generatePdf(
        imagePaths: List<String>, 
        outputName: String,
        category: String = "General",
        filters: Map<String, FilterType> = emptyMap(),
        signatures: Map<String, SignaturePlacement> = emptyMap(),
        pageSize: PdfPageSize = PdfPageSize.A4,
        quality: Int = 80
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        
        // ... (rest of the logic remains the same) ...
        
        imagePaths.forEachIndexed { index, path ->
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeFile(path, options) ?: return@forEachIndexed
            
            val filterType = filters[path] ?: FilterType.ORIGINAL
            var processedBitmap = if (filterType != FilterType.ORIGINAL) {
                applyFilter(originalBitmap, filterType)
            } else {
                originalBitmap
            }

            // Aplicar compresión JPEG para reducir peso si quality < 100
            if (quality < 100) {
                val stream = java.io.ByteArrayOutputStream()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val byteArray = stream.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                
                if (processedBitmap != originalBitmap) processedBitmap.recycle()
                processedBitmap = compressedBitmap
            }

            val pageWidth = pageSize.width
            val pageHeight = pageSize.height
            
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            }

            val scale = minOf(pageWidth.toFloat() / processedBitmap.width, pageHeight.toFloat() / processedBitmap.height)
            val scaledWidth = (processedBitmap.width * scale).toInt()
            val scaledHeight = (processedBitmap.height * scale).toInt()
            
            val left = (pageWidth - scaledWidth) / 2f
            val top = (pageHeight - scaledHeight) / 2f
            
            val scaledBitmap = Bitmap.createScaledBitmap(processedBitmap, scaledWidth, scaledHeight, true)
            canvas.drawBitmap(scaledBitmap, left, top, paint)

            signatures[path]?.let { signature ->
                val signatureBitmap = BitmapFactory.decodeFile(signature.imagePath)
                if (signatureBitmap != null) {
                    val signatureWidth = (pageWidth * signature.widthRatio.coerceIn(0.2f, 0.65f)).toInt()
                    val signatureHeight = (signatureBitmap.height * (signatureWidth.toFloat() / signatureBitmap.width)).toInt()
                    val marginX = pageWidth * 0.08f
                    val marginY = pageHeight * 0.08f
                    val signatureLeft = when (signature.horizontal) {
                        SignatureHorizontal.LEFT -> marginX
                        SignatureHorizontal.CENTER -> (pageWidth - signatureWidth) / 2f
                        SignatureHorizontal.RIGHT -> pageWidth - signatureWidth - marginX
                    }
                    val signatureTop = when (signature.vertical) {
                        SignatureVertical.TOP -> marginY
                        SignatureVertical.MIDDLE -> (pageHeight - signatureHeight) / 2f
                        SignatureVertical.BOTTOM -> pageHeight - signatureHeight - marginY
                    }
                    val scaledSignature = Bitmap.createScaledBitmap(signatureBitmap, signatureWidth, signatureHeight, true)

                    canvas.drawBitmap(scaledSignature, signatureLeft, signatureTop, paint)

                    signatureBitmap.recycle()
                    scaledSignature.recycle()
                }
            }
            
            pdfDocument.finishPage(page)
            
            if (processedBitmap != originalBitmap) processedBitmap.recycle()
            originalBitmap.recycle()
            scaledBitmap.recycle()
        }

        val categoryDir = File(context.filesDir, category)
        if (!categoryDir.exists()) categoryDir.mkdirs()

        val file = File(categoryDir, "$outputName.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        
        return@withContext file
    }

    private fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix()
        when (filterType) {
            FilterType.GRAYSCALE -> {
                colorMatrix.setSaturation(0f)
            }
            FilterType.BLACK_WHITE -> {
                return applyBlackWhiteFilter(bitmap)
            }
            FilterType.DOCUMENT -> {
                // Blanco y negro + Alto Contraste
                colorMatrix.setSaturation(0f)
                val contrast = 1.5f
                val brightness = 10f
                val matrix = floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
                colorMatrix.set(matrix)
            }
            else -> {}
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyBlackWhiteFilter(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            val luminance = (0.299f * red + 0.587f * green + 0.114f * blue).toInt()
            val value = if (luminance > 145) 255 else 0
            pixels[i] = Color.argb(Color.alpha(color), value, value, value)
        }

        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
}
