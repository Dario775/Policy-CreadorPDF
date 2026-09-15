package com.speedscan.feature.crop

import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedscan.core.utils.PerspectiveTransformer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imagePath: String,
    initialBounds: android.graphics.Rect? = null,
    onCropDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var isA4Locked by remember { mutableStateOf(false) }
    
    // Corners in normalized coordinates (0..1)
    var topLeft by remember { mutableStateOf(initialBounds?.let { PointF(it.left / 100f, it.top / 100f) } ?: PointF(0.05f, 0.05f)) }
    var topRight by remember { mutableStateOf(initialBounds?.let { PointF(it.right / 100f, it.top / 100f) } ?: PointF(0.95f, 0.05f)) }
    var bottomLeft by remember { mutableStateOf(initialBounds?.let { PointF(it.left / 100f, it.bottom / 100f) } ?: PointF(0.05f, 0.95f)) }
    var bottomRight by remember { mutableStateOf(initialBounds?.let { PointF(it.right / 100f, it.bottom / 100f) } ?: PointF(0.95f, 0.95f)) }

    // State for the magnifier
    var activeHandle by remember { mutableStateOf<PointF?>(null) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Ajustar Documento", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) }
                },
                actions = {
                    Button(
                        onClick = {
                            bitmap?.let { b ->
                                val tl = PointF(topLeft.x * b.width, topLeft.y * b.height)
                                val tr = PointF(topRight.x * b.width, topRight.y * b.height)
                                val bl = PointF(bottomLeft.x * b.width, bottomLeft.y * b.height)
                                val br = PointF(bottomRight.x * b.width, bottomRight.y * b.height)
                                
                                val result = PerspectiveTransformer.transform(b, tl, tr, bl, br)
                                val croppedFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(croppedFile).use { out ->
                                    result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                onCropDone(croppedFile.absolutePath)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC6FF00), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("LISTO")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CropToolButton(
                        icon = Icons.Default.Fullscreen,
                        label = "Todo",
                        selected = false,
                        onClick = {
                            topLeft = PointF(0f, 0f); topRight = PointF(1f, 0f)
                            bottomLeft = PointF(0f, 1f); bottomRight = PointF(1f, 1f)
                            isA4Locked = false
                        }
                    )
                    
                    CropToolButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Auto",
                        selected = false,
                        onClick = {
                            initialBounds?.let { 
                                topLeft = PointF(it.left / 100f, it.top / 100f)
                                topRight = PointF(it.right / 100f, it.top / 100f)
                                bottomLeft = PointF(it.left / 100f, it.bottom / 100f)
                                bottomRight = PointF(it.right / 100f, it.bottom / 100f)
                                isA4Locked = false
                            }
                        }
                    )

                    CropToolButton(
                        icon = Icons.Default.AspectRatio,
                        label = "Formato A4",
                        selected = isA4Locked,
                        onClick = {
                            isA4Locked = true
                            // Forzar ratio A4 centrado
                            val ratio = 0.707f 
                            val h = 0.8f
                            val w = h * ratio
                            val x1 = (1f - w) / 2
                            val y1 = (1f - h) / 2
                            topLeft = PointF(x1, y1); topRight = PointF(x1 + w, y1)
                            bottomLeft = PointF(x1, y1 + h); bottomRight = PointF(x1 + w, y1 + h)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imgRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
                    val containerRatio = maxWidth / maxHeight
                    
                    val boxWidth: androidx.compose.ui.unit.Dp
                    val boxHeight: androidx.compose.ui.unit.Dp
                    
                    if (imgRatio > containerRatio) {
                        boxWidth = maxWidth
                        boxHeight = maxWidth / imgRatio
                    } else {
                        boxHeight = maxHeight
                        boxWidth = maxHeight * imgRatio
                    }

                    Box(
                        modifier = Modifier
                            .size(boxWidth, boxHeight)
                            .onGloballyPositioned { viewSize = it.size }
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        if (viewSize.width > 0) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val pTL = Offset(topLeft.x * w, topLeft.y * h)
                                val pTR = Offset(topRight.x * w, topRight.y * h)
                                val pBL = Offset(bottomLeft.x * w, bottomLeft.y * h)
                                val pBR = Offset(bottomRight.x * w, bottomRight.y * h)
                                
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(pTL.x, pTL.y)
                                    lineTo(pTR.x, pTR.y)
                                    lineTo(pBR.x, pBR.y)
                                    lineTo(pBL.x, pBL.y)
                                    close()
                                }
                                
                                drawPath(path, Color(0xFFC6FF00), style = Stroke(width = 2.dp.toPx()))
                            }
                            
                            CornerHandle(topLeft, viewSize, { activeHandle = it }) { delta ->
                                val newX = (topLeft.x + delta.x / viewSize.width).coerceIn(0f, 1f)
                                val newY = (topLeft.y + delta.y / viewSize.height).coerceIn(0f, 1f)
                                val newPoint = PointF(newX, newY)
                                if (isA4Locked) {
                                    topLeft = newPoint
                                    topRight = PointF(topRight.x, newY)
                                    bottomLeft = PointF(newX, bottomLeft.y)
                                } else {
                                    topLeft = newPoint
                                }
                                activeHandle = newPoint // Actualizar lupa
                            }
                            
                            CornerHandle(topRight, viewSize, { activeHandle = it }) { delta ->
                                val newX = (topRight.x + delta.x / viewSize.width).coerceIn(0f, 1f)
                                val newY = (topRight.y + delta.y / viewSize.height).coerceIn(0f, 1f)
                                val newPoint = PointF(newX, newY)
                                if (isA4Locked) {
                                    topRight = newPoint
                                    topLeft = PointF(topLeft.x, newY)
                                    bottomRight = PointF(newX, bottomRight.y)
                                } else {
                                    topRight = newPoint
                                }
                                activeHandle = newPoint // Actualizar lupa
                            }

                            CornerHandle(bottomLeft, viewSize, { activeHandle = it }) { delta ->
                                val newX = (bottomLeft.x + delta.x / viewSize.width).coerceIn(0f, 1f)
                                val newY = (bottomLeft.y + delta.y / viewSize.height).coerceIn(0f, 1f)
                                val newPoint = PointF(newX, newY)
                                if (isA4Locked) {
                                    bottomLeft = newPoint
                                    topLeft = PointF(newX, topLeft.y)
                                    bottomRight = PointF(bottomRight.x, newY)
                                } else {
                                    bottomLeft = newPoint
                                }
                                activeHandle = newPoint // Actualizar lupa
                            }

                            CornerHandle(bottomRight, viewSize, { activeHandle = it }) { delta ->
                                val newX = (bottomRight.x + delta.x / viewSize.width).coerceIn(0f, 1f)
                                val newY = (bottomRight.y + delta.y / viewSize.height).coerceIn(0f, 1f)
                                val newPoint = PointF(newX, newY)
                                if (isA4Locked) {
                                    bottomRight = newPoint
                                    topRight = PointF(newX, topRight.y)
                                    bottomLeft = PointF(bottomLeft.x, newY)
                                } else {
                                    bottomRight = newPoint
                                }
                                activeHandle = newPoint // Actualizar lupa
                            }
                        }
                    }

                    // Lupa Real usando Canvas
                    activeHandle?.let { handle ->
                        val magnifierSize = 140.dp
                        val zoomFactor = 3f
                        
                        Canvas(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .size(magnifierSize)
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFFC6FF00), CircleShape)
                                .background(Color.DarkGray)
                        ) {
                            // Calcular el área del bitmap a mostrar
                            val bmpWidth = bitmap?.width?.toFloat() ?: 1f
                            val bmpHeight = bitmap?.height?.toFloat() ?: 1f
                            
                            // Centro del área a mostrar (en píxeles del bitmap)
                            val centerX = handle.x * bmpWidth
                            val centerY = handle.y * bmpHeight
                            
                            // Tamaño del área visible en el bitmap
                            val viewWidth = size.width / zoomFactor
                            val viewHeight = size.height / zoomFactor
                            
                            // Rectángulo fuente (del bitmap original)
                            val srcLeft = (centerX - viewWidth / 2).coerceIn(0f, bmpWidth - viewWidth)
                            val srcTop = (centerY - viewHeight / 2).coerceIn(0f, bmpHeight - viewHeight)
                            
                            bitmap?.let { bmp ->
                                drawIntoCanvas { canvas ->
                                    val srcRect = android.graphics.Rect(
                                        srcLeft.toInt(),
                                        srcTop.toInt(),
                                        (srcLeft + viewWidth).toInt().coerceAtMost(bmp.width),
                                        (srcTop + viewHeight).toInt().coerceAtMost(bmp.height)
                                    )
                                    val dstRect = android.graphics.Rect(
                                        0, 0, size.width.toInt(), size.height.toInt()
                                    )
                                    canvas.nativeCanvas.drawBitmap(bmp, srcRect, dstRect, null)
                                }
                            }
                            
                            // Dibujar cruceta
                            val crossColor = Color(0xFFC6FF00)
                            val crossWidth = 2.dp.toPx()
                            val crossLength = 20.dp.toPx()
                            val cx = size.width / 2
                            val cy = size.height / 2
                            
                            drawLine(crossColor, Offset(cx, cy - crossLength), Offset(cx, cy + crossLength), strokeWidth = crossWidth)
                            drawLine(crossColor, Offset(cx - crossLength, cy), Offset(cx + crossLength, cy), strokeWidth = crossWidth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CropToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color(0xFFC6FF00) else Color.White,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            color = if (selected) Color(0xFFC6FF00) else Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun CornerHandle(
    point: PointF,
    viewSize: IntSize,
    onDragging: (PointF?) -> Unit,
    onMove: (Offset) -> Unit
) {
    val handleSize = 48.dp // Área táctil más grande
    val visualSize = 20.dp
    
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x * viewSize.width - handleSize.toPx() / 2).roundToInt(),
                    (point.y * viewSize.height - handleSize.toPx() / 2).roundToInt()
                )
            }
            .size(handleSize)
            .pointerInput(viewSize) {
                detectDragGestures(
                    onDragStart = { onDragging(point) },
                    onDragEnd = { onDragging(null) },
                    onDragCancel = { onDragging(null) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Círculo central (Punto de mira)
        Box(
            modifier = Modifier
                .size(visualSize)
                .background(Color.White, CircleShape)
                .border(2.dp, Color(0xFFC6FF00), CircleShape)
        )
    }
}
