package com.speedscan.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun importPdf(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext emptyList()

        descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                buildList {
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val bitmap = renderPage(page)
                            val file = File(context.cacheDir, "pdf_page_${System.currentTimeMillis()}_$index.jpg")
                            FileOutputStream(file).use { output ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                            }
                            bitmap.recycle()
                            add(file.absolutePath)
                        }
                    }
                }
            }
        }
    }

    private fun renderPage(page: PdfRenderer.Page): Bitmap {
        val scale = 2
        val bitmap = Bitmap.createBitmap(
            page.width * scale,
            page.height * scale,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
