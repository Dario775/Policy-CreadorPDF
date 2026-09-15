package com.speedscan.feature.signature

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

data class PathData(
    val path: List<Offset>,
    val color: Color = Color.Black,
    val strokeWidth: Float = 5f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    onSignatureSaved: (String) -> Unit, // Path to saved signature image
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    var paths by remember { mutableStateOf<List<PathData>>(emptyList()) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var strokeWidth by remember { mutableStateOf(5f) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    
    val colors = listOf(
        Color.Black,
        Color(0xFF1565C0), // Azul
        Color(0xFF2E7D32), // Verde
        Color(0xFFC62828)  // Rojo
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Tu Firma", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancelar")
                    }
                },
                actions = {
                    // Borrar todo
                    IconButton(
                        onClick = { paths = emptyList() },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, "Borrar", tint = if (paths.isNotEmpty()) Color.Red else Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            // Instrucciones
            Text(
                text = "Dibuja tu firma en el recuadro",
                modifier = Modifier.padding(16.dp),
                color = Color.Gray,
                fontSize = 14.sp
            )

            // Canvas para firma
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    currentPath = currentPath + change.position
                                },
                                onDragEnd = {
                                    if (currentPath.isNotEmpty()) {
                                        paths = paths + PathData(currentPath, selectedColor, strokeWidth)
                                        currentPath = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    // Dibujar paths guardados
                    paths.forEach { pathData ->
                        if (pathData.path.size > 1) {
                            for (i in 0 until pathData.path.size - 1) {
                                drawLine(
                                    color = pathData.color,
                                    start = pathData.path[i],
                                    end = pathData.path[i + 1],
                                    strokeWidth = pathData.strokeWidth * density.density,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                    
                    // Dibujar path actual
                    if (currentPath.size > 1) {
                        for (i in 0 until currentPath.size - 1) {
                            drawLine(
                                color = selectedColor,
                                start = currentPath[i],
                                end = currentPath[i + 1],
                                strokeWidth = strokeWidth * density.density,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Línea guía
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp)
                        .fillMaxWidth(0.8f)
                        .height(1.dp)
                        .background(Color.LightGray)
                )
                
                Text(
                    text = "×",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 32.dp, bottom = 52.dp),
                    fontSize = 24.sp,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de color
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color:", color = Color.Gray, modifier = Modifier.padding(end = 12.dp))
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(if (selectedColor == color) 40.dp else 32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedColor == color) 3.dp else 0.dp,
                                color = Color(0xFFC6FF00),
                                shape = CircleShape
                            )
                            .clickable { selectedColor = color }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de grosor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Grosor:", color = Color.Gray, modifier = Modifier.padding(end = 12.dp))
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 2f..12f,
                    modifier = Modifier.width(200.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2962FF),
                        activeTrackColor = Color(0xFF2962FF)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón Guardar
            Button(
                onClick = {
                    if (paths.isNotEmpty()) {
                        val signaturePath = saveSignatureToBitmap(context, paths, density.density)
                        if (signaturePath != null) {
                            onSignatureSaved(signaturePath)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                enabled = paths.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2962FF)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("USAR ESTA FIRMA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

fun saveSignatureToBitmap(context: android.content.Context, paths: List<PathData>, density: Float): String? {
    return try {
        val width = 800
        val height = 400
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Fondo transparente
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Calcular escala (el canvas de compose puede tener diferente tamaño)
        val scaleX = width / 1000f  // Asumimos un canvas de referencia
        val scaleY = height / 500f

        paths.forEach { pathData ->
            paint.color = android.graphics.Color.argb(
                255,
                (pathData.color.red * 255).toInt(),
                (pathData.color.green * 255).toInt(),
                (pathData.color.blue * 255).toInt()
            )
            paint.strokeWidth = pathData.strokeWidth * density
            
            val androidPath = AndroidPath()
            if (pathData.path.isNotEmpty()) {
                androidPath.moveTo(pathData.path[0].x * scaleX, pathData.path[0].y * scaleY)
                for (i in 1 until pathData.path.size) {
                    androidPath.lineTo(pathData.path[i].x * scaleX, pathData.path[i].y * scaleY)
                }
            }
            canvas.drawPath(androidPath, paint)
        }

        // Guardar
        val file = File(context.filesDir, "signature_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
