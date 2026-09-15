package com.speedscan.feature.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSettingsScreen(
    currentScanCount: Int,
    isPremium: Boolean,
    productPrice: String = "---",
    isLoading: Boolean = false,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onRestorePurchase: () -> Unit,
    privacyOptionsRequired: Boolean = false,
    onPrivacyOptionsClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Creador PDF Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
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
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono Premium
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPremium) Icons.Filled.WorkspacePremium else Icons.Filled.Diamond,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = if (isPremium) Color(0xFFFFD700) else Color(0xFF6A11CB)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = if (isPremium) "Creador PDF Pro" else "Obtén Creador PDF Pro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isPremium) "Pro activo" else "Desbloquea todo el potencial",
                fontSize = 16.sp,
                color = if (isPremium) Color(0xFF4CAF50) else Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress/Usage indicator for free users
            if (!isPremium) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Uso de cuenta gratuita",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = currentScanCount.toFloat() / 5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF6A11CB),
                            trackColor = Color(0xFFE0E0E0)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "$currentScanCount de 5 escaneos gratuitos utilizados",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Benefits List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumBenefitItem(
                    icon = Icons.Default.Block,
                    title = "Sin Anuncios",
                    description = "Elimina banners e interstitials para siempre."
                )
                PremiumBenefitItem(
                    icon = Icons.Default.AllInclusive,
                    title = "Escaneos Ilimitados",
                    description = "Crea tantos documentos PDF como necesites."
                )
                PremiumBenefitItem(
                    icon = Icons.Default.TextSnippet,
                    title = "Extracción de Texto (OCR)",
                    description = "Convierte imágenes escaneadas en texto editable."
                )
                PremiumBenefitItem(
                    icon = Icons.Default.HighQuality,
                    title = "Calidad HD",
                    description = "Exporta documentos con calidad máxima."
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Pricing Button
            if (!isPremium) {
                val buttonText = when {
                    isLoading -> "Cargando..."
                    productPrice == "---" -> "PRECIO NO DISPONIBLE"
                    else -> "DESBLOQUEAR PRO POR $productPrice"
                }

                Button(
                    onClick = onUpgrade,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (productPrice == "---") Color.Gray else Color(0xFF2962FF)
                    ),
                    enabled = !isLoading && productPrice != "---"
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = buttonText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Text(
                    "Compra única para toda la vida. Sin suscripciones.",
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                TextButton(
                    onClick = onRestorePurchase,
                    enabled = !isLoading,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Restaurar compra")
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    enabled = false,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PRO ACTIVO")
                }
            }

            if (privacyOptionsRequired) {
                TextButton(
                    onClick = onPrivacyOptionsClick,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacidad de anuncios")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun PremiumBenefitItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF6A11CB).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF6A11CB), modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(description, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
fun PremiumFeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
