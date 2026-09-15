package com.speedscan.feature.security

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onBiometricClick: () -> Unit,
    showBiometric: Boolean = true
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            isVerifying = true
            if (onVerifyPin(pin)) {
                onUnlock()
            } else {
                isError = true
                errorMessage = "PIN incorrecto"
                delay(1000)
                pin = ""
                isError = false
                errorMessage = ""
            }
            isVerifying = false
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
            // Logo/Icono
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
                text = "Ingresa tu PIN para continuar",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Indicadores de PIN
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> Color.Red
                                    index < pin.length -> Color(0xFF2962FF)
                                    else -> Color.Gray.copy(alpha = 0.3f)
                                }
                            )
                            .border(
                                width = 2.dp,
                                color = if (isError) Color.Red else Color.Gray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }
            }

            // Mensaje de error
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

            // Teclado numérico
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (showBiometric) "bio" else "", "0", "del")
                ).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        row.forEach { key ->
                            when (key) {
                                "bio" -> {
                                    IconButton(
                                        onClick = onBiometricClick,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(Color(0xFF2962FF).copy(alpha = 0.2f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Fingerprint,
                                            contentDescription = "Biométrico",
                                            tint = Color(0xFF2962FF),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                "del" -> {
                                    IconButton(
                                        onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Backspace,
                                            contentDescription = "Borrar",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                "" -> {
                                    Spacer(modifier = Modifier.size(72.dp))
                                }
                                else -> {
                                    NumberKey(
                                        number = key,
                                        onClick = {
                                            if (pin.length < 4 && !isVerifying) {
                                                pin += key
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (isVerifying) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = Color(0xFF2962FF),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun NumberKey(number: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupPinScreen(
    onPinSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1 = enter, 2 = confirm
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(currentPin) {
        if (currentPin.length == 4) {
            when (step) {
                1 -> {
                    firstPin = currentPin
                    currentPin = ""
                    step = 2
                }
                2 -> {
                    if (currentPin == firstPin) {
                        isSaving = true
                        onPinSet(currentPin)
                    } else {
                        isError = true
                        errorMessage = "Los PINs no coinciden"
                        delay(1500)
                        currentPin = ""
                        firstPin = ""
                        step = 1
                        isError = false
                        errorMessage = ""
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configurar PIN", fontWeight = FontWeight.Bold) },
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
                    if (step == 1) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF2962FF),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (step == 1) "Crea un PIN de 4 dígitos" else "Confirma tu PIN",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Indicadores de PIN
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isError -> Color.Red
                                        index < currentPin.length -> Color(0xFF2962FF)
                                        else -> Color.Gray.copy(alpha = 0.3f)
                                    }
                                )
                                .border(2.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Teclado
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "del")
                    ).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            row.forEach { key ->
                                when (key) {
                                    "del" -> {
                                        IconButton(
                                            onClick = { if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1) },
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Icon(Icons.Default.Backspace, "Borrar", tint = Color.White)
                                        }
                                    }
                                    "" -> Spacer(modifier = Modifier.size(72.dp))
                                    else -> NumberKey(number = key, onClick = {
                                        if (currentPin.length < 4 && !isSaving) currentPin += key
                                    })
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Indicador de paso
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (step >= 1) Color(0xFF2962FF) else Color.Gray)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (step >= 2) Color(0xFF2962FF) else Color.Gray)
                    )
                }
            }
        }
    }
}
