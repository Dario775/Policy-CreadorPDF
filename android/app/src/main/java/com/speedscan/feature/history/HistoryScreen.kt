package com.speedscan.feature.history

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SortType { DATE, NAME, SIZE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyFiles: List<File>,
    onBackHome: () -> Unit,
    onOpenPdf: (File) -> Unit,
    onSharePdf: (File) -> Unit,
    onDeletePdf: (File) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(SortType.DATE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val filteredAndSortedFiles = remember(historyFiles, searchQuery, sortType) {
        historyFiles
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .let { files ->
                when (sortType) {
                    SortType.DATE -> files.sortedByDescending { it.lastModified() }
                    SortType.NAME -> files.sortedBy { it.name.lowercase() }
                    SortType.SIZE -> files.sortedByDescending { it.length() }
                }
            }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Mis Documentos",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackHome) {
                            Icon(Icons.Default.ArrowBack, "Volver", tint = Color.Black)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Ordenar", tint = Color.Black)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Por fecha") },
                                    onClick = { sortType = SortType.DATE; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                                    trailingIcon = { if (sortType == SortType.DATE) Icon(Icons.Default.Check, null, tint = Color(0xFF2962FF)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Por nombre") },
                                    onClick = { sortType = SortType.NAME; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, null) },
                                    trailingIcon = { if (sortType == SortType.NAME) Icon(Icons.Default.Check, null, tint = Color(0xFF2962FF)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Por tamaño") },
                                    onClick = { sortType = SortType.SIZE; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Storage, null) },
                                    trailingIcon = { if (sortType == SortType.SIZE) Icon(Icons.Default.Check, null, tint = Color(0xFF2962FF)) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar documentos...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2962FF),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            // Stats Bar
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        value = historyFiles.size.toString(),
                        label = "Documentos",
                        icon = Icons.Default.Description
                    )
                    StatItem(
                        value = formatFileSize(historyFiles.sumOf { it.length() }),
                        label = "Total",
                        icon = Icons.Default.Storage
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Document List
            if (filteredAndSortedFiles.isEmpty()) {
                EmptyState(searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAndSortedFiles, key = { it.absolutePath }) { file ->
                        DocumentCard(
                            file = file,
                            onOpen = { onOpenPdf(file) },
                            onShare = { onSharePdf(file) },
                            onRename = { fileToRename = file },
                            onDelete = { fileToDelete = file }
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    fileToRename?.let { file ->
        RenameDialog(
            currentName = file.nameWithoutExtension,
            onDismiss = { fileToRename = null },
            onConfirm = { newName ->
                val newFile = File(file.parent, "$newName.pdf")
                if (file.renameTo(newFile)) {
                    fileToRename = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.Warning, null, tint = Color.Red) },
            title = { Text("Eliminar documento") },
            text = { Text("¿Estás seguro de que deseas eliminar \"${file.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePdf(file)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun StatItem(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF2962FF), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun EmptyState(isSearch: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearch) Icons.Default.SearchOff else Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearch) "No se encontraron resultados" else "No hay documentos aún",
            color = Color.Gray,
            fontSize = 16.sp
        )
        if (!isSearch) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Escanea tu primer documento",
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCard(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val lastModified = dateFormat.format(Date(file.lastModified()))
    val fileSize = formatFileSize(file.length())
    
    // Generar thumbnail del PDF
    val thumbnail = remember(file.absolutePath) {
        generatePdfThumbnail(file)
    }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen, // Tocar la tarjeta abre el PDF
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            null,
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("PDF", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lastModified, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(fileSize, fontSize = 12.sp, color = Color.Gray)
                }
            }

            // Actions
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onShare) { // Botón de compartir
                    Icon(Icons.Default.Share, "Compartir", tint = Color(0xFF2962FF))
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Más opciones", tint = Color.Gray)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            onClick = { onRename(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = Color.Red) },
                            onClick = { onDelete(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null, tint = Color(0xFF2962FF)) },
        title = { Text("Renombrar documento") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nuevo nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank() && newName != currentName
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun generatePdfThumbnail(file: File): Bitmap? {
    return try {
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(parcelFileDescriptor)
        val page = pdfRenderer.openPage(0)
        
        val scale = 2f
        val width = (page.width * scale).toInt()
        val height = (page.height * scale).toInt()
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        page.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
        
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
