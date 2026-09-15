package com.speedscan.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BiometricLockScreen(
    onBiometricClick: () -> Unit,
    onUsePinClick: () -> Unit,
    showPinOption: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Icono de candado (app bloqueada)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Creador PDF",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Desbloquea para continuar",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Botón principal de desbloqueo
            Button(
                onClick = onBiometricClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2962FF)
                )
            ) {
                Text(
                    text = "Desbloquear con huella",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showPinOption) {
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onUsePinClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Usar PIN",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
