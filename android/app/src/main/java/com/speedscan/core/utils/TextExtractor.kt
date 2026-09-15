package com.speedscan.core.utils

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextExtractor @Inject constructor() {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(imagePath: String): String {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return "No se pudo leer la imagen"
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(inputImage).await()

            if (result.text.isEmpty()) "No se detectó texto en la página" else result.text
        } catch (e: Exception) {
            e.printStackTrace()
            "Error al extraer texto: ${e.message}"
        } finally {
            bitmap.recycle()
        }
    }
}
