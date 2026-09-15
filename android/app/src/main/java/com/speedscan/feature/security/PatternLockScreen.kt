package com.speedscan.feature.security

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternLockScreen(
    onUnlock: () -> Unit,
    onVerifyPattern: suspend (String) -> Boolean,
    onBiometricClick: () -> Unit,
    showBiometric: Boolean = false
) {
    var pattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isDrawingComplete by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Verificar solo cuando el usuario termina de dibujar
    LaunchedEffect(isDrawingComplete) {
        if (isDrawingComplete && pattern.size >= 4) {
            isVerifying = true
            val patternString = pattern.joinToString("")
            if (onVerifyPattern(patternString)) {
                onUnlock()
            } else {
                isError = true
                errorMessage = "Patrón incorrecto"
                delay(1000)
                pattern = emptyList()
                isError = false
                errorMessage = ""
            }
            isVerifying = false
            isDrawingComplete = false
        } else if (isDrawingComplete && pattern.size < 4) {
            // Patrón muy corto
            isError = true
            errorMessage = "Conecta al menos 4 puntos"
            delay(1000)
            pattern = emptyList()
            isError = false
            errorMessage = ""
            isDrawingComplete = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF2962FF),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Creador PDF",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Dibuja tu patrón para continuar",
                color = Color.Gray,
                fontSize = 14.sp
            )

            AnimatedVisibility(
                visible = errorMessage.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Patrón Grid
            PatternGrid(
                pattern = pattern,
                onPatternChange = { if (!isVerifying) pattern = it },
                onDrawingComplete = { isDrawingComplete = true },
                isError = isError,
                modifier = Modifier.size(280.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (showBiometric) {
                OutlinedButton(
                    onClick = onBiometricClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2962FF)
                    )
                ) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usar huella digital")
                }
            }
        }
    }
}

@Composable
fun PatternGrid(
    pattern: List<Int>,
    onPatternChange: (List<Int>) -> Unit,
    onDrawingComplete: () -> Unit = {},
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    var currentPosition by remember { mutableStateOf<Offset?>(null) }
    var currentPatternState by remember { mutableStateOf<List<Int>>(emptyList()) }
    
    // Sincronizar con el patrón externo
    LaunchedEffect(pattern) {
        currentPatternState = pattern
    }
    
    val dotColor = if (isError) Color.Red else Color(0xFF2962FF)
    val lineColor = if (isError) Color.Red.copy(alpha = 0.5f) else Color(0xFF2962FF).copy(alpha = 0.5f)

    BoxWithConstraints(modifier = modifier) {
        val gridSize = constraints.maxWidth.toFloat()
        val cellSize = gridSize / 3
        val dotRadius = cellSize / 6
        val hitThreshold = cellSize / 2
        
        // Calcular posiciones de los puntos FUERA del Canvas
        val dotPositions = remember(gridSize) {
            val positions = mutableListOf<Offset>()
            for (row in 0..2) {
                for (col in 0..2) {
                    val x = cellSize / 2 + col * cellSize
                    val y = cellSize / 2 + row * cellSize
                    positions.add(Offset(x, y))
                }
            }
            positions
        }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dotPositions) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPosition = offset
                            val hitDot = findHitDot(offset, dotPositions, hitThreshold)
                            if (hitDot != null) {
                                currentPatternState = listOf(hitDot)
                                onPatternChange(listOf(hitDot))
                            }
                        },
                        onDrag = { change, _ ->
                            currentPosition = change.position
                            val hitDot = findHitDot(change.position, dotPositions, hitThreshold)
                            if (hitDot != null && hitDot !in currentPatternState) {
                                val newPattern = currentPatternState + hitDot
                                currentPatternState = newPattern
                                onPatternChange(newPattern)
                            }
                        },
                        onDragEnd = {
                            currentPosition = null
                            // Notificar que el usuario terminó de dibujar
                            if (currentPatternState.isNotEmpty()) {
                                onDrawingComplete()
                            }
                        }
                    )
                }
        ) {
            // Dibujar líneas entre puntos del patrón
            if (currentPatternState.size > 1) {
                for (i in 0 until currentPatternState.size - 1) {
                    val start = dotPositions[currentPatternState[i]]
                    val end = dotPositions[currentPatternState[i + 1]]
                    drawLine(
                        color = lineColor,
                        start = start,
                        end = end,
                        strokeWidth = dotRadius,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Dibujar línea al dedo actual
            if (currentPatternState.isNotEmpty() && currentPosition != null) {
                val lastDot = dotPositions[currentPatternState.last()]
                drawLine(
                    color = lineColor,
                    start = lastDot,
                    end = currentPosition!!,
                    strokeWidth = dotRadius / 2,
                    cap = StrokeCap.Round
                )
            }

            // Dibujar puntos
            dotPositions.forEachIndexed { index, position ->
                val isSelected = index in currentPatternState
                val radius = if (isSelected) dotRadius * 1.5f else dotRadius
                
                // Círculo exterior
                drawCircle(
                    color = if (isSelected) dotColor else Color.Gray.copy(alpha = 0.5f),
                    radius = radius,
                    center = position
                )
                
                // Círculo interior (para puntos seleccionados)
                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = dotRadius / 3,
                        center = position
                    )
                }
            }
        }
    }
}

private fun findHitDot(position: Offset, dots: List<Offset>, threshold: Float): Int? {
    dots.forEachIndexed { index, dot ->
        val distance = sqrt((position.x - dot.x).pow(2) + (position.y - dot.y).pow(2))
        if (distance < threshold) {
            return index
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupPatternScreen(
    onPatternSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isDrawingComplete by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(isDrawingComplete) {
        if (isDrawingComplete) {
            if (currentPattern.size >= 4) {
                when (step) {
                    1 -> {
                        firstPattern = currentPattern
                        currentPattern = emptyList()
                        step = 2
                    }
                    2 -> {
                        if (currentPattern == firstPattern) {
                            isSaving = true
                            onPatternSet(currentPattern.joinToString(""))
                        } else {
                            isError = true
                            errorMessage = "Los patrones no coinciden"
                            delay(1500)
                            currentPattern = emptyList()
                            isError = false
                            errorMessage = ""
                        }
                    }
                }
            } else if (currentPattern.isNotEmpty()) {
                isError = true
                errorMessage = "Conecta al menos 4 puntos"
                delay(1500)
                currentPattern = emptyList()
                isError = false
                errorMessage = ""
            }
            isDrawingComplete = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configurar Patrón", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancelar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    if (step == 1) Icons.Default.Pattern else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF2962FF),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (step == 1) "Dibuja un patrón" else "Confirma tu patrón",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Conecta al menos 4 puntos",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                PatternGrid(
                    pattern = currentPattern,
                    onPatternChange = { if (!isSaving) currentPattern = it },
                    onDrawingComplete = { isDrawingComplete = true },
                    isError = isError,
                    modifier = Modifier.size(280.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Indicador de paso
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (step >= 1) Color(0xFF2962FF) else Color.Gray,
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (step >= 2) Color(0xFF2962FF) else Color.Gray,
                                CircleShape
                            )
                    )
                }

                if (currentPattern.isNotEmpty() && step == 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { currentPattern = emptyList() }) {
                        Text("Limpiar", color = Color.Gray)
                    }
                }
            }
        }
    }
}
