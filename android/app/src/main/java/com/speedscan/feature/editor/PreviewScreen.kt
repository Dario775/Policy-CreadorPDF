package com.speedscan.feature.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlin.math.abs

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import com.speedscan.core.domain.model.FilterType
import com.speedscan.core.domain.model.SignatureHorizontal
import com.speedscan.core.domain.model.SignaturePlacement
import com.speedscan.core.domain.model.SignatureVertical

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    imagePaths: List<String>,
    pageFilters: Map<String, FilterType>,
    pageSignatures: Map<String, SignaturePlacement>,
    isPremium: Boolean,
    onBack: () -> Unit,
    onAddPageClick: () -> Unit,
    onGalleryClick: () -> Unit, // New parameter
    onDeletePage: (Int) -> Unit,
    onFilterChange: (String, FilterType) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onExtractText: suspend (String) -> String,
    onAddSignature: (String) -> Unit,
    onUpdateSignature: (String, SignaturePlacement) -> Unit,
    onRemoveSignature: (String) -> Unit,
    onGeneratePdf: (String, String, com.speedscan.core.utils.PdfPageSize, Int) -> Unit,
    onUpgradeClick: () -> Unit
) {
    // ... existing state variables ...
    var ocrResult by remember { mutableStateOf<String?>(null) }
    var selectedFullScreenImage by remember { mutableStateOf<String?>(null) }
    var signaturePageToEdit by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragItemHeight by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()
    
    if (showSaveDialog) {
        SavePdfDialog(
            isPremium = isPremium,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name, category, pageSize, quality ->
                showSaveDialog = false
                onGeneratePdf(name, category, pageSize, quality)
            }
        )
    }

    val context = LocalContext.current

    signaturePageToEdit?.let { pagePath ->
        pageSignatures[pagePath]?.let { signature ->
            SignaturePlacementDialog(
                signature = signature,
                onDismiss = { signaturePageToEdit = null },
                onRemove = {
                    signaturePageToEdit = null
                    onRemoveSignature(pagePath)
                },
                onConfirm = { updatedSignature ->
                    signaturePageToEdit = null
                    onUpdateSignature(pagePath, updatedSignature)
                }
            )
        }
    }

    if (ocrResult != null) {
        AlertDialog(
            onDismissRequest = { ocrResult = null },
            title = { Text("Texto Extraído") },
            text = { 
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(ocrResult!!)
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Creador PDF OCR", ocrResult)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                }) {
                    Text("COPIAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrResult = null }) {
                    Text("CERRAR")
                }
            }
        )
    }

    if (isExtracting) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analizando texto...")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Revisar Documento (${imagePaths.size})", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { onDeletePage(-1) }) {
                        Text("CANCELAR", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("FINALIZAR Y COMPARTIR", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp, start = 16.dp, end = 16.dp), // Add bottom padding for FAB
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(imagePaths, key = { _, path -> path }) { index, path ->
                        // ... existing item code ...
                        var isVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { isVisible = true }
                        
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(300, delayMillis = index * 50)) + 
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(300, delayMillis = index * 50))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = if (draggedIndex == index) dragOffsetY else 0f
                                        scaleX = if (draggedIndex == index) 1.02f else 1f
                                        scaleY = if (draggedIndex == index) 1.02f else 1f
                                        shadowElevation = if (draggedIndex == index) 16f else 0f
                                    }
                                    .pointerInput(index, imagePaths.size, dragItemHeight) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggedIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val current = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                                dragOffsetY += dragAmount.y

                                                if (abs(dragOffsetY) > dragItemHeight * 0.55f) {
                                                    val direction = if (dragOffsetY > 0) 1 else -1
                                                    val target = (current + direction).coerceIn(0, imagePaths.lastIndex)
                                                    if (target != current) {
                                                        onMovePage(current, target)
                                                        draggedIndex = target
                                                        dragOffsetY = 0f
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                PageItem(
                                    path = path,
                                    pageNumber = index + 1,
                                    currentFilter = pageFilters[path] ?: FilterType.ORIGINAL,
                                    hasSignature = pageSignatures.containsKey(path),
                                    isFirst = index == 0,
                                    isLast = index == imagePaths.size - 1,
                                    onItemHeightChanged = { dragItemHeight = it.toFloat().coerceAtLeast(1f) },
                                    onDelete = { onDeletePage(index) },
                                    onFilterChange = { onFilterChange(path, it) },
                                    onMove = { direction -> onMovePage(index, index + direction) },
                                    isPremium = isPremium,
                                    onOcrClick = {
                                        scope.launch {
                                            isExtracting = true
                                            ocrResult = onExtractText(path)
                                            isExtracting = false
                                        }
                                    },
                                    onUpgradeClick = onUpgradeClick,
                                    onSignatureClick = { onAddSignature(path) },
                                    onEditSignatureClick = { signaturePageToEdit = path },
                                    onClick = { selectedFullScreenImage = path }
                                )
                            }
                        }
                    }
                }
            }

            // Expanding FAB Logic
            var isExpanded by remember { mutableStateOf(false) }
            val transition = updateTransition(targetState = isExpanded, label = "fab_transition")
            
            val rotation by transition.animateFloat(
                label = "rotation",
                transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            ) { expanded -> if (expanded) 135f else 0f }
            
            val expansionProgress by transition.animateFloat(
                label = "expansion",
                transitionSpec = {
                    if (targetState) spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
                    else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
                }
            ) { expanded -> if (expanded) 1f else 0f }
            
            val fabAlpha by transition.animateFloat(
                label = "alpha",
                transitionSpec = { tween(durationMillis = 300) }
            ) { expanded -> if (expanded) 1f else 0f }

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
                    .padding(bottom = 24.dp, end = 24.dp), // Adjusted padding
                contentAlignment = Alignment.Center
            ) {
                // Galería
                if (expansionProgress > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-80.dp) * expansionProgress, y = (-60.dp) * expansionProgress)
                            .alpha(fabAlpha)
                    ) {
                        PreviewFabOption(
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
                if (expansionProgress > 0) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-100.dp) * expansionProgress)
                            .alpha(fabAlpha)
                    ) {
                        PreviewFabOption(
                            icon = Icons.Default.CameraAlt,
                            label = "Cámara",
                            onClick = { 
                                isExpanded = false
                                onAddPageClick() 
                            },
                            showLabelLeft = true
                        )
                    }
                }

                // Botón Principal (+)
                FloatingActionButton(
                    onClick = { isExpanded = !isExpanded },
                    containerColor = Color(0xFF2962FF),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar",
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }
        }
    }

    // Full Screen Preview Dialog
    selectedFullScreenImage?.let { path ->
        Dialog(
            onDismissRequest = { selectedFullScreenImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val filter = pageFilters[path] ?: FilterType.ORIGINAL
                    val colorFilter = when (filter) {
                        FilterType.GRAYSCALE -> {
                            val m = ColorMatrix()
                            m.setToSaturation(0f)
                            ColorFilter.colorMatrix(m)
                        }
                        FilterType.BLACK_WHITE -> {
                            val m = floatArrayOf(
                                2f, 0f, 0f, 0f, -120f,
                                0f, 2f, 0f, 0f, -120f,
                                0f, 0f, 2f, 0f, -120f,
                                0f, 0f, 0f, 1f, 0f
                            )
                            ColorFilter.colorMatrix(ColorMatrix(m))
                        }
                        FilterType.DOCUMENT -> {
                            val m = floatArrayOf(
                                1.5f, 0f, 0f, 0f, 10f,
                                0f, 1.5f, 0f, 0f, 10f,
                                0f, 0f, 1.5f, 0f, 10f,
                                0f, 0f, 0f, 1f, 0f
                            )
                            ColorFilter.colorMatrix(ColorMatrix(m))
                        }
                        else -> null
                    }

                    ZoomablePageImage(
                        path = path,
                        colorFilter = colorFilter,
                        onClose = { selectedFullScreenImage = null }
                    )

                    // Top Bar for Close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { selectedFullScreenImage = null },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    // Bottom info
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(32.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "PELLIZCA PARA ACERCAR",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomablePageImage(
    path: String,
    colorFilter: ColorFilter?,
    onClose: () -> Unit
) {
    var scale by remember(path) { mutableStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val minScale = 1f
    val maxScale = 5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(path) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset(
                                x = (size.width / 2f - tapOffset.x) * (scale - 1f),
                                y = (size.height / 2f - tapOffset.y) * (scale - 1f)
                            )
                        }
                    },
                    onTap = {
                        if (scale == 1f) onClose()
                    }
                )
            }
            .pointerInput(path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                    scale = newScale
                    offset = if (newScale == minScale) {
                        Offset.Zero
                    } else {
                        val maxX = size.width * (newScale - 1f) / 2f
                        val maxY = size.height * (newScale - 1f) / 2f
                        Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(File(path)),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
    }
}

@Composable
fun PageItem(
    path: String, 
    pageNumber: Int, 
    currentFilter: FilterType,
    hasSignature: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onItemHeightChanged: (Int) -> Unit,
    onDelete: () -> Unit,
    onFilterChange: (FilterType) -> Unit,
    onMove: (Int) -> Unit,
    isPremium: Boolean,
    onOcrClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onSignatureClick: () -> Unit,
    onEditSignatureClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onItemHeightChanged(it.size.height) }
    ) {
        Column {
            Box {
                val colorFilter = when (currentFilter) {
                    FilterType.GRAYSCALE -> {
                        val m = ColorMatrix()
                        m.setToSaturation(0f)
                        ColorFilter.colorMatrix(m)
                    }
                    FilterType.BLACK_WHITE -> {
                        val m = floatArrayOf(
                            2f, 0f, 0f, 0f, -120f,
                            0f, 2f, 0f, 0f, -120f,
                            0f, 0f, 2f, 0f, -120f,
                            0f, 0f, 0f, 1f, 0f
                        )
                        ColorFilter.colorMatrix(ColorMatrix(m))
                    }
                    FilterType.DOCUMENT -> {
                        val m = floatArrayOf(
                            1.5f, 0f, 0f, 0f, 10f,
                            0f, 1.5f, 0f, 0f, 10f,
                            0f, 0f, 1.5f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                        ColorFilter.colorMatrix(ColorMatrix(m))
                    }
                    else -> null
                }

                Image(
                    painter = rememberAsyncImagePainter(File(path)),
                    contentDescription = "Página $pageNumber",
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clickable { onClick() }
                )
                
                // Overlay de orden (Flechas)
                Row(
                   modifier = Modifier
                       .align(Alignment.BottomCenter)
                       .fillMaxWidth()
                       .background(Color.Black.copy(alpha = 0.4f))
                       .padding(vertical = 4.dp),
                   horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (!isFirst) {
                        IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.ArrowBack, "", tint = Color.White)
                        }
                    }
                    if (!isLast) {
                        IconButton(onClick = { onMove(1) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.NavigateNext, "", tint = Color.White)
                        }
                    }
                }

                // Número de página
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$pageNumber",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón Eliminar
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Botón OCR (Nuevo)
                IconButton(
                    onClick = if (isPremium) onOcrClick else onUpgradeClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(28.dp)
                ) {
                    Box {
                        Icon(
                            Icons.Filled.TextFields,
                            contentDescription = "Extraer Texto",
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(16.dp)
                        )
                        if (!isPremium) {
                            Icon(
                                Icons.Filled.Diamond,
                                null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                // Botón Firma Digital
                IconButton(
                    onClick = if (hasSignature) onEditSignatureClick else onSignatureClick,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(
                            if (hasSignature) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.8f),
                            CircleShape
                        )
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Draw,
                        contentDescription = "Firma Digital",
                        tint = if (hasSignature) Color.White else Color(0xFF9C27B0),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Selector de Filtros
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterIconButton(
                    icon = Icons.Filled.Close, 
                    isSelected = currentFilter == FilterType.ORIGINAL,
                    onClick = { onFilterChange(FilterType.ORIGINAL) }
                )
                FilterIconButton(
                    icon = Icons.Filled.Check, 
                    isSelected = currentFilter == FilterType.GRAYSCALE,
                    onClick = { onFilterChange(FilterType.GRAYSCALE) },
                    label = "GRIS"
                )
                FilterIconButton(
                    icon = Icons.Filled.Check, 
                    isSelected = currentFilter == FilterType.BLACK_WHITE,
                    onClick = { onFilterChange(FilterType.BLACK_WHITE) },
                    label = "B&N"
                )
                FilterIconButton(
                    icon = Icons.Filled.Check,
                    isSelected = currentFilter == FilterType.DOCUMENT,
                    onClick = { onFilterChange(FilterType.DOCUMENT) },
                    label = "DOC"
                )
            }
        }
    }
}

@Composable
fun FilterIconButton(
    icon: ImageVector, 
    isSelected: Boolean, 
    onClick: () -> Unit,
    label: String? = null
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1f, label = "scale")
    val alpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0.6f, label = "alpha")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .scale(scale)
            .alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF2962FF) else Color.LightGray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (label != null) {
                Text(label, color = if (isSelected) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(icon, contentDescription = null, tint = if (isSelected) Color.White else Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SignaturePlacementDialog(
    signature: SignaturePlacement,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onConfirm: (SignaturePlacement) -> Unit
) {
    var horizontal by remember(signature) { mutableStateOf(signature.horizontal) }
    var vertical by remember(signature) { mutableStateOf(signature.vertical) }
    var widthRatio by remember(signature) { mutableStateOf(signature.widthRatio.coerceIn(0.2f, 0.65f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Draw, null, tint = Color(0xFF2962FF)) },
        title = { Text("Ajustar firma", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Posición horizontal", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignatureOptionChip("Izq.", horizontal == SignatureHorizontal.LEFT) { horizontal = SignatureHorizontal.LEFT }
                    SignatureOptionChip("Centro", horizontal == SignatureHorizontal.CENTER) { horizontal = SignatureHorizontal.CENTER }
                    SignatureOptionChip("Der.", horizontal == SignatureHorizontal.RIGHT) { horizontal = SignatureHorizontal.RIGHT }
                }

                Text("Posición vertical", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignatureOptionChip("Arriba", vertical == SignatureVertical.TOP) { vertical = SignatureVertical.TOP }
                    SignatureOptionChip("Medio", vertical == SignatureVertical.MIDDLE) { vertical = SignatureVertical.MIDDLE }
                    SignatureOptionChip("Abajo", vertical == SignatureVertical.BOTTOM) { vertical = SignatureVertical.BOTTOM }
                }

                Text("Tamaño", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = widthRatio,
                        onValueChange = { widthRatio = it },
                        valueRange = 0.2f..0.65f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${(widthRatio * 100).toInt()}%", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        signature.copy(
                            horizontal = horizontal,
                            vertical = vertical,
                            widthRatio = widthRatio
                        )
                    )
                }
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text("Quitar", color = Color.Red)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePdfDialog(
    isPremium: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, com.speedscan.core.utils.PdfPageSize, Int) -> Unit
) {
    var name by remember { mutableStateOf("Escaneo_${System.currentTimeMillis() / 1000}") }
    var selectedCategory by remember { mutableStateOf("General") }
    var quality by remember { mutableStateOf(80f) }
    var selectedPageSize by remember { mutableStateOf(com.speedscan.core.utils.PdfPageSize.A4) }
    val maxQuality = if (isPremium) 100f else 80f
    
    val categories = listOf("General", "Personal", "Trabajo", "Estudios", "Facturas")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar PDF", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del archivo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Categoría", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                Text("Ajustes de Calidad", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = quality.coerceAtMost(maxQuality),
                        onValueChange = { quality = it.coerceAtMost(maxQuality) },
                        valueRange = 30f..maxQuality,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF2962FF), activeTrackColor = Color(0xFF2962FF))
                    )
                    Text(
                        text = "${quality.coerceAtMost(maxQuality).toInt()}%",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    if (isPremium) "Calidad baja = archivos más pequeños." else "Calidad máxima disponible en Pro.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Tamaño de Página", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.speedscan.core.utils.PdfPageSize.values().forEach { size ->
                        FilterChip(
                            selected = selectedPageSize == size,
                            onClick = { selectedPageSize = size },
                            label = { Text(size.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedCategory, selectedPageSize, quality.coerceAtMost(maxQuality).toInt()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF))
            ) {
                Text("GUARDAR Y COMPARTIR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}

@Composable
fun PreviewFabOption(icon: ImageVector, label: String, onClick: () -> Unit, showLabelLeft: Boolean = false) {
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
