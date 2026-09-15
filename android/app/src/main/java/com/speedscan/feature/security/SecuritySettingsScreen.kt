package com.speedscan.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedscan.core.security.LockType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    currentLockType: LockType,
    isBiometricAvailable: Boolean,
    onBack: () -> Unit,
    onSelectLockType: (LockType) -> Unit,
    onSetupPin: () -> Unit,
    onSetupPattern: () -> Unit,
    onSetupBiometric: () -> Unit,
    onRemoveLock: () -> Unit
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color.Red) },
            title = { Text("Desactivar bloqueo") },
            text = { Text("¿Estás seguro de que deseas desactivar el bloqueo? Tus documentos ya no estarán protegidos.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirm = false
                        onRemoveLock()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Desactivar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Seguridad", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
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
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        tint = if (currentLockType != LockType.NONE) Color(0xFF4CAF50) else Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = when (currentLockType) {
                                LockType.NONE -> "Sin Protección"
                                LockType.PIN -> "Protegido con PIN"
                                LockType.PATTERN -> "Protegido con Patrón"
                                LockType.BIOMETRIC -> "Protegido con Biometría"
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentLockType != LockType.NONE) 
                                "Tus documentos están seguros" 
                                else "Selecciona un método de bloqueo",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Opciones de bloqueo
            Text(
                text = "MÉTODO DE BLOQUEO",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    // Sin contraseña
                    LockTypeOption(
                        icon = Icons.Default.LockOpen,
                        title = "Sin contraseña",
                        subtitle = "Acceso directo a la app",
                        isSelected = currentLockType == LockType.NONE,
                        onClick = {
                            if (currentLockType != LockType.NONE) {
                                showRemoveConfirm = true
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // PIN
                    LockTypeOption(
                        icon = Icons.Default.Pin,
                        title = "PIN",
                        subtitle = "4 dígitos numéricos",
                        isSelected = currentLockType == LockType.PIN,
                        onClick = onSetupPin
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Patrón
                    LockTypeOption(
                        icon = Icons.Default.Pattern,
                        title = "Patrón",
                        subtitle = "Dibuja un patrón para desbloquear",
                        isSelected = currentLockType == LockType.PATTERN,
                        onClick = onSetupPattern
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Biométrico
                    LockTypeOption(
                        icon = Icons.Default.Fingerprint,
                        title = "Huella / Cara",
                        subtitle = if (isBiometricAvailable) 
                            "Usa biometría para desbloquear" 
                            else "No disponible en este dispositivo",
                        isSelected = currentLockType == LockType.BIOMETRIC,
                        enabled = isBiometricAvailable,
                        onClick = if (isBiometricAvailable) onSetupBiometric else { {} }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "El bloqueo se solicita cada vez que abres la app. Asegúrate de recordar tu contraseña.",
                        color = Color(0xFF5D4037),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LockTypeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) Color(0xFF2962FF).copy(alpha = 0.1f) 
                    else Color.Gray.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = when {
                    !enabled -> Color.Gray.copy(alpha = 0.5f)
                    isSelected -> Color(0xFF2962FF)
                    else -> Color.Gray
                },
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.Black else Color.Gray
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
