package com.speedscan.feature.camera

import android.content.Context
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    currentImages: List<String>,
    onPhotoCaptured: (String, android.graphics.Rect?) -> Unit,
    onGalleryClick: () -> Unit,
    onReviewClicked: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // UI States
    var detectedObjectBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var showFlashEffect by remember { mutableStateOf(false) }

    // Executor para análisis de imágenes (Evitar Memory Leaks)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Flash animation
    val flashAlpha by animateFloatAsState(targetValue = if (showFlashEffect) 1f else 0f, label = "flash")

    if (showFlashEffect) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            showFlashEffect = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Visor de Cámara
        // 1. Visor de Cámara
        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        
        LaunchedEffect(Unit) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val imageCaptureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                
                imageCapture = imageCaptureBuilder.build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy) { rect -> detectedObjectBounds = rect }
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis
                    )
                    cameraControl = camera.cameraControl
                } catch (e: Exception) { 
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        // 2. Guía Estática de Encuadre (UX Profesional)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val cornerLength = 40.dp.toPx()
            val color = if (detectedObjectBounds != null) Color(0xFFC6FF00) else Color.White.copy(alpha = 0.5f)
            
            // Marco guía sutil (Esquinas estilo escáner)
            val left = size.width * 0.1f
            val top = size.height * 0.2f
            val right = size.width * 0.9f
            val bottom = size.height * 0.8f
            
            // Dibujar solo las esquinas para no tapar la visión pero dar guía
            // Superior Izquierda
            drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
            drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
            
            // Superior Derecha
            drawLine(color, Offset(right, top), Offset(right - cornerLength, top), strokeWidth)
            drawLine(color, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)
            
            // Inferior Izquierda
            drawLine(color, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
            drawLine(color, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth)
            
            // Inferior Derecha
            drawLine(color, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
            drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)
        }

        // 3. Controles Superiores (Flash)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { isFlashOn = !isFlashOn }) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
            
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White
                )
            }
        }

        // 4. Barra Inferior "Pro"
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(bottom = 40.dp, top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (detectedObjectBounds != null) "Documento Detectado" else "Busca un documento...",
                color = Color.White,
                fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Botón Galería / Preview
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentImages.isEmpty()) Color(0xFF2962FF) else Color.DarkGray)
                        .clickable { 
                            if (currentImages.isNotEmpty()) onReviewClicked() 
                            else onGalleryClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (currentImages.isNotEmpty()) {
                        AsyncImage(
                            model = File(currentImages.last()),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Badge contador
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Red, CircleShape)
                                .padding(4.dp)
                            ) {
                             Text(text = "${currentImages.size}", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Mostrar icono de galería cuando no hay imágenes
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Importar desde Galería",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Botón Disparador Gigante
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(4.dp)
                        .border(4.dp, Color.Black, CircleShape)
                        .clickable {
                            showFlashEffect = true // Gatillo visual
                            capturePhoto(context, imageCapture) { path ->
                                onPhotoCaptured(path, detectedObjectBounds)
                            }
                        }
                )

                // Botón "Listo" (Flecha)
                if (currentImages.isNotEmpty()) {
                    IconButton(
                        onClick = onReviewClicked,
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFF2962FF), CircleShape)
                    ) {
                        Icon(Icons.Default.NavigateNext, contentDescription = "Revisar", tint = Color.White)
                    }
                } else {
                     Spacer(modifier = Modifier.size(50.dp))
                }
            }
        }
        
        // Efecto Flash Blanco (Pantallazo)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha))
        )
    }
}

fun capturePhoto(context: Context, imageCapture: ImageCapture?, onSaved: (String) -> Unit) {
    val file = File(context.filesDir, "page_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    
    imageCapture?.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
                onSaved(file.absolutePath)
            }
            override fun onError(exc: ImageCaptureException) { 
                Toast.makeText(context, "Error al capturar: ${exc.message}", Toast.LENGTH_LONG).show()
                exc.printStackTrace()
            }
        }
    )
}
