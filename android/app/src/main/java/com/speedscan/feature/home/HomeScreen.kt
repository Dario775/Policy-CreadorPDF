package com.speedscan.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isPremium: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPdfClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onPremiumClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val transition = updateTransition(targetState = isExpanded, label = "fab_transition")
    
    val rotation by transition.animateFloat(
        label = "rotation",
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) }
    ) { expanded ->
        if (expanded) 135f else 0f
    }
    
    val expansionProgress by transition.animateFloat(
        label = "expansion",
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
            } else {
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
            }
        }
    ) { expanded ->
        if (expanded) 1f else 0f
    }
    
    val fabAlpha by transition.animateFloat(
        label = "alpha",
        transitionSpec = { tween(durationMillis = 300) }
    ) { expanded ->
        if (expanded) 1f else 0f
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onPremiumClick) {
                        val infiniteTransition = rememberInfiniteTransition(label = "premium_pulse")
                        val premiumScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        Icon(
                            imageVector = if (isPremium) Icons.Default.WorkspacePremium else Icons.Default.Diamond,
                            contentDescription = "Premium",
                            tint = if (isPremium) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = if (!isPremium) Modifier.scale(premiumScale) else Modifier
                        )
                    }
                },
                title = {
                    Text(
                        text = "Creador PDF",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                },
                actions = {
                    IconButton(onClick = onSecurityClick) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Seguridad",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historial",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Contenido central (Home Vacío) con animación de flotación
            val infiniteTransition = rememberInfiniteTransition(label = "floating")
            val floatAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floating"
            )
            
            val shadowScale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shadow"
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Sombra flotante
                    Box(
                        modifier = Modifier
                            .padding(top = 180.dp)
                            .size(100.dp, 20.dp)
                            .scale(shadowScale)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.05f))
                    )
                    
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.speedscan.R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .offset(y = floatAnim.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Listo para escanear",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Captura documentos con claridad profesional",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }

            // Overlay oscuro
            if (isExpanded || transition.currentState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f * fabAlpha))
                        .clickable { isExpanded = false }
                )
            }

            // Opciones del FAB
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 39.dp, end = 31.dp),
                contentAlignment = Alignment.Center
            ) {
                // Galería
                val galleryOffsetX = (-80.dp) * expansionProgress
                val galleryOffsetY = (-60.dp) * expansionProgress
                
                if (expansionProgress > 0) {
                   Box(
                       modifier = Modifier
                           .offset(x = galleryOffsetX, y = galleryOffsetY)
                           .alpha(fabAlpha)
                   ) {
                       FabOption(
                           icon = Icons.Default.Image,
                           label = "Galería",
                           onClick = { 
                               isExpanded = false
                               onGalleryClick() 
                           },
                           showLabelLeft = true
                       )
                   }
                }

                // Cámara
                val cameraOffsetY = (-100.dp) * expansionProgress
                
                if (expansionProgress > 0) {
                    Box(
                        modifier = Modifier
                            .offset(y = cameraOffsetY)
                            .alpha(fabAlpha)
                    ) {
                        FabOption(
                            icon = Icons.Default.CameraAlt,
                            label = "Cámara",
                            onClick = { 
                                isExpanded = false
                                onCameraClick() 
                            },
                             showLabelLeft = true
                        )
                    }
                }

                // PDF
                val pdfOffsetX = (-80.dp) * expansionProgress
                val pdfOffsetY = (-140.dp) * expansionProgress

                if (expansionProgress > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = pdfOffsetX, y = pdfOffsetY)
                            .alpha(fabAlpha)
                    ) {
                        FabOption(
                            icon = Icons.Default.PictureAsPdf,
                            label = "PDF",
                            onClick = {
                                isExpanded = false
                                onPdfClick()
                            },
                            showLabelLeft = true
                        )
                    }
                }
            }

            // Botón Principal (+)
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded },
                containerColor = Color(0xFF2962FF),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 24.dp)
                    .size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo Scan",
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
fun FabOption(icon: ImageVector, label: String, onClick: () -> Unit, showLabelLeft: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (showLabelLeft) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = Color.White,
            contentColor = Color(0xFF2962FF),
            shape = CircleShape,
            modifier = Modifier.size(50.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}
